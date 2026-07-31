import { useState } from 'react';
import { Button, FormField } from '../shared/ui';
import { OTP } from '../mock/data';

/**
 * Screen 9 — per-campus policy.
 *
 * repeat_entry_result is the key guard-service reads to decide what a second
 * scan on the same day shows. GREEN or AMBER — never a boolean, because a
 * boolean cannot express amber, which is the entire point of the setting.
 *
 * The save bar appears only when something is dirty. A permanently visible
 * "Save" on a settings page trains people to press it having changed nothing.
 */
const SECTIONS = [
  { title: 'Approvals', fields: [
    { label: 'Host approval required', as: 'select', options: ['Yes', 'No'], value: 'Yes',
      help: 'If No, a verified visitor request issues a pass without review.' },
    { label: 'Auto-approve returning visitors', as: 'select', options: ['Yes', 'No'], value: 'No' },
  ] },
  { title: 'Passes', fields: [
    { label: 'Daily pass validity (days)', type: 'number', value: 365 },
    { label: 'Maximum visitor pass length (days)', type: 'number', value: 7 },
    { label: 'Photo required for a pass', as: 'select', options: ['Yes', 'No'], value: 'Yes' },
  ] },
  { title: 'Gate', fields: [
    { label: 'Repeat entry result', as: 'select', options: ['AMBER', 'GREEN'], value: 'AMBER',
      help: 'What the guard sees on a second scan the same day. The entry is logged either way.' },
  ] },
  { title: 'Security', fields: [
    { label: 'OTP expiry (minutes)', type: 'number', value: OTP.expiryMinutes },
    { label: 'Maximum OTP attempts', type: 'number', value: OTP.maxAttempts },
  ] },
];

export default function CampusPolicy() {
  const [dirty, setDirty] = useState(false);

  return (
    <div className="p-stack" style={{ maxWidth: 640, paddingBottom: 80 }}>
      <div>
        <h1 className="p-h1">Campus policy</h1>
        <p className="p-caption">These settings apply to this campus only.</p>
      </div>

      {SECTIONS.map((s) => (
        <div className="p-card p-pad p-stack" key={s.title}>
          <span className="p-label">{s.title}</span>
          {s.fields.map((f) => (
            <FormField key={f.label} {...f} defaultValue={f.value}
                       onChange={() => setDirty(true)} value={undefined} />
          ))}
        </div>
      ))}

      {dirty && (
        <div className="p-card p-pad-sm p-spread"
             style={{ position: 'sticky', bottom: 'var(--s-4)', boxShadow: 'var(--sh-overlay)' }}>
          <span className="p-small">You have unsaved changes.</span>
          <div className="p-row">
            <Button variant="secondary" onClick={() => setDirty(false)}>Discard</Button>
            <Button onClick={() => setDirty(false)}>Save changes</Button>
          </div>
        </div>
      )}
    </div>
  );
}
