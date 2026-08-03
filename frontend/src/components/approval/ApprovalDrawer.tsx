import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, History, ShieldAlert, X } from 'lucide-react';
import {
  Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Textarea,
} from '@ui/index';
import { DescriptionList } from '@components/data';
import { FormError } from '@components/feedback';
import { RequestStatusBadge } from '@components/pass';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys, passKeys, requestKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { flags } from '@lib/config';
import { useApiFormErrors } from '@features/auth/useApiForm';
import { useToast } from '@hooks/useToast';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { z } from 'zod';
import { LIMITS } from '@lib/validation/patterns';

/**
 * VisitorRequestDecisionDto. `reviewedBy` is @JsonIgnore server-side — the
 * backend takes the reviewer from the token, because a body that names its own
 * reviewer would let one faculty member record an approval under another's name.
 */
const rejectSchema = z.object({
  rejectReason: z
    .string()
    .min(1, 'Give a reason — the applicant is shown it')
    .max(LIMITS.reason.max, 'Keep the reason under 500 characters'),
});
type RejectValues = z.infer<typeof rejectSchema>;

/**
 * The approval drawer and the reject modal.
 *
 * LIVES IN components/, NOT IN A FEATURE, and that is deliberate. Both a
 * Faculty member and a Campus Admin approve visitor requests, against the same
 * endpoint with the same server-side rules. Two copies would drift the day one
 * of them gained a check the other did not — and the check in question is a
 * blocklist match, so the drift would be a security one.
 *
 * Phase 4 imports this rather than writing its own.
 *
 * A REJECTION REASON IS MANDATORY and the applicant reads it. The backend's
 * @AssertTrue enforces it; requiring it here means the visitor gets a sentence
 * instead of a dead end, and the host gets one fewer phone call.
 *
 * Prior visits are shown because "have I met this person before" is the
 * question a host actually asks, and `/visitor-requests/by-email` answers it in
 * one call.
 */
export function ApprovalDrawer({
  request, open, onOpenChange,
}: {
  request: VisitorRequestResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const priorVisits = useQuery({
    queryKey: requestKeys.byEmail(request?.visitorEmail ?? ''),
    queryFn: () => visitorRequestApi.byEmail(request?.visitorEmail as string),
    enabled: open && Boolean(request?.visitorEmail),
  });

  /**
   * Blocker B10: BlocklistController is SA/CA only, so a FACULTY caller is 403.
   * Flagged off rather than shown as "clear" — a fabricated all-clear on a
   * security control is worse than no control at all.
   */
  const blocklist = useQuery({
    queryKey: authKeys.blocklistList({ email: request?.visitorEmail ?? '', page: 0, size: 1 }),
    queryFn: () => authApi.listBlocklist({ email: request?.visitorEmail as string, page: 0, size: 1 }),
    enabled: flags.blocklistCheckLine && open && Boolean(request?.visitorEmail),
    retry: false,
  });

  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<RejectValues>({
    resolver: zodResolver(rejectSchema),
    defaultValues: { rejectReason: '' },
  });
  const applyApiErrors = useApiFormErrors<RejectValues>(setError, setFormErrors);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: requestKeys.all });
    void queryClient.invalidateQueries({ queryKey: passKeys.all });
  };

  const approve = useMutation({
    mutationFn: () => visitorRequestApi.decide(request?.id as number, { decision: 'APPROVED' }),
    onSuccess: () => {
      invalidate();
      toast.success('Approved', 'A pass is being issued and emailed to the visitor.');
      onOpenChange(false);
    },
    // A 400 here is usually the B2 business rule, and its message is written
    // for a human. Rendering it verbatim tells the host exactly what is wrong.
    onError: (error) => toast.fromError(error, 'That request could not be approved.'),
  });

  const reject = useMutation({
    mutationFn: (values: RejectValues) =>
      visitorRequestApi.decide(request?.id as number, {
        decision: 'REJECTED',
        rejectReason: values.rejectReason,
      }),
    onSuccess: () => {
      invalidate();
      toast.success('Rejected', 'The applicant has been told, with your reason.');
      setRejecting(false);
      reset();
      onOpenChange(false);
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  if (!request) return null;

  const decided = request.status !== 'PENDING';
  const priorCount = (priorVisits.data ?? []).filter((visit) => visit.id !== request.id).length;
  const blocked = (blocklist.data?.items.length ?? 0) > 0;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent side="right">
          <DialogHeader>
            <DialogTitle>{request.visitorName}</DialogTitle>
            <DialogDescription>
              Requested {formatDateTime(request.createdAt)}
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="flex flex-col gap-[var(--sp-6)]">
            <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
              <RequestStatusBadge status={request.status} />
            </div>

            <DescriptionList
              columns={1}
              items={[
                { label: 'Email', value: request.visitorEmail },
                { label: 'Phone', value: request.visitorPhone },
                { label: 'Visit dates', value: formatValidity(request.visitFrom, request.visitTo) },
                { label: 'Purpose', value: request.purpose },
                ...(request.rejectReason
                  ? [{ label: 'Reason given', value: request.rejectReason }]
                  : []),
              ]}
            />

            <section>
              <h3 className="text-label mb-[var(--sp-2)] text-[var(--ink-500)]">Checks</h3>
              <ul className="flex flex-col gap-[var(--sp-2)]">
                <li className="text-body flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
                  <History className="size-4 shrink-0 text-[var(--ink-500)]" aria-hidden />
                  {priorVisits.isPending
                    ? 'Checking prior visits…'
                    : priorCount === 0
                      ? 'No prior visits to this campus'
                      : `${priorCount} prior ${priorCount === 1 ? 'visit' : 'visits'} to this campus`}
                </li>

                {/* Omitted entirely for roles that would 403, never faked. */}
                {flags.blocklistCheckLine ? (
                  <li className="text-body flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
                    <ShieldAlert className="size-4 shrink-0 text-[var(--ink-500)]" aria-hidden />
                    {blocklist.isPending
                      ? 'Checking the blocklist…'
                      : blocked
                        ? 'Blocklist: match found — do not approve'
                        : 'Blocklist: clear'}
                  </li>
                ) : null}

                {!request.otpVerified ? (
                  <li className="text-caption rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--ink-500)]">
                    This visitor has not verified their email yet, so a pass cannot be
                    issued. Approving will be refused until verification is in place.
                  </li>
                ) : null}
              </ul>
            </section>
          </DialogBody>

          {!decided ? (
            <DialogFooter>
              <Button variant="secondary" onClick={() => setRejecting(true)}>
                <X aria-hidden />Reject
              </Button>
              <Button onClick={() => approve.mutate()} loading={approve.isPending}>
                <Check aria-hidden />Approve
              </Button>
            </DialogFooter>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={rejecting} onOpenChange={(next) => { setRejecting(next); if (!next) reset(); }}>
        <DialogContent>
          <form noValidate onSubmit={handleSubmit((values) => reject.mutate(values))}>
            <DialogHeader>
              <DialogTitle>Reject this request</DialogTitle>
              <DialogDescription>
                {request.visitorName} is shown the reason you give. A clear one prevents
                a duplicate application.
              </DialogDescription>
            </DialogHeader>

            <DialogBody className="flex flex-col gap-[var(--sp-4)]">
              <FormError messages={formErrors} />
              <Field label="Reason" required error={errors.rejectReason?.message}>
                {({ id, describedBy }) => (
                  <Textarea
                    id={id} rows={4} aria-describedby={describedBy}
                    invalid={Boolean(errors.rejectReason)}
                    placeholder="e.g. The lab is closed that week — please pick another date."
                    {...register('rejectReason')}
                  />
                )}
              </Field>
            </DialogBody>

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setRejecting(false)}>
                Cancel
              </Button>
              <Button type="submit" variant="danger" loading={reject.isPending}>
                Reject request
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
