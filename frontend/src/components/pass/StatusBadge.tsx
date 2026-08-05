import { Pause } from 'lucide-react';
import { Badge } from '@ui/index';
import type {
  PassStatus, RequestStatus, BatchStatus, ProfileVerificationStatus,
} from '@/types/enums';

const PASS_LABEL: Record<PassStatus, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  EXPIRED: 'Expired',
  REVOKED: 'Revoked',
};

/**
 * Neutral for every status, always. Differentiated by the WORD — a red REVOKED
 * badge would be indistinguishable at a glance from a guard's DENY verdict, and
 * verdict colour is reserved so it keeps meaning something at the gate.
 */
export function PassStatusBadge({ status, note }: { status: PassStatus; note?: string }) {
  return (
    <Badge tone="neutral">
      {status === 'PAUSED' && <Pause className="size-3" aria-hidden />}
      {PASS_LABEL[status]}
      {note && <span className="text-[var(--ink-500)]">· {note}</span>}
    </Badge>
  );
}

const REQUEST_LABEL: Record<RequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return <Badge tone="neutral">{REQUEST_LABEL[status]}</Badge>;
}

const BATCH_LABEL: Record<BatchStatus, string> = {
  VALIDATING: 'Validating',
  VALIDATED: 'Awaiting confirmation',
  PROCESSING: 'Generating',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

export function BatchStatusBadge({ status }: { status: BatchStatus }) {
  return <Badge tone="neutral">{BATCH_LABEL[status]}</Badge>;
}

/**
 * Where a student's self-declared details stand.
 *
 * "Not submitted" rather than "Draft", and "With faculty" rather than
 * "Submitted": the student is the main reader of this badge, and the state
 * names are written from the server's point of view, not theirs. A student
 * seeing "Submitted" reasonably wonders whether anything is expected of them
 * next; "With faculty" answers that.
 *
 * Neutral tone like every other badge here. Green for VERIFIED would be the
 * obvious choice and is exactly what the file-level note warns against —
 * verdict colour belongs to the gate.
 */
const PROFILE_VERIFICATION_LABEL: Record<ProfileVerificationStatus, string> = {
  DRAFT: 'Not submitted',
  SUBMITTED: 'With faculty',
  VERIFIED: 'Verified',
  REJECTED: 'Needs changes',
};

export function ProfileVerificationBadge({ status }: { status: ProfileVerificationStatus }) {
  return <Badge tone="neutral">{PROFILE_VERIFICATION_LABEL[status]}</Badge>;
}
