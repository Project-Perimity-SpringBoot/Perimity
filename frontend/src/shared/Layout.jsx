import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ROLE_LABEL, ROLES } from './roles';

/**
 * Navbar, sidebar, and the routed page.
 *
 * The sidebar is role-aware: each entry declares who may see it, so a Guard
 * never sees a link to Campus Settings. Add your screens to NAV below - one
 * line each, and nobody has to touch this file's logic.
 *
 * NO INSTITUTION NAME ANYWHERE. The product name is Perimity; the campus name
 * comes from the API for the logged-in user's campus. That rule is in every
 * governing document and CI fails a build that breaks it.
 */
const NAV = [
  { to: '/my-pass',   label: 'My Pass',        roles: [ROLES.STUDENT, ROLES.VISITOR, ROLES.FACULTY] },
  { to: '/approvals', label: 'Approvals',      roles: [ROLES.FACULTY, ROLES.CAMPUS_ADMIN] },
  { to: '/scan',      label: 'Scan',           roles: [ROLES.GUARD] },
  { to: '/campus',    label: 'Campus',         roles: [ROLES.CAMPUS_ADMIN] },
  { to: '/admin',     label: 'Platform',       roles: [ROLES.SUPER_ADMIN] },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const visible = NAV.filter((item) => item.roles.includes(user.role));

  return (
    <div className="app">
      <header className="navbar">
        <span className="brand">Perimity</span>
        <div className="navbar-right">
          <span className="who">
            {user.name} · {ROLE_LABEL[user.role] ?? user.role}
          </span>
          <button onClick={signOut}>Log out</button>
        </div>
      </header>

      <div className="body">
        <nav className="sidebar">
          {visible.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
          {visible.length === 0 && <span className="muted">No screens yet</span>}
        </nav>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
