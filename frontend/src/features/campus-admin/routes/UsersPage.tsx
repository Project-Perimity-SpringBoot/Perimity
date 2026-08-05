import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { KeyRound, Plus } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Input, NativeSelect, Textarea,
} from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar } from '@components/data';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { ROLES, creatableRolesFor, type Role } from '@/types/enums';
import type { UserResponse } from '@/types/auth.types';
import { ROLE_LABEL } from '@/layouts/navigation';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useToast } from '@hooks/useToast';
import { useUrlPagination } from '@hooks/useUrlPagination';
import {
  statusChangeSchema, userCreateSchema, type StatusChangeValues, type UserCreateValues,
} from '../schemas/admin.schemas';

const isRole = (value: string): value is Role => (ROLES as readonly string[]).includes(value);

/*
 * The role matrix that lived here has MOVED to @/types/enums as CREATABLE_ROLES,
 * read through creatableRolesFor(). Deleted rather than left in place: main and
 * this branch each grew a copy of the same table, and two mirrors of one server
 * constant is how they end up disagreeing.
 *
 * The table is a mirror of auth-service's UserAdminController.CREATABLE. A
 * Campus Admin gets FACULTY and GUARD - not STUDENT, and not another
 * CAMPUS_ADMIN. Change the server first, then the mirror.
 */

/**
 * Batch 5 screens 3 and 4 — user management.
 *
 * DEACTIVATE, NEVER DELETE. There is no delete endpoint and there should not
 * be: an account that vanishes takes its audit trail and its pass history with
 * it. Deactivation carries a mandatory reason for the same purpose.
 *
 * Email, role and campus are permanent, and the create form says so BEFORE the
 * account exists rather than in an error afterwards. Email is the identity key
 * across all six services; changing it would orphan the person's profile,
 * passes and entries.
 *
 * Filtering is client-side because no endpoint here accepts a text search.
 */
export default function UsersPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(20);
  const roleParam = params.get('role') ?? '';
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);
  const [creating, setCreating] = useState(false);
  const [deactivating, setDeactivating] = useState<UserResponse | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  /*
   * What this admin may actually create, per UserAdminController.CREATABLE.
   * A CAMPUS_ADMIN gets FACULTY and GUARD - not STUDENT (faculty create their
   * own students) and not another CAMPUS_ADMIN (that is the Super Admin's call).
   *
   * Empty for any role the server lets nowhere near this endpoint, which is
   * also the signal to hide "Add user" entirely rather than open a form with an
   * empty dropdown.
   *
   * MERGE NOTE: main and this branch fixed the same bug independently and both
   * survived, leaving two variables for one value. Kept main's NAME, because
   * other lines in this file already read it, and this branch's ACCESSOR -
   * creatableRolesFor() handles a null role internally, where the direct map
   * lookup needed CREATABLE_ROLES imported and it was not.
   */
  const creatableRoles = creatableRolesFor(identity?.role);

  const listQuery = { ...pageRequest, ...(isRole(roleParam) ? { role: roleParam } : {}) };

  const users = useQuery({
    queryKey: authKeys.userList(listQuery),
    queryFn: () => authApi.listUsers(listQuery),
  });

  const visible = useMemo(() => {
    const items = users.data?.items ?? [];
    const term = debounced.trim().toLowerCase();
    if (!term) return items;
    return items.filter(
      (user) =>
        user.name.toLowerCase().includes(term) || user.email.toLowerCase().includes(term),
    );
  }, [users.data, debounced]);

  const createForm = useForm<UserCreateValues>({
    resolver: zodResolver(userCreateSchema),
    defaultValues: {
      email: '',
      name: '',
      phone: '',
      temporaryPassword: '',
      /*
       * Was hardcoded to STUDENT, which is the ONE role a Campus Admin cannot
       * create - so the form opened pre-set to fail. Default to the first role
       * this actor is actually allowed to make.
       *
       * FACULTY, not STUDENT, as the last-resort fallback. It only fires when
       * the list is empty, in which case the form should not be reachable at
       * all - but if it ever is, falling back to the single role this page's
       * own audience is forbidden from creating would recreate the bug.
       */
      role: creatableRoles[0] ?? 'FACULTY',
    },
  });
  const applyCreateErrors = useApiFormErrors<UserCreateValues>(createForm.setError, setFormErrors);
  const watchedRole = createForm.watch('role');

  const statusForm = useForm<StatusChangeValues>({
    resolver: zodResolver(statusChangeSchema),
    defaultValues: { reason: '' },
  });

  const create = useMutation({
    mutationFn: (values: UserCreateValues) =>
      authApi.createUser({
        email: values.email,
        name: values.name,
        ...(values.phone ? { phone: values.phone } : {}),
        role: values.role,
        // campusId is required for every role except SUPER_ADMIN, and
        // forbidden for it. A Campus Admin can only create at their own campus.
        ...(values.role === 'SUPER_ADMIN' ? {} : { campusId: identity?.campusId ?? null }),
        ...(values.role === 'VISITOR' ? {} : { temporaryPassword: values.temporaryPassword }),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.users() });
      toast.success('Account created', 'They must change the temporary password at first sign-in.');
      setCreating(false);
      createForm.reset();
    },
    onError: (error) => { setFormErrors([]); applyCreateErrors(error); },
  });

  const changeStatus = useMutation({
    mutationFn: (values: StatusChangeValues) =>
      authApi.changeUserStatus(deactivating?.id as number, {
        active: !(deactivating?.active ?? true),
        reason: values.reason,
        // Not stripped server-side on this DTO — the client supplies its own id.
        changedBy: identity?.userId as number,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.users() });
      toast.success(deactivating?.active ? 'Account deactivated' : 'Account reactivated');
      setDeactivating(null);
      statusForm.reset();
    },
    onError: (error) => toast.fromError(error, 'That account could not be updated.'),
  });

  const resetPassword = useMutation({
    mutationFn: (email: string) => authApi.requestPasswordReset({ email }),
    onSuccess: () =>
      toast.success('Reset email sent', 'The link goes to the account holder, not to you.'),
    onError: (error) => toast.fromError(error, 'That reset could not be requested.'),
  });

  const columns: ColumnDef<UserResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Name',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption truncate text-[var(--ink-500)]">{info.row.original.email}</p>
        </div>
      ),
    },
    { id: 'role', header: 'Role', cell: (info) => ROLE_LABEL[info.row.original.role] },
    {
      id: 'lastLoginAt',
      header: 'Last sign-in',
      accessorKey: 'lastLoginAt',
      cell: (info) =>
        info.row.original.lastLoginAt ? formatDateTime(info.row.original.lastLoginAt) : 'Never',
    },
    {
      id: 'status',
      header: 'Status',
      cell: (info) => {
        const user = info.row.original;
        return (
          <Badge>
            {!user.active ? 'Deactivated' : user.locked ? 'Locked' : user.mustChangePassword ? 'Password pending' : 'Active'}
          </Badge>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => {
        const user = info.row.original;
        return (
          <div className="flex justify-end gap-[var(--sp-2)]">
            <Button size="sm" variant="ghost" onClick={() => resetPassword.mutate(user.email)}>
              <KeyRound aria-hidden />Reset
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setDeactivating(user)}>
              {user.active ? 'Deactivate' : 'Reactivate'}
            </Button>
          </div>
        );
      },
    },
  ];

  if (users.isError) return <ErrorState error={users.error} onRetry={() => void users.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Users"
        description="Accounts at this campus. Deactivated accounts keep their history."
        actions={creatableRoles.length > 0
          ? <Button onClick={() => setCreating(true)}><Plus aria-hidden />Add user</Button>
          : undefined}
      />

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search name or email on this page"
        resultCount={visible.length}
        filters={
          <NativeSelect
            aria-label="Filter by role" className="sm:w-44" value={roleParam}
            onChange={(event) => setFilter('role', event.target.value || null)}
          >
            <option value="">All roles</option>
            {ROLES.map((role) => (
              <option key={role} value={role}>{ROLE_LABEL[role]}</option>
            ))}
          </NativeSelect>
        }
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={visible}
          loading={users.isPending}
          {...(users.data ? { page: users.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No accounts match"
          emptyDescription="Clear the filters, or add a user."
        />
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Search filters the current page only — this backend has no server-side text
        search, so widen the page size to search further.
      </p>

      <Dialog open={creating} onOpenChange={(open) => { if (!open) { setCreating(false); createForm.reset(); setFormErrors([]); } }}>
        <DialogContent>
          <form noValidate onSubmit={createForm.handleSubmit((values) => create.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Add a user</DialogTitle>
              <DialogDescription>
                Email, role and campus are permanent. Create a new account to change any
                of them — email is this person’s identity across every service.
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Full name" required error={createForm.formState.errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy}
                         invalid={Boolean(createForm.formState.errors.name)}
                         {...createForm.register('name')} />
                )}
              </Field>

              <Field label="Email" required error={createForm.formState.errors.email?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} type="email" aria-describedby={describedBy}
                         invalid={Boolean(createForm.formState.errors.email)}
                         {...createForm.register('email')} />
                )}
              </Field>

              <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
                <Field
                  label="Role"
                  required
                  /* Says WHY the list is short. Without this the absence of
                     "Student" reads as a missing feature - it was reported as a
                     bug once already. A rule the user cannot see is a rule they
                     assume is broken. */
                  hint={
                    identity?.role === 'CAMPUS_ADMIN'
                      ? 'Students are added by their faculty or through bulk onboarding, not here.'
                      : undefined
                  }
                  error={createForm.formState.errors.role?.message}
                >
                  {({ id, describedBy }) => (
                    /* Only what the SERVER will accept from this actor. The list
                       used to be every role but SUPER_ADMIN, which meant a
                       Campus Admin could pick STUDENT and get a 403 after
                       filling the whole form. See CREATABLE_ROLES. */
                    <NativeSelect id={id} aria-describedby={describedBy} {...createForm.register('role')}>
                      {creatableRoles.map((role) => (
                        <option key={role} value={role}>{ROLE_LABEL[role]}</option>
                      ))}
                    </NativeSelect>
                  )}
                </Field>

                <Field label="Phone" error={createForm.formState.errors.phone?.message}>
                  {({ id, describedBy }) => (
                    <Input id={id} type="tel" aria-describedby={describedBy}
                           placeholder="+919876543210"
                           invalid={Boolean(createForm.formState.errors.phone)}
                           {...createForm.register('phone')} />
                  )}
                </Field>
              </div>

              {watchedRole === 'VISITOR' ? (
                <p className="text-caption rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--ink-500)]">
                  A visitor signs in with a one-time code emailed to them and never has a
                  password.
                </p>
              ) : (
                <Field
                  label="Temporary password" required
                  error={createForm.formState.errors.temporaryPassword?.message}
                  hint="At least 8 characters with an uppercase letter, a lowercase letter and a number. They must change it at first sign-in."
                >
                  {({ id, describedBy }) => (
                    <Input id={id} type="text" autoComplete="off" aria-describedby={describedBy}
                           invalid={Boolean(createForm.formState.errors.temporaryPassword)}
                           {...createForm.register('temporaryPassword')} />
                  )}
                </Field>
              )}
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setCreating(false)}>Cancel</Button>
              <Button type="submit" loading={create.isPending}>Create account</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deactivating !== null}
        onOpenChange={(open) => { if (!open) { setDeactivating(null); statusForm.reset(); } }}
        title={deactivating?.active ? `Deactivate ${deactivating.name}?` : `Reactivate ${deactivating?.name ?? ''}?`}
        description={
          deactivating?.active
            ? 'They can no longer sign in. Nothing is deleted — their passes, entries and audit trail stay intact and readable.'
            : 'They will be able to sign in again with their existing password.'
        }
        confirmLabel={deactivating?.active ? 'Deactivate' : 'Reactivate'}
        destructive={deactivating?.active ?? false}
        loading={changeStatus.isPending}
        confirmDisabled={!statusForm.watch('reason')}
        onConfirm={() => void statusForm.handleSubmit((values) => changeStatus.mutate(values))()}
      >
        <Field label="Reason" required error={statusForm.formState.errors.reason?.message}
               hint="Recorded in the audit log.">
          {({ id, describedBy }) => (
            <Textarea id={id} rows={3} aria-describedby={describedBy}
                      invalid={Boolean(statusForm.formState.errors.reason)}
                      {...statusForm.register('reason')} />
          )}
        </Field>
      </ConfirmDialog>
    </div>
  );
}
