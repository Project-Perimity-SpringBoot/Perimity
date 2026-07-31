import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, FormField } from '../shared/ui';
import { DEPARTMENTS, PEOPLE } from '../mock/data';

/**
 * Screen 2 — the visitor request form.
 *
 * NO semester field. NO document upload at this stage — a one-time visitor
 * asked to upload ID before they know whether they are even approved will
 * abandon the form, and the host can request documents later if needed.
 *
 * Host is a picker rather than free text: the request has to route to a real
 * person, and a typo'd host name is a request that nobody ever sees.
 */
export default function ApplyForPass() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: '', phone: '', purpose: '', host: '', dept: '', from: '', to: '',
  });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = 'Enter your full name';
    if (!form.purpose.trim()) errs.purpose = 'Say why you are visiting';
    if (!form.host) errs.host = 'Choose who you are visiting';
    if (form.from && form.to && form.to < form.from) errs.to = 'The end date cannot be before the start date';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    setTimeout(() => navigate('/visitor/submitted'), 400);
  };

  return (
    <form className="p-stack" onSubmit={submit} style={{ maxWidth: 560 }}>
      <div>
        <h1 className="p-h1">Apply for a visitor pass</h1>
        <p className="p-body p-muted" style={{ margin: 0 }}>
          Your host reviews this request. You will get an email either way.
        </p>
      </div>

      <div className="p-card p-pad p-stack">
        <FormField label="Full name" required value={form.name}
                   onChange={set('name')} error={errors.name} />
        <FormField label="Phone number" type="tel" value={form.phone} onChange={set('phone')}
                   help="Used only if the campus needs to reach you on the day" />
        <FormField label="Purpose of visit" as="textarea" required value={form.purpose}
                   onChange={set('purpose')} error={errors.purpose} />
        <FormField label="Department" as="select" value={form.dept} onChange={set('dept')}
                   options={[{ value: '', label: 'Select…' }, ...DEPARTMENTS]} />
        <FormField label="Host" as="select" required value={form.host}
                   onChange={set('host')} error={errors.host}
                   options={[{ value: '', label: 'Select…' },
                             { value: 'anaya', label: `${PEOPLE.faculty.name} · ${PEOPLE.faculty.dept}` },
                             { value: 'iyer',  label: 'Dr. M. Iyer · Electronics' }]} />
        <div className="p-row" style={{ alignItems: 'flex-start' }}>
          <div className="p-grow"><FormField label="Visit from" type="date" value={form.from} onChange={set('from')} /></div>
          <div className="p-grow"><FormField label="Visit to" type="date" value={form.to} onChange={set('to')} error={errors.to} /></div>
        </div>
      </div>

      <Button type="submit" size="lg" loading={busy}>Submit request</Button>
    </form>
  );
}
