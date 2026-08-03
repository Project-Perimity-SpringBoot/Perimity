import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { ColumnDef } from '@tanstack/react-table';
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

/**
 * Phase 4 screen 10 — events, and creating one.
 *
 * ==========================================================================
 * THE EVENT OWNS THE DATE RANGE
 * ==========================================================================
 * These two dates become the validity of every pass in an event visitor batch.
 * 580 attendees, one range, set once here. The bulk sheet has no date columns
 * and must never gain any — per-row dates would be 580 chances to issue a pass
 * that outlives the programme it was for.
 *
 * ==========================================================================
 * CANCEL, NEVER DELETE
 * ==========================================================================
 * eventApi.cancel revokes every pass issued for the event and leaves the row.
 * A deleted event would orphan the entry logs recorded against it, and those
 * logs are the attendance record somebody will ask for afterwards.
 *
 * Statuses render as neutral badges told apart by their word. Green and red
 * belong to the guard's verdict screens and appear nowhere else.
 */
export default function EventsPage() {
  const navigate = useNavigate();
  const { request: pageRequest, setPage } = useUrlPagination(20);
  const [creating, setCreating] = useState(false);

  // No `sort` — Spring Data emits two ORDER BY clauses and the query fails.
  const events = useQuery({
    queryKey: eventKeys.list(pageRequest),
    queryFn: () => eventApi.list(pageRequest),
  });

  const columns: ColumnDef<EventResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Event',
      accessorKey: 'name',
      /* span, not div/p. DataTable renders mobilePrimaryColumn inside a <p> in
         its stacked sub-640px form, and a <div> or <p> there is invalid HTML —
         the browser re-parents the nodes and the card layout breaks. Only React's
         console warning says so; the build and the types are perfectly happy. */
      cell: (info) => (
        <span className="block min-w-0">
          <span className="text-body-md block truncate text-[var(--ink-900)]">
            {info.row.original.name}
          </span>
          {info.row.original.description ? (
            <span className="text-caption block truncate text-[var(--ink-500)]">
              {info.row.original.description}
            </span>
          ) : null}
        </span>
      ),
    },
    {
      id: 'validFrom',
      header: 'Dates',
      accessorKey: 'validFrom',
      cell: (info) => formatValidity(info.row.original.validFrom, info.row.original.validTo),
    },
    {
      id: 'issuedPassCount',
      header: 'Registered',
      accessorKey: 'issuedPassCount',
      cell: (info) => info.row.original.issuedPassCount,
    },
    {
      id: 'state',
      header: 'State',
      cell: (info) => {
        const event = info.row.original;
        if (event.cancelled) return <Badge>Cancelled</Badge>;
        return <Badge>{event.runningToday ? 'Running today' : 'Scheduled'}</Badge>;
      },
    },
  ];

  if (events.isError) {
    return <ErrorState error={events.error} onRetry={() => void events.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Events"
        description="An event's dates become the validity of every attendee pass issued for it."
        actions={<Button onClick={() => setCreating(true)}>Create event</Button>}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={events.data?.items ?? []}
          loading={events.isPending}
          {...(events.data ? { page: events.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          /* The row IS the link to attendance — the same affordance the admin
             queue uses for its rows, rather than a second column of buttons. */
          onRowClick={(row) => navigate(`/faculty/events/${row.id}/attendance`)}
          emptyHeading="No events yet"
          emptyDescription="Create one, then upload an attendee sheet against it."
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
        // Empty string would store a blank description rather than none.
        description: values.description ? values.description : null,
        validFrom: values.validFrom,
        validTo: values.validTo,
        // createdBy and campusId are @JsonIgnore server-side. Not sent.
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
              These dates apply to every attendee pass issued for this event.
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="flex flex-col gap-[var(--sp-4)]">
            <FormError messages={formErrors} />

            <Field label="Name" required error={errors.name?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  aria-describedby={describedBy}
                  invalid={Boolean(errors.name)}
                  maxLength={LIMITS.eventName.max}
                  placeholder="e.g. Annual Tech Symposium"
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
                  {...register('description')}
                />
              )}
            </Field>

            <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
              <Field label="First day" required error={errors.validFrom?.message}>
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

              <Field label="Last day" required error={errors.validTo?.message}>
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
            <Button type="submit" loading={create.isPending}>Create event</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
