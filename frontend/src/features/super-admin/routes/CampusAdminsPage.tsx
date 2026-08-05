import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, Plus, Edit3 } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Input, Textarea,
} from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar } from '@components/data';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { campusApi } from '@lib/api/services/campus.api';
import { authKeys, campusKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { UserResponse } from '@/types/auth.types';
import type { CampusResponse } from '@/types/campus.types';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useUrlPagination } from '@hooks/useUrlPagination';
import {
  createAdminSchema, adminEditSchema, statusChangeFallback,
  type CreateAdminValues, type AdminEditValues,
} from '../schemas/platform.schemas';

/**
 * Super Admin Campus Admins Console.
 *
 * Enforces rule: ONE active Campus Admin per campus.
 * To assign or create a second Campus Admin for a campus, the existing Campus
 * Admin account must be suspended / deactivated first.
 */
export default function CampusAdminsPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { request: pageRequest, setPage } = useUrlPagination(20);
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<UserResponse | null>(null);
  const [suspending, setSuspending] = useState<UserResponse | null>(null);
  const [suspendReason, setSuspendReason] = useState('');
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const listQuery = { ...pageRequest, role: 'CAMPUS_ADMIN' as const };

  const admins = useQuery({
    queryKey: authKeys.userList(listQuery),
    queryFn: () => authApi.listUsers(listQuery),
  });

  const campuses = useQuery({
    queryKey: campusKeys.list(true),
    queryFn: () => campusApi.list(true),
  });

  const campusMap = useMemo(() => {
    const map = new Map<number, CampusResponse>();
    (campuses.data ?? []).forEach((c) => map.set(c.id, c));
    return map;
  }, [campuses.data]);

  const visible = useMemo(() => {
    const items = admins.data?.items ?? [];
    const term = debounced.trim().toLowerCase();
    if (!term) return items;
    return items.filter(
      (user) =>
        user.name.toLowerCase().includes(term) ||
        user.email.toLowerCase().includes(term) ||
        (user.campusId && campusMap.get(user.campusId)?.name.toLowerCase().includes(term)),
    );
  }, [admins.data, debounced, campusMap]);

  const createForm = useForm<CreateAdminValues>({
    resolver: zodResolver(createAdminSchema),
    defaultValues: { campusId: 0, name: '', email: '', temporaryPassword: '' },
  });
  const applyCreateErrors = useApiFormErrors<CreateAdminValues>(createForm.setError, setFormErrors);

  const editForm = useForm<AdminEditValues>({
    resolver: zodResolver(adminEditSchema),
    values: {
      name: editing?.name ?? '',
      phone: editing?.phone ?? '',
    },
  });
  const applyEditErrors = useApiFormErrors<AdminEditValues>(editForm.setError, setFormErrors);

  const selectedCampusId = createForm.watch('campusId');

  const activeAdminForSelectedCampus = useMemo(() => {
    if (!selectedCampusId) return null;
    return (admins.data?.items ?? []).find(
      (user) => user.campusId === Number(selectedCampusId) && user.active,
    );
  }, [selectedCampusId, admins.data]);

  const closeCreate = () => {
    setCreating(false);
    setFormErrors([]);
    createForm.reset();
  };

  const closeEdit = () => {
    setEditing(null);
    setFormErrors([]);
    editForm.reset();
  };

  const createAdmin = useMutation({
    mutationFn: async (values: CreateAdminValues) => {
      const activeExisting = (admins.data?.items ?? []).find(
        (u) => u.campusId === Number(values.campusId) && u.active,
      );
      if (activeExisting) {
        const campusName = campusMap.get(Number(values.campusId))?.name ?? 'This campus';
        throw new Error(
          `${campusName} already has an active Campus Admin (${activeExisting.name}). You must suspend the existing Campus Admin account before creating a new one.`,
        );
      }

      const admin = await authApi.createUser({
        email: values.email,
        name: values.name,
        role: 'CAMPUS_ADMIN',
        campusId: Number(values.campusId),
        temporaryPassword: values.temporaryPassword,
      });

      const targetCampus = campusMap.get(Number(values.campusId));
      if (targetCampus) {
        await campusApi.update(targetCampus.id, {
          name: targetCampus.name,
          address: targetCampus.address,
          contactEmail: targetCampus.contactEmail,
          contactPhone: targetCampus.contactPhone,
          logoS3Key: targetCampus.logoS3Key,
          adminUserId: admin.id,
        });
      }

      return admin;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.all });
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success('Campus admin created', 'They must change the temporary password at first sign-in.');
      closeCreate();
    },
    onError: (error) => {
      setFormErrors([]);
      applyCreateErrors(error);
    },
  });

  const updateAdmin = useMutation({
    mutationFn: (values: AdminEditValues) => {
      return authApi.updateUser(editing!.id, {
        name: values.name,
        phone: values.phone || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.all });
      toast.success('Campus admin details updated');
      closeEdit();
    },
    onError: (error) => {
      setFormErrors([]);
      applyEditErrors(error);
    },
  });

  const changeStatus = useMutation({
    mutationFn: () => {
      if (!suspending) throw new Error('No user selected');
      const targetActive = !suspending.active;
      if (targetActive && suspending.campusId) {
        const existingActive = (admins.data?.items ?? []).find(
          (u) => u.campusId === suspending.campusId && u.active && u.id !== suspending.id,
        );
        if (existingActive) {
          const campusName = campusMap.get(suspending.campusId)?.name ?? 'This campus';
          throw new Error(
            `Cannot activate: ${campusName} already has an active Campus Admin (${existingActive.name}). Please suspend ${existingActive.name} first.`,
          );
        }
      }

      return authApi.changeUserStatus(suspending.id, {
        active: targetActive,
        reason: suspendReason,
        changedBy: identity?.userId as number,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.all });
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success(suspending?.active ? 'Campus admin suspended' : 'Campus admin activated');
      setSuspending(null);
      setSuspendReason('');
    },
    onError: (error) => toast.fromError(error, 'Could not update admin status.'),
  });

  const columns: ColumnDef<UserResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Admin Name',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md font-medium truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption truncate text-[var(--ink-500)]">{info.row.original.email}</p>
        </div>
      ),
    },
    {
      id: 'campus',
      header: 'Assigned Campus',
      cell: (info) => {
        const cid = info.row.original.campusId;
        const campus = cid ? campusMap.get(cid) : null;
        if (!campus) return <span className="text-[var(--ink-400)]">—</span>;
        return (
          <div className="min-w-0">
            <p className="text-body-md truncate text-[var(--ink-800)]">{campus.name}</p>
            <p className="text-caption text-mono text-[var(--ink-500)]">{campus.code}</p>
          </div>
        );
      },
    },
    {
      id: 'lastLoginAt',
      header: 'Last sign-in',
      cell: (info) =>
        info.row.original.lastLoginAt ? formatDateTime(info.row.original.lastLoginAt) : 'Never',
    },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => (
        <Badge>
          {info.row.original.active ? 'Active' : 'Suspended'}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => {
        const user = info.row.original;
        return (
          <div className="flex justify-end gap-[var(--sp-2)]">
            <Button size="sm" variant="ghost" onClick={() => setEditing(user)}>
              <Edit3 className="size-3.5" aria-hidden /> Edit
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSuspending(user)}
              className={user.active ? 'text-red-600 hover:text-red-700' : 'text-emerald-600'}
            >
              {user.active ? 'Suspend' : 'Activate'}
            </Button>
          </div>
        );
      },
    },
  ];

  if (admins.isError) return <ErrorState error={admins.error} onRetry={() => void admins.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Campus admins"
        description="Manage all Campus Admin accounts on the platform. Rule: One active Campus Admin per campus."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden /> Add Campus Admin
          </Button>
        }
      />

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search name, email, or campus..."
        resultCount={visible.length}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={visible}
          loading={admins.isPending}
          {...(admins.data ? { page: admins.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No campus admins"
          emptyDescription="Create a campus admin to assign management of a campus."
        />
      </div>

      {/* CREATE ADMIN DIALOG */}
      <Dialog open={creating} onOpenChange={(next) => { if (!next) closeCreate(); }}>
        <DialogContent>
          <form noValidate onSubmit={createForm.handleSubmit((values) => createAdmin.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Add a Campus Admin</DialogTitle>
              <DialogDescription>
                Assign a Campus Admin account to manage a campus. Each campus may have only ONE active Campus Admin.
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Target Campus" required error={createForm.formState.errors.campusId?.message}>
                {({ id }) => (
                  <select
                    id={id}
                    className="flex h-10 w-full rounded-[var(--r-sm)] border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-body-md text-[var(--ink-900)] focus:outline-none focus:ring-2 focus:ring-[var(--brand-500)]"
                    value={createForm.watch('campusId') || ''}
                    onChange={(e) => createForm.setValue('campusId', Number(e.target.value), { shouldValidate: true })}
                  >
                    <option value="">Select a campus...</option>
                    {(campuses.data ?? []).map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.code})
                      </option>
                    ))}
                  </select>
                )}
              </Field>

              {activeAdminForSelectedCampus && (
                <div className="rounded-[var(--r-md)] border border-amber-300 bg-amber-50 p-[var(--sp-3)] text-amber-900 text-body-sm flex gap-[var(--sp-2)] items-start">
                  <AlertTriangle className="size-5 shrink-0 text-amber-600 mt-0.5" />
                  <div>
                    <p className="font-semibold">One Campus, One Admin Rule</p>
                    <p className="mt-1">
                      <strong>{campusMap.get(Number(selectedCampusId))?.name}</strong> already has an active Campus Admin (
                      <strong>{activeAdminForSelectedCampus.name}</strong> - {activeAdminForSelectedCampus.email}).
                    </p>
                    <p className="mt-1 text-amber-800 font-medium">
                      You must suspend {activeAdminForSelectedCampus.name}&apos;s account before creating a new active admin for this campus.
                    </p>
                  </div>
                </div>
              )}

              <Field label="Full name" required error={createForm.formState.errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    aria-describedby={describedBy}
                    invalid={Boolean(createForm.formState.errors.name)}
                    {...createForm.register('name')}
                  />
                )}
              </Field>

              <Field label="Email address" required error={createForm.formState.errors.email?.message}>
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    type="email"
                    aria-describedby={describedBy}
                    invalid={Boolean(createForm.formState.errors.email)}
                    {...createForm.register('email')}
                  />
                )}
              </Field>

              <Field
                label="Temporary password"
                required
                error={createForm.formState.errors.temporaryPassword?.message}
                hint="At least 8 characters with upper, lower, and digit. Must change at first login."
              >
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    type="text"
                    autoComplete="off"
                    aria-describedby={describedBy}
                    invalid={Boolean(createForm.formState.errors.temporaryPassword)}
                    {...createForm.register('temporaryPassword')}
                  />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={closeCreate}>
                Cancel
              </Button>
              <Button
                type="submit"
                loading={createAdmin.isPending}
                disabled={Boolean(activeAdminForSelectedCampus)}
              >
                Create Admin
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* EDIT ADMIN DIALOG */}
      <Dialog open={editing !== null} onOpenChange={(next) => { if (!next) closeEdit(); }}>
        <DialogContent>
          <form noValidate onSubmit={editForm.handleSubmit((values) => updateAdmin.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Edit Admin: {editing?.name}</DialogTitle>
              <DialogDescription>
                Update details for this Campus Admin ({editing?.email}).
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Full name" required error={editForm.formState.errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    aria-describedby={describedBy}
                    invalid={Boolean(editForm.formState.errors.name)}
                    {...editForm.register('name')}
                  />
                )}
              </Field>

              <Field label="Contact phone" error={editForm.formState.errors.phone?.message} hint="10 digits only">
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    type="tel"
                    placeholder="9876543210"
                    maxLength={10}
                    aria-describedby={describedBy}
                    invalid={Boolean(editForm.formState.errors.phone)}
                    {...editForm.register('phone', {
                      onChange: (e) => {
                        const digits = e.target.value.replace(/\D/g, '').slice(0, 10);
                        editForm.setValue('phone', digits, { shouldValidate: true });
                      },
                    })}
                  />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={closeEdit}>
                Cancel
              </Button>
              <Button type="submit" loading={updateAdmin.isPending}>
                Save Changes
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* SUSPEND / REINSTATE DIALOG */}
      <ConfirmDialog
        open={suspending !== null}
        onOpenChange={(next) => { if (!next) { setSuspending(null); setSuspendReason(''); } }}
        title={suspending?.active ? `Suspend Campus Admin ${suspending.name}?` : `Activate Campus Admin ${suspending?.name ?? ''}?`}
        description={
          suspending?.active
            ? 'Deactivating stops this admin from signing in or managing the campus. You can then assign a new Campus Admin for this campus.'
            : 'Reactivating restores access. Note: A campus can have only ONE active Campus Admin.'
        }
        confirmLabel={suspending?.active ? 'Suspend' : 'Activate'}
        destructive={suspending?.active ?? false}
        loading={changeStatus.isPending}
        confirmDisabled={suspendReason.trim().length < statusChangeFallback.minReason}
        onConfirm={() => changeStatus.mutate()}
      >
        <Field label="Reason" required hint="Recorded in security audit logs.">
          {({ id }) => (
            <Textarea
              id={id}
              rows={3}
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
              placeholder="e.g. Offboarding, replacement admin assigned"
            />
          )}
        </Field>
      </ConfirmDialog>
    </div>
  );
}
