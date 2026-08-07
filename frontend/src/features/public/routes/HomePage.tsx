import { Link } from 'react-router';
import {
  ArrowRight, BadgeCheck, Building2, CalendarDays, ChevronRight, ClipboardList, GraduationCap,
  Layers, Lock, LogIn, Mail, QrCode, ScanLine, ShieldCheck, Timer, UserRound, Users,
} from 'lucide-react';
import { Button } from '@ui/index';
import type { Role } from '@/types/enums';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';
import { ROLE_LABEL } from '@/layouts/navigation';

interface RoleCard {
  role: Role;
  title: string;
  method: string;
  blurb: string;
  icon: typeof UserRound;
  to: string;
  tint: string;
}

/**
 * Login method per role is authoritative — it comes from Role.java.
 * `tint` orders the cards darkest to lightest across the grid.
 */
const ROLE_CARDS: RoleCard[] = [
  {
    role: 'STUDENT', title: 'Student Portal', method: 'Password or email code',
    blurb: 'Your active pass, entry history and profile documents.',
    icon: GraduationCap, to: '/login', tint: 'var(--portal-1)',
  },
  {
    role: 'FACULTY', title: 'Faculty Portal', method: 'Password or email code',
    blurb: 'Approve visitor requests, run events and issue passes in bulk.',
    icon: BadgeCheck, to: '/login', tint: 'var(--portal-2)',
  },
  {
    role: 'VISITOR', title: 'Visitor Pass', method: 'Email code, no account needed',
    blurb: 'Request a pass, confirm your email, collect the QR.',
    icon: UserRound, to: '/register/visitor', tint: 'var(--portal-3)',
  },
  {
    role: 'GUARD', title: 'Guard Console', method: 'Password only',
    blurb: 'Scan at the gate, one verdict per pass, logged instantly.',
    // Guards get their own entrance. The dark, large-target screen is built
    // for a phone held one-handed at a gate, and it deliberately offers no
    // email-code option — Role.canLoginWithOtp() excludes GUARD, so that link
    // would send a guard to wait for a code that is never issued.
    icon: ScanLine, to: '/guard/login', tint: 'var(--portal-4)',
  },
  {
    role: 'CAMPUS_ADMIN', title: 'Campus Admin', method: 'Password only',
    blurb: 'Accounts, gates, departments, blocklist and entry policy.',
    icon: Building2, to: '/login', tint: 'var(--portal-5)',
  },
  {
    role: 'SUPER_ADMIN', title: 'Super Admin', method: 'Password only',
    blurb: 'Campuses across the platform and the admins who run them.',
    icon: ShieldCheck, to: '/login', tint: 'var(--portal-6)',
  },
];

/**
 * Hero panel. The PICT page this layout follows fills this space with one
 * institution's accreditations. Perimity is campus-agnostic — that is the
 * whole product thesis — so putting a college here would contradict the
 * sentence directly to its left. These are the system's own properties.
 */
const CAPABILITIES = [
  { icon: QrCode, title: 'QR-verified entry', body: 'Encrypted, time-bound tokens checked at the gate' },
  { icon: Users, title: 'Six roles, one system', body: 'Student, faculty, visitor, guard, admin, platform' },
  { icon: ClipboardList, title: 'Complete audit trail', body: 'Every scan, approval and refusal is recorded' },
  { icon: Layers, title: 'One deployment, any campus', body: 'Campus-scoped data, a single shared codebase' },
];

const FEATURES = [
  { icon: QrCode, title: 'QR pass generation', body: 'Encrypted tokens issued per pass and delivered as a PDF.', bg: 'var(--feat-1-bg)', fg: 'var(--feat-1-fg)' },
  { icon: Timer, title: 'Time-bound validity', body: 'A pass is checked against its own window on every scan.', bg: 'var(--feat-2-bg)', fg: 'var(--feat-2-fg)' },
  { icon: CalendarDays, title: 'Event passes', body: 'Create an event and issue passes to its whole attendee list.', bg: 'var(--feat-3-bg)', fg: 'var(--feat-3-fg)' },
  { icon: Users, title: 'Visitor approvals', body: 'A request reaches its host, who approves or refuses with a reason.', bg: 'var(--feat-4-bg)', fg: 'var(--feat-4-fg)' },
  { icon: Lock, title: 'Role-scoped access', body: 'Every endpoint is authorised by role and by campus, not by screen.', bg: 'var(--feat-5-bg)', fg: 'var(--feat-5-fg)' },
  { icon: ClipboardList, title: 'Live entry logs', body: 'Each scan writes an append-only record the moment it happens.', bg: 'var(--feat-6-bg)', fg: 'var(--feat-6-fg)' },
  { icon: Mail, title: 'Email delivery', body: 'Passes and one-time codes arrive without anyone chasing them.', bg: 'var(--feat-7-bg)', fg: 'var(--feat-7-fg)' },
  { icon: Building2, title: 'Campus console', body: 'Gates, departments, blocklist and entry policy in one place.', bg: 'var(--feat-8-bg)', fg: 'var(--feat-8-fg)' },
];

const STEPS = [
  { n: 1, title: 'Request', body: 'Fill in one short form and pick your host.' },
  { n: 2, title: 'Verify', body: 'Confirm your email with a 6-digit code.' },
  { n: 3, title: 'Get your QR pass', body: 'Your pass arrives by email as a PDF.' },
  { n: 4, title: 'Scan at the gate', body: 'One scan, one verdict, logged instantly.' },
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
   *
   * ONLY THE PRESENTATION CHANGED. The three branches at the bottom of this
   * file are the ones that took four attempts to get right; they are byte for
   * byte what they were.
   */
  const { isAuthenticated, role, profile, identity, logout } = useAuth();
  const signedInAs = profile?.name ?? identity?.name ?? '';

  return (
    <div className="min-h-dvh bg-[var(--surface)]">
      {/* ── utility bar ─────────────────────────────────────────────── */}
      <div className="bg-[var(--home-bar)]">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-2)]">
          <span className="text-small flex items-center gap-[var(--sp-4)] text-[var(--home-bar-ink)]">
            <span className="flex items-center gap-[var(--sp-2)]">
              <ShieldCheck className="size-4" aria-hidden />
              Forgery-proof digital gate passes
            </span>
            <a href="mailto:perimity.info@gmail.com" className="hidden items-center gap-[var(--sp-2)] hover:text-white sm:flex">
              <Mail className="size-4" aria-hidden />
              perimity.info@gmail.com
            </a>
          </span>
          <Link to="/register/visitor" className="text-small flex items-center gap-[var(--sp-1)] text-[var(--home-bar-ink)] hover:text-white">
            New here? Request a visitor pass
            <ChevronRight className="size-4" aria-hidden />
          </Link>
        </div>
      </div>

      {/* ── header ──────────────────────────────────────────────────── */}
      <header className="border-b border-[var(--border)] bg-[var(--surface)]">
        <div className="mx-auto flex max-w-[1200px] items-center justify-between gap-[var(--sp-4)] px-[var(--sp-6)] py-[var(--sp-4)]">
          <span className="flex items-center gap-[var(--sp-3)]">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
              <ShieldCheck className="size-6 text-white" aria-hidden />
            </span>
            <span>
              <span className="text-h2 block leading-tight text-[var(--ink-900)]">Perimity</span>
              <span className="text-small block leading-tight text-[var(--brand-600)]">Smart Campus Access</span>
            </span>
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
            <div className="flex items-center gap-[var(--sp-2)]">
              <Button asChild size="sm">
                <Link to="/login">Sign in</Link>
              </Button>
              <Button asChild variant="secondary" size="sm">
                <Link to="/guard/login">Guard sign-in</Link>
              </Button>
            </div>
          )}
        </div>
      </header>

      <main>
        {/* ── hero ──────────────────────────────────────────────────── */}
        <section className="bg-[var(--surface)]">
          <div className="mx-auto grid max-w-[1200px] items-center gap-[var(--sp-8)] px-[var(--sp-6)] py-[var(--sp-12)] lg:grid-cols-[1.05fr_0.95fr]">
            <div>
              <span className="text-small inline-flex items-center gap-[var(--sp-2)] rounded-[var(--r-pill)] border border-[var(--brand-200)] bg-[var(--brand-50)] px-[var(--sp-4)] py-[var(--sp-2)] text-[var(--brand-600)]">
                <ShieldCheck className="size-4" aria-hidden />
                Secure digital solution
              </span>

              <h1 className="text-hero mt-[var(--sp-6)] text-[var(--home-ink-deep)]">
                Perimity
              </h1>
              <p className="text-hero-sub mt-[var(--sp-2)] text-[var(--brand-600)]">
                Replace the paper gate register.
              </p>

              <p className="text-body mt-[var(--sp-6)] max-w-xl text-[var(--ink-700)]">
                Digital, searchable, forgery-proof gate passes verified in seconds — designed for
                any campus rather than configured for one.
              </p>
              <p className="text-body mt-[var(--sp-3)] max-w-xl text-[var(--ink-500)]">
                QR-based verification with live entry logs, a complete audit trail, and one flow
                each for students, faculty, visitors and guards.
              </p>

              <div className="mt-[var(--sp-8)] flex flex-wrap gap-[var(--sp-3)]">
                <Button asChild size="lg">
                  <Link to="/register/visitor">
                    Request a visitor pass
                    <ArrowRight aria-hidden />
                  </Link>
                </Button>
                <Button asChild size="lg" variant="secondary">
                  <Link to="/login">
                    Sign in
                    <ChevronRight aria-hidden />
                  </Link>
                </Button>
              </div>
            </div>

            {/* Right panel — what the system is, not who is running it. */}
            <div className="rounded-[var(--r-lg)] bg-[var(--home-hero-panel)] p-[var(--sp-6)]">
              <h2 className="text-h1 text-[var(--home-ink-deep)]">Built for every campus</h2>
              <p className="text-small mt-[var(--sp-2)] text-[var(--brand-600)]">
                Six microservices · Six roles · One codebase
              </p>

              <ul className="mt-[var(--sp-6)] grid gap-[var(--sp-3)]">
                {CAPABILITIES.map((item) => (
                  <li
                    key={item.title}
                    className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)] border border-[var(--brand-200)] bg-[var(--surface)] p-[var(--sp-4)]"
                  >
                    <item.icon className="mt-[var(--sp-1)] size-5 shrink-0 text-[var(--brand-600)]" aria-hidden />
                    <span>
                      <span className="text-body-md block text-[var(--ink-900)]">{item.title}</span>
                      <span className="text-small block text-[var(--brand-600)]">{item.body}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        {/* ── portals ───────────────────────────────────────────────── */}
        <section aria-labelledby="roles" className="bg-[var(--surface-subtle)] py-[var(--sp-16)]">
          <div className="mx-auto max-w-[1200px] px-[var(--sp-6)]">
            <h2 id="roles" className="text-section text-center text-[var(--home-ink-deep)]">
              Campus access portals
            </h2>
            <p className="text-body mt-[var(--sp-3)] text-center text-[var(--ink-500)]">
              One way in for every kind of person who passes through a gate
            </p>

            {/* Without this, the cards below are a trap: they all lead to
                /login, which bounces you back to wherever you are signed in. */}
            {isAuthenticated && role ? (
              <p className="text-small mx-auto mt-[var(--sp-6)] max-w-3xl rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface)] px-[var(--sp-4)] py-[var(--sp-3)] text-center text-[var(--ink-700)]">
                You are signed in as <strong>{signedInAs || ROLE_LABEL[role]}</strong> ({ROLE_LABEL[role]}).
                Your own role below takes you straight back in. Choosing a different one signs you
                out and opens that sign-in screen.
              </p>
            ) : null}

            <ul className="mt-[var(--sp-8)] grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-3">
              {ROLE_CARDS.map((card) => {
                const isCurrent = isAuthenticated && role === card.role;

                const cardClass =
                  'flex h-full w-full flex-col items-start gap-[var(--sp-3)] rounded-[var(--r-lg)] ' +
                  'p-[var(--sp-6)] text-left text-white shadow-[var(--sh-card)] ' +
                  'transition-transform duration-[var(--motion-base)] ease-[var(--ease-out)] ' +
                  'hover:-translate-y-1 focus-visible:-translate-y-1';

                const body = (
                  <>
                    <span className="flex size-14 items-center justify-center rounded-[var(--r-md)] bg-white/15">
                      <card.icon className="size-7" aria-hidden />
                    </span>
                    <span className="text-h1 mt-[var(--sp-2)] block">{card.title}</span>
                    <span className="text-small block text-white/80">{card.blurb}</span>
                    <span className="text-caption block text-white/70">{card.method}</span>
                    <span className="text-small mt-auto inline-flex items-center gap-[var(--sp-2)] rounded-[var(--r-sm)] bg-white/15 px-[var(--sp-4)] py-[var(--sp-2)] pt-[var(--sp-2)]">
                      {isCurrent ? 'Continue' : card.role === 'VISITOR' ? 'Get a pass' : 'Sign in'}
                      <ChevronRight className="size-4" aria-hidden />
                    </span>
                  </>
                );

                return (
                  <li key={card.role} className="flex">
                    {isCurrent && role ? (
                      /*
                       * The role you are ALREADY signed in as. Straight through
                       * to the dashboard — signing out only to sign back in as
                       * the same person would be busywork.
                       */
                      <Link to={LANDING_ROUTE[role]} className={cardClass} style={{ background: card.tint }}>
                        {body}
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
                        style={{ background: card.tint }}
                        onClick={() => void logout(card.to)}
                      >
                        {body}
                      </button>
                    ) : (
                      <Link to={card.to} className={cardClass} style={{ background: card.tint }}>
                        {body}
                      </Link>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        </section>

        {/* ── features ──────────────────────────────────────────────── */}
        <section aria-labelledby="features" className="bg-[var(--surface)] py-[var(--sp-16)]">
          <div className="mx-auto max-w-[1200px] px-[var(--sp-6)]">
            <h2 id="features" className="text-section text-center text-[var(--ink-900)]">
              System features
            </h2>
            <p className="text-body mt-[var(--sp-3)] text-center text-[var(--ink-500)]">
              What the six services actually do, in the order a pass moves through them
            </p>

            <ul className="mt-[var(--sp-8)] grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
              {FEATURES.map((feature) => (
                <li
                  key={feature.title}
                  className="rounded-[var(--r-lg)] border border-[var(--border)] p-[var(--sp-6)]"
                  style={{ background: feature.bg }}
                >
                  <span
                    className="flex size-11 items-center justify-center rounded-[var(--r-md)] bg-[var(--surface)]"
                    style={{ color: feature.fg }}
                  >
                    <feature.icon className="size-5" aria-hidden />
                  </span>
                  <h3 className="text-h2 mt-[var(--sp-4)] text-[var(--ink-900)]">{feature.title}</h3>
                  <p className="text-small mt-[var(--sp-2)] text-[var(--ink-700)]">{feature.body}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ── how it works ──────────────────────────────────────────── */}
        <section aria-labelledby="how" className="bg-[var(--surface-subtle)] py-[var(--sp-16)]">
          <div className="mx-auto max-w-[1200px] px-[var(--sp-6)]">
            <h2 id="how" className="text-section text-center text-[var(--home-ink-deep)]">
              How a visitor pass works
            </h2>
            <p className="text-body mt-[var(--sp-3)] text-center text-[var(--ink-500)]">
              Four steps, no paper, no phone call to the gate
            </p>

            <ol className="mt-[var(--sp-8)] grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
              {STEPS.map((step) => (
                <li key={step.n} className="rounded-[var(--r-lg)] border border-[var(--border)] bg-[var(--surface)] p-[var(--sp-6)]">
                  <span className="flex size-11 items-center justify-center rounded-[var(--r-circle)] bg-[var(--brand-50)] text-[var(--brand-600)]">
                    <span className="text-mono">{step.n}</span>
                  </span>
                  <h3 className="text-h2 mt-[var(--sp-4)] text-[var(--ink-900)]">{step.title}</h3>
                  <p className="text-small mt-[var(--sp-2)] text-[var(--ink-500)]">{step.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>
      </main>

      {/* ── footer ──────────────────────────────────────────────────── */}
      <footer style={{ background: 'var(--home-footer)' }}>
        <div className="mx-auto grid max-w-[1200px] gap-[var(--sp-8)] px-[var(--sp-6)] py-[var(--sp-16)] lg:grid-cols-[1.05fr_0.95fr]">
          <div>
            <h2 className="text-stat text-white">About Perimity</h2>
            <span aria-hidden className="mt-[var(--sp-3)] block h-1 w-16 rounded-[var(--r-pill)] bg-[var(--brand-300)]" />

            <p className="text-body mt-[var(--sp-6)] max-w-lg text-[var(--home-footer-ink)]">
              A campus-agnostic gate pass and access management system. One deployment serves every
              campus, with each campus's data scoped to itself.
            </p>

            <dl className="mt-[var(--sp-8)] grid gap-[var(--sp-6)]">
              <div className="flex items-start gap-[var(--sp-4)]">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-white/10">
                  <QrCode className="size-5 text-white" aria-hidden />
                </span>
                <div>
                  <dt className="text-body-md text-white">Secure QR Passes</dt>
                  <dd className="text-small text-[var(--home-footer-ink)]">
                    Encrypted, time-bound QR codes delivered directly via email as PDF passes
                  </dd>
                </div>
              </div>
              <div className="flex items-start gap-[var(--sp-4)]">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-white/10">
                  <ShieldCheck className="size-5 text-white" aria-hidden />
                </span>
                <div>
                  <dt className="text-body-md text-white">Real-Time Gate Auditing</dt>
                  <dd className="text-small text-[var(--home-footer-ink)]">
                    Instant guard scanner verdicts with full entry logs and campus security controls
                  </dd>
                </div>
              </div>
              <div className="flex items-start gap-[var(--sp-4)]">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-white/10">
                  <Mail className="size-5 text-white" aria-hidden />
                </span>
                <div>
                  <dt className="text-body-md text-white">Support</dt>
                  <dd className="text-small text-[var(--home-footer-ink)]">perimity.info@gmail.com</dd>
                </div>
              </div>
            </dl>
          </div>

          {/* Action card. Brand and neutral only — see the note in tokens.css
              about why nothing here is green. */}
          <div className="rounded-[var(--r-lg)] border border-white/10 bg-white/5 p-[var(--sp-6)]">
            <h3 className="text-h1 text-white">Get started</h3>
            <p className="text-small mt-[var(--sp-2)] text-[var(--home-footer-ink)]">
              Pick the way in that matches who you are
            </p>

            <div className="mt-[var(--sp-6)] grid gap-[var(--sp-3)]">
              <Button asChild size="lg" block>
                <Link to="/register/visitor">
                  <UserRound aria-hidden />
                  Request a visitor pass
                </Link>
              </Button>

              <Link
                to="/login"
                className="text-body-md flex items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-sm)] border border-white/25 px-[var(--sp-4)] py-[var(--sp-3)] text-white transition-colors hover:bg-white/10"
              >
                <LogIn className="size-5" aria-hidden />
                Student / Faculty sign-in
              </Link>

              <div className="mt-[var(--sp-2)] grid grid-cols-2 gap-[var(--sp-3)] border-t border-white/10 pt-[var(--sp-4)]">
                <Link
                  to="/login"
                  className="text-small flex items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-sm)] border border-white/15 px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--home-footer-ink)] transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Building2 className="size-4" aria-hidden />
                  Admin
                </Link>
                <Link
                  to="/guard/login"
                  className="text-small flex items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-sm)] border border-white/15 px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--home-footer-ink)] transition-colors hover:bg-white/10 hover:text-white"
                >
                  <ScanLine className="size-4" aria-hidden />
                  Guard
                </Link>
              </div>
            </div>

            <p className="text-caption mt-[var(--sp-6)] text-center text-[var(--home-footer-ink)]">
              Secure digital gate passes for safe campus access
            </p>
          </div>
        </div>

        <div className="border-t border-white/10">
          <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-6)]">
            <span className="text-small text-[var(--home-footer-ink)]">Perimity</span>
            <nav aria-label="Footer" className="flex gap-[var(--sp-4)] text-small text-[var(--home-footer-ink)]">
              <Link to="/support" className="hover:text-white">Support</Link>
              <Link to="/privacy" className="hover:text-white">Privacy</Link>
              <Link to="/terms" className="hover:text-white">Terms</Link>
            </nav>
          </div>
        </div>
      </footer>
    </div>
  );
}
