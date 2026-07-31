import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, FormField, ErrorState, DetailSkeleton } from '../shared/ui';
import { gatepass, useApi } from '../api';

/**
 * Create and edit, one component. LIVE.
 *
 * The date pair is validated here as well as server-side — not because the
 * server check is untrusted, but because "the end date must not be before the
 * start" is worth saying while the user's hand is still on the field, rather
 * than after a round trip that clears nothing and explains less.
 */
export default function EventForm() {
  const { id } = useParams();
  const nav = useNavigate();
  const editing = Boolean(id);

  const { data: existing, loading, error } = useApi(
    () => gatepass.event(id), [id], { skip: !editing });

  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const v = form ?? (existing
    ? { name: existing.name, description: existing.description ?? '',
        validFrom: existing.validFrom, validTo: existing.validTo }
    : { name: '', description: '', validFrom: '', validTo: '' });

  const set = (k) => (e) => setForm({ ...v, [k]: e.target.value });

  if (editing && loading) return <DetailSkeleton />;
  if (editing && error)   return <ErrorState title="Could not load this event" message={error.message} />;

  const datesBackwards = v.validFrom && v.validTo && v.validTo < v.validFrom;
  const ready = v.name.trim() && v.validFrom && v.validTo && !datesBackwards;

  const save = async () => {
    setSaving(true); setSaveError(null);
    try {
      const saved = editing
        ? await gatepass.updateEvent(id, v)
        : await gatepass.createEvent(v);
      nav(`/events/${saved.id}`);
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-stack" style={{ maxWidth: 560 }}>
      <h1 className="p-h1">{editing ? 'Edit event' : 'Create event'}</h1>

      <div className="p-card p-pad">
        <div className="p-stack">
          <FormField label="Event name" required value={v.name} onChange={set('name')} />
          <FormField label="Description" as="textarea" value={v.description}
                     onChange={set('description')} />
          <FormField label="First day" type="date" required value={v.validFrom}
                     onChange={set('validFrom')}
                     help="Passes for this event scan from this date." />
          <FormField label="Last day" type="date" required value={v.validTo}
                     onChange={set('validTo')}
                     error={datesBackwards ? 'The last day cannot be before the first.' : undefined} />

          {saveError && <ErrorState title="Could not save" message={saveError.message} onRetry={save} />}

          <div className="p-row">
            <Button variant="secondary" onClick={() => nav(-1)}>Cancel</Button>
            <Button onClick={save} disabled={!ready} loading={saving}>
              {editing ? 'Save changes' : 'Create event'}
            </Button>
          </div>
        </div>
      </div>

      {editing && (
        <p className="p-caption">
          Changing the dates does not re-issue passes already sent. Holders keep
          the pass they have; only the window it scans in moves.
        </p>
      )}
    </div>
  );
}
