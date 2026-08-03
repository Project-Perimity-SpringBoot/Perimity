import { NavLink } from 'react-router';
import { PanelLeftClose, PanelLeftOpen, ShieldCheck } from 'lucide-react';
import { Button, Tooltip } from '@ui/index';
import { useUiStore } from '@stores/uiStore';
import { cn } from '@lib/utils/cn';
import type { NavItem } from './navigation';

export interface SidebarProps {
  items: NavItem[];
  badges: Partial<Record<NonNullable<NavItem['badge']>, number>>;
  /** Tablet renders the same list as an icon rail. */
  variant: 'full' | 'rail';
}

export function Sidebar({ items, badges, variant }: SidebarProps) {
  const { sidebarCollapsed, toggleSidebar } = useUiStore();
  const rail = variant === 'rail' || sidebarCollapsed;

  return (
    <aside
      className={cn(
        'flex shrink-0 flex-col border-r border-[var(--border)] bg-[var(--surface)]',
        'transition-[width] duration-[var(--motion-base)] ease-[var(--ease-out)]',
      )}
      style={{ width: rail ? 'var(--sidebar-rail-w)' : 'var(--sidebar-w)' }}
    >
      <div
        className={cn(
          'flex h-[var(--topbar-h)] items-center gap-[var(--sp-2)] border-b border-[var(--border)] px-[var(--sp-4)]',
          rail && 'justify-center px-0',
        )}
      >
        <ShieldCheck className="size-5 shrink-0 text-[var(--brand-600)]" aria-hidden />
        {!rail && <span className="text-h3 text-[var(--ink-900)]">Perimity</span>}
      </div>

      <nav aria-label="Primary" className="flex-1 overflow-y-auto scrollbar-thin p-[var(--sp-2)]">
        <ul className="flex flex-col gap-[2px]">
          {items.map((item) => {
            const count = item.badge ? badges[item.badge] : undefined;
            const link = (
              <NavLink
                to={item.to}
                end={item.end ?? false}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-[var(--sp-3)] rounded-[var(--r-sm)] px-[var(--sp-3)] py-[var(--sp-2)]',
                    'text-small transition-colors duration-[var(--motion-fast)]',
                    rail && 'justify-center px-0',
                    isActive
                      ? 'bg-[var(--brand-50)] text-[var(--brand-600)]'
                      : 'text-[var(--ink-700)] hover:bg-[var(--surface-sunken)]',
                  )
                }
              >
                <item.icon className="size-4 shrink-0" aria-hidden />
                {!rail && <span className="flex-1 truncate">{item.label}</span>}
                {!rail && count !== undefined && count > 0 && (
                  <span className="text-caption rounded-[var(--r-pill)] bg-[var(--status-bg)] px-[var(--sp-2)] text-[var(--status-fg)] tabular-nums">
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
