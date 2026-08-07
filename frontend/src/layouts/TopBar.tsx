import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { useQuery } from '@tanstack/react-query';
import { LogOut, KeyRound, ChevronDown, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router';
import { Avatar, Badge, Button } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { campusApi } from '@lib/api/services/campus.api';
import { campusKeys } from '@lib/query/keys';
import { cn } from '@lib/utils/cn';
import { ROLE_LABEL } from './navigation';

export function TopBar({ title }: { title?: string }) {
  const { identity, profile, logout } = useAuth();

  // Resolved once and cached for the session. The token carries only the id.
  const campus = useQuery({
    queryKey: campusKeys.detail(identity?.campusId ?? 0),
    queryFn: () => campusApi.getOne(identity!.campusId!),
    enabled: identity?.campusId != null,
    staleTime: 30 * 60_000,
  });

  const name = profile?.name ?? identity?.name ?? '';

  return (
    <header
      className="sticky top-0 z-30 flex items-center justify-between gap-[var(--sp-4)] border-b border-[var(--border)] bg-[var(--surface)] px-[var(--sp-4)] lg:px-[var(--sp-6)]"
      style={{ height: 'var(--topbar-h)' }}
    >
      <div className="flex min-w-0 items-center gap-[var(--sp-3)]">
        {/*
          Back to the role picker without signing out. The session is deliberately
          kept: HomePage already decides what happens next — the card for the role
          you are signed in as offers Continue, any other card signs you out first.
          Clearing the session here would make that distinction impossible.
        */}
        <Button asChild variant="ghost" size="sm" className="-ml-[var(--sp-1)] shrink-0 gap-[var(--sp-2)]">
          <Link to="/">
            <ArrowLeft aria-hidden />
            <span className="hidden sm:inline">Home</span>
            <span className="sr-only sm:hidden">Back to home</span>
          </Link>
        </Button>
        <span className="h-5 w-px shrink-0 bg-[var(--border)]" aria-hidden />
        {title && <p className="text-body-md truncate text-[var(--ink-900)]">{title}</p>}
        {campus.data && <Badge tone="neutral">{campus.data.name}</Badge>}
      </div>

      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <Button variant="ghost" className="gap-[var(--sp-2)] px-[var(--sp-2)]">
            <Avatar name={name} />
            <span className="hidden text-left sm:block">
              <span className="text-small block leading-tight text-[var(--ink-900)]">{name}</span>
              <span className="text-caption block leading-tight text-[var(--ink-500)]">
                {identity ? ROLE_LABEL[identity.role] : ''}
              </span>
            </span>
            <ChevronDown className="size-4 text-[var(--ink-400)]" aria-hidden />
          </Button>
        </DropdownMenu.Trigger>

        <DropdownMenu.Portal>
          <DropdownMenu.Content
            align="end"
            sideOffset={6}
            className={cn(
              'z-50 min-w-56 rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface)] p-[var(--sp-1)]',
              'shadow-[var(--sh-overlay)]',
            )}
          >
            <div className="px-[var(--sp-3)] py-[var(--sp-2)]">
              <p className="text-small text-[var(--ink-900)]">{name}</p>
              <p className="text-caption truncate text-[var(--ink-500)]">{identity?.email}</p>
            </div>
            <DropdownMenu.Separator className="my-[var(--sp-1)] h-px bg-[var(--border)]" />
            <DropdownMenu.Item asChild>
              <a
                href="/change-password"
                className="flex cursor-pointer items-center gap-[var(--sp-2)] rounded-[var(--r-sm)] px-[var(--sp-3)] py-[var(--sp-2)] text-small outline-none data-[highlighted]:bg-[var(--surface-sunken)]"
              >
                <KeyRound className="size-4" aria-hidden />
                Change password
              </a>
            </DropdownMenu.Item>
            <DropdownMenu.Item
              onSelect={() => void logout()}
              className="flex cursor-pointer items-center gap-[var(--sp-2)] rounded-[var(--r-sm)] px-[var(--sp-3)] py-[var(--sp-2)] text-small outline-none data-[highlighted]:bg-[var(--surface-sunken)]"
            >
              <LogOut className="size-4" aria-hidden />
              Sign out
            </DropdownMenu.Item>
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    </header>
  );
}
