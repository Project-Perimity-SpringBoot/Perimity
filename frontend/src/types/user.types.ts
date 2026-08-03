import type { DocumentType, ProfileType } from './enums';

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
  address: string | null;
  /** A storage key. Resolve via /students/{id}/photo-url. */
  photoS3Key: string | null;
  createdAt: string;
  updatedAt: string | null;
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
