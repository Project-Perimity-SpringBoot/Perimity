import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Input, Switch,
} from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { ErrorState, FormError } from '@components/feedback';
import { gateApi } from '@lib/api/services/campus.api';
import { campusKeys } from '@lib/query/keys';
import type { CampusGateResponse } from '@/types/campus.types';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { gateSchema, type GateValues } from '../schemas/admin.schemas';

/**
 * Batch 5 screens 6 and 7 — gates.
 *
 * CampusGate carries exactly three fields: name, location and active. The
 * mockup also promises gate type, active hours and assigned guards; none of
 * them exist on the entity or in any DTO, so none are shown. The note at the
 * foot says so plainly rather than leaving an admin to wonder where the hours
 * setting went.
 *
 * A closed gate is not deleted, because entry logs reference the gate that
 * recorded them and a deleted gate would orphan yesterday's evidence.
 */
export default function GatesPage() {
  const { campusId } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<CampusGateResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const gates = useQuery({
    queryKey: campusKeys.gates(campusId ?? 0, true),
    queryFn: () => gateApi.list(campusId as number, true),
    enabled: campusId !== null,
  });

  const open = creating || editing !== null;

  const { register, handleSubmit, reset, setError, setValue, watch, formState: { errors } } =
    useForm<GateValues>({
      resolver: zodResolver(gateSchema),
      values: {
        name: editing?.name ?? '',
        location: editing?.location ?? '',
        active: editing?.active ?? true,
      },
    });
  const applyApiErrors = useApiFormErrors<GateValues>(setError, setFormErrors);

  const close = () => { setCreating(false); setEditing(null); setFormErrors([]); reset(); };

  const save = useMutation({
    mutationFn: (values: GateValues) =>
      editing
        ? gateApi.update(campusId as number, editing.id, {
            name: values.name,
            ...(values.location ? { location: values.location } : { location: null }),
            active: values.active,
          })
        : gateApi.create(campusId as number, {
            name: values.name,
            ...(values.location ? { location: values.location } : {}),
          }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: campusKeys.all });
      toast.success(editing ? 'Gate updated' : 'Gate created');
      close();
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  const columns: ColumnDef<CampusGateResponse, unknown>[] = [
    { id: 'name', header: 'Gate', accessorKey: 'name' },
    { id: 'location', header: 'Location', cell: (info) => info.row.original.location ?? '—' },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => <Badge>{info.row.original.active ? 'Open' : 'Closed'}</Badge>,
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => (
        <div className="flex justify-end">
          <Button size="sm" variant="ghost" onClick={() => setEditing(info.row.original)}>Edit</Button>
        </div>
      ),
    },
  ];

  if (gates.isError) return <ErrorState error={gates.error} onRetry={() => void gates.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Gates"
        description="A guard pins their whole shift to one of these, and every entry is logged against it."
        actions={<Button onClick={() => setCreating(true)}><Plus aria-hidden />Add gate</Button>}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={gates.data ?? []}
          loading={gates.isPending}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No gates yet"
          emptyDescription="Nobody can start a shift until this campus has at least one gate."
        />
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        A gate has a name, a location and an open/closed state. Active hours, gate type
        and assigned guards are not part of the current data model.
      </p>

      <Dialog open={open} onOpenChange={(next) => { if (!next) close(); }}>
        <DialogContent>
          <form noValidate onSubmit={handleSubmit((values) => save.mutate(values))}>
            <DialogHeader>
              <DialogTitle>{editing ? 'Edit gate' : 'Add gate'}</DialogTitle>
              <DialogDescription>
                Guards see this name when they choose where to stand, and it appears on
                every entry record.
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Gate name" required error={errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy} placeholder="Main Gate"
                         invalid={Boolean(errors.name)} {...register('name')} />
                )}
              </Field>

              <Field label="Location" error={errors.location?.message} hint="Optional.">
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy} placeholder="North side, near the library"
                         invalid={Boolean(errors.location)} {...register('location')} />
                )}
              </Field>

              {editing ? (
                <Field label="Open" hint="A closed gate cannot be chosen at the start of a shift. Past entries keep referencing it.">
                  {({ id }) => (
                    <Switch id={id} checked={watch('active')}
                            onCheckedChange={(checked) => setValue('active', checked)} />
                  )}
                </Field>
              ) : null}
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={close}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>
                {editing ? 'Save changes' : 'Create gate'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
