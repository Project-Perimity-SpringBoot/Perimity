import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { ColumnDef } from '@tanstack/react-table';
import { CalendarRange, Plus, Sparkles, Users } from 'lucide-react';
import {
  Badge, Button, Dialog, DialogBody, DialogContent, DialogDescription,
  DialogFooter, DialogHeader, DialogTitle, Field, Input, Textarea,
} from '@ui/index';
import { DataTable } from '@components/data';
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
      header: 'Event Name & Details',
      accessorKey: 'name',
      cell: (info) => (
        <span className="flex items-center gap-3 min-w-0">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 font-bold text-xs text-indigo-700 border border-indigo-100">
            <CalendarRange className="size-4 text-indigo-600" />
          </span>
          <span className="block min-w-0">
            <span className="block truncate font-bold text-slate-900 text-sm">
              {info.row.original.name}
            </span>
            {info.row.original.description && (
              <span className="block truncate text-xs text-slate-500 max-w-[40ch]">
                {info.row.original.description}
              </span>
            )}
          </span>
        </span>
      ),
    },
    {
      id: 'validFrom',
      header: 'Event Duration',
      accessorKey: 'validFrom',
      cell: (info) => (
        <span className="font-semibold text-xs text-slate-700">
          {formatValidity(info.row.original.validFrom, info.row.original.validTo)}
        </span>
      ),
    },
    {
      id: 'issuedPassCount',
      header: 'Attendees',
      accessorKey: 'issuedPassCount',
      cell: (info) => (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 font-mono text-xs font-bold text-slate-700 border border-slate-200/60">
          <Users className="size-3 text-slate-500" />
          {info.row.original.issuedPassCount} registered
        </span>
      ),
    },
    {
      id: 'state',
      header: 'Status',
      cell: (info) => {
        const event = info.row.original;
        if (event.cancelled) return <Badge tone="neutral">Cancelled</Badge>;
        return (
          <Badge tone={event.runningToday ? 'brand' : 'neutral'}>
            {event.runningToday ? 'Running Today' : 'Scheduled'}
          </Badge>
        );
      },
    },
  ];

  if (events.isError) {
    return <ErrorState error={events.error} onRetry={() => void events.refetch()} />;
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6 pb-16">
      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" /> Event & Attendance Management
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">Campus Events</h1>
            <p className="text-sm text-indigo-200/90 max-w-2xl">
              Create campus events and manage attendee rosters. Event dates dictate the validity window for all issued attendee passes.
            </p>
          </div>

          <Button
            size="lg"
            onClick={() => setCreating(true)}
            className="gap-2 bg-indigo-600 font-semibold text-white shadow-lg shadow-indigo-500/30 hover:bg-indigo-500"
          >
            <Plus className="size-4" /> Create New Event
          </Button>
        </div>
      </div>

      {/* Events Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
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
          emptyDescription="Create an event to start issuing event visitor passes and managing attendance."
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
            <DialogTitle>Create New Campus Event</DialogTitle>
            <DialogDescription>
              Set event dates. These dates will automatically bound the validity of all attendee passes.
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="flex flex-col gap-4">
            <FormError messages={formErrors} />

            <Field label="Event Name" required error={errors.name?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  aria-describedby={describedBy}
                  invalid={Boolean(errors.name)}
                  maxLength={LIMITS.eventName.max}
                  placeholder="e.g. Annual Technical Symposium 2026"
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
                  placeholder="Brief summary of the event activities and venue..."
                  {...register('description')}
                />
              )}
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Start Date" required error={errors.validFrom?.message}>
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

              <Field label="End Date" required error={errors.validTo?.message}>
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
            <Button type="submit" loading={create.isPending} className="bg-indigo-600 text-white">
              Create Event
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
