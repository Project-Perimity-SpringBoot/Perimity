import { Link } from 'react-router';
import {
  ArrowLeft,
  CheckCircle2,
  Database,
  Eye,
  FileText,
  KeyRound,
  Lock,
  Mail,
  Server,
  ShieldCheck,
  UserCheck
} from 'lucide-react';
import { Button } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';

export default function PrivacyPage() {
  const { isAuthenticated, role, logout } = useAuth();

  return (
    <div className="min-h-dvh bg-[var(--surface)]">
      {/* Utility Bar */}
      <div className="bg-[var(--home-bar)]">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-2)]">
          <span className="text-small flex items-center gap-[var(--sp-4)] text-[var(--home-bar-ink)]">
            <span className="flex items-center gap-[var(--sp-2)]">
              <ShieldCheck className="size-4" aria-hidden />
              Perimity Privacy & Security Policy
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
                Privacy Policy
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
      <section className="bg-gradient-to-b from-[var(--home-bar)] to-[#111827] px-[var(--sp-6)] py-[var(--sp-10)] text-white">
        <div className="mx-auto max-w-[900px]">
          <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-1.5 text-xs font-medium text-[var(--brand-300)]">
            <Lock className="size-4" /> Data Protection & Privacy Compliance
          </span>
          <h1 className="mt-4 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Perimity Privacy Policy
          </h1>
          <p className="mt-2 text-sm text-gray-300">
            Last Updated: August 2026 &bull; Effective for all Perimity digital gate pass systems across campuses
          </p>
        </div>
      </section>

      {/* Main Content Layout */}
      <div className="mx-auto max-w-[1200px] px-[var(--sp-6)] py-[var(--sp-12)]">
        <div className="grid gap-10 lg:grid-cols-12">
          {/* Left Sticky Sidebar Table of Contents */}
          <aside className="lg:col-span-4">
            <div className="sticky top-6 rounded-xl border border-[var(--border)] bg-[var(--surface)] p-6 shadow-xs">
              <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--ink-900)]">
                Policy Sections
              </h3>
              <nav className="mt-4 flex flex-col space-y-2 text-xs font-medium text-[var(--ink-700)]">
                <a href="#overview" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <FileText className="size-4 text-[var(--brand-600)]" /> 1. Executive Summary & Overview
                </a>
                <a href="#collection" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <Database className="size-4 text-[var(--brand-600)]" /> 2. Information We Collect
                </a>
                <a href="#usage" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <UserCheck className="size-4 text-[var(--brand-600)]" /> 3. How We Use Data
                </a>
                <a href="#security" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <KeyRound className="size-4 text-[var(--brand-600)]" /> 4. Pass Encryption & Security
                </a>
                <a href="#retention" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <Server className="size-4 text-[var(--brand-600)]" /> 5. Data Retention & Isolation
                </a>
                <a href="#rights" className="flex items-center gap-2 rounded-md p-2 hover:bg-[var(--surface-subtle)] hover:text-[var(--brand-600)]">
                  <Eye className="size-4 text-[var(--brand-600)]" /> 6. Your Rights & Contacts
                </a>
              </nav>

              <div className="mt-6 rounded-lg bg-[var(--brand-50)] p-4 text-xs text-[var(--notice-info-fg)]">
                <p className="font-bold">Zero Commercial Data Sales</p>
                <p className="mt-1 leading-relaxed">
                  Perimity never sells or monetizes personal data, visitor entry logs, or campus telemetry to third parties.
                </p>
              </div>
            </div>
          </aside>

          {/* Right Detailed Policy Text */}
          <main className="space-y-12 lg:col-span-8">
            {/* Section 1 */}
            <section id="overview" className="scroll-mt-6">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                1. Executive Summary & System Overview
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  Perimity ("we", "our", or "the platform") provides encrypted, digital gate pass issuance and access auditing for educational campuses, research institutes, and gated facilities.
                </p>
                <p>
                  This Privacy Policy details how personal information, pass issuance metadata, visitor logs, and guard verification entries are gathered, encrypted, processed, and safeguarded when using the Perimity web application or gate scanning infrastructure.
                </p>
              </div>

              {/* Guarantees Box */}
              <div className="mt-6 grid gap-4 sm:grid-cols-2">
                <div className="rounded-lg border border-[var(--border)] bg-[var(--surface-subtle)] p-4">
                  <CheckCircle2 className="size-5 text-[var(--allow-fg)]" />
                  <h4 className="mt-2 text-sm font-bold text-[var(--ink-900)]">Encrypted Digital Passes</h4>
                  <p className="mt-1 text-xs text-[var(--ink-500)]">
                    Pass tokens are cryptographically signed with AES/RSA algorithms to prevent forgery and unauthorized tampering.
                  </p>
                </div>
                <div className="rounded-lg border border-[var(--border)] bg-[var(--surface-subtle)] p-4">
                  <CheckCircle2 className="size-5 text-[var(--allow-fg)]" />
                  <h4 className="mt-2 text-sm font-bold text-[var(--ink-900)]">Multi-Tenant Campus Isolation</h4>
                  <p className="mt-1 text-xs text-[var(--ink-500)]">
                    Each campus operates in an isolated data scope, accessible only to authorized administrators and hosts of that institution.
                  </p>
                </div>
              </div>
            </section>

            {/* Section 2 */}
            <section id="collection" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                2. Information We Collect
              </h2>
              <div className="mt-4 space-y-4 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>We collect information required to issue valid gate passes and maintain a secure audit log for campus safety:</p>

                <ul className="list-disc space-y-2 pl-5 text-sm text-[var(--ink-700)]">
                  <li>
                    <strong>Visitor Pass Applicants:</strong> Full name, email address, phone number, identity document type, purpose of visit, host faculty/department name, and requested visit time window.
                  </li>
                  <li>
                    <strong>Students & Faculty:</strong> Registered full name, institutional email address, department, roll number / faculty ID, profile verification status, and password hashes.
                  </li>
                  <li>
                    <strong>Security Personnel (Guards):</strong> Guard badge ID, shift session timestamps, gate location assignments, and scan activity logs.
                  </li>
                  <li>
                    <strong>Gate Log Metadata:</strong> QR pass verification timestamps, scan verdict (ALLOW / DENY / EXPIRED), scanning gate ID, and guard notes.
                  </li>
                  <li>
                    <strong>System & Device Data:</strong> IP address, user agent details, session tokens, and security error logs for system integrity.
                  </li>
                </ul>
              </div>
            </section>

            {/* Section 3 */}
            <section id="usage" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                3. How We Use Your Information
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>All data collected serves specific access control and campus security functions:</p>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-lg border border-[var(--border)] p-4">
                    <h4 className="font-semibold text-[var(--ink-900)]">Pass Delivery & Verification</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      Sending single-use 6-digit email OTP verification codes and issuing PDF pass attachments containing valid QR codes.
                    </p>
                  </div>
                  <div className="rounded-lg border border-[var(--border)] p-4">
                    <h4 className="font-semibold text-[var(--ink-900)]">Host Approvals Workflow</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      Routing visitor pass applications to designated faculty members or department heads for review and decision.
                    </p>
                  </div>
                  <div className="rounded-lg border border-[var(--border)] p-4">
                    <h4 className="font-semibold text-[var(--ink-900)]">Real-Time Gate Auditing</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      Recording instantaneous entry verdicts at security gates to provide real-time campus attendance visibility and audit compliance.
                    </p>
                  </div>
                  <div className="rounded-lg border border-[var(--border)] p-4">
                    <h4 className="font-semibold text-[var(--ink-900)]">Security & Blocklist Enforcement</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">
                      Preventing unauthorized entry or pass reuse, and assisting campus security in blocklist enforcement for revoked individuals.
                    </p>
                  </div>
                </div>
              </div>
            </section>

            {/* Section 4 */}
            <section id="security" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                4. Pass Cryptography & Security Measures
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  Perimity employs enterprise-grade cryptographic standards across all microservices:
                </p>
                <ul className="list-disc space-y-2 pl-5 text-sm">
                  <li>
                    <strong>QR Pass Encryption:</strong> Pass payloads contain time-stamped, cryptographically signed tokens preventing code duplication or screenshot reuse.
                  </li>
                  <li>
                    <strong>Data Encryption in Transit:</strong> All HTTP traffic between client browser, gateway server, and Spring Boot services is encrypted via TLS 1.3.
                  </li>
                  <li>
                    <strong>Authentication & Password Protection:</strong> User passwords are encrypted using BCrypt hashing algorithms with salted parameters.
                  </li>
                  <li>
                    <strong>Role-Based Access Control (RBAC):</strong> Endpoints are secured by granular JWT authorizations, ensuring users only access resource endpoints matching their assigned role.
                  </li>
                </ul>
              </div>
            </section>

            {/* Section 5 */}
            <section id="retention" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                5. Data Retention & Multi-Tenant Isolation
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  <strong>Retention Policy:</strong> Visitor pass records and entry audit logs are retained for a default period of 90 days (or according to institutional campus compliance requirements) after which log payloads are archived or purged.
                </p>
                <p>
                  <strong>Tenant Isolation:</strong> Data from one campus is logically isolated from other institutions on the platform. Campus Admins can only view records and entry logs for their assigned campus.
                </p>
              </div>
            </section>

            {/* Section 6 */}
            <section id="rights" className="scroll-mt-6 border-t border-[var(--border)] pt-8">
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                6. Your Rights & Data Privacy Contact
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-relaxed text-[var(--ink-700)]">
                <p>
                  Students, faculty, and visitors have the right to request review, correction, or deletion of personal profile information maintained in the system, subject to campus regulatory guidelines.
                </p>
                <div className="mt-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-xl border border-[var(--brand-200)] bg-[var(--brand-50)] p-6">
                  <div>
                    <h4 className="text-base font-bold text-[var(--ink-900)]">Data Privacy Officer</h4>
                    <p className="mt-1 text-xs text-[var(--ink-500)]">For privacy inquiries, log deletion, or compliance concerns</p>
                    <a href="mailto:privacy@perimity.com" className="mt-2 inline-flex items-center gap-2 font-semibold text-[var(--brand-600)] hover:underline text-sm">
                      <Mail className="size-4" /> privacy@perimity.com
                    </a>
                  </div>
                  <Button asChild size="sm">
                    <Link to="/support">Contact Support Desk</Link>
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
              <Link to="/privacy" className="text-white font-semibold underline">
                Privacy
              </Link>
              <Link to="/terms" className="hover:text-white">
                Terms
              </Link>
            </nav>
          </div>
        </div>
      </footer>
    </div>
  );
}
