import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus, ShieldBan } from 'lucide-react';
import {
  Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Input, Textarea,
} from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar, StatCard } from '@components/data';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { BlocklistEntryResponse } from '@/types/auth.types';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useToast } from '@hooks/useToast';
import { useUrlPagination } from '@hooks/useUrlPagination';
import { blocklistSchema, type BlocklistValues } from '../schemas/admin.schemas';

/**
 * Batch 5 screen 8 — the campus blocklist.
 *
 * A REASON IS MANDATORY at five characters. FR-BLK-1 requires it, and the
 * practical version is that an entry nobody can defend six months later is an
 * entry that should not have been made.
 *
 * The mockup's "Expires" column has no source — there is no expiresAt field on
 * the entity or the DTO — so it is absent rather than faked, and the note says
 * every entry is permanent until removed.
 *
 * Blocklists are per-campus. Barred here is not barred elsewhere, and the copy
 * says so because the word sounds global and is not.
 */
export default function BlocklistPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { request: pageRequest, setPage } = useUrlPagination(20);
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<BlocklistEntryResponse | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const listQuery = { ...pageRequest, ...(debounced ? { email: debounced } : {}) };

  const entries = useQuery({
    queryKey: authKeys.blocklistList(listQuery),
    queryFn: () => authApi.listBlocklist(listQuery),
  });

  const count = useQuery({
    queryKey: authKeys.blocklistCount(),
    queryFn: () => authApi.blocklistCount(),
  });

  const { register, handleSubmit, reset, setError, setValue, formState: { errors } } = useForm<BlocklistValues>({
    resolver: zodResolver(blocklistSchema),
    defaultValues: { email: '', phone: '', reason: '' },
  });
  const applyApiErrors = useApiFormErrors<BlocklistValues>(setError, setFormErrors);

  const add = useMutation({
    mutationFn: (values: BlocklistValues) =>
      authApi.addToBlocklist({
        campusId: identity?.campusId as number,
        ...(values.email ? { email: values.email } : {}),
        ...(values.phone ? { phone: values.phone } : {}),
        reason: values.reason,
        createdBy: identity?.userId as number,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.blocklist() });
      toast.success('Added to the blocklist');
      setAdding(false);
      reset();
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  const remove = useMutation({
    mutationFn: (id: number) => authApi.removeFromBlocklist(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.blocklist() });
      toast.success('Removed from the blocklist');
      setRemoving(null);
    },
    onError: (error) => toast.fromError(error, 'That entry could not be removed.'),
  });

  const columns: ColumnDef<BlocklistEntryResponse, unknown>[] = [
    {
      id: 'identity',
      header: 'Email or phone',
      cell: (info) => (
        <span className="text-mono">{info.row.original.email ?? info.row.original.phone ?? '—'}</span>
      ),
    },
    {
      id: 'reason',
      header: 'Reason',
      accessorKey: 'reason',
      cell: (info) => <span className="line-clamp-2 max-w-[40ch]">{info.row.original.reason}</span>,
    },
    {
      id: 'createdAt',
      header: 'Added',
      accessorKey: 'createdAt',
      cell: (info) => formatDateTime(info.row.original.createdAt),
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => (
        <div className="flex justify-end">
          <Button size="sm" variant="ghost" onClick={() => setRemoving(info.row.original)}>Remove</Button>
        </div>
      ),
    },
  ];

  if (entries.isError) return <ErrorState error={entries.error} onRetry={() => void entries.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Blocklist"
        description="Blocked identities are skipped during bulk upload and refused at registration. This list applies to this campus only."
        actions={<Button onClick={() => setAdding(true)}><Plus aria-hidden />Add entry</Button>}
      />

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
        <StatCard label="Blocked identities" value={count.data ?? null} icon={ShieldBan} loading={count.isPending} />
      </div>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search by email"
        resultCount={entries.data?.total}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={entries.data?.items ?? []}
          loading={entries.isPending}
          {...(entries.data ? { page: entries.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="identity"
          getRowId={(row) => String(row.id)}
          emptyHeading="Nobody is blocked"
          emptyDescription="Entries added here are checked on every registration and every bulk-upload row."
        />
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Entries are permanent until removed — there is no expiry date in the data
        model. A blocked registration is refused with a non-specific message that does
        not reveal the block or its reason.
      </p>

      <Dialog open={adding} onOpenChange={(open) => { if (!open) { setAdding(false); reset(); setFormErrors([]); } }}>
        <DialogContent>
          <form noValidate onSubmit={handleSubmit((values) => add.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Add to the blocklist</DialogTitle>
              <DialogDescription>
                Give an email address, a phone number, or both. The reason is recorded in
                the audit log and is never shown to the person.
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Email" error={errors.email?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} type="email" aria-describedby={describedBy}
                         invalid={Boolean(errors.email)} {...register('email')} />
                )}
              </Field>

              <Field label="Phone" hint="Optional. 10 digits only." error={errors.phone?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} type="tel" placeholder="9876543210" aria-describedby={describedBy}
                         maxLength={10}
                         inputMode="numeric"
                         invalid={Boolean(errors.phone)}
                         {...register('phone', {
                           onChange: (e) => {
                             const digits = e.target.value.replace(/\D/g, '').slice(0, 10);
                             setValue('phone', digits, { shouldValidate: true });
                           },
                         })} />
                )}
              </Field>

              <Field label="Reason" required error={errors.reason?.message}>
                {({ id, describedBy }) => (
                  <Textarea id={id} rows={3} aria-describedby={describedBy}
                            invalid={Boolean(errors.reason)} {...register('reason')} />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setAdding(false)}>Cancel</Button>
              <Button type="submit" loading={add.isPending}>Add entry</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => { if (!open) setRemoving(null); }}
        title="Remove this entry?"
        description={`${removing?.email ?? removing?.phone ?? 'This identity'} will be able to register and be issued passes again. Removal is permanent — the entry is deleted, not archived.`}
        confirmLabel="Remove"
        destructive
        loading={remove.isPending}
        onConfirm={() => { if (removing) remove.mutate(removing.id); }}
      />
    </div>
  );
}
