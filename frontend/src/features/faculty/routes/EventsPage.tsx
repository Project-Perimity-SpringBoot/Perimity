import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { ColumnDef } from '@tanstack/react-table';
import { CalendarRange, Plus } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription,
  DialogFooter, DialogHeader, DialogTitle, Field, Input, Textarea,
} from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { ErrorState, FormError } from '@components/feedback';
import { eventApi } from '@lib/api/services/gatepass.api';
import { eventKeys } from '@lib/query/keys';
import { formatValidity } from '@lib/format/datetime';
import { LIMITS } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import { useUrlPagination } from '@hooks/useUrlPagination';
import type { EventResponse } from '@/types/gatepass.types';
import { eventSchema, type EventValues } from '../schemas/faculty.schemas';

export default function EventsPage() {
  const navigate = useNavigate();
  const { request: pageRequest, setPage } = useUrlPagination(20);
  const [creating, setCreating] = useState(false);

  const events = useQuery({
    queryKey: eventKeys.list(pageRequest),
    queryFn: () => eventApi.list(pageRequest),
  });

  const columns: ColumnDef<EventResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Event',
      accessorKey: 'name',
      cell: (info) => (
        <span className="flex min-w-0 items-center gap-[var(--sp-3)]">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)]">
            <CalendarRange className="size-4 text-[var(--brand-600)]" aria-hidden />
          </span>
          <span className="block min-w-0">
            <span className="text-body-md block truncate text-[var(--ink-900)]">
              {info.row.original.name}
            </span>
            {info.row.original.description && (
              <span className="text-caption block max-w-[40ch] truncate text-[var(--ink-500)]">
                {info.row.original.description}
              </span>
            )}
          </span>
        </span>
      ),
    },
    {
      id: 'validFrom',
      header: 'Runs',
      accessorKey: 'validFrom',
      cell: (info) => formatValidity(info.row.original.validFrom, info.row.original.validTo),
    },
    {
      id: 'issuedPassCount',
      header: 'Attendees',
      accessorKey: 'issuedPassCount',
      cell: (info) => (
        <span className="tabular-nums">{info.row.original.issuedPassCount} registered</span>
      ),
    },
    {
      id: 'state',
      header: 'Status',
      cell: (info) => {
        const event = info.row.original;
        if (event.cancelled) return <Badge>Cancelled</Badge>;
        return (
          <Badge tone={event.runningToday ? 'brand' : 'neutral'}>
            {event.runningToday ? 'Running today' : 'Scheduled'}
          </Badge>
        );
      },
    },
  ];

  if (events.isError) {
    return <ErrorState error={events.error} onRetry={() => void events.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Events' }]}
        title="Campus events"
        description="Event dates set the validity window for every attendee pass issued against them."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden /> Create event
          </Button>
        }
      />

      <div className="surface-panel overflow-hidden">
        <DataTable
          columns={columns}
          data={events.data?.items ?? []}
          loading={events.isPending}
          {...(events.data ? { page: events.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          onRowClick={(row) => navigate(`/faculty/events/${row.id}/attendance`)}
          emptyHeading="No events created yet"
          emptyDescription="Create an event to start issuing attendee passes and tracking attendance."
        />
      </div>

      <CreateEventDialog open={creating} onOpenChange={setCreating} />
    </div>
  );
}

function CreateEventDialog({
  open, onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const {
    register, handleSubmit, reset, formState: { errors },
  } = useForm<EventValues>({
    resolver: zodResolver(eventSchema),
    defaultValues: { name: '', description: '', validFrom: '', validTo: '' },
  });

  const create = useMutation({
    mutationFn: (values: EventValues) =>
      eventApi.create({
        name: values.name,
        description: values.description ? values.description : null,
        validFrom: values.validFrom,
        validTo: values.validTo,
      }),
    onSuccess: (event) => {
      void queryClient.invalidateQueries({ queryKey: eventKeys.all });
      toast.success('Event created', `${event.name} is ready for an attendee sheet.`);
      reset();
      onOpenChange(false);
    },
    onError: (error) => {
      setFormErrors([]);
      toast.fromError(error, 'That event could not be created.');
    },
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { onOpenChange(next); if (!next) reset(); }}>
      <DialogContent>
        <form noValidate onSubmit={handleSubmit((values) => create.mutate(values))}>
          <DialogHeader>
            <DialogTitle>Create an event</DialogTitle>
            <DialogDescription>
              These dates bound the validity of every attendee pass issued for the event.
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="flex flex-col gap-[var(--sp-4)]">
            <FormError messages={formErrors} />

            <Field label="Event name" required error={errors.name?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  aria-describedby={describedBy}
                  invalid={Boolean(errors.name)}
                  maxLength={LIMITS.eventName.max}
                  placeholder="Annual Technical Symposium 2026"
                  {...register('name')}
                />
              )}
            </Field>

            <Field label="Description" error={errors.description?.message}>
              {({ id, describedBy }) => (
                <Textarea
                  id={id}
                  rows={3}
                  aria-describedby={describedBy}
                  invalid={Boolean(errors.description)}
                  maxLength={LIMITS.eventDescription.max}
                  placeholder="Brief summary of the activities and venue"
                  {...register('description')}
                />
              )}
            </Field>

            <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
              <Field label="Start date" required error={errors.validFrom?.message}>
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    type="date"
                    aria-describedby={describedBy}
                    invalid={Boolean(errors.validFrom)}
                    {...register('validFrom')}
                  />
                )}
              </Field>

              <Field label="End date" required error={errors.validTo?.message}>
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    type="date"
                    aria-describedby={describedBy}
                    invalid={Boolean(errors.validTo)}
                    {...register('validTo')}
                  />
                )}
              </Field>
            </div>
          </DialogBody>

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create event
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
