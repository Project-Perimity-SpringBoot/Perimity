import { useState } from 'react';
import { Button, FormField, Modal, StatusBadge } from '../shared/ui';
import { PEOPLE, DEPARTMENTS, CAMPUS } from '../mock/data';

/**
 * Screens 6 and 7 — profile view and edit.
 *
 * THE SENSITIVE-FIELD WARNING IS THE WHOLE POINT OF THIS SCREEN.
 * Editing name, photo, government ID or department moves the holder's pass to
 * PAUSED until a faculty member re-approves it. A student who changes their
 * photo and then cannot get through the gate tomorrow, with no warning today,
 * will file a bug against the gate.
 *
 * So it is said three times, escalating: a glyph on each field, a persistent
 * banner, and a confirmation modal that restates it at the moment of saving.
 * That is not redundancy — each one catches a different reader.
 *
 * Government ID is masked. It is rendered by faculty and guards who have no
 * need for the digits, and an API that returns them in a list response is a
 * breach waiting for someone to open a network tab.
 */
const SENSITIVE = ['name', 'photo', 'govId', 'dept'];

export default function Profile({ editable = false }) {
  const [form, setForm] = useState({
    name: PEOPLE.student.name, roll: PEOPLE.student.roll, dept: PEOPLE.student.dept,
    email: 'sneha.kulkarni@example.com', phone: '+91 98765 43210',
    emergency: 'A. Kulkarni · +91 98765 11111',
  });
  const [confirming, setConfirming] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const Row = ({ label, value }) => (
    <div className="p-spread" style={{ padding: 'var(--s-3) 0', borderBottom: '1px solid var(--border)' }}>
      <span className="p-small p-muted">{label}</span>
      <span className="p-small">{value}</span>
    </div>
  );

  if (!editable) {
    return (
      <div className="p-stack" style={{ maxWidth: 560 }}>
        <div className="p-spread">
          <h1 className="p-h1">Profile</h1>
          <StatusBadge status="ACTIVE" note="verified" />
        </div>
        <div className="p-card p-pad">
          <Row label="Full name" value={form.name} />
          <Row label="Roll number" value={<span className="p-mono">{form.roll}</span>} />
          <Row label="Department" value={form.dept} />
          <Row label="Campus" value={CAMPUS.name} />
          <Row label="Email" value={form.email} />
          <Row label="Phone" value={form.phone} />
          <Row label="Government ID" value={<><span className="p-mono">{PEOPLE.student.govId}</span> ✓</>} />
          <Row label="Emergency contact" value={form.emergency} />
        </div>
        <Button onClick={() => (window.location.href = '/student/profile/edit')}>Edit profile</Button>
      </div>
    );
  }

  return (
    <div className="p-stack" style={{ maxWidth: 560 }}>
      <h1 className="p-h1">Edit profile</h1>

      <div className="p-card p-pad" style={{ borderLeft: '4px solid var(--review-solid)' }}>
        <p className="p-body" style={{ margin: 0 }}>
          Changing your <strong>name, photo, ID or department</strong> will pause
          your pass until a faculty member re-approves it.
        </p>
      </div>

      <div className="p-card p-pad p-stack">
        <FormField label="Full name" value={form.name} onChange={set('name')} pausesPass />
        <FormField label="Roll number" value={form.roll} disabled
                   help="Roll number cannot be changed here — contact your campus admin." />
        <FormField label="Department" as="select" value={form.dept} onChange={set('dept')}
                   pausesPass options={DEPARTMENTS} />
        <FormField label="Email" type="email" value={form.email} disabled
                   help="Your email is your identity across Perimity and cannot change." />
        <FormField label="Phone" type="tel" value={form.phone} onChange={set('phone')} />
        <FormField label="Emergency contact" value={form.emergency} onChange={set('emergency')} />
      </div>

      <div className="p-row">
        <Button onClick={() => setConfirming(true)}>Save changes</Button>
        <Button variant="secondary">Cancel</Button>
      </div>

      <Modal
        open={confirming} onClose={() => setConfirming(false)}
        title="This will pause your pass"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirming(false)}>Go back</Button>
            <Button onClick={() => setConfirming(false)}>Save and pause pass</Button>
          </>
        }
      >
        <p className="p-body" style={{ marginTop: 0 }}>
          You changed a field that requires re-approval. Your daily pass will
          move to <strong>Paused</strong> and will not open a gate until
          {' '}{PEOPLE.faculty.name} approves it.
        </p>
        <p className="p-body p-muted" style={{ marginBottom: 0 }}>
          Your event pass is not affected.
        </p>
      </Modal>
    </div>
  );
}
