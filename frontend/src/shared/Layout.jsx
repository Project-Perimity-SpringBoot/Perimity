import { useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { navItems } from './featureRoutes';
import { ROLES, ROLE_LABEL } from './roles';
import { AppShell } from './ui';
import { campus, useApi } from '../api';

/**
 * ALSO FINISHED. Nobody edits this file to add a screen.
 *
 * It does two things and no more: filter the derived nav by the signed-in
 * role, and pick the tone. Everything visual lives in AppShell.
 *
 * NO INSTITUTION NAME ANYWHERE. The campus name renders from the API for the
 * signed-in user's campus — the product is campus-agnostic and a hardcoded
 * name is the one change that would quietly break that.
 */
const ICONS = {
  '/student': '⌂', '/student/pass': '▣', '/student/passes': '≡', '/student/entries': '⇢',
  '/student/profile': '◍', '/student/documents': '❐',
  '/visitor': '▣',
  '/approvals': '✓',
  '/campus': '⌂', '/campus/users': '◍', '/campus/departments': '⊞', '/campus/gates': '⌷',
  '/campus/blocklist': '⊘', '/campus/policy': '⚙', '/campus/audit': '≡',
  '/admin': '⌂', '/admin/campuses': '⊞', '/admin/admins': '◍', '/admin/audit': '≡',
  '/guard/log': '≡',
};

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const items = navItems
    .filter((i) => !i.roles || i.roles.includes(user.role))
    .map((i) => ({ ...i, icon: ICONS[i.to] }));

  const platform = user.role === ROLES.SUPER_ADMIN;

  /*
   * The campus NAME is not on the token and not on /api/auth/me — UserResponse
   * carries campusId and nothing else. So it is fetched once here rather than
   * read off a field that does not exist, which is what an earlier draft did
   * and which rendered a blank chip with no error to explain it.
   */
  const { data: myCampus } = useApi(
    () => campus.get(user.campusId), [user.campusId], { skip: platform || !user.campusId });

  return (
    <AppShell
      items={items}
      user={user.name}
      roleLabel={ROLE_LABEL[user.role] ?? user.role}
      onSignOut={signOut}
      tone={platform ? 'platform' : 'campus'}
      // The chip is the only thing telling two near-identical admin consoles
      // apart at a glance. Suspending the wrong campus is unrecoverable.
      scopeLabel={platform ? 'Platform' : myCampus?.name}
    />
  );
}
