import { useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  Clock,
  GraduationCap,
  HelpCircle,
  LifeBuoy,
  Lock,
  Mail,
  Phone,
  ScanLine,
  Search,
  Send,
  ShieldCheck,
  UserRound,
} from 'lucide-react';
import { Button } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';

interface FAQItem {
  id: string;
  category: string;
  question: string;
  answer: string;
}

const FAQS: FAQItem[] = [
  {
    id: 'faq-1',
    category: 'Visitor Passes',
    question: 'How do I request a visitor gate pass for campus access?',
    answer: 'To request a visitor pass, click "Request a visitor pass" on the home page, select your host faculty member or campus department, enter your visit details, and submit. You will receive a 6-digit email verification code. Once verified, your request is sent to the host for approval.',
  },
  {
    id: 'faq-2',
    category: 'Visitor Passes',
    question: 'Why haven\'t I received my OTP verification code or pass PDF by email?',
    answer: 'Email delivery usually takes less than a minute. Please check your Spam/Junk folder first. Ensure that your email address was typed correctly. If you still do not receive the email within 5 minutes, click "Resend code" on the verification screen or contact support.',
  },
  {
    id: 'faq-3',
    category: 'Pass Scanning',
    question: 'What happens if my QR pass is flagged as EXPIRED or DENIED at the gate?',
    answer: 'Passes are valid strictly within their scheduled entry window. If a pass shows EXPIRED, your entry window has passed. If DENIED, either the pass signature was invalid, the host declined the request, or your account was flagged by campus administration. Contact campus security or your host.',
  },
  {
    id: 'faq-4',
    category: 'Faculty & Host',
    question: 'How do faculty members approve or decline visitor requests?',
    answer: 'Faculty members can sign into their account and navigate to the Approvals page (/faculty/approvals). Here you can inspect all incoming visitor requests, verify visit reasons, and either approve or reject requests with an optional note.',
  },
  {
    id: 'faq-5',
    category: 'Student Verification',
    question: 'How does student profile verification work?',
    answer: 'When a student registers, their profile enters a PENDING verification state. Department faculty review student details and documents on the Student Verification portal (/faculty/students/verification) to approve or request detail corrections.',
  },
  {
    id: 'faq-6',
    category: 'Guards & Gates',
    question: 'Can guards manually look up a pass if a phone screen is damaged?',
    answer: 'Yes! Guards on duty can switch to Manual Entry mode on the Guard Console (/guard/manual) to look up visitor passes by reference ID or student pass by registration number.',
  },
  {
    id: 'faq-7',
    category: 'Accounts & Security',
    question: 'How do I reset my password if I forget it?',
    answer: 'Go to the Sign In page and click "Forgot password?". Enter your registered email address to receive a secure password reset link and single-use token.',
  },
];

const CATEGORIES = [
  {
    icon: UserRound,
    title: 'Visitor Pass Support',
    desc: 'Requesting passes, host approvals, email OTPs, and pass PDF downloads.',
  },
  {
    icon: GraduationCap,
    title: 'Student & Faculty Help',
    desc: 'Profile verification, bulk onboarding, event passes, and approval queues.',
  },
  {
    icon: ScanLine,
    title: 'Gate & Guard Operations',
    desc: 'QR scanner usage, entry verdicts, shift sessions, and manual pass lookups.',
  },
  {
    icon: Lock,
    title: 'Account & Security',
    desc: 'Password recovery, multi-campus permissions, blocklists, and audit logs.',
  },
];

export default function SupportPage() {
  const { isAuthenticated, role, logout } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [openFaq, setOpenFaq] = useState<string | null>('faq-1');
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    role: 'VISITOR',
    subject: '',
    message: '',
  });
  const [formSubmitted, setFormSubmitted] = useState(false);

  const filteredFaqs = FAQS.filter(
    (faq) =>
      faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.answer.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name || !formData.email || !formData.message) return;
    setFormSubmitted(true);
  };

  return (
    <div className="min-h-dvh bg-[var(--surface)]">
      {/* Utility Bar */}
      <div className="bg-[var(--home-bar)]">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-2)]">
          <span className="text-small flex items-center gap-[var(--sp-4)] text-[var(--home-bar-ink)]">
            <span className="flex items-center gap-[var(--sp-2)]">
              <ShieldCheck className="size-4" aria-hidden />
              Perimity Support & Knowledge Center
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
                Help & Support Center
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

      {/* Hero Section */}
      <section className="bg-gradient-to-b from-[var(--home-bar)] to-[#111827] px-[var(--sp-6)] py-[var(--sp-12)] text-white">
        <div className="mx-auto max-w-[900px] text-center">
          <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-1.5 text-xs font-medium text-[var(--brand-300)]">
            <LifeBuoy className="size-4" /> 24/7 Campus Assistance & Helpdesk
          </span>
          <h1 className="mt-4 text-3xl font-extrabold tracking-tight sm:text-4xl md:text-5xl">
            How can we help you today?
          </h1>
          <p className="mt-3 text-base text-gray-300 sm:text-lg">
            Find answers to common gate pass questions, troubleshoot QR scanning, or reach out to our dedicated support team.
          </p>

          {/* Search Box */}
          <div className="relative mx-auto mt-8 max-w-xl">
            <Search className="absolute left-4 top-1/2 size-5 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Search help topics (e.g. visitor pass, expired QR, OTP code)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full rounded-xl border border-white/20 bg-white/10 py-3.5 pl-12 pr-4 text-white placeholder-gray-400 backdrop-blur-md transition focus:border-[var(--brand-300)] focus:bg-white/15 focus:outline-none"
            />
          </div>
        </div>
      </section>

      {/* Support Categories */}
      <section className="mx-auto max-w-[1200px] px-[var(--sp-6)] py-[var(--sp-10)]">
        <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">Browse Help Topics</h2>
        <p className="mt-1 text-sm text-[var(--ink-500)]">Quick assistance organized by role and feature</p>

        <div className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {CATEGORIES.map((cat, i) => (
            <div
              key={i}
              className="rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface)] p-[var(--sp-6)] shadow-xs transition hover:border-[var(--brand-300)] hover:shadow-md"
            >
              <span className="flex size-12 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)] text-[var(--brand-600)]">
                <cat.icon className="size-6" />
              </span>
              <h3 className="mt-4 text-base font-bold text-[var(--ink-900)]">{cat.title}</h3>
              <p className="mt-2 text-xs leading-relaxed text-[var(--ink-500)]">{cat.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* FAQ Section */}
      <section className="border-t border-[var(--border)] bg-[var(--surface-subtle)] px-[var(--sp-6)] py-16 sm:py-24">
        <div className="mx-auto max-w-[900px]">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-[var(--ink-900)] sm:text-2xl">
                Frequently Asked Questions
              </h2>
              <p className="mt-1 text-sm text-[var(--ink-500)]">
                Instant solutions for common gate access inquiries
              </p>
            </div>
            <span className="text-xs font-semibold text-[var(--brand-600)]">
              {filteredFaqs.length} results
            </span>
          </div>

          <div className="mt-6 space-y-4">
            {filteredFaqs.length > 0 ? (
              filteredFaqs.map((faq) => {
                const isOpen = openFaq === faq.id;
                return (
                  <div
                    key={faq.id}
                    className="overflow-hidden rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface)] shadow-xs transition"
                  >
                    <button
                      onClick={() => setOpenFaq(isOpen ? null : faq.id)}
                      className="flex w-full items-center justify-between p-5 text-left transition hover:bg-[var(--surface-subtle)]"
                    >
                      <span className="flex items-center gap-3">
                        <HelpCircle className="size-5 shrink-0 text-[var(--brand-600)]" />
                        <span className="text-base font-semibold text-[var(--ink-900)]">
                          {faq.question}
                        </span>
                      </span>
                      <ChevronDown
                        className={`size-5 text-[var(--ink-500)] transition-transform duration-200 ${
                          isOpen ? 'rotate-180' : ''
                        }`}
                      />
                    </button>
                    {isOpen && (
                      <div className="border-t border-[var(--border)] bg-[var(--surface-subtle)] p-5 text-sm leading-relaxed text-[var(--ink-700)]">
                        <span className="mb-2 inline-block rounded bg-[var(--brand-50)] px-2 py-0.5 text-xs font-semibold text-[var(--brand-600)]">
                          {faq.category}
                        </span>
                        <p className="mt-1">{faq.answer}</p>
                      </div>
                    )}
                  </div>
                );
              })
            ) : (
              <div className="rounded-[var(--r-md)] border border-dashed border-[var(--border-strong)] p-8 text-center">
                <AlertCircle className="mx-auto size-8 text-[var(--ink-400)]" />
                <p className="mt-2 text-sm font-semibold text-[var(--ink-700)]">No matching questions found</p>
                <p className="mt-1 text-xs text-[var(--ink-500)]">Try searching for a different keyword or contact our support team below.</p>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Contact Form & Direct Channels */}
      <section className="mx-auto max-w-[1200px] px-[var(--sp-6)] py-16 sm:py-24">
        <div className="grid gap-8 lg:grid-cols-12">
          {/* Left info panel */}
          <div className="lg:col-span-5">
            <span className="text-xs font-bold uppercase tracking-wider text-[var(--brand-600)]">
              Direct Contact
            </span>
            <h2 className="mt-2 text-2xl font-bold text-[var(--ink-900)] sm:text-3xl">
              Get in Touch with Support
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-[var(--ink-500)]">
              Need assistance with an urgent gate pass issue or administrative query? Send a message to our support desk or reach out directly.
            </p>

            <div className="mt-8 space-y-5">
              <div className="flex items-start gap-4 rounded-lg border border-[var(--border)] p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-[var(--brand-50)] text-[var(--brand-600)]">
                  <Mail className="size-5" />
                </span>
                <div>
                  <h4 className="text-sm font-bold text-[var(--ink-900)]">Email Support</h4>
                  <p className="text-xs text-[var(--ink-500)]">Direct inquiries & ticket escalation</p>
                  <a
                    href="mailto:perimity.info@gmail.com"
                    className="mt-1 block text-sm font-semibold text-[var(--brand-600)] hover:underline"
                  >
                    perimity.info@gmail.com
                  </a>
                </div>
              </div>

              <div className="flex items-start gap-4 rounded-lg border border-[var(--border)] p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-[var(--brand-50)] text-[var(--brand-600)]">
                  <Phone className="size-5" />
                </span>
                <div>
                  <h4 className="text-sm font-bold text-[var(--ink-900)]">Campus Gate Security Hotline</h4>
                  <p className="text-xs text-[var(--ink-500)]">For guards on duty and urgent entry issues</p>
                  <span className="mt-1 block text-sm font-semibold text-[var(--ink-900)]">
                    +91 9876543210
                  </span>
                </div>
              </div>

              <div className="flex items-start gap-4 rounded-lg border border-[var(--border)] p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-[var(--brand-50)] text-[var(--brand-600)]">
                  <Clock className="size-5" />
                </span>
                <div>
                  <h4 className="text-sm font-bold text-[var(--ink-900)]">Response Time</h4>
                  <p className="text-xs text-[var(--ink-500)]">Support desk active 24/7</p>
                  <span className="mt-1 block text-xs font-medium text-[var(--allow-fg)]">
                    Average response within 15 minutes
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Right Form panel */}
          <div className="lg:col-span-7">
            <div className="rounded-xl border border-[var(--border)] bg-[var(--surface)] p-6 shadow-sm sm:p-8">
              <h3 className="text-xl font-bold text-[var(--ink-900)]">Send Support Request</h3>
              <p className="mt-1 text-xs text-[var(--ink-500)]">
                Fill in the form below and a support agent will reach out shortly.
              </p>

              {formSubmitted ? (
                <div className="mt-6 rounded-lg bg-[var(--allow-bg)] p-6 text-center border border-[var(--allow-fg)]/20">
                  <CheckCircle2 className="mx-auto size-10 text-[var(--allow-fg)]" />
                  <h4 className="mt-3 text-lg font-bold text-[var(--allow-fg)]">
                    Support Ticket Submitted!
                  </h4>
                  <p className="mt-1 text-xs text-[var(--ink-700)]">
                    Thank you for reaching out. We have logged your request and sent a confirmation to{' '}
                    <strong>{formData.email}</strong>.
                  </p>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="mt-4"
                    onClick={() => {
                      setFormSubmitted(false);
                      setFormData({ name: '', email: '', role: 'VISITOR', subject: '', message: '' });
                    }}
                  >
                    Submit Another Request
                  </Button>
                </div>
              ) : (
                <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-700)]">
                        Your Name *
                      </label>
                      <input
                        type="text"
                        required
                        placeholder="John Doe"
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        className="mt-1.5 w-full rounded-md border border-[var(--border-strong)] px-3 py-2 text-sm focus:border-[var(--brand-600)] focus:outline-none"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-700)]">
                        Email Address *
                      </label>
                      <input
                        type="email"
                        required
                        placeholder="john@example.com"
                        value={formData.email}
                        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                        className="mt-1.5 w-full rounded-md border border-[var(--border-strong)] px-3 py-2 text-sm focus:border-[var(--brand-600)] focus:outline-none"
                      />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-700)]">
                        User Role
                      </label>
                      <select
                        value={formData.role}
                        onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                        className="mt-1.5 w-full rounded-md border border-[var(--border-strong)] bg-white px-3 py-2 text-sm focus:border-[var(--brand-600)] focus:outline-none"
                      >
                        <option value="VISITOR">Visitor</option>
                        <option value="STUDENT">Student</option>
                        <option value="FACULTY">Faculty</option>
                        <option value="GUARD">Guard</option>
                        <option value="CAMPUS_ADMIN">Campus Admin</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-700)]">
                        Subject
                      </label>
                      <input
                        type="text"
                        placeholder="e.g. Pass PDF issue"
                        value={formData.subject}
                        onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
                        className="mt-1.5 w-full rounded-md border border-[var(--border-strong)] px-3 py-2 text-sm focus:border-[var(--brand-600)] focus:outline-none"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-[var(--ink-700)]">
                      Describe your Issue *
                    </label>
                    <textarea
                      rows={4}
                      required
                      placeholder="Please provide details (Pass ID, campus name, error code, etc.)..."
                      value={formData.message}
                      onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                      className="mt-1.5 w-full rounded-md border border-[var(--border-strong)] px-3 py-2 text-sm focus:border-[var(--brand-600)] focus:outline-none"
                    />
                  </div>

                  <Button type="submit" size="lg" block className="mt-2">
                    <Send className="size-4" />
                    Submit Support Ticket
                  </Button>
                </form>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-[#111827] text-white">
        <div className="border-t border-white/10">
          <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-6)]">
            <span className="text-small text-[var(--home-footer-ink)]">
              &copy; {new Date().getFullYear()} Perimity. All rights reserved.
            </span>
            <nav aria-label="Footer" className="flex gap-[var(--sp-4)] text-small text-[var(--home-footer-ink)]">
              <Link to="/support" className="text-white font-semibold underline">
                Support
              </Link>
              <Link to="/privacy" className="hover:text-white">
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
