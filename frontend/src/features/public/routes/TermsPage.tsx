import { Link } from 'react-router';
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  FileCheck,
  Gavel,
  Mail,
  QrCode,
  ShieldAlert,
  ShieldCheck,
  UserCheck
} from 'lucide-react';
import { Button } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';

export default function TermsPage() {
  const { isAuthenticated, role, logout } = useAuth();

  return (
    <div className="min-h-dvh bg-[var(--surface)]">
      {/* Utility Bar */}
      <div className="bg-[var(--home-bar)]">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-2)]">
          <span className="text-small flex items-center gap-[var(--sp-4)] text-[var(--home-bar-ink)]">
            <span className="flex items-center gap-[var(--sp-2)]">
              <ShieldCheck className="size-4" aria-hidden />
              Perimity Terms of Service & Campus Access Agreement
            </span>
          </span>
          <Link
            to="/"
            className="text-small flex items-center gap-[var(--sp-1)] text-[var(--home-bar-ink)] hover:text-white"
          >
            <ArrowLeft className="size-4" />
            Back to Home
          </Link>
        </div>
      </div>

      {/* Header */}
      <header className="border-b border-[var(--border)] bg-[var(--surface)]">
        <div className="mx-auto flex max-w-[1200px] items-center justify-between gap-[var(--sp-4)] px-[var(--sp-6)] py-[var(--sp-4)]">
          <Link to="/" className="flex items-center gap-[var(--sp-3)]">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
              <ShieldCheck className="size-6 text-white" aria-hidden />
            </span>
            <span>
              <span className="text-h2 block leading-tight text-[var(--ink-900)]">Perimity</span>
              <span className="text-small block leading-tight text-[var(--brand-600)]">
                Terms of Service
              </span>
            </span>
          </Link>

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

      {/* Hero Banner */}
      <section className="bg-gradient-to-b from-[var(--home-bar)] to-[#111827] px-[var(--sp-6)] pt-16 pb-24 sm:pt-20 sm:pb-28 text-white">
        <div className="mx-auto max-w-[900px]">
          <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl md:text-5xl">
            Perimity Terms of Service
          </h1>
          <p className="mt-3 text-sm text-gray-300 sm:text-base">
            Last Updated: August 2026 &bull; User Agreement for Campus Gate Access & QR Pass Management
          </p>
        </div>
      </section>

      {/* Main Content Layout */}
      <div className="mx-auto max-w-[1200px] px-[var(--sp-6)] py-16 sm:py-24">
        <div className="grid gap-10 lg:grid-cols-12">
          {/* Left Sticky Sidebar Table of Contents */}
          <aside className="lg:col-span-4">
            <div className="sticky top-6 rounded-xl border border-[var(--border)] bg-[var(--surface)] p-6 shadow-xs">
              <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--ink-900)]">
                Terms Sections
              </h3>
              <nav className="mt-4 flex flex-col space-y-2 text-xs font-medium text-[var(--ink-700)]">
                <a href="#acceptance" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <FileCheck className="size-4 text-[var(--brand-600)]" /> 1. Acceptance of Terms
                </a>
                <a href="#pass-rules" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <QrCode className="size-4 text-[var(--brand-600)]" /> 2. Gate Pass Rules & Non-Transferability
                </a>
                <a href="#guard-discretion" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <ShieldAlert className="size-4 text-[var(--brand-600)]" /> 3. Security Guard Authority & Entry
                </a>
                <a href="#user-obligations" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <UserCheck className="size-4 text-[var(--brand-600)]" /> 4. User Obligations & Accuracy
                </a>
                <a href="#prohibited" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <AlertTriangle className="size-4 text-[var(--brand-600)]" /> 5. Prohibited Misconduct
                </a>
                <a href="#governing-law" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <Gavel className="size-4 text-[var(--brand-600)]" /> 6. Governing Law & Disclaimers
                </a>
              </nav>

              <div className="mt-6 rounded-lg bg-[var(--deny-bg)]/50 p-4 text-xs text-[var(--deny-fg)] border border-[var(--deny-fg)]/20">
                <p className="font-bold">Pass Forgery Warning</p>
                <p className="mt-1 leading-relaxed">
                  Attempting to alter, forge, or reuse digital QR passes is a violation of campus security policies and results in permanent account suspension and blocklisting.
                </p>
              </div>
            </div>
          </aside>

          {/* Right Detailed Terms Text */}
          <main className="space-y-12 lg:col-span-8">
            {/* Section 1 */}
            <section id="acceptance" className="scroll-mt-6">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                1. Acceptance of Terms & Scope of Agreement
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  By accessing or using the Perimity Digital Gate Pass System ("Service"), applying for a visitor pass, or authenticating via student, faculty, guard, or administrative credentials, you agree to be bound by these Terms of Service.
                </p>
                <p>
                  If you do not agree with any part of these terms, you must not proceed with pass applications or system usage.
                </p>
              </div>
            </section>

            {/* Section 2 */}
            <section id="pass-rules" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                2. Digital Gate Pass Rules & Non-Transferability
              </h2>
              <div className="mt-4 space-y-4 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  Every QR gate pass issued by Perimity is personal, time-bound, and strictly non-transferable:
                </p>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-lg border border-[var(--border)] bg-[var(--surface-subtle)] p-4">
                    <CheckCircle2 className="size-5 text-[var(--brand-600)]" />
                    <h4 className="mt-2 text-sm font-bold text-[var(--ink-900)]">Personal & Non-Transferable</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      A pass is issued solely to the named applicant. Transferring or selling a pass to another person is strictly forbidden.
                    </p>
                  </div>
                  <div className="rounded-lg border border-[var(--border)] bg-[var(--surface-subtle)] p-4">
                    <CheckCircle2 className="size-5 text-[var(--brand-600)]" />
                    <h4 className="mt-2 text-sm font-bold text-[var(--ink-900)]">Strict Entry Windows</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      Passes are active only within the approved start and end timestamps. Scans outside this window will produce an EXPIRED verdict.
                    </p>
                  </div>
                </div>
              </div>
            </section>

            {/* Section 3 */}
            <section id="guard-discretion" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                3. Security Guard Authority & Final Entry Verdict
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  An approved digital QR pass grants conditional permission to present for physical gate scanning. It does not guarantee automatic entry.
                </p>
                <p>
                  Campus Security Officers (Guards) maintain absolute final authority at all gate entry points to inspect photo identification, verify physical credentials, or deny entry if campus safety concerns arise.
                </p>
              </div>
            </section>

            {/* Section 4 */}
            <section id="user-obligations" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                4. User Obligations & Accuracy of Information
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  Users must provide truthful, complete, and accurate information when applying for passes or registering profiles:
                </p>
                <ul className="list-disc space-y-2 pl-5 text-sm">
                  <li>
                    <strong>Visitors:</strong> Must state genuine visit reasons and valid government or institutional ID details.
                  </li>
                  <li>
                    <strong>Faculty Hosts:</strong> Are responsible for verifying visitor legitimacy before granting pass approval.
                  </li>
                  <li>
                    <strong>Students:</strong> Must upload valid profile details and adhere to department verification requirements.
                  </li>
                </ul>
              </div>
            </section>

            {/* Section 5 */}
            <section id="prohibited" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                5. Prohibited Misconduct & Enforcement
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>The following activities constitute severe breaches of these Terms:</p>

                <ul className="list-disc space-y-2 pl-5 text-sm">
                  <li>Forging, altering, or tampering with encrypted QR payload strings or PDF pass headers.</li>
                  <li>Sharing single-use verification OTPs or login credentials with unauthorized third parties.</li>
                  <li>Attempting reverse engineering, API brute-forcing, or denial-of-service against Perimity microservices.</li>
                  <li>Misrepresenting identity or posing as campus faculty or administrative personnel.</li>
                </ul>

                <p className="mt-3">
                  Violations will result in immediate pass revocation, placement on the campus security blocklist, and referral to campus administration or law enforcement.
                </p>
              </div>
            </section>

            {/* Section 6 */}
            <section id="governing-law" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                6. Service Availability & Legal Contact
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  <strong>Service Availability:</strong> Perimity strives for 99.9% uptime, but does not guarantee uninterrupted operational availability during scheduled system upgrades or force majeure events.
                </p>
                <div className="mt-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-xl border border-[var(--border)] bg-[var(--surface-subtle)] p-6">
                  <div>
                    <h4 className="text-base font-bold text-[var(--ink-900)]">Legal & Compliance Office</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">For formal terms inquiries or legal notices</p>
                    <a href="mailto:legal@perimity.com" className="mt-2 inline-flex items-center gap-2 font-semibold text-[var(--brand-600)] hover:underline text-sm">
                      <Mail className="size-4" /> legal@perimity.com
                    </a>
                  </div>
                  <Button asChild size="sm" variant="secondary">
                    <Link to="/support">Support Center</Link>
                  </Button>
                </div>
              </div>
            </section>
          </main>
        </div>
      </div>

      {/* Footer */}
      <footer className="bg-[#111827] text-white">
        <div className="border-t border-white/10">
          <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-6)]">
            <span className="text-small text-[var(--home-footer-ink)]">
              &copy; {new Date().getFullYear()} Perimity. All rights reserved.
            </span>
            <nav aria-label="Footer" className="flex gap-[var(--sp-4)] text-small text-[var(--home-footer-ink)]">
              <Link to="/support" className="hover:text-white">
                Support
              </Link>
              <Link to="/privacy" className="hover:text-white">
                Privacy
              </Link>
              <Link to="/terms" className="text-white font-semibold underline">
                Terms
              </Link>
            </nav>
          </div>
        </div>
      </footer>
    </div>
  );
}
