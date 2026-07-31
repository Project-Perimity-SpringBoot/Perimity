/**
 * THE SINGLE SOURCE OF MOCK DATA.
 *
 * Every figure here comes from the CONSTANTS BLOCK in the design pack. Import
 * from this file and never retype a name or a number into a screen.
 *
 * Why this matters more than it looks: the pack's own checklist says the bulk
 * arithmetic must reconcile (600 = 580 + 20) and that mock data must not
 * contradict itself across screens. Fifty-six screens each inventing their own
 * "Rohan Mehta" is how you end up demoing a pass code on one screen that does
 * not exist on the next.
 *
 * It also unblocks the whole build: every screen can be written, reviewed and
 * screenshotted before a single endpoint is wired. Swapping to the real API is
 * then one import change per screen, not a rewrite.
 */

export const TODAY = 'Tuesday, 8 July 2026';

export const CAMPUS = { name: 'Main Campus', code: 'MAIN' };
export const CAMPUS_ALT = { name: 'North Campus', code: 'NRTH' };

export const DEPARTMENTS = [
  'Computer Science', 'Electronics', 'Mechanical', 'Data Science', 'Embedded Systems',
];

export const PEOPLE = {
  faculty:     { name: 'Dr. Anaya Rao', dept: 'Computer Science', role: 'FACULTY' },
  campusAdmin: { name: 'Dr. S. Verma',  role: 'CAMPUS_ADMIN', campus: 'Main Campus' },
  student: {
    name: 'Sneha Kulkarni', roll: '2026-118', dept: 'Data Science',
    code: 'S-20418', role: 'STUDENT',
    govId: '••••••••9012',            // masked. Never render in full.
  },
  visitor: {
    name: 'Rohan Mehta', email: 'rohan.mehta@example.com',
    code: 'PM-4192', purpose: 'Guest lecture', role: 'VISITOR',
  },
  guard: { name: 'R. Singh', gate: 'Gate 2', gateId: 'GATE-2', since: '08:00', role: 'GUARD' },
};

export const GATES = [
  { id: 'GATE-1', name: 'Gate 1', location: 'Main entrance',   type: 'Vehicle + pedestrian', active: true },
  { id: 'GATE-2', name: 'Gate 2', location: 'Hostel gate',     type: 'Pedestrian only',      active: true },
  { id: 'GATE-3', name: 'Gate 3', location: 'Service entrance',type: 'Vendor / logistics',   active: true },
];

export const EVENT = {
  name: 'TechFest 2026', code: 'EV-2214',
  from: '14 Aug 2026', to: '16 Aug 2026', range: '14–16 Aug 2026',
  registered: 580, attended: 464, rate: 80,
  perDay: [{ day: '14 Aug', n: 464 }, { day: '15 Aug', n: 431 }, { day: '16 Aug', n: 398 }],
};

/** 600 = 580 valid + 20 errors = 580 passes. Do not break this arithmetic. */
export const BULK = {
  uploaded: 600, valid: 580, errors: 20, passes: 580,
  generated: 312,                     // mid-flight, for the progress screen
  stages: { identity: 580, qr: 312, email: 297 },
  errorRows: [
    { row: 34, email: 'not-an-email',            result: 'Invalid email' },
    { row: 51, email: 'rohan.mehta@example.com', result: 'Duplicate in sheet' },
    { row: 77, email: 'blocked@example.com',     result: 'On blocklist' },
  ],
};

export const PASSES = [
  {
    id: 1, type: 'daily', code: 'S-20418', holder: PEOPLE.student.name,
    campus: CAMPUS.name, validity: 'Rolling daily — no end date',
    status: 'ACTIVE', issued: '12 Jan 2026',
  },
  {
    id: 2, type: 'event', code: 'EV-2214', holder: PEOPLE.student.name,
    eventName: EVENT.name, campus: CAMPUS.name, validity: EVENT.range,
    status: 'PENDING', note: 'starts 14 Aug',   // NOT "Upcoming" — that is not a status
  },
  {
    id: 3, type: 'event', code: 'EV-1988', holder: PEOPLE.student.name,
    eventName: 'Sports Day', campus: CAMPUS.name, validity: '02 Jun 2026',
    status: 'EXPIRED',
  },
  {
    id: 4, type: 'daily', code: 'S-19207', holder: PEOPLE.student.name,
    campus: CAMPUS.name, validity: '2025', status: 'REVOKED',
    note: 'expired credential',
  },
  {
    id: 5, type: 'visitor', code: 'PM-4192', holder: PEOPLE.visitor.name,
    campus: CAMPUS.name, validity: '08–09 Jul 2026', status: 'ACTIVE',
  },
];

/** ENTRY ONLY. No exit rows, no duration, no direction — anywhere, ever. */
export const ENTRIES = [
  { id: 1, at: '09:38', day: 'Today',    gate: 'Gate 1', holder: PEOPLE.student.name, attributedTo: null },
  { id: 2, at: '08:52', day: 'Monday',   gate: 'Gate 2', holder: PEOPLE.student.name, attributedTo: null },
  { id: 3, at: '10:04', day: '14 Aug',   gate: 'Gate 1', holder: PEOPLE.student.name, attributedTo: EVENT.name },
];

export const VISITOR_REQUESTS = [
  { id: 1, name: 'Rohan Mehta',  code: 'PM-4192', email: 'rohan.mehta@example.com',
    purpose: 'Guest lecture', host: PEOPLE.faculty.name, dept: 'Computer Science',
    dates: '08–09 Jul 2026', status: 'PENDING', blocklist: 'clear' },
  { id: 2, name: 'Priya Nair',   code: 'PM-4193', email: 'priya.nair@example.com',
    purpose: 'Campus tour', host: 'Dr. M. Iyer', dept: 'Electronics',
    dates: '09 Jul 2026', status: 'PENDING', blocklist: 'clear' },
  { id: 3, name: 'Imran Sheikh', code: 'PM-4194', email: 'imran.sheikh@example.com',
    purpose: 'Vendor meeting', host: 'Dr. S. Verma', dept: 'Mechanical',
    dates: '10 Jul 2026', status: 'PENDING', blocklist: 'match found' },
];

export const AUDIT = [
  { id: 1, time: '09:41:02', actor: PEOPLE.faculty.name, action: 'approved visitor request',
    target: 'PM-4192', ip: '10.4.2.88', agent: 'Chrome 141 · Windows' },
  { id: 2, time: '09:12:47', actor: PEOPLE.campusAdmin.name, action: 'changed gate configuration',
    target: 'Gate 2', ip: '10.4.2.15', agent: 'Chrome 141 · Windows',
    changes: [
      { field: 'Active hours', from: '06:00 – 22:00', to: '05:30 – 23:00' },
      { field: 'Approval required', from: 'Yes', to: 'No' },
    ] },
  { id: 3, time: '08:58:15', actor: 'System', action: 'revoked pass',
    target: 'S-19207 — expired credential' },
];

export const PLATFORM = {
  campuses: 4, users: 11840, activePasses: 3902, entriesToday: 1147, servicesHealthy: '6 of 6',
  rows: [
    { campus: 'Main Campus',        code: 'MAIN', users: 4210, passes: 1284, entries: 312, status: 'Active' },
    { campus: 'North Campus',       code: 'NRTH', users: 3105, passes: 1042, entries: 288, status: 'Active' },
    { campus: 'Riverside Institute',code: 'RVSD', users: 2890, passes: 998,  entries: 301, status: 'Active' },
    { campus: 'Lakeside Campus',    code: 'LKSD', users: 1635, passes: 578,  entries: 246, status: 'Suspended' },
  ],
};

export const CAMPUS_STATS = {
  activePasses: 1284, entriesToday: 312, visitorQueue: 15, gatesLive: 6,
  // There is NO exitsToday. Entry-only is a product rule, not an oversight.
};

/** The OTP numbers, in one place so no screen invents its own. */
export const OTP = { length: 6, expiryMinutes: 10, maxAttempts: 5, resendSeconds: 60 };
