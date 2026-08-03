import { NavLink } from 'react-router';
import { cn } from '@lib/utils/cn';
import type { NavItem } from './navigation';

/** The sidebar becomes this below 768px. Max five destinations. */
export function BottomTabBar({ items }: { items: NavItem[] }) {
  const visible = items.slice(0, 5);
  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-40 flex border-t border-[var(--border)] bg-[var(--surface)] md:hidden"
      style={{ height: 'var(--tabbar-h)', paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      {visible.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end ?? false}
          className={({ isActive }) =>
            cn(
              'flex flex-1 flex-col items-center justify-center gap-[2px] text-caption',
              isActive ? 'text-[var(--brand-600)]' : 'text-[var(--ink-500)]',
            )
          }
        >
          <item.icon className="size-5" aria-hidden />
          <span className="truncate px-1">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
