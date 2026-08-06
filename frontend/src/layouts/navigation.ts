import {
  BadgeCheck, Building2, CalendarRange, ClipboardList, FileSpreadsheet, FileText, Gauge, IdCard,
  LayoutDashboard, ListChecks, LogIn, ScanLine, Settings2, ShieldBan,
  UserPen, UserPlus, Users, UsersRound, type LucideIcon,
} from 'lucide-react';
import type { Role } from '@/types/enums';
import type { Capability } from '@lib/auth/permissions';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Hidden unless the signed-in user holds it. Mirrors the server, for UX only. */
  capability?: Capability;
  /** Query key name whose value renders as a count pill. */
  badge?: 'pendingRequests' | 'pendingStudentVerification';
  end?: boolean;
}

export const NAVIGATION: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { to: '/platform', label: 'Platform overview', icon: Gauge, end: true },
    { to: '/platform/campuses', label: 'Campuses', icon: Building2 },
    { to: '/platform/admins', label: 'Campus admins', icon: UsersRound },
  ],
  CAMPUS_ADMIN: [
    { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
    { to: '/admin/users', label: 'Users', icon: Users },
    { to: '/admin/departments', label: 'Departments', icon: UsersRound },
    { to: '/admin/gates', label: 'Gates', icon: LogIn },
    { to: '/admin/blocklist', label: 'Blocklist', icon: ShieldBan, capability: 'blocklist:manage' },
    { to: '/admin/policy', label: 'Policy', icon: Settings2, capability: 'config:manage' },
    { to: '/admin/entry-logs', label: 'Entry logs', icon: ScanLine, capability: 'entrylog:view' },
  ],
  FACULTY: [
    { to: '/faculty', label: 'Overview', icon: LayoutDashboard, end: true },
    { to: '/faculty/approvals', label: 'Approvals', icon: ClipboardList, badge: 'pendingRequests' },
    /* Gated on profile:createStudent, which only FACULTY and the two admin
       roles hold - it mirrors UserAdminController.CREATABLE, where FACULTY is
       the only role that may create a STUDENT. */
    { to: '/faculty/students/new', label: 'Add student', icon: UserPlus, capability: 'profile:createStudent' },
    /* Same capability as Add student: whoever may create a student is who may
       check that student's own details. The server allows any staff role, so
       this is the narrower of the two rules and the safer default. */
    {
      to: '/faculty/students/verification',
      label: 'Check details',
      icon: BadgeCheck,
      capability: 'profile:createStudent',
      badge: 'pendingStudentVerification',
    },
    /* Bulk onboarding from an intake form. Same capability as adding one
       student, because an import is the same act at volume - it must not be a
       way to do what the single path forbids. */
    {
      to: '/faculty/students/import',
      label: 'Import students',
      icon: FileSpreadsheet,
      capability: 'profile:createStudent',
    },
    { to: '/faculty/onboarding', label: 'Onboarding', icon: ListChecks, capability: 'bulk:run' },
    { to: '/faculty/events', label: 'Events', icon: CalendarRange, capability: 'event:manage' },
  ],
  STUDENT: [
    { to: '/student', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/student/passes', label: 'My passes', icon: IdCard },
    { to: '/student/entries', label: 'Entry history', icon: ScanLine },
    { to: '/student/profile', label: 'Profile', icon: BadgeCheck },
    { to: '/student/profile/details', label: 'My details', icon: UserPen },
    { to: '/student/documents', label: 'Documents', icon: FileText },
  ],
  VISITOR: [
    { to: '/visitor', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/visitor/pass', label: 'My pass', icon: IdCard },
  ],
  GUARD: [{ to: '/guard', label: 'Scanner', icon: ScanLine, end: true }],
};

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super Admin',
  CAMPUS_ADMIN: 'Campus Admin',
  FACULTY: 'Faculty',
  STUDENT: 'Student',
  VISITOR: 'Visitor',
  GUARD: 'Guard',
};
