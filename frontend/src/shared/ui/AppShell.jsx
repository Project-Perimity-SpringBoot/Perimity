import { NavLink, Outlet } from 'react-router-dom';

/**
 * Sidebar on desktop, icon rail on tablet, bottom tab bar on mobile - one
 * component, three layouts, chosen by CSS so no screen ever branches on width.
 *
 * `tone="platform"` gives the Super Admin console a darker bar. That console
 * operates across campuses and can suspend one; it must not look like the
 * Campus Admin console it sits beside in someone's browser tabs.
 *
 * items: [{ to, label, icon?, badge? }]
 */
export default function AppShell({ items = [], user, roleLabel, onSignOut, tone = 'campus', scopeLabel }) {
  const platform = tone === 'platform';

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--surface-sunken)' }}>
      <header
        style={{
          height: 'var(--topbar-h)', flex: '0 0 auto',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '0 var(--s-4)',
          background: platform ? 'var(--ink-900)' : 'var(--surface)',
          color: platform ? 'var(--surface)' : 'var(--ink-900)',
          borderBottom: '1px solid var(--border)',
        }}
      >
        <div className="p-row" style={{ gap: 'var(--s-3)' }}>
          <strong style={{ letterSpacing: '-0.02em' }}>Perimity</strong>
          {scopeLabel && (
            <span className="p-label" style={{
              color: platform ? 'var(--brand-200)' : 'var(--ink-500)',
              border: '1px solid currentColor', padding: '2px 8px', borderRadius: 'var(--r-pill)',
            }}>
              {scopeLabel}
            </span>
          )}
        </div>
        <div className="p-row">
          <span className="p-small" style={{ opacity: .8 }}>{user} · {roleLabel}</span>
          <button className="p-btn p-btn--secondary" style={{ minHeight: 36 }} onClick={onSignOut}>
            Log out
          </button>
        </div>
      </header>

      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <nav className="p-nav" aria-label="Main">
          {items.map((it) => (
            <NavLink key={it.to} to={it.to} className="p-nav__item">
              <span className="p-nav__icon" aria-hidden>{it.icon ?? '•'}</span>
              <span className="p-nav__label">{it.label}</span>
              {it.badge > 0 && <span className="p-nav__badge">{it.badge}</span>}
            </NavLink>
          ))}
        </nav>

        <main style={{ flex: 1, minWidth: 0, padding: 'var(--s-6)', paddingBottom: 'calc(var(--tabbar-h) + var(--s-6))' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
