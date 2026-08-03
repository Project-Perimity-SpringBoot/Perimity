import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { Building2, Plus, UserPlus } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Input, Textarea,
} from '@ui/index';
import { DataTable, PageHeader, StatCard } from '@components/data';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { campusApi } from '@lib/api/services/campus.api';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys, campusKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { CampusResponse } from '@/types/campus.types';
import { useApiFormErrors } from '@features/auth/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import {
  campusSchema, firstAdminSchema, statusChangeFallback,
  type CampusValues, type FirstAdminValues,
} from '../schemas/platform.schemas';

/**
 * Batch 6 screens 2 and 3 — campuses, create, edit, suspend, and assigning the
 * first Campus Admin.
 *
 * This is the heart of the Super Admin console and, per blocker B5, close to
 * all of it: a Super Admin has campusId = null, so every campus-scoped handler
 * throws. Campus CRUD, status, stats, gates, config and POST /users are the
 * reachable set, and this screen is built from exactly that.
 *
 * SUSPEND, NEVER DELETE. A suspended campus keeps every record readable; there
 * is no delete endpoint and there should not be.
 */
export default function CampusesPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<CampusResponse | null>(null);
  const [suspending, setSuspending] = useState<CampusResponse | null>(null);
  const [assigningAdmin, setAssigningAdmin] = useState<CampusResponse | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [suspendReason, setSuspendReason] = useState('');

  const campuses = useQuery({
    queryKey: campusKeys.list(true),
    queryFn: () => campusApi.list(true),
  });

  const stats = useQuery({
    queryKey: campusKeys.stats(),
    queryFn: () => campusApi.stats(),
  });

  const open = creating || editing !== null;

  const campusForm = useForm<CampusValues>({
    resolver: zodResolver(campusSchema),
    values: {
      code: editing?.code ?? '',
      name: editing?.name ?? '',
      address: editing?.address ?? '',
      contactEmail: editing?.contactEmail ?? '',
      contactPhone: editing?.contactPhone ?? '',
    },
  });
  const applyCampusErrors = useApiFormErrors<CampusValues>(campusForm.setError, setFormErrors);

  const adminForm = useForm<FirstAdminValues>({
    resolver: zodResolver(firstAdminSchema),
    defaultValues: { name: '', email: '', temporaryPassword: '' },
  });
  const applyAdminErrors = useApiFormErrors<FirstAdminValues>(adminForm.setError, setFormErrors);

  const close = () => { setCreating(false); setEditing(null); setFormErrors([]); campusForm.reset(); };

  const save = useMutation({
    mutationFn: (values: CampusValues) => {
      const shared = {
        name: values.name,
        ...(values.address ? { address: values.address } : {}),
        ...(values.contactEmail ? { contactEmail: values.contactEmail } : {}),
        ...(values.contactPhone ? { contactPhone: values.contactPhone } : {}),
      };
      // `code` is rejected on update. Sending it produces a 400, so it is only
      // ever part of the create body.
      return editing
        ? campusApi.update(editing.id, shared)
        : campusApi.create({ code: values.code, ...shared });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success(editing ? 'Campus updated' : 'Campus created');
      close();
    },
    onError: (error) => { setFormErrors([]); applyCampusErrors(error); },
  });

  const changeStatus = useMutation({
    mutationFn: () =>
      campusApi.changeStatus(suspending?.id as number, {
        active: !(suspending?.active ?? true),
        reason: suspendReason,
        changedBy: identity?.userId as number,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success(suspending?.active ? 'Campus suspended' : 'Campus reinstated');
      setSuspending(null);
      setSuspendReason('');
    },
    onError: (error) => toast.fromError(error, 'That campus could not be updated.'),
  });

  /**
   * Assigning an admin is TWO writes to TWO services, and it has to be.
   *
   * `users.campus_id` in auth-service is the authoritative link — it lands in
   * the JWT and drives every permission check. `campuses.admin_user_id` in
   * campus-service is a denormalised copy, and it is the ONLY one a platform
   * account can read: listing users calls CurrentUser.campusId(), which throws
   * for a Super Admin (blocker B5).
   *
   * There is no foreign key between them — separate databases — and auth-service
   * does not tell campus-service when a CAMPUS_ADMIN is created. So creating the
   * account alone leaves the campus record claiming it has nobody, and the
   * platform console reports a campus as unadministered while its admin is
   * signing in perfectly happily.
   *
   * Closing that here rather than by hand keeps the two in step for every
   * campus, not just the one somebody remembered to patch.
   *
   * NOTE the spread: PUT /campuses/{id} is a full replace, not a patch. Sending
   * only adminUserId would blank the name, address and contact details.
   */
  const createAdmin = useMutation({
    mutationFn: async (values: FirstAdminValues) => {
      const campus = assigningAdmin as CampusResponse;

      const admin = await authApi.createUser({
        email: values.email,
        name: values.name,
        role: 'CAMPUS_ADMIN',
        campusId: campus.id,
        temporaryPassword: values.temporaryPassword,
      });

      await campusApi.update(campus.id, {
        name: campus.name,
        address: campus.address,
        contactEmail: campus.contactEmail,
        contactPhone: campus.contactPhone,
        logoS3Key: campus.logoS3Key,
        adminUserId: admin.id,
      });

      return admin;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.users() });
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success('Campus admin assigned', 'They must change the temporary password at first sign-in.');
      setAssigningAdmin(null);
      adminForm.reset();
    },
    /*
     * If the second call fails the account still exists, so the campus is
     * administrable even though the console will not show it. Say exactly that
     * — "could not be created" would send somebody off to create a duplicate.
     */
    onError: (error) => { setFormErrors([]); applyAdminErrors(error); },
  });

  const columns: ColumnDef<CampusResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Campus',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption text-mono text-[var(--ink-500)]">{info.row.original.code}</p>
        </div>
      ),
    },
    { id: 'activeGateCount', header: 'Gates', accessorKey: 'activeGateCount' },
    { id: 'contactEmail', header: 'Contact', cell: (info) => info.row.original.contactEmail ?? '—' },
    {
      id: 'createdAt',
      header: 'Created',
      accessorKey: 'createdAt',
      cell: (info) => formatDateTime(info.row.original.createdAt),
    },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => <Badge>{info.row.original.active ? 'Active' : 'Suspended'}</Badge>,
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => {
        const campus = info.row.original;
        return (
          <div className="flex justify-end gap-[var(--sp-2)]">
            <Button size="sm" variant="ghost" onClick={() => setAssigningAdmin(campus)}>
              <UserPlus aria-hidden />Admin
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(campus)}>Edit</Button>
            <Button size="sm" variant="ghost" onClick={() => setSuspending(campus)}>
              {campus.active ? 'Suspend' : 'Reinstate'}
            </Button>
          </div>
        );
      },
    },
  ];

  if (campuses.isError) {
    return <ErrorState error={campuses.error} onRetry={() => void campuses.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Campuses"
        description="Every campus on the platform, including suspended ones."
        actions={<Button onClick={() => setCreating(true)}><Plus aria-hidden />Add campus</Button>}
      />

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
        <StatCard label="Campuses" value={stats.data?.totalCampuses ?? null} icon={Building2} loading={stats.isPending} />
        <StatCard label="Active" value={stats.data?.activeCampuses ?? null} loading={stats.isPending} />
        <StatCard label="Suspended" value={stats.data?.inactiveCampuses ?? null} loading={stats.isPending}
                  hint="Read-only. Nothing is deleted." />
      </div>

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={campuses.data ?? []}
          loading={campuses.isPending}
          mobilePrimaryColumn="name"
          getRowId={(row) => row.code}
          emptyHeading="No campuses yet"
          emptyDescription="Create the first one, then assign it a Campus Admin."
        />
      </div>

      <Dialog open={open} onOpenChange={(next) => { if (!next) close(); }}>
        <DialogContent>
          <form noValidate onSubmit={campusForm.handleSubmit((values) => save.mutate(values))}>
            <DialogHeader>
              <DialogTitle>{editing ? `Edit ${editing.name}` : 'Add a campus'}</DialogTitle>
              <DialogDescription>
                {editing
                  ? 'The campus code is permanent — it is part of every storage path and pass URL for this campus.'
                  : 'Choose the code carefully. It becomes part of every storage path for this campus and cannot be changed afterwards.'}
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Campus name" required error={campusForm.formState.errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy}
                         invalid={Boolean(campusForm.formState.errors.name)}
                         {...campusForm.register('name')} />
                )}
              </Field>

              <Field
                label="Campus code" required
                error={campusForm.formState.errors.code?.message}
                hint={editing ? 'Permanent.' : 'Short, letters, numbers and hyphens. Permanent.'}
              >
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy} disabled={editing !== null}
                         invalid={Boolean(campusForm.formState.errors.code)}
                         {...campusForm.register('code')} />
                )}
              </Field>

              <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
                <Field label="Contact email" error={campusForm.formState.errors.contactEmail?.message}>
                  {({ id, describedBy }) => (
                    <Input id={id} type="email" aria-describedby={describedBy}
                           invalid={Boolean(campusForm.formState.errors.contactEmail)}
                           {...campusForm.register('contactEmail')} />
                  )}
                </Field>
                <Field label="Contact phone" error={campusForm.formState.errors.contactPhone?.message}>
                  {({ id, describedBy }) => (
                    <Input id={id} type="tel" placeholder="+919876543210" aria-describedby={describedBy}
                           invalid={Boolean(campusForm.formState.errors.contactPhone)}
                           {...campusForm.register('contactPhone')} />
                  )}
                </Field>
              </div>

              <Field label="Address" error={campusForm.formState.errors.address?.message}>
                {({ id, describedBy }) => (
                  <Textarea id={id} rows={3} aria-describedby={describedBy}
                            invalid={Boolean(campusForm.formState.errors.address)}
                            {...campusForm.register('address')} />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={close}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>
                {editing ? 'Save changes' : 'Create campus'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={assigningAdmin !== null}
        onOpenChange={(next) => { if (!next) { setAssigningAdmin(null); adminForm.reset(); setFormErrors([]); } }}
      >
        <DialogContent>
          <form noValidate onSubmit={adminForm.handleSubmit((values) => createAdmin.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Add a Campus Admin</DialogTitle>
              <DialogDescription>
                {assigningAdmin
                  ? `This account administers ${assigningAdmin.name} and cannot be moved to another campus afterwards.`
                  : ''}
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Full name" required error={adminForm.formState.errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy}
                         invalid={Boolean(adminForm.formState.errors.name)}
                         {...adminForm.register('name')} />
                )}
              </Field>

              <Field label="Email" required error={adminForm.formState.errors.email?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} type="email" aria-describedby={describedBy}
                         invalid={Boolean(adminForm.formState.errors.email)}
                         {...adminForm.register('email')} />
                )}
              </Field>

              <Field
                label="Temporary password" required
                error={adminForm.formState.errors.temporaryPassword?.message}
                hint="At least 8 characters with an uppercase letter, a lowercase letter and a number. They must change it at first sign-in."
              >
                {({ id, describedBy }) => (
                  <Input id={id} type="text" autoComplete="off" aria-describedby={describedBy}
                         invalid={Boolean(adminForm.formState.errors.temporaryPassword)}
                         {...adminForm.register('temporaryPassword')} />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setAssigningAdmin(null)}>
                Cancel
              </Button>
              <Button type="submit" loading={createAdmin.isPending}>Create admin</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={suspending !== null}
        onOpenChange={(next) => { if (!next) { setSuspending(null); setSuspendReason(''); } }}
        title={suspending?.active ? `Suspend ${suspending.name}?` : `Reinstate ${suspending?.name ?? ''}?`}
        description={
          suspending?.active
            ? 'New activity stops at this campus. Nothing is deleted — every existing record stays readable.'
            : 'The campus returns to normal operation.'
        }
        confirmLabel={suspending?.active ? 'Suspend' : 'Reinstate'}
        destructive={suspending?.active ?? false}
        loading={changeStatus.isPending}
        confirmDisabled={suspendReason.trim().length < statusChangeFallback.minReason}
        onConfirm={() => changeStatus.mutate()}
      >
        <Field label="Reason" required hint="Recorded against the campus record.">
          {({ id }) => (
            <Textarea id={id} rows={3} value={suspendReason}
                      onChange={(event) => setSuspendReason(event.target.value)} />
          )}
        </Field>
      </ConfirmDialog>
    </div>
  );
}
