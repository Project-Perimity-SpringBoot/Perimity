import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { Button, Field, Input, NativeSelect, SkeletonText, Switch } from '@ui/index';
import { PageHeader } from '@components/data';
import { ConfirmDialog, ErrorState } from '@components/feedback';
import { campusConfigApi } from '@lib/api/services/campus.api';
import { campusKeys } from '@lib/query/keys';
import type { CampusConfigUpsertRequest } from '@/types/campus.types';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';

/**
 * Batch 5 screen 9 — campus policy.
 *
 * EXACTLY THE SIX SEEDED KEYS AND NOTHING ELSE. Inventing "session timeout" or
 * "auto-approve returning visitors" would put a switch in front of an admin
 * that silently does nothing, which is worse than the setting being absent.
 *
 * Three of the six are currently read by nobody, and two more are read under
 * different names than campus-service seeds (blocker B7). Each carries a
 * visible note saying so. An admin who flips a switch deserves to know whether
 * anything downstream is listening.
 */
interface PolicyKey {
  key: string;
  label: string;
  description: string;
  valueType: 'BOOLEAN' | 'INTEGER' | 'STRING';
  choices?: readonly string[];
  min?: number;
  max?: number;
  /** Empty string means it is consumed. Otherwise this explains what is not. */
  notConsumed?: string;
}

const POLICY_KEYS: readonly PolicyKey[] = [
  {
    key: 'visitor_approval_required',
    label: 'Visitor requests need approval',
    description: 'When off, a submitted request is auto-approved without a host decision.',
    valueType: 'BOOLEAN',
    notConsumed: 'gatepass-service currently reads a different key name, so this setting has no effect yet.',
  },
  {
    key: 'repeat_entry_result',
    label: 'Second scan the same day',
    description: 'What the guard sees when someone already scanned in today. The entry is logged either way.',
    valueType: 'STRING',
    choices: ['AMBER', 'GREEN'],
  },
  {
    key: 'daily_pass_validity_days',
    label: 'Daily pass validity (days)',
    description: 'How long a newly issued standing pass remains valid.',
    valueType: 'INTEGER',
    min: 1,
    max: 3650,
    notConsumed: 'gatepass-service currently reads a different key name, so this setting has no effect yet.',
  },
  {
    key: 'max_visitor_duration_days',
    label: 'Longest visit (days)',
    description: 'The maximum span a single visitor request may cover.',
    valueType: 'INTEGER',
    min: 1,
    max: 365,
    notConsumed: 'Not read by any service yet.',
  },
  {
    key: 'otp_expiry_minutes',
    label: 'Sign-in code lifetime (minutes)',
    description: 'How long an emailed one-time code stays valid.',
    valueType: 'INTEGER',
    min: 1,
    max: 60,
    notConsumed: 'auth-service uses its own property for this, so changing it here has no effect yet.',
  },
  {
    key: 'photo_required_for_pass',
    label: 'Require a photo before issuing a pass',
    description: 'Whether a holder must have uploaded a photo before a pass is issued.',
    valueType: 'BOOLEAN',
    notConsumed: 'Not read by any service yet.',
  },
];

export default function PolicyPage() {
  const { campusId } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [restoring, setRestoring] = useState(false);

  const config = useQuery({
    queryKey: campusKeys.config(campusId ?? 0),
    queryFn: () => campusConfigApi.list(campusId as number),
    enabled: campusId !== null,
  });

  useEffect(() => {
    if (!config.data) return;
    const next: Record<string, string> = {};
    for (const entry of config.data) next[entry.configKey] = entry.configValue ?? '';
    setDraft(next);
  }, [config.data]);

  const save = useMutation({
    mutationFn: (settings: CampusConfigUpsertRequest[]) =>
      campusConfigApi.upsertAll(campusId as number, { settings }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: campusKeys.config(campusId ?? 0) });
      toast.success('Policy saved');
    },
    onError: (error) => toast.fromError(error, 'That policy could not be saved.'),
  });

  const restore = useMutation({
    mutationFn: () => campusConfigApi.restoreDefaults(campusId as number),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: campusKeys.config(campusId ?? 0) });
      setRestoring(false);
      toast.success('Defaults restored');
    },
    onError: (error) => toast.fromError(error, 'Defaults could not be restored.'),
  });

  if (config.isPending) {
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={10} /></div>;
  }
  if (config.isError) return <ErrorState error={config.error} onRetry={() => void config.refetch()} />;

  const stored = new Map(config.data.map((entry) => [entry.configKey, entry.configValue ?? '']));
  const dirty = POLICY_KEYS.some((policy) => (draft[policy.key] ?? '') !== (stored.get(policy.key) ?? ''));

  const submit = () => {
    // All-or-nothing on the server, so the whole set is sent rather than a diff.
    const settings: CampusConfigUpsertRequest[] = POLICY_KEYS.map((policy) => ({
      configKey: policy.key,
      configValue: draft[policy.key] ?? '',
      valueType: policy.valueType,
      description: policy.label,
    }));
    save.mutate(settings);
  };

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Campus policy"
        description="Settings that change how passes, visits and gates behave at this campus."
        actions={
          <Button variant="secondary" onClick={() => setRestoring(true)}>
            <RotateCcw aria-hidden />Restore defaults
          </Button>
        }
      />

      <div className="surface-card divide-y divide-[var(--border)]">
        {POLICY_KEYS.map((policy) => {
          const value = draft[policy.key] ?? '';
          return (
            <div
              key={policy.key}
              className="grid gap-[var(--sp-4)] p-[var(--sp-6)] sm:grid-cols-[1fr_220px] sm:items-start"
            >
              <div className="min-w-0">
                <p className="text-body-md text-[var(--ink-900)]">{policy.label}</p>
                <p className="text-caption mt-[var(--sp-1)] text-[var(--ink-500)]">{policy.description}</p>
                <p className="text-caption mt-[var(--sp-1)] text-mono text-[var(--ink-400)]">{policy.key}</p>
                {policy.notConsumed ? (
                  <p className="text-caption mt-[var(--sp-2)] rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-2)] py-[var(--sp-1)] text-[var(--ink-500)]">
                    {policy.notConsumed}
                  </p>
                ) : null}
              </div>

              <div>
                {policy.valueType === 'BOOLEAN' ? (
                  <Switch
                    aria-label={policy.label}
                    checked={value === 'true'}
                    onCheckedChange={(checked) =>
                      setDraft((prev) => ({ ...prev, [policy.key]: checked ? 'true' : 'false' }))
                    }
                  />
                ) : policy.choices ? (
                  <Field label={policy.label} className="[&>label]:sr-only">
                    {({ id }) => (
                      <NativeSelect
                        id={id} value={value}
                        onChange={(event) =>
                          setDraft((prev) => ({ ...prev, [policy.key]: event.target.value }))
                        }
                      >
                        {policy.choices?.map((choice) => (
                          <option key={choice} value={choice}>{choice}</option>
                        ))}
                      </NativeSelect>
                    )}
                  </Field>
                ) : (
                  <Field label={policy.label} className="[&>label]:sr-only">
                    {({ id }) => (
                      <Input
                        id={id} type="number" inputMode="numeric"
                        min={policy.min} max={policy.max} value={value}
                        onChange={(event) =>
                          setDraft((prev) => ({ ...prev, [policy.key]: event.target.value }))
                        }
                      />
                    )}
                  </Field>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* The save bar appears only when something changed — a permanently
          enabled Save invites a click that does nothing. */}
      {dirty ? (
        <div className="surface-card sticky bottom-[var(--sp-4)] flex flex-wrap items-center justify-between gap-[var(--sp-3)] p-[var(--sp-4)]">
          <p className="text-small text-[var(--ink-700)]">You have unsaved changes.</p>
          <div className="flex gap-[var(--sp-2)]">
            <Button
              variant="secondary"
              onClick={() => {
                const next: Record<string, string> = {};
                for (const entry of config.data) next[entry.configKey] = entry.configValue ?? '';
                setDraft(next);
              }}
            >
              Discard
            </Button>
            <Button loading={save.isPending} onClick={submit}>Save policy</Button>
          </div>
        </div>
      ) : null}

      <ConfirmDialog
        open={restoring}
        onOpenChange={setRestoring}
        title="Restore default policy?"
        description="Every setting on this page returns to its seeded value. Anything you have customised for this campus is overwritten."
        confirmLabel="Restore defaults"
        destructive
        loading={restore.isPending}
        onConfirm={() => restore.mutate()}
      />
    </div>
  );
}
