import { NavLink } from 'react-router';
import { PanelLeftClose, PanelLeftOpen, ShieldCheck } from 'lucide-react';
import { Avatar, Button, Tooltip } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { useUiStore } from '@stores/uiStore';
import { cn } from '@lib/utils/cn';
import { ROLE_LABEL, type NavItem } from './navigation';

export interface SidebarProps {
  items: NavItem[];
  badges: Partial<Record<NonNullable<NavItem['badge']>, number>>;
  /** Tablet renders the same list as an icon rail. */
  variant: 'full' | 'rail';
}

export function Sidebar({ items, badges, variant }: SidebarProps) {
  const { sidebarCollapsed, toggleSidebar } = useUiStore();
  const { identity, profile } = useAuth();
  const rail = variant === 'rail' || sidebarCollapsed;
  const name = profile?.name ?? identity?.name ?? '';

  return (
    <aside
      className={cn(
        'flex shrink-0 flex-col border-r border-[var(--border)] bg-[var(--surface)]',
        'transition-[width] duration-[var(--motion-base)] ease-[var(--ease-out)]',
      )}
      style={{ width: rail ? 'var(--sidebar-rail-w)' : 'var(--sidebar-w)' }}
    >
      {/* The mark sits in a filled tile rather than floating as a bare icon.
          It reads as a product logo at a glance and, more usefully, it keeps
          its shape when the sidebar collapses to the rail. */}
      <div
        className={cn(
          'flex h-[var(--topbar-h)] items-center gap-[var(--sp-3)] px-[var(--sp-4)]',
          rail && 'justify-center px-0',
        )}
      >
        <span className="flex size-8 shrink-0 items-center justify-center rounded-[var(--r-sm)] bg-[var(--brand-600)]">
          <ShieldCheck className="size-5 text-white" aria-hidden />
        </span>
        {!rail && <span className="text-h3 truncate text-[var(--ink-900)]">Perimity</span>}
      </div>

      <nav
        aria-label="Primary"
        className="flex-1 overflow-y-auto scrollbar-thin px-[var(--sp-2)] py-[var(--sp-2)]"
      >
        <ul className="flex flex-col gap-[var(--sp-1)]">
          {items.map((item) => {
            const count = item.badge ? badges[item.badge] : undefined;
            const link = (
              <NavLink
                to={item.to}
                end={item.end ?? false}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-[var(--sp-3)] rounded-[var(--r-sm)] px-[var(--sp-3)] py-[var(--sp-2)]',
                    'text-body-md transition-colors duration-[var(--motion-fast)]',
                    rail && 'justify-center px-0',
                    isActive
                      ? 'bg-[var(--brand-50)] text-[var(--brand-600)]'
                      : 'text-[var(--ink-500)] hover:bg-[var(--surface-sunken)] hover:text-[var(--ink-900)]',
                  )
                }
              >
                <item.icon className="size-[18px] shrink-0" aria-hidden />
                {!rail && <span className="flex-1 truncate">{item.label}</span>}
                {!rail && count !== undefined && count > 0 && (
                  <span className="text-caption rounded-[var(--r-pill)] bg-[var(--brand-600)] px-[var(--sp-2)] text-white tabular-nums">
                    {count}
                  </span>
                )}
              </NavLink>
            );

            return (
              <li key={item.to}>
                {rail ? <Tooltip content={item.label}>{link}</Tooltip> : link}
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Who am I, pinned to the bottom. The top bar carries the same identity
          as a menu; this is the ambient version — a signed-in user should be
          able to answer "which account is this" without opening anything. */}
      {!rail && name && (
        <div className="border-t border-[var(--border)] px-[var(--sp-3)] py-[var(--sp-3)]">
          <div className="flex min-w-0 items-center gap-[var(--sp-2)]">
            <Avatar name={name} />
            <div className="min-w-0 flex-1">
              <p className="text-small truncate text-[var(--ink-900)]">{name}</p>
              <p className="text-caption truncate text-[var(--ink-500)]">
                {identity ? ROLE_LABEL[identity.role] : ''}
              </p>
            </div>
          </div>
        </div>
      )}

      {variant === 'full' && (
        <div className="border-t border-[var(--border)] p-[var(--sp-2)]">
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleSidebar}
            aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="w-full"
          >
            {sidebarCollapsed ? <PanelLeftOpen aria-hidden /> : <PanelLeftClose aria-hidden />}
          </Button>
        </div>
      )}
    </aside>
  );
}
