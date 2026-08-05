import type { DocumentType, Gender, ProfileType, ProfileVerificationStatus } from './enums';

export interface StudentProfileResponse {
  id: number;
  userId: number;
  campusId: number;
  departmentId: number | null;
  departmentName: string | null;
  rollNo: string | null;
  /** "********9012". The full value never leaves the server. */
  govIdMasked: string | null;
  govIdPresent: boolean;
  photoS3Key: string | null;

  /*
   * Self-declared. Unverified until verificationStatus says otherwise, so do
   * not treat any of it as identity — a pass carries the account name from
   * auth-service, not these.
   *
   * NULL ON THE DIRECTORY LIST. `/students` returns the forDirectory shape,
   * which blanks address, dateOfBirth and both phone numbers so that paging
   * through a campus cannot be used to harvest contact details. The single
   * reads (`/me`, `/{id}`, `/by-user/{id}`) and the pending queue return them
   * in full. A screen that needs a phone number must fetch one profile, not
   * read it off a list row — it will silently be null there.
   */
  firstName: string | null;
  middleName: string | null;
  lastName: string | null;
  /** The three parts joined, or null when none is set. Convenience, not identity. */
  displayName: string | null;
  /** ISO date, no time — "2004-08-19". */
  dateOfBirth: string | null;
  gender: Gender | null;
  address: string | null;
  phoneCountryCode: string | null;
  phoneNumber: string | null;
  altPhoneCountryCode: string | null;
  altPhoneNumber: string | null;

  verificationStatus: ProfileVerificationStatus;
  /** The server's own view of whether the student may edit right now. */
  editable: boolean;
  submittedAt: string | null;
  verifiedBy: number | null;
  verifiedAt: string | null;
  /** Why it was refused. Shown to the student so they can correct it. */
  verificationRemarks: string | null;

  createdAt: string;
  updatedAt: string | null;
}

/**
 * Body of PUT /students/me/details.
 *
 * WHOLE-OBJECT, unlike StudentProfileUpdateRequest which is a partial patch.
 * Every field is sent every time; omitting one clears it. The two endpoints
 * have deliberately different semantics — see the DTO javadoc in user-service.
 *
 * No id: the account comes from the token. Nothing here can be pointed at
 * another student.
 */
export interface StudentSelfDetailsRequest {
  firstName: string;
  middleName?: string | null;
  lastName: string;
  dateOfBirth: string;
  gender: Gender;
  address: string;
  phoneCountryCode: string;
  phoneNumber: string;
  altPhoneCountryCode?: string | null;
  altPhoneNumber?: string | null;
}

/**
 * Body of PATCH /students/{id}/verification.
 *
 * There is no verifiedBy field and there must not be one — the server takes the
 * reviewer from the token. remarks is required when approved is false.
 */
export interface StudentVerificationDecisionRequest {
  approved: boolean;
  remarks?: string | null;
}

/* ======================================================================
 * BULK STUDENT IMPORT
 * ====================================================================== */

export const IMPORT_BATCH_STATUSES = [
  'VALIDATING', 'VALIDATED', 'PROCESSING', 'COMPLETED', 'FAILED',
] as const;
export type ImportBatchStatus = (typeof IMPORT_BATCH_STATUSES)[number];

export const IMPORT_ROW_OUTCOMES = ['PENDING', 'CREATED', 'UPDATED', 'REJECTED'] as const;
export type ImportRowOutcome = (typeof IMPORT_ROW_OUTCOMES)[number];

export interface ImportBatchResponse {
  id: number;
  campusId: number;
  uploadedBy: number;
  filename: string | null;
  status: ImportBatchStatus;

  totalRows: number;
  /** Rows that passed validation. Eligible to be written on confirm. */
  validRows: number;
  createdCount: number;
  updatedCount: number;
  rejectedCount: number;

  /**
   * Imported, but with no photo. These students have an account and verified
   * details and CANNOT hold a pass until a photo exists, so this is shown as
   * its own number rather than folded into a success count.
   */
  missingPhotoCount: number;

  failureReason: string | null;
  confirmedAt: string | null;
  finishedAt: string | null;
  createdAt: string;

  /**
   * Whether confirm may run. True for VALIDATED and also for FAILED — a
   * confirm that died partway is resumable, and re-running it picks up only
   * the rows that were never written.
   */
  confirmable: boolean;
}

/**
 * The campus intake form.
 *
 * `configured` and `driveAvailable` are separate on purpose. A campus can have
 * its form set up perfectly while the server has no Drive access at all, and
 * the two need different advice — "finish setting up" versus "download the
 * sheet and upload it instead". One flag would send people to fix the wrong
 * thing.
 */
export interface ImportSettingsResponse {
  campusId: number;
  formUrl: string | null;
  responsesSheetId: string | null;
  configured: boolean;
  driveAvailable: boolean;
  updatedBy: number | null;
  updatedAt: string | null;
}

export interface ImportSettingsRequest {
  formUrl?: string | null;
  /** A whole Google Sheets URL. The server pulls the id out of it. */
  responsesSheetUrl?: string | null;
}

export interface ImportRowResponse {
  id: number;
  /** 1-based, counting the header, so it matches the spreadsheet on screen. */
  rowNumber: number;
  email: string | null;
  fullName: string | null;
  rollNo: string | null;
  departmentLabel: string | null;
  /** Whether a Drive link was found. Not whether the image downloaded. */
  hasPhoto: boolean;
  outcome: ImportRowOutcome;
  /** Why a row was rejected, written for a person with the sheet open. */
  message: string | null;
  userId: number | null;
}

export interface FacultyProfileResponse {
  id: number;
  userId: number;
  campusId: number;
  departmentId: number | null;
  departmentName: string | null;
  employeeId: string | null;
  designation: string | null;
  qualification: string | null;
  photoS3Key: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** No `head` and no `userCount` exist on the entity. */
export interface DepartmentResponse {
  id: number;
  campusId: number;
  code: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface DocumentResponse {
  id: number;
  userId: number;
  docType: DocumentType;
  s3Key: string;
  fileName: string;
  mimeType: string | null;
  verified: boolean;
  verifiedBy: number | null;
  verifiedAt: string | null;
  verificationRemarks: string | null;
  createdAt: string;
}

export interface PresignedUrlResponse {
  url: string;
  expiresAt: string;
  validForMinutes: number;
}

export interface ProfileSummaryResponse {
  userId: number;
  campusId: number;
  profileType: ProfileType;
  identifierCode: string | null;
  departmentId: number | null;
  photoS3Key: string | null;
  photoUrl: string | null;
}

/* ── Requests ── */

export interface StudentProfileCreateRequest {
  userId: number;
  campusId: number;
  departmentId?: number | null;
  rollNo?: string | null;
  /** 12 digits, or empty. */
  govId?: string | null;
  address?: string | null;
  photoS3Key?: string | null;
}

/** userId and campusId are immutable. */
export type StudentProfileUpdateRequest = Omit<
  StudentProfileCreateRequest, 'userId' | 'campusId'
>;

export interface FacultyProfileCreateRequest {
  userId: number;
  campusId: number;
  departmentId?: number | null;
  employeeId?: string | null;
  designation?: string | null;
  qualification?: string | null;
  photoS3Key?: string | null;
}

export type FacultyProfileUpdateRequest = Omit<
  FacultyProfileCreateRequest, 'userId' | 'campusId'
>;

export interface DepartmentCreateRequest {
  campusId: number;
  code: string;
  name: string;
}

export interface DepartmentUpdateRequest {
  name: string;
  active: boolean;
}

export interface DocumentVerificationRequest {
  verified: boolean;
  verifiedBy: number;
  /** Required when verified is false. */
  remarks?: string | null;
}

export interface DocumentUploadRequest {
  userId: number;
  docType: DocumentType;
  file: File;
}

/**
 * Fields that move a pass to PAUSED when edited. Transcribed from the
 * behaviour of StudentProfileService / FacultyProfileService, which call
 * gatepass-service's internal pauseHolder on these changes.
 */
export const SENSITIVE_STUDENT_FIELDS = ['rollNo', 'govId', 'departmentId', 'photoS3Key'] as const;
export const SENSITIVE_FACULTY_FIELDS = ['employeeId', 'departmentId', 'photoS3Key'] as const;
