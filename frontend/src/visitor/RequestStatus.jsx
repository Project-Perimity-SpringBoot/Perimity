import { useNavigate } from 'react-router-dom';
import { Button, LifecycleStrip, StatusBadge } from '../shared/ui';
import { PEOPLE } from '../mock/data';

/**
 * Screens 3 and 4 — submitted, and rejected. One component, two modes.
 *
 * The rejected state did not exist in the original design and is required:
 * a visitor who is refused and told nothing will simply submit again, which
 * is worse for them and for the host.
 *
 * The rejection reason is shown verbatim because the host was required to
 * write one — it is in the audit log either way, so hiding it from the person
 * it concerns achieves nothing.
 */
export default function RequestStatus({ mode = 'submitted' }) {
  const navigate = useNavigate();
  const rejected = mode === 'rejected';

  return (
    <div className="p-stack" style={{ maxWidth: 560 }}>
      <div className="p-card p-pad p-stack">
        <div className="p-spread">
          <h1 className="p-h2">{rejected ? 'Request not approved' : 'Request submitted'}</h1>
          <StatusBadge status={rejected ? 'REVOKED' : 'PENDING'} />
        </div>

        <LifecycleStrip status={rejected ? 'REVOKED' : 'PENDING'} />

        <dl className="p-pass__meta">
          <dt>Request code</dt><dd className="p-mono">{PEOPLE.visitor.code}</dd>
          <dt>Host</dt><dd>{PEOPLE.faculty.name} · {PEOPLE.faculty.dept}</dd>
          <dt>Dates</dt><dd>08–09 Jul 2026</dd>
        </dl>

        {rejected ? (
          <div className="p-card p-pad-sm" style={{ background: 'var(--surface-subtle)' }}>
            <span className="p-label">Reason given</span>
            <p className="p-body" style={{ margin: 0 }}>
              The requested dates fall during a campus closure. Please choose a
              date after 12 July.
            </p>
            <span className="p-caption">Recorded 8 Jul 2026</span>
          </div>
        ) : (
          <p className="p-body p-muted" style={{ margin: 0 }}>
            {PEOPLE.faculty.name} will review this. You will get an email either
            way — if approved, it will contain your QR pass.
          </p>
        )}
      </div>

      {rejected
        ? <Button onClick={() => navigate('/visitor/apply')}>Submit a new request</Button>
        : <Button variant="secondary" onClick={() => navigate('/visitor')}>Back to dashboard</Button>}
    </div>
  );
}
