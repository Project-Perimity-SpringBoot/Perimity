import { Link, Outlet, useLocation } from 'react-router';
import { ArrowLeft, ClipboardList, Layers, QrCode, Users } from 'lucide-react';
import { PerimityLogoMark } from '../components/PerimityLogo';

/**
 * Every auth screen: one lit card on a dark field.
 *
 * SELF-CONTAINED ON PURPOSE. This file imports nothing but react-router and
 * lucide, and uses only tokens and classes the original layout already used
 * (--brand-600, --ink-*, --sp-*, --r-*, surface-card). No new token, no new
 * utility, no router change, no sibling file. Drop it in on any state of the
 * tree and it works.
 *
 * The backdrop gradient is written inline rather than added to tokens.css for
 * the same reason. That is a deliberate exception to the rule at the top of
 * tokens.css, not an oversight: one hard-coded value here is cheaper than a
 * second file that has to arrive with it.
 *
 * The pages inside supply their own <h1> and the paragraph under it. Both are
 * centred with `[&_h1]` variants rather than editing seven files — those
 * compile to `.card h1`, an element-plus-class selector, which outranks the
 * plain `.text-h1` utility on specificity rather than on source order. That
 * matters: `text-h1` sits in @layer utilities alongside Tailwind's own, so
 * anything relying on order would be decided at build time and could flip.
 *
 * `showBack` is opt-out because cancelling is right on all but one of these
 * routes. The exception is /change-password, which a user holding a temporary
 * password is required to complete — PasswordChangeGate would bounce them
 * straight back, so offering a way out there would be a lie. If your router
 * does not pass the prop yet, the default simply shows the link everywhere.
 */

const AUTH_BG = 'linear-gradient(155deg, #1e3a8a 0%, #172554 55%, #0f172a 100%)';
const PANEL_INK = '#c7d2fe';

/** Facts about the system, not about a college — Perimity is campus-agnostic. */
const FACTS = [
  { icon: Users, value: '6', label: 'User roles' },
  { icon: Layers, value: '6', label: 'Microservices' },
  { icon: QrCode, value: 'QR', label: 'Encrypted passes' },
  { icon: ClipboardList, value: '100%', label: 'Scans audited' },
];

export function AuthLayout({ showBack = true }: { showBack?: boolean }) {
  const { pathname } = useLocation();

  return (
    <div
      className="flex min-h-dvh flex-col items-center justify-center px-[var(--sp-4)] py-[var(--sp-8)]"
      style={{ background: AUTH_BG }}
    >
      <div className="grid w-full max-w-[1100px] items-center gap-[var(--sp-8)] lg:grid-cols-[1fr_460px]">

        {/* Brand panel. Hidden below lg: on a phone it would push the form off
            the fold, and the form is the only reason anyone opens this page. */}
        <div className="hidden text-center lg:block">
          <span className="mx-auto flex size-28 items-center justify-center rounded-[var(--r-lg)] bg-[var(--brand-600)]">
            <PerimityLogoMark className="size-14 text-white" aria-hidden />
          </span>

          <p className="mt-[var(--sp-6)] text-[length:34px] font-extrabold leading-tight tracking-tight text-white">
            Perimity
          </p>
          <p className="text-body mt-[var(--sp-3)]" style={{ color: PANEL_INK }}>
            Smart Campus Access &mdash; digital gate passes, verified in seconds
          </p>

          <ul className="mx-auto mt-[var(--sp-8)] grid max-w-md grid-cols-2 gap-[var(--sp-4)]">
            {FACTS.map((fact) => (
              <li
                key={fact.label}
                className="rounded-[var(--r-md)] border border-white/10 bg-white/5 p-[var(--sp-4)]"
              >
                <fact.icon className="mx-auto size-5" style={{ color: PANEL_INK }} aria-hidden />
                <p className="text-h1 mt-[var(--sp-2)] text-white">{fact.value}</p>
                <p className="text-small" style={{ color: PANEL_INK }}>{fact.label}</p>
              </li>
            ))}
          </ul>
        </div>

        {/* The card. */}
        <div className="mx-auto w-full max-w-md">
          <div className="surface-card p-[var(--sp-6)] sm:p-[var(--sp-8)] [&_h1]:text-center [&_h1+p]:text-center">
            <span className="mx-auto mb-[var(--sp-6)] flex size-16 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
              <PerimityLogoMark className="size-8 text-white" aria-hidden />
            </span>

            <Outlet />
          </div>

          {showBack && (
            <Link
              to="/"
              className="text-small mt-[var(--sp-6)] flex items-center justify-center gap-[var(--sp-2)] transition-colors hover:text-white"
              style={{ color: PANEL_INK }}
            >
              <ArrowLeft className="size-4" aria-hidden />
              Back to home
            </Link>
          )}

          {/*
            The other doors, only where they are a real alternative. On a reset
            or a code screen the user is mid-flow, and an exit link there is an
            invitation to abandon it.
          */}
          {pathname === '/login' && (
            <div
              className="text-small mt-[var(--sp-4)] flex items-center justify-center gap-[var(--sp-4)]"
              style={{ color: PANEL_INK }}
            >
              <Link to="/guard/login" className="hover:text-white">Guard sign-in</Link>
              <span aria-hidden className="opacity-40">|</span>
              <Link to="/register/visitor" className="hover:text-white">Register as a visitor</Link>
            </div>
          )}
        </div>
      </div>

      <p className="text-caption mt-[var(--sp-8)]" style={{ color: PANEL_INK }}>
        Secure digital gate passes for safe campus access
      </p>
    </div>
  );
}
