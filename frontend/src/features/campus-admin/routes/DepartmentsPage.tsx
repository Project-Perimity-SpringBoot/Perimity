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
import { departmentApi } from '@lib/api/services/user.api';
import { departmentKeys } from '@lib/query/keys';
import type { DepartmentResponse } from '@/types/user.types';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { departmentSchema, type DepartmentValues } from '../schemas/admin.schemas';

/**
 * Batch 5 screen 5 — departments.
 *
 * The mockup shows "Head" and "Member count" columns. Neither field exists on
 * DepartmentResponse and no endpoint produces them, so neither is shown. A
 * column populated from a guess is worse than a column that is not there.
 *
 * Deactivate rather than delete: departments are referenced by student and
 * faculty profiles, and there is no delete endpoint.
 */
export default function DepartmentsPage() {
  const { campusId } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<DepartmentResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const departments = useQuery({
    queryKey: departmentKeys.list(campusId ?? undefined, false),
    queryFn: () => departmentApi.list(campusId ?? undefined, false),
  });

  const open = creating || editing !== null;

  const { register, handleSubmit, reset, setError, setValue, watch, formState: { errors } } =
    useForm<DepartmentValues>({
      resolver: zodResolver(departmentSchema),
      values: {
        code: editing?.code ?? '',
        name: editing?.name ?? '',
        active: editing?.active ?? true,
      },
    });
  const applyApiErrors = useApiFormErrors<DepartmentValues>(setError, setFormErrors);

  const close = () => { setCreating(false); setEditing(null); setFormErrors([]); reset(); };

  const save = useMutation({
    mutationFn: (values: DepartmentValues) =>
      editing
        ? departmentApi.update(editing.id, { name: values.name, active: values.active }, campusId ?? undefined)
        : departmentApi.create({
            campusId: campusId as number,
            code: values.code,
            name: values.name,
          }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: departmentKeys.all });
      toast.success(editing ? 'Department updated' : 'Department created');
      close();
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  const columns: ColumnDef<DepartmentResponse, unknown>[] = [
    { id: 'name', header: 'Department', accessorKey: 'name' },
    {
      id: 'code',
      header: 'Code',
      accessorKey: 'code',
      cell: (info) => <span className="text-mono">{info.row.original.code}</span>,
    },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => <Badge>{info.row.original.active ? 'Active' : 'Inactive'}</Badge>,
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

  if (departments.isError) {
    return <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Departments"
        description="The list every student, faculty member and visitor form selects from."
        actions={<Button onClick={() => setCreating(true)}><Plus aria-hidden />Add department</Button>}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={departments.data ?? []}
          loading={departments.isPending}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No departments yet"
          emptyDescription="Add one before creating student or faculty profiles."
        />
      </div>

      <Dialog open={open} onOpenChange={(next) => { if (!next) close(); }}>
        <DialogContent>
          <form noValidate onSubmit={handleSubmit((values) => save.mutate(values))}>
            <DialogHeader>
              <DialogTitle>{editing ? 'Edit department' : 'Add department'}</DialogTitle>
              <DialogDescription>
                {editing
                  ? 'The code is permanent — profiles and reports reference it.'
                  : 'Choose the code carefully. It cannot be changed afterwards.'}
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />

              <Field label="Name" required error={errors.name?.message}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy}
                         invalid={Boolean(errors.name)} {...register('name')} />
                )}
              </Field>

              <Field label="Code" required error={errors.code?.message}
                     hint={editing ? 'Permanent.' : 'Short and permanent, e.g. CSE.'}>
                {({ id, describedBy }) => (
                  <Input id={id} aria-describedby={describedBy} disabled={editing !== null}
                         invalid={Boolean(errors.code)} {...register('code')} />
                )}
              </Field>

              {editing ? (
                <Field label="Active" hint="An inactive department is hidden from every picker.">
                  {({ id }) => (
                    <Switch
                      id={id}
                      checked={watch('active')}
                      onCheckedChange={(checked) => setValue('active', checked)}
                    />
                  )}
                </Field>
              ) : null}
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={close}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>
                {editing ? 'Save changes' : 'Create department'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
