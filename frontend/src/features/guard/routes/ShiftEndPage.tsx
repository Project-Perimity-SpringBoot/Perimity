import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { ConfirmDialog, EmptyState, ErrorState } from '@components/feedback';
import { DescriptionList, PageHeader } from '@components/data';
import { sessionApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { formatDateTime, formatTime } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';

/**
 * Phase 6 — end-shift confirmation, with the shift summary.
 *
 * ==========================================================================
 * THIS IS WHERE THE COUNTERS LIVE, AND NOWHERE ELSE
 * ==========================================================================
 * The scanner screen has no totals on purpose: nothing competes with the
 * viewfinder. But a guard handing over at the end of a shift genuinely needs
 * the numbers, and this is the one moment they are looking for them.
 *
 * The totals come off the session record — ScanSessionResponse carries
 * totalScans, allowedCount and deniedCount, maintained as each scan is written.
 * Nothing is recomputed from the entry log here, so the summary and the
 * register cannot disagree.
 *
 * Note allowedCount counts AMBER as permitted, because an amber scan is a
 * person who walked through the gate. Counting it as denied would make this
 * summary disagree with the register it exists to summarise.
 *
 * Ending is confirmed rather than immediate: it is irreversible, the guard
 * cannot scan again without starting a new shift, and a mis-tap at a gate with
 * a queue is a bad five minutes.
 */
export default function ShiftEndPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);

  const session = useQuery({
    queryKey: guardKeys.currentSession(),
    queryFn: () => sessionApi.current(),
    retry: false,
  });

  const end = useMutation({
    mutationFn: (id: string) => sessionApi.end(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: guardKeys.all });
      toast.success('Shift ended');
      setConfirming(false);
      // GuardSessionGate bounces a guard with no open shift back to
      // /guard/session, so this lands on "start a shift" rather than an empty
      // scanner that cannot scan.
      navigate('/guard/session');
    },
    onError: (error) => toast.fromError(error, 'That shift could not be ended.'),
  });

  if (session.isPending) {
    return <div className="surface-card m-[var(--sp-4)] p-[var(--sp-6)]"><SkeletonText lines={6} /></div>;
  }
  if (session.isError) {
    return <ErrorState error={session.error} onRetry={() => void session.refetch()} />;
  }

  // Null once the shift has been ended in another tab, or if this page is
  // reached without one. GuardSessionGate normally prevents it; this keeps the
  // page from throwing on a property of null if it slips through.
  if (!session.data) {
    return (
      <EmptyState
        heading="No open shift"
        description="This shift has already ended. Start a new one to scan again."
      />
    );
  }

  const s = session.data;
  const refused = s.deniedCount;

  return (
    <div className="flex flex-col gap-[var(--sp-5)] p-[var(--sp-4)]">
      <Button variant="link" asChild className="self-start">
        <Link to="/guard"><ArrowLeft aria-hidden />Back to scanning</Link>
      </Button>

      <PageHeader
        title="End this shift"
        description={`${s.gateName} · started ${formatTime(s.startedAt)}`}
      />

      <section className="surface-card p-[var(--sp-6)]">
        <DescriptionList
          items={[
            { label: 'Gate', value: s.gateName },
            { label: 'Started', value: formatDateTime(s.startedAt) },
            { label: 'People let in', value: String(s.allowedCount) },
            { label: 'Refused', value: String(refused) },
            { label: 'Scans in total', value: String(s.totalScans) },
          ]}
        />
      </section>

      <p className="text-caption text-[var(--ink-500)]">
        &ldquo;People let in&rdquo; includes anyone shown as CHECK — an amber scan is
        somebody who entered, with a note on the register. The totals are kept on the shift
        record as each scan is written, so they always match the entry log.
      </p>

      <Button size="lg" block onClick={() => setConfirming(true)}>
        End shift
      </Button>

      <ConfirmDialog
        open={confirming}
        onOpenChange={(open) => { if (!open) setConfirming(false); }}
        title="End this shift?"
        description={`You will not be able to scan again until you start a new shift. ${s.totalScans} scans at ${s.gateName} will be closed off and kept on the register.`}
        confirmLabel="End shift"
        loading={end.isPending}
        onConfirm={() => end.mutate(s.id)}
      />
    </div>
  );
}
