import { Link } from 'react-router';
import {
  ArrowRight, BadgeCheck, Building2, GraduationCap, IdCard, ScanLine, ShieldCheck, UserRound,
} from 'lucide-react';
import { Badge, Button } from '@ui/index';
import type { Role } from '@/types/enums';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';
import { ROLE_LABEL } from '@/layouts/navigation';

interface RoleCard {
  role: Role;
  title: string;
  method: string;
  icon: typeof UserRound;
  to: string;
}

/** Login method per role is authoritative — it comes from Role.java. */
const ROLE_CARDS: RoleCard[] = [
  { role: 'VISITOR', title: 'Visitor', method: 'Email code, no account needed', icon: UserRound, to: '/register/visitor' },
  { role: 'STUDENT', title: 'Student', method: 'Password or email code', icon: GraduationCap, to: '/login' },
  { role: 'FACULTY', title: 'Faculty', method: 'Password or email code', icon: BadgeCheck, to: '/login' },
  { role: 'CAMPUS_ADMIN', title: 'Campus Admin', method: 'Password only', icon: Building2, to: '/login' },
  { role: 'SUPER_ADMIN', title: 'Super Admin', method: 'Password only', icon: ShieldCheck, to: '/login' },
  // Guards get their own entrance. The dark, large-target screen is built for a
  // phone held one-handed at a gate, and it deliberately offers no email-code
  // option — Role.canLoginWithOtp() excludes GUARD, so that link would send a
  // guard to wait for a code that is never issued.
  { role: 'GUARD', title: 'Guard', method: 'Password only', icon: ScanLine, to: '/guard/login' },
];

const STEPS = [
  { n: 1, title: 'Request', body: 'Fill in one short form and pick your host.' },
  { n: 2, title: 'Verify', body: 'Confirm your email with a 6-digit code.' },
  { n: 3, title: 'Get your QR pass', body: 'Your pass arrives by email as a PDF.' },
  { n: 4, title: 'Scan at the gate', body: 'One scan, one green light, logged instantly.' },
];

export default function HomePage() {
  /*
   * The home page is public, so a signed-in user can reach it — by signing out
   * (which lands here), or with the back button.
   *
   * Every role card points at /login, and PublicOnlyRoute sends an
   * authenticated user straight back to their own dashboard. Correct, but it
   * reads as a bug: you click "Student" and arrive at the Super Admin console.
   *
   * So the page states who you are and offers the two things that actually
   * work from here — continue, or sign out and pick a different account.
   */
  const { isAuthenticated, role, profile, identity, logout } = useAuth();
  const signedInAs = profile?.name ?? identity?.name ?? '';

  return (
    <div className="min-h-dvh bg-[var(--desk)]">
      <header className="mx-auto flex max-w-[1200px] items-center justify-between px-[var(--sp-6)] py-[var(--sp-4)]">
        <span className="flex items-center gap-[var(--sp-2)]">
          <ShieldCheck className="size-5 text-[var(--brand-600)]" aria-hidden />
          <span className="text-h3 text-[var(--ink-900)]">Perimity</span>
        </span>
        {isAuthenticated && role ? (
          <div className="flex items-center gap-[var(--sp-2)]">
            <Button asChild size="sm">
              <Link to={LANDING_ROUTE[role]}>Go to your dashboard</Link>
            </Button>
            <Button variant="secondary" size="sm" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
        ) : (
          <Button asChild variant="secondary" size="sm">
            <Link to="/login">Sign in</Link>
          </Button>
        )}
      </header>

      <main className="mx-auto max-w-[1200px] px-[var(--sp-6)] pb-[var(--sp-16)]">
        <section className="grid items-center gap-[var(--sp-8)] py-[var(--sp-12)] lg:grid-cols-[1.1fr_0.9fr]">
          <div>
            <h1 className="text-display text-[var(--ink-900)]">Replace the paper gate register.</h1>
            <p className="text-body mt-[var(--sp-4)] max-w-xl text-[var(--ink-700)]">
              Digital, searchable, forgery-proof gate passes verified in seconds. One deployment
              serves every campus.
            </p>
            <div className="mt-[var(--sp-6)] flex flex-wrap gap-[var(--sp-3)]">
              <Button asChild size="lg">
                <Link to="/register/visitor">
                  Request a visitor pass
                  <ArrowRight aria-hidden />
                </Link>
              </Button>
              <Button asChild size="lg" variant="secondary">
                <Link to="/login">Sign in</Link>
              </Button>
            </div>
          </div>

          {/* A sample pass, so the thing being replaced is visible immediately. */}
          <div className="surface-card relative overflow-hidden p-[var(--sp-6)]">
            <span aria-hidden className="absolute inset-x-0 top-0 h-1 bg-[var(--pass-visitor)]" />
            <Badge tone="visitor">Visitor pass</Badge>
            <p className="text-h2 mt-[var(--sp-3)] text-[var(--ink-900)]">Guest lecture</p>
            <p className="text-mono mt-[var(--sp-1)] text-[var(--ink-700)]">PM-4192</p>
            <div className="mt-[var(--sp-6)] flex items-center gap-[var(--sp-4)]">
              <span className="flex size-24 items-center justify-center rounded-[var(--r-md)] bg-[var(--surface-sunken)]">
                <IdCard className="size-10 text-[var(--ink-400)]" aria-hidden />
              </span>
              <div className="text-small text-[var(--ink-500)]">
                <p>Valid for the dates your host approves.</p>
                <p className="mt-[var(--sp-1)]">Show the QR at any gate.</p>
              </div>
            </div>
          </div>
        </section>

        <section aria-labelledby="how" className="py-[var(--sp-8)]">
          <h2 id="how" className="text-h2 text-[var(--ink-900)]">How it works</h2>
          <ol className="mt-[var(--sp-6)] grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step) => (
              <li key={step.n} className="surface-card p-[var(--sp-4)]">
                <span className="text-mono text-[var(--brand-600)]">0{step.n}</span>
                <h3 className="text-h3 mt-[var(--sp-2)] text-[var(--ink-900)]">{step.title}</h3>
                <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        <section aria-labelledby="roles" className="py-[var(--sp-8)]">
          <h2 id="roles" className="text-h2 text-[var(--ink-900)]">Choose your role</h2>
          {/* Without this, the cards below are a trap: they all lead to /login,
              which bounces you back to wherever you are already signed in. */}
          {isAuthenticated && role ? (
            <p className="text-small mt-[var(--sp-4)] rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--ink-700)]">
              You are signed in as <strong>{signedInAs || ROLE_LABEL[role]}</strong> ({ROLE_LABEL[role]}).
              Your own role below takes you straight back in. Choosing a different
              one signs you out and opens that sign-in screen.
            </p>
          ) : null}

          <ul className="mt-[var(--sp-6)] grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-3">
            {ROLE_CARDS.map((card) => {
              const body = (
                <>
                  <card.icon className="size-5 shrink-0 text-[var(--brand-600)]" aria-hidden />
                  <span className="text-left">
                    <span className="text-h3 block text-[var(--ink-900)]">{card.title}</span>
                    <span className="text-small block text-[var(--ink-500)]">{card.method}</span>
                  </span>
                </>
              );
              const isCurrent = isAuthenticated && role === card.role;
              const cardClass =
                'surface-card flex h-full w-full items-start gap-[var(--sp-3)] p-[var(--sp-4)] ' +
                'transition-colors hover:bg-[var(--surface-subtle)]';

              return (
                <li key={card.role}>
                  {isCurrent && role ? (
                    /*
                     * The role you are ALREADY signed in as. Straight through
                     * to the dashboard — signing out only to sign back in as
                     * the same person would be busywork.
                     */
                    <Link to={LANDING_ROUTE[role]} className={cardClass}>
                      {body}
                      <span className="text-caption ml-auto shrink-0 self-center text-[var(--brand-600)]">
                        Continue
                      </span>
                    </Link>
                  ) : isAuthenticated ? (
                    /*
                     * A DIFFERENT way in, so the current session has to end
                     * first. PublicOnlyRoute sends an authenticated user
                     * straight back to their own dashboard, so a plain link
                     * here lands you exactly where you were and reads as a
                     * broken redirect.
                     *
                     * logout() takes the destination, so the session ends and
                     * the sign-in screen appears in one step rather than
                     * bouncing through the home page.
                     */
                    <button
                      type="button"
                      className={cardClass}
                      onClick={() => void logout(card.to)}
                    >
                      {body}
                    </button>
                  ) : (
                    <Link to={card.to} className={cardClass}>
                      {body}
                    </Link>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      </main>

      <footer className="border-t border-[var(--border)] bg-[var(--surface)]">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-6)]">
          <span className="text-small text-[var(--ink-500)]">Perimity</span>
          <nav aria-label="Footer" className="flex gap-[var(--sp-4)] text-small text-[var(--ink-500)]">
            <a href="mailto:support@example.com" className="hover:text-[var(--ink-900)]">Support</a>
            <Link to="/privacy" className="hover:text-[var(--ink-900)]">Privacy</Link>
            <Link to="/terms" className="hover:text-[var(--ink-900)]">Terms</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
