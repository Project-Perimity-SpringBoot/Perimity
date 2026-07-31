import { Button, LifecycleStrip, PassCard } from '../shared/ui';
import { PASSES, CAMPUS, PEOPLE } from '../mock/data';

/**
 * Screens 2 and 3 — pass detail, and the PAUSED state.
 *
 * The paused panel is the most important copy in the student area. PAUSED is
 * new in v1.1 and looks like a fault: the student did nothing wrong, their
 * pass simply stopped working. So the panel answers the three questions they
 * will actually have, in order — why, who fixes it, and what still works.
 *
 * That last line matters: their EVENT pass is unaffected, and without saying so
 * they will assume everything is dead.
 */
export default function PassDetail({ paused = false }) {
  const pass = PASSES.find((p) => p.type === 'daily' && p.status === 'ACTIVE');
  const status = paused ? 'PAUSED' : 'ACTIVE';

  return (
    <div className="p-stack" style={{ maxWidth: 560 }}>
      <h1 className="p-h1">Daily pass</h1>

      {paused && (
        <div className="p-card p-pad" style={{ borderLeft: '4px solid var(--review-solid)' }}>
          <span className="p-label" style={{ color: 'var(--review-fg)' }}>Pass paused</span>
          <p className="p-body" style={{ margin: '4px 0 0' }}>
            Your pass is paused because you changed your photo on 6 Jul.
            {' '}{PEOPLE.faculty.name} must re-approve it before you can enter.
            {' '}<strong>Your event pass is unaffected.</strong>
          </p>
        </div>
      )}

      <LifecycleStrip status={status} />

      <PassCard type="daily" holder={pass.holder} code={pass.code} campus={CAMPUS.name}
                validity={pass.validity} status={status} />

      <div className="p-card p-pad">
        <dl className="p-pass__meta">
          <dt>Department</dt><dd>{PEOPLE.student.dept}</dd>
          <dt>Roll number</dt><dd className="p-mono">{PEOPLE.student.roll}</dd>
          <dt>Issued</dt><dd>{pass.issued}</dd>
        </dl>
      </div>

      <div className="p-row">
        <Button disabled={paused}>Download PDF</Button>
        <Button variant="secondary">Report lost</Button>
      </div>
    </div>
  );
}
