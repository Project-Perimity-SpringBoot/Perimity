import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { DoorOpen, LogOut } from 'lucide-react';
import { Button, Skeleton } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { gateApi } from '@lib/api/services/campus.api';
import { sessionApi } from '@lib/api/services/guard.api';
import { campusKeys, guardKeys } from '@lib/query/keys';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { cn } from '@lib/utils/cn';

/**
 * Batch 1 screen 8 — gate session start.
 *
 * COMES IMMEDIATELY AFTER GUARD SIGN-IN AND CANNOT BE SKIPPED. `POST
 * /api/guard/scan` returns 400 without an open session, and every entry log is
 * written against the session's gate — so a guard who skipped this would scan
 * people in against nothing. `GuardSessionGate` redirects here from every guard
 * route until a session exists.
 *
 * ONE GATE FOR THE WHOLE SHIFT. There is no mid-shift switch, because a silent
 * switch would make yesterday's gate report wrong with nothing to reveal it.
 * Changing gate means ending the shift and starting another.
 *
 * Gates are large tap targets rather than a dropdown: this is done outdoors,
 * possibly gloved, and it is the last screen before scanning starts.
 */
export default function GateSessionPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { campusId, logout } = useAuth();
  const [selected, setSelected] = useState<number | null>(null);

  const gates = useQuery({
    queryKey: campusKeys.gates(campusId ?? 0, false),
    queryFn: () => gateApi.list(campusId as number),
    enabled: campusId !== null,
  });

  const start = useMutation({
    mutationFn: (gate: { id: number; name: string }) =>
      sessionApi.start({
        gateId: gate.id,
        gateName: gate.name,
        // Flat and bounded — DeviceInfoRules caps it at ten short entries and
        // rejects nesting outright.
        deviceInfo: { userAgent: navigator.userAgent.slice(0, 200) },
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: guardKeys.currentSession() });
      navigate('/guard', { replace: true });
    },
    onError: (error) => toast.fromError(error, 'That shift could not be started.'),
  });

  const chosen = gates.data?.find((gate) => gate.id === selected);

  return (
    <div className="mx-auto flex w-full max-w-lg flex-col gap-[var(--sp-6)] p-[var(--sp-4)]">
      <header className="flex items-start justify-between gap-[var(--sp-3)]">
        <div>
          <h1 className="text-h1 text-[var(--ink-900)]">Start your shift</h1>
          <p className="text-body mt-[var(--sp-2)] text-[var(--ink-700)]">
            Pick the gate you are working. You are bound to it for the whole shift and
            every scan is logged against it.
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => void logout()} aria-label="Sign out">
          <LogOut aria-hidden />
        </Button>
      </header>

      {campusId === null ? (
        <EmptyState
          heading="No campus on this account"
          description="A guard must belong to a campus. Your campus administrator sets that."
        />
      ) : gates.isError ? (
        <ErrorState error={gates.error} onRetry={() => void gates.refetch()} />
      ) : gates.isPending ? (
        <div className="flex flex-col gap-[var(--sp-2)]">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
      ) : gates.data.length === 0 ? (
        <EmptyState
          icon={DoorOpen}
          heading="No gates are open"
          description="Your campus administrator has not configured an active gate. Nobody can scan until one exists."
        />
      ) : (
        <ul className="flex flex-col gap-[var(--sp-2)]" role="radiogroup" aria-label="Gate">
          {gates.data.map((gate) => (
            <li key={gate.id}>
              <button
                type="button"
                role="radio"
                aria-checked={selected === gate.id}
                onClick={() => setSelected(gate.id)}
                className={cn(
                  'flex min-h-14 w-full items-center gap-[var(--sp-3)] rounded-[var(--r-md)]',
                  'border p-[var(--sp-4)] text-left',
                  selected === gate.id
                    ? 'border-[var(--brand-600)] bg-[var(--brand-50)]'
                    : 'border-[var(--border-strong)] bg-[var(--surface)]',
                )}
              >
                <DoorOpen className="size-5 shrink-0 text-[var(--ink-500)]" aria-hidden />
                <span className="min-w-0">
                  <span className="text-h3 block text-[var(--ink-900)]">{gate.name}</span>
                  {gate.location ? (
                    <span className="text-small block text-[var(--ink-500)]">{gate.location}</span>
                  ) : null}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <Button
        size="lg"
        block
        className="min-h-14"
        disabled={!chosen}
        loading={start.isPending}
        onClick={() => { if (chosen) start.mutate({ id: chosen.id, name: chosen.name }); }}
      >
        {chosen ? `Start shift at ${chosen.name}` : 'Select a gate'}
      </Button>

      <p className="text-caption text-center text-[var(--ink-500)]">
        To work a different gate, end this shift and start another. There is no
        mid-shift switch — it would make the gate report wrong with nothing to show it.
      </p>
    </div>
  );
}
