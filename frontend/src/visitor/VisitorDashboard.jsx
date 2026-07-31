import { useNavigate } from 'react-router-dom';
import { Button, EmptyState, PassCard } from '../shared/ui';
import { PASSES, PEOPLE, CAMPUS } from '../mock/data';

/**
 * Screen 1 — visitor home. Two states: nothing yet, or a live pass.
 *
 * A visitor has exactly one job here, so there is exactly one action. The
 * signed-in email is shown because a visitor authenticates by email code and
 * may not remember which address they used.
 */
export default function VisitorDashboard({ hasPass = false }) {
  const navigate = useNavigate();
  const pass = PASSES.find((p) => p.type === 'visitor');

  return (
    <div className="p-stack" style={{ maxWidth: 560 }}>
      <div>
        <h1 className="p-h1">Welcome, {PEOPLE.visitor.name.split(' ')[0]}</h1>
        <p className="p-caption">Signed in as {PEOPLE.visitor.email}</p>
      </div>

      {hasPass ? (
        <PassCard
          type="visitor" holder={pass.holder} code={pass.code} campus={CAMPUS.name}
          validity={pass.validity} status={pass.status}
          footer={
            <div className="p-row">
              <Button>Download PDF</Button>
              <Button variant="secondary">Add to wallet</Button>
            </div>
          }
        />
      ) : (
        <div className="p-card">
          <EmptyState
            icon="○" title="No active pass"
            message="You do not have a visitor pass yet. Apply and your host will review it."
            actionLabel="Apply for a visitor pass"
            onAction={() => navigate('/visitor/apply')}
          />
        </div>
      )}
    </div>
  );
}
