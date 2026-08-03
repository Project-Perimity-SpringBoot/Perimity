import type { UserResponse } from '@/types/auth.types';
import type {
  BulkUploadBatchResponse, BulkValidationSummaryResponse, EventResponse,
  GatePassResponse, VisitorRequestResponse,
} from '@/types/gatepass.types';
import type { CampusGateResponse, CampusResponse, CampusStatsResponse } from '@/types/campus.types';
import type { DepartmentResponse, StudentProfileResponse } from '@/types/user.types';
import type { EntryLogResponse, ScanSessionResponse } from '@/types/guard.types';

/**
 * The constants block, verbatim. Every member's mocks use these exact people,
 * codes and numbers so two screens never disagree about who Sneha Kulkarni is.
 *
 * NO REAL INSTITUTION NAME appears anywhere in this file, deliberately — the
 * product is campus-agnostic and a fixture is the easiest place for one to leak
 * in and then get screenshotted into a demo.
 */
export const NOW = '2026-08-02T09:38:00';
const TODAY = '2026-08-02';

export const MOCK_USERS: Record<string, UserResponse> = {
  student: {
    id: 108, email: 'sneha.kulkarni@example.com', name: 'Sneha Kulkarni', phone: '+919876543210',
    role: 'STUDENT', campusId: 1, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2026-01-12T10:00:00', updatedAt: null,
  },
  faculty: {
    id: 42, email: 'a.rao@example.com', name: 'Dr. Anaya Rao', phone: null,
    role: 'FACULTY', campusId: 1, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2025-08-01T10:00:00', updatedAt: null,
  },
  campusAdmin: {
    id: 7, email: 's.verma@example.com', name: 'Dr. S. Verma', phone: null,
    role: 'CAMPUS_ADMIN', campusId: 1, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2025-06-01T10:00:00', updatedAt: null,
  },
  superAdmin: {
    id: 1, email: 'platform@example.com', name: 'Platform Owner', phone: null,
    // campusId is NULL for SUPER_ADMIN. Keeping that true in the fixture is what
    // makes blocker B5 visible while developing against mocks.
    role: 'SUPER_ADMIN', campusId: null, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2025-01-01T10:00:00', updatedAt: null,
  },
  visitor: {
    id: 210, email: 'rohan.mehta@example.com', name: 'Rohan Mehta', phone: '+919000000001',
    role: 'VISITOR', campusId: 1, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2026-07-30T10:00:00', updatedAt: null,
  },
  guard: {
    id: 55, email: 'r.singh@example.com', name: 'R. Singh', phone: null,
    role: 'GUARD', campusId: 1, active: true, locked: false, mustChangePassword: false,
    lastLoginAt: NOW, createdAt: '2025-09-01T10:00:00', updatedAt: null,
  },
};

export const MOCK_PASSES: GatePassResponse[] = [
  {
    id: 20418, holderUserId: 108, holderName: 'Sneha Kulkarni', campusId: 1,
    visitorRequestId: null, passType: 'DAILY', eventId: null, eventName: null,
    validFrom: '2026-01-12', validTo: null, status: 'ACTIVE', scannable: true,
    revokedReason: null, revokedBy: null, revokedAt: null, pausedReason: null,
    qrKey: 'campuses/1/qr/20418.png', pdfKey: 'campuses/1/pdf/20418.pdf',
    createdAt: '2026-01-12T10:04:00', updatedAt: null,
  },
  {
    id: 2214, holderUserId: 108, holderName: 'Sneha Kulkarni', campusId: 1,
    visitorRequestId: null, passType: 'EVENT', eventId: 9, eventName: 'TechFest 2026',
    validFrom: '2026-08-14', validTo: '2026-08-16', status: 'PENDING', scannable: false,
    revokedReason: null, revokedBy: null, revokedAt: null, pausedReason: null,
    qrKey: null, pdfKey: null, createdAt: '2026-07-28T11:00:00', updatedAt: null,
  },
  {
    id: 19207, holderUserId: 108, holderName: 'Sneha Kulkarni', campusId: 1,
    visitorRequestId: null, passType: 'DAILY', eventId: null, eventName: null,
    validFrom: '2025-01-10', validTo: '2025-12-31', status: 'REVOKED', scannable: false,
    revokedReason: 'Credential expired at end of academic year', revokedBy: 7,
    revokedAt: '2026-01-02T09:00:00', pausedReason: null,
    qrKey: null, pdfKey: null, createdAt: '2025-01-10T10:00:00', updatedAt: null,
  },
  {
    id: 4192, holderUserId: 210, holderName: 'Rohan Mehta', campusId: 1,
    visitorRequestId: 4192, passType: 'DAILY', eventId: null, eventName: null,
    validFrom: TODAY, validTo: '2026-08-03', status: 'ACTIVE', scannable: true,
    revokedReason: null, revokedBy: null, revokedAt: null, pausedReason: null,
    qrKey: 'campuses/1/qr/4192.png', pdfKey: null,
    createdAt: '2026-08-01T14:00:00', updatedAt: null,
  },
];

export const MOCK_REQUESTS: VisitorRequestResponse[] = [
  {
    id: 4192, campusId: 1, visitorName: 'Rohan Mehta', visitorEmail: 'rohan.mehta@example.com',
    visitorPhone: '+919000000001', purpose: 'Guest lecture on distributed systems',
    hostUserId: 42, eventId: null, visitFrom: TODAY, visitTo: '2026-08-03',
    // otpVerified stays FALSE — blocker B2 is real and the fixture must not hide
    // it, or the approve button looks like it works right up to the live backend.
    otpVerified: false, visitorUserId: null, status: 'PENDING',
    reviewedBy: null, reviewedAt: null, rejectReason: null,
    createdAt: '2026-08-01T14:00:00', updatedAt: null,
  },
  {
    id: 4193, campusId: 1, visitorName: 'Priya Nair', visitorEmail: 'priya.nair@example.com',
    visitorPhone: null, purpose: 'Campus tour for prospective students',
    hostUserId: 42, eventId: null, visitFrom: '2026-08-05', visitTo: '2026-08-05',
    otpVerified: false, visitorUserId: null, status: 'PENDING',
    reviewedBy: null, reviewedAt: null, rejectReason: null,
    createdAt: '2026-08-01T16:20:00', updatedAt: null,
  },
  {
    id: 4188, campusId: 1, visitorName: 'Imran Sheikh', visitorEmail: 'imran.sheikh@example.com',
    visitorPhone: null, purpose: 'Vendor meeting, procurement',
    hostUserId: 42, eventId: null, visitFrom: '2026-07-28', visitTo: '2026-07-28',
    otpVerified: true, visitorUserId: 211, status: 'REJECTED',
    reviewedBy: 42, reviewedAt: '2026-07-27T09:15:00',
    rejectReason: 'The lab is closed that week. Please pick a date after 10 August.',
    createdAt: '2026-07-26T10:00:00', updatedAt: '2026-07-27T09:15:00',
  },
];

export const MOCK_EVENTS: EventResponse[] = [
  {
    id: 9, campusId: 1, name: 'TechFest 2026', description: 'Three-day annual technical festival.',
    validFrom: '2026-08-14', validTo: '2026-08-16', createdBy: 42,
    cancelled: false, cancelledAt: null, runningToday: false, issuedPassCount: 580,
    createdAt: '2026-07-20T10:00:00', updatedAt: null,
  },
  {
    id: 6, campusId: 1, name: 'Open Day', description: null,
    validFrom: TODAY, validTo: TODAY, createdBy: 42,
    cancelled: false, cancelledAt: null, runningToday: true, issuedPassCount: 120,
    createdAt: '2026-07-15T10:00:00', updatedAt: null,
  },
];

/** 600 uploaded = 580 valid + 20 errors. Do not break this arithmetic. */
export const MOCK_BULK_SUMMARY: BulkValidationSummaryResponse = {
  batchId: 77, totalRows: 600, validRows: 580, invalidRows: 20,
  errors: [
    { rowNumber: 34, email: 'not-an-email', reason: 'Invalid email address' },
    { rowNumber: 51, email: 'rohan.mehta@example.com', reason: 'Duplicate row in this sheet' },
    // FR-BLK-4: a blocklist refusal carries NO reason, by design.
    { rowNumber: 77, email: 'blocked@example.com', reason: 'Refused' },
  ],
  errorReportKey: 'campuses/1/bulk/77/errors.csv',
  awaitingConfirmation: true,
};

export const MOCK_BULK_BATCH: BulkUploadBatchResponse = {
  id: 77, campusId: 1, uploadedBy: 42, passType: 'EVENT', eventId: 9,
  objectKey: 'campuses/1/bulk/77/sheet.xlsx', originalFilename: 'techfest-attendees.xlsx',
  status: 'PROCESSING', totalRows: 600, validRows: 580, invalidRows: 20,
  processedRows: 312, percentComplete: 53, errorReportKey: 'campuses/1/bulk/77/errors.csv',
  failureMessage: null, completedAt: null,
  createdAt: '2026-08-02T09:10:00', updatedAt: NOW,
};

export const MOCK_CAMPUSES: CampusResponse[] = [
  {
    id: 1, code: 'MAIN', name: 'Main Campus', address: '—',
    contactEmail: 'main@example.com', contactPhone: null, logoS3Key: null,
    adminUserId: 7, active: true, activeGateCount: 6,
    createdAt: '2025-01-01T10:00:00', updatedAt: null,
  },
  {
    id: 2, code: 'NRTH', name: 'North Campus', address: null,
    contactEmail: 'north@example.com', contactPhone: null, logoS3Key: null,
    adminUserId: null, active: true, activeGateCount: 3,
    createdAt: '2025-04-01T10:00:00', updatedAt: null,
  },
  {
    id: 3, code: 'LKSD', name: 'Lakeside Campus', address: null,
    contactEmail: null, contactPhone: null, logoS3Key: null,
    adminUserId: null, active: false, activeGateCount: 0,
    createdAt: '2025-09-01T10:00:00', updatedAt: null,
  },
];

export const MOCK_CAMPUS_STATS: CampusStatsResponse = {
  totalCampuses: 3, activeCampuses: 2, inactiveCampuses: 1,
};

export const MOCK_GATES: CampusGateResponse[] = [
  { id: 1, campusId: 1, name: 'Main Gate', location: 'South entrance', active: true, createdAt: '2025-01-01T10:00:00', updatedAt: null },
  { id: 2, campusId: 1, name: 'Gate 2', location: 'Near the library', active: true, createdAt: '2025-01-01T10:00:00', updatedAt: null },
  { id: 3, campusId: 1, name: 'Service Gate', location: null, active: false, createdAt: '2025-01-01T10:00:00', updatedAt: null },
];

export const MOCK_DEPARTMENTS: DepartmentResponse[] = [
  { id: 1, campusId: 1, code: 'CSE', name: 'Computer Science', active: true, createdAt: '2025-01-01T10:00:00', updatedAt: null },
  { id: 2, campusId: 1, code: 'DS', name: 'Data Science', active: true, createdAt: '2025-01-01T10:00:00', updatedAt: null },
  { id: 3, campusId: 1, code: 'MECH', name: 'Mechanical', active: true, createdAt: '2025-01-01T10:00:00', updatedAt: null },
];

export const MOCK_STUDENT_PROFILE: StudentProfileResponse = {
  id: 501, userId: 108, campusId: 1, departmentId: 2, departmentName: 'Data Science',
  rollNo: 'S-20418', govIdMasked: '********9012', govIdPresent: true,
  address: null, photoS3Key: null,
  createdAt: '2026-01-12T10:00:00', updatedAt: null,
};

export const MOCK_SESSION: ScanSessionResponse = {
  id: '66ad1f2c9e4b2a0012ab34cd', guardUserId: 55, campusId: 1,
  gateId: 2, gateName: 'Gate 2', state: 'OPEN',
  startedAt: '2026-08-02T08:00:00', endedAt: null,
  totalScans: 47, allowedCount: 44, deniedCount: 3,
};

/** ENTRY ONLY. No exit rows, ever — the product does not scan people out. */
export const MOCK_ENTRY_LOGS: EntryLogResponse[] = [
  {
    id: '66ad2011', campusId: 1, gateId: 2, gateName: 'Gate 2', guardUserId: 55,
    sessionId: MOCK_SESSION.id, passId: 20418, holderUserId: 108, holderName: 'Sneha Kulkarni',
    passType: 'DAILY', eventId: null, attributedEventId: 6, eventAttributed: true,
    scanResult: 'ALLOWED', denialReason: null, scannedAt: NOW, scanDate: TODAY,
  },
  {
    id: '66ad2012', campusId: 1, gateId: 2, gateName: 'Gate 2', guardUserId: 55,
    sessionId: MOCK_SESSION.id, passId: 4192, holderUserId: 210, holderName: 'Rohan Mehta',
    passType: 'DAILY', eventId: null, attributedEventId: null, eventAttributed: false,
    scanResult: 'AMBER', denialReason: null, scannedAt: '2026-08-02T09:31:00', scanDate: TODAY,
  },
  {
    id: '66ad2013', campusId: 1, gateId: 2, gateName: 'Gate 2', guardUserId: 55,
    sessionId: MOCK_SESSION.id, passId: 19207, holderUserId: 108, holderName: 'Sneha Kulkarni',
    passType: 'DAILY', eventId: null, attributedEventId: null, eventAttributed: false,
    scanResult: 'DENIED', denialReason: 'PASS_REVOKED', scannedAt: '2026-08-02T09:12:00', scanDate: TODAY,
  },
];
