import type { PassStatus, RequestStatus } from '@/types/enums';
import type { PageRequest } from '@/types/api';

/**
 * Every query key in the app. Ad-hoc key arrays are how stale caches happen,
 * so nothing outside this file constructs one.
 */

export const authKeys = {
  all: ['auth'] as const,
  me: () => [...authKeys.all, 'me'] as const,
  users: () => [...authKeys.all, 'users'] as const,
  userList: (params: PageRequest & { role?: string }) =>
    [...authKeys.users(), 'list', params] as const,
  user: (id: number) => [...authKeys.users(), 'detail', id] as const,
  blocklist: () => [...authKeys.all, 'blocklist'] as const,
  blocklistList: (params: PageRequest & { email?: string }) =>
    [...authKeys.blocklist(), 'list', params] as const,
  blocklistCount: () => [...authKeys.blocklist(), 'count'] as const,
  audit: () => [...authKeys.all, 'audit'] as const,
  auditList: (params: PageRequest & { action?: string }) =>
    [...authKeys.audit(), 'list', params] as const,
  auditRange: (from: string, to: string, params: PageRequest) =>
    [...authKeys.audit(), 'range', from, to, params] as const,
  auditActor: (actorUserId: number, params: PageRequest) =>
    [...authKeys.audit(), 'actor', actorUserId, params] as const,
};

export const passKeys = {
  all: ['passes'] as const,
  mine: () => [...passKeys.all, 'mine'] as const,
  mineActive: () => [...passKeys.all, 'mine', 'active'] as const,
  detail: (id: number) => [...passKeys.all, 'detail', id] as const,
  byHolder: (holderUserId: number) => [...passKeys.all, 'holder', holderUserId] as const,
  byEvent: (eventId: number) => [...passKeys.all, 'event', eventId] as const,
  count: (status: PassStatus) => [...passKeys.all, 'count', status] as const,
};

export const requestKeys = {
  all: ['visitor-requests'] as const,
  detail: (id: number) => [...requestKeys.all, 'detail', id] as const,
  pass: (id: number) => [...requestKeys.all, 'detail', id, 'pass'] as const,
  queue: (status: RequestStatus, params: PageRequest) =>
    [...requestKeys.all, 'queue', status, params] as const,
  myQueue: (status: RequestStatus, params: PageRequest) =>
    [...requestKeys.all, 'my-queue', status, params] as const,
  myHistory: () => [...requestKeys.all, 'my-history'] as const,
  byEmail: (email: string) => [...requestKeys.all, 'by-email', email] as const,
  pendingCount: () => [...requestKeys.all, 'pending-count'] as const,
};

export const eventKeys = {
  all: ['events'] as const,
  list: (params: PageRequest) => [...eventKeys.all, 'list', params] as const,
  detail: (id: number) => [...eventKeys.all, 'detail', id] as const,
  running: () => [...eventKeys.all, 'running'] as const,
  attendanceSummary: (id: number) => [...eventKeys.all, 'detail', id, 'attendance'] as const,
};

export const bulkKeys = {
  all: ['bulk'] as const,
  list: (params: PageRequest) => [...bulkKeys.all, 'list', params] as const,
  batch: (batchId: number) => [...bulkKeys.all, 'batch', batchId] as const,
  progress: (batchId: number) => [...bulkKeys.all, 'batch', batchId, 'progress'] as const,
};

export const campusKeys = {
  all: ['campus'] as const,
  list: (includeInactive: boolean) => [...campusKeys.all, 'list', includeInactive] as const,
  detail: (id: number) => [...campusKeys.all, 'detail', id] as const,
  byCode: (code: string) => [...campusKeys.all, 'by-code', code] as const,
  stats: () => [...campusKeys.all, 'stats'] as const,
  gates: (campusId: number, includeClosed: boolean) =>
    [...campusKeys.all, campusId, 'gates', includeClosed] as const,
  gate: (campusId: number, gateId: number) =>
    [...campusKeys.all, campusId, 'gates', gateId] as const,
  config: (campusId: number) => [...campusKeys.all, campusId, 'config'] as const,
  logoUrl: (campusId: number) => [...campusKeys.all, campusId, 'logo-url'] as const,
};

export const profileKeys = {
  all: ['profiles'] as const,
  student: (id: number) => [...profileKeys.all, 'student', id] as const,
  studentByUser: (userId: number) => [...profileKeys.all, 'student', 'user', userId] as const,
  myStudent: () => [...profileKeys.all, 'student', 'me'] as const,
  studentList: (params: PageRequest & { campusId?: number; departmentId?: number }) =>
    [...profileKeys.all, 'student', 'list', params] as const,
  studentCount: (campusId?: number) => [...profileKeys.all, 'student', 'count', campusId] as const,
  faculty: (id: number) => [...profileKeys.all, 'faculty', id] as const,
  facultyByUser: (userId: number) => [...profileKeys.all, 'faculty', 'user', userId] as const,
  myFaculty: () => [...profileKeys.all, 'faculty', 'me'] as const,
  facultyList: (params: PageRequest & { campusId?: number; departmentId?: number }) =>
    [...profileKeys.all, 'faculty', 'list', params] as const,
  facultyCount: (campusId?: number) => [...profileKeys.all, 'faculty', 'count', campusId] as const,
  photoUrl: (kind: 'student' | 'faculty', id: number) =>
    [...profileKeys.all, kind, id, 'photo-url'] as const,
};

export const departmentKeys = {
  all: ['departments'] as const,
  list: (campusId: number | undefined, activeOnly: boolean) =>
    [...departmentKeys.all, 'list', campusId, activeOnly] as const,
  detail: (id: number) => [...departmentKeys.all, 'detail', id] as const,
};

export const documentKeys = {
  all: ['documents'] as const,
  mine: () => [...documentKeys.all, 'mine'] as const,
  detail: (id: number) => [...documentKeys.all, 'detail', id] as const,
  url: (id: number) => [...documentKeys.all, 'detail', id, 'url'] as const,
  forUser: (userId: number, docType?: string) =>
    [...documentKeys.all, 'user', userId, docType ?? 'all'] as const,
  pending: (userId: number) => [...documentKeys.all, 'user', userId, 'pending'] as const,
};

export const guardKeys = {
  all: ['guard'] as const,
  currentSession: () => [...guardKeys.all, 'session', 'current'] as const,
  openSessions: () => [...guardKeys.all, 'session', 'open'] as const,
  sessionHistory: () => [...guardKeys.all, 'session', 'history'] as const,
  entryLogs: () => [...guardKeys.all, 'entry-logs'] as const,
  entryLogSearch: (filter: unknown, params: PageRequest) =>
    [...guardKeys.entryLogs(), 'search', filter, params] as const,
  entryLogStats: (filter: unknown) => [...guardKeys.entryLogs(), 'stats', filter] as const,
  entryLogsByHolder: (holderUserId: number, params: PageRequest) =>
    [...guardKeys.entryLogs(), 'holder', holderUserId, params] as const,
  entryLogsByPass: (passId: number) => [...guardKeys.entryLogs(), 'pass', passId] as const,
  entryLogsBySession: (sessionId: string) =>
    [...guardKeys.entryLogs(), 'session', sessionId] as const,
  eventAttendance: (eventId: number, from: string, to: string) =>
    [...guardKeys.entryLogs(), 'event', eventId, from, to] as const,
};

export const qrKeys = {
  all: ['qr'] as const,
  byPass: (passId: number) => [...qrKeys.all, 'pass', passId] as const,
  job: (jobId: number) => [...qrKeys.all, 'job', jobId] as const,
  batchProgress: (batchId: number) => [...qrKeys.all, 'batch', batchId] as const,
};
