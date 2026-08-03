import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { ArrowLeft, PauseCircle } from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { ErrorState } from '@components/feedback';
import { DescriptionList, PageHeader } from '@components/data';
import { LifecycleStrip, PassCard } from '@components/pass';
import { passApi } from '@lib/api/services/gatepass.api';
import { passKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { displayPassCode } from '@lib/format/passCode';

/**
 * Phase 3 screens 2 and 3 — pass detail, and the paused variant of it.
 *
 * Paused is not a separate route. It is the same pass with a different status,
 * and giving it its own URL would mean a link that 404s the moment staff
 * re-verify the profile. The explanation block below is what makes it "screen
 * 3": on a PAUSED pass the page leads with why, before the pass itself.
 *
 * REVOKED is handled by the same block with different copy. A revoked pass is
 * permanent and the reason comes from the server, so the student sees the
 * actual reason rather than a generic refusal.
 */
export default function PassDetailPage() {
  const { id } = useParams();
  const passId = Number(id);

  const pass = useQuery({
    queryKey: passKeys.detail(passId),
    queryFn: () => passApi.getOne(passId),
    enabled: Number.isInteger(passId) && passId > 0,
  });

  if (!Number.isInteger(passId) || passId <= 0) {
    return (
      <ErrorState
        error={new Error('That pass number is not valid.')}
        onRetry={() => window.history.back()}
      />
    );
  }
  if (pass.isPending) {
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={8} /></div>;
  }
  if (pass.isError) {
    return <ErrorState error={pass.error} onRetry={() => void pass.refetch()} />;
  }

  const p = pass.data;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <Button variant="link" asChild className="self-start">
        <Link to="/student"><ArrowLeft aria-hidden />Back to your passes</Link>
      </Button>

      <PageHeader title={displayPassCode(p)} description={p.eventName ?? 'Campus entry pass'} />

      {/* Screen 3. Leads the page when paused - the student's first question is
          "why won't this work", not "what are its dates". */}
      {p.status === 'PAUSED' && (
        <section
          role="alert"
          className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)]
                     border border-[var(--status-border)] bg-[var(--status-bg)] p-[var(--sp-4)]"
        >
          <PauseCircle aria-hidden className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" />
          <div>
            <h2 className="text-body-md text-[var(--ink-900)]">This pass is paused</h2>
            <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
              It will not scan at the gate. A pass pauses automatically when your name,
              photo, government ID or department changes, so that what a guard sees still
              matches your record. Staff re-verify and it resumes — you keep the same QR
              code and nothing is reissued.
            </p>
          </div>
        </section>
      )}

      {p.status === 'REVOKED' && (
        <section
          role="alert"
          className="rounded-[var(--r-md)] border border-[var(--status-border)]
                     bg-[var(--status-bg)] p-[var(--sp-4)]"
        >
          <h2 className="text-body-md text-[var(--ink-900)]">This pass has been revoked</h2>
          <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
            {p.revokedReason
              ? p.revokedReason
              : 'No reason was recorded. Ask your department if you think this is wrong.'}
          </p>
        </section>
      )}

      <PassCard pass={p} variant="detail" />

      <section className="surface-card p-[var(--sp-6)]">
        <h2 className="text-h3 mb-[var(--sp-4)] text-[var(--ink-900)]">Where this pass is</h2>
        <LifecycleStrip current={p.status} />
      </section>

      <section className="surface-card p-[var(--sp-6)]">
        <DescriptionList
          items={[
            { label: 'Pass', value: <span className="text-mono">{displayPassCode(p)}</span> },
            { label: 'Type', value: p.passType },
            { label: 'Valid', value: formatValidity(p.validFrom, p.validTo) },
            ...(p.eventName ? [{ label: 'Event', value: p.eventName }] : []),
            {
              label: 'Scannable now',
              // Server-computed. Preferred over recomputing from status, so the
              // screen cannot disagree with the gate about the same pass.
              value: p.scannable ? 'Yes' : 'No',
            },
            ...(p.revokedAt
              ? [{ label: 'Revoked', value: formatDateTime(p.revokedAt), wide: true }]
              : []),
          ]}
        />
      </section>

      <p className="text-caption text-[var(--ink-500)]">
        Entry is scanned on the way in only. There is no exit scan, so nothing here
        records when you left.
      </p>
    </div>
  );
}
