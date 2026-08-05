import { userClient } from '../client';
import { unwrap, unwrapList, unwrapPage, unwrapScalar, SCALAR_KEYS } from '../normalize';
import { fetchFile } from '../download';
import type { DownloadedFile, Paged, PageRequest } from '@/types/api';
import type { DocumentType } from '@/types/enums';
import type {
  DepartmentCreateRequest, DepartmentResponse, DepartmentUpdateRequest,
  DocumentResponse, DocumentVerificationRequest,
  FacultyProfileCreateRequest, FacultyProfileResponse, FacultyProfileUpdateRequest,
  PresignedUrlResponse,
  StudentProfileCreateRequest, StudentProfileResponse, StudentProfileUpdateRequest,
  StudentSelfDetailsRequest, StudentVerificationDecisionRequest,
  ImportBatchResponse, ImportRowResponse, ImportRowOutcome,
  ImportSettingsRequest, ImportSettingsResponse,
} from '@/types/user.types';

const STUDENTS = '/api/user/students';
const FACULTY = '/api/user/faculty';
const DEPARTMENTS = '/api/user/departments';
const DOCUMENTS = '/api/user/documents';

interface DirectoryQuery extends PageRequest {
  campusId?: number;
  departmentId?: number;
}

export const studentApi = {
  async create(body: StudentProfileCreateRequest): Promise<StudentProfileResponse> {
    const { data } = await userClient.post<unknown>(STUDENTS, body);
    return unwrap<StudentProfileResponse>(data);
  },
  async getOne(id: number): Promise<StudentProfileResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/${id}`);
    return unwrap<StudentProfileResponse>(data);
  },
  async byUser(userId: number): Promise<StudentProfileResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/by-user/${userId}`);
    return unwrap<StudentProfileResponse>(data);
  },
  async me(): Promise<StudentProfileResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/me`);
    return unwrap<StudentProfileResponse>(data);
  },
  async list(query: DirectoryQuery = {}): Promise<Paged<StudentProfileResponse>> {
    const { data } = await userClient.get<unknown>(STUDENTS, { params: query });
    return unwrapPage<StudentProfileResponse>(data);
  },
  async count(campusId?: number): Promise<number> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/count`, { params: { campusId } });
    return unwrapScalar<number>(data, SCALAR_KEYS.profileCount);
  },
  /** Editing rollNo, govId, departmentId or the photo pauses the holder's pass. */
  async update(id: number, body: StudentProfileUpdateRequest): Promise<StudentProfileResponse> {
    const { data } = await userClient.put<unknown>(`${STUDENTS}/${id}`, body);
    return unwrap<StudentProfileResponse>(data);
  },
  async uploadPhoto(id: number, file: File): Promise<StudentProfileResponse> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await userClient.post<unknown>(`${STUDENTS}/${id}/photo`, form);
    return unwrap<StudentProfileResponse>(data);
  },
  async photoUrl(id: number): Promise<PresignedUrlResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/${id}/photo-url`);
    return unwrap<PresignedUrlResponse>(data);
  },
  async removePhoto(id: number): Promise<StudentProfileResponse> {
    const { data } = await userClient.delete<unknown>(`${STUDENTS}/${id}/photo`);
    return unwrap<StudentProfileResponse>(data);
  },

  /* ------------------------------------------------------------------
   * Self-declared details and verification
   * ------------------------------------------------------------------ */

  /**
   * Save the signed-in student's own details.
   *
   * No id in the path — the server reads the account from the token. That is
   * the point: there is no parameter here that could be swapped for another
   * student's, so no ownership check can be forgotten.
   *
   * WHOLE-OBJECT. Unlike update() above, which patches, this replaces every
   * field. Send the complete form, not a diff.
   *
   * Refused with 409 while the profile is SUBMITTED. Succeeds while VERIFIED
   * but resets the profile to DRAFT and clears the approval.
   */
  async updateOwnDetails(body: StudentSelfDetailsRequest): Promise<StudentProfileResponse> {
    const { data } = await userClient.put<unknown>(`${STUDENTS}/me/details`, body);
    return unwrap<StudentProfileResponse>(data);
  },

  /**
   * Hand the details to faculty. No body — the only input is who is asking.
   *
   * Not idempotent, and the server refuses a second call with 409: submitting
   * stamps the timestamp the review queue is ordered by, so a double click
   * would otherwise send the student to the back of it.
   */
  async submitOwnDetails(): Promise<StudentProfileResponse> {
    const { data } = await userClient.post<unknown>(`${STUDENTS}/me/details/submit`);
    return unwrap<StudentProfileResponse>(data);
  },

  /**
   * The reviewer's queue — students waiting for a decision, OLDEST FIRST.
   *
   * Do not add a sort parameter. The server orders by submittedAt ascending on
   * purpose so nobody is buried at the back of a backlog, and passing a Sort
   * makes Spring Data emit two ORDER BY clauses.
   */
  async listPendingVerification(query: DirectoryQuery = {}): Promise<Paged<StudentProfileResponse>> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/pending`, { params: query });
    return unwrapPage<StudentProfileResponse>(data);
  },

  async countPendingVerification(campusId?: number): Promise<number> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/pending/count`, {
      params: { campusId },
    });
    return unwrapScalar<number>(data, SCALAR_KEYS.profileCount);
  },

  /**
   * Approve or reject a student's submitted details.
   *
   * remarks is mandatory when approved is false — the student reads it and
   * cannot correct anything without it. The reviewer's identity is NOT sent;
   * the server takes it from the token.
   */
  async decideVerification(
    id: number,
    body: StudentVerificationDecisionRequest,
  ): Promise<StudentProfileResponse> {
    const { data } = await userClient.patch<unknown>(`${STUDENTS}/${id}/verification`, body);
    return unwrap<StudentProfileResponse>(data);
  },
};

/**
 * Bulk onboarding from a Google Form responses sheet.
 *
 * Upload and confirm are TWO calls on purpose. Nothing is created until a
 * person has read the preview and pressed confirm — rows import as VERIFIED
 * and verifiedBy records whoever confirmed, so there has to be a moment a
 * named human took responsibility. See docs/BULK_STUDENT_ONBOARDING.md.
 */
export const studentImportApi = {
  /**
   * Upload a sheet and validate every row. Creates no accounts.
   *
   * A batch that comes back FAILED is still a successful request — the answer
   * is "this sheet cannot be used, here is why". Read `status`, not the HTTP
   * code, to decide what to show.
   */
  async upload(file: File): Promise<ImportBatchResponse> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await userClient.post<unknown>(`${STUDENTS}/import`, form);
    return unwrap<ImportBatchResponse>(data);
  },

  async getBatch(id: number): Promise<ImportBatchResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/import/${id}`);
    return unwrap<ImportBatchResponse>(data);
  },

  /**
   * Rows of a batch. Defaults to REJECTED only, which is what a person opening
   * the preview actually wants — a list of 197 fine rows is not worth reading.
   * Pass 'ALL' for everything.
   */
  async rows(
    id: number,
    outcome: ImportRowOutcome | 'ALL' = 'REJECTED',
    query: PageRequest = {},
  ): Promise<Paged<ImportRowResponse>> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/import/${id}/rows`, {
      params: { ...query, outcome },
    });
    return unwrapPage<ImportRowResponse>(data);
  },

  /**
   * Create the accounts. The only call here that writes.
   *
   * Safe to retry: a batch that failed partway resumes from the rows that were
   * never written, and matching on email means nothing is duplicated.
   */
  async confirm(id: number): Promise<ImportBatchResponse> {
    const { data } = await userClient.post<unknown>(`${STUDENTS}/import/${id}/confirm`);
    return unwrap<ImportBatchResponse>(data);
  },

  async list(query: PageRequest = {}): Promise<Paged<ImportBatchResponse>> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/import`, { params: query });
    return unwrapPage<ImportBatchResponse>(data);
  },

  /* ---------------------------------------------------------------- form */

  async settings(): Promise<ImportSettingsResponse> {
    const { data } = await userClient.get<unknown>(`${STUDENTS}/import/settings`);
    return unwrap<ImportSettingsResponse>(data);
  },

  /**
   * Both fields take whole URLs. The server extracts the ids, and refuses a
   * form link pasted where the responses sheet belongs — they are both
   * docs.google.com addresses with the id in the same place, so nothing about
   * the shape tells them apart.
   */
  async saveSettings(body: ImportSettingsRequest): Promise<ImportSettingsResponse> {
    const { data } = await userClient.put<unknown>(`${STUDENTS}/import/settings`, body);
    return unwrap<ImportSettingsResponse>(data);
  },

  /**
   * Read the latest responses straight from Drive and check every row.
   *
   * Same parser, validator and preview as an upload — only the source differs.
   * It exists because downloading a file and immediately uploading it again is
   * a round trip the server can do itself, and every manual step is a chance
   * to import last month's copy out of a downloads folder.
   */
  async pull(): Promise<ImportBatchResponse> {
    const { data } = await userClient.post<unknown>(`${STUDENTS}/import/pull`);
    return unwrap<ImportBatchResponse>(data);
  },

  /** The responses sheet as a file, for reading in Excel before importing. */
  async download(): Promise<DownloadedFile> {
    return fetchFile(userClient, `${STUDENTS}/import/download`);
  },
};

export const facultyApi = {
  async create(body: FacultyProfileCreateRequest): Promise<FacultyProfileResponse> {
    const { data } = await userClient.post<unknown>(FACULTY, body);
    return unwrap<FacultyProfileResponse>(data);
  },
  async getOne(id: number): Promise<FacultyProfileResponse> {
    const { data } = await userClient.get<unknown>(`${FACULTY}/${id}`);
    return unwrap<FacultyProfileResponse>(data);
  },
  async byUser(userId: number): Promise<FacultyProfileResponse> {
    const { data } = await userClient.get<unknown>(`${FACULTY}/by-user/${userId}`);
    return unwrap<FacultyProfileResponse>(data);
  },
  async me(): Promise<FacultyProfileResponse> {
    const { data } = await userClient.get<unknown>(`${FACULTY}/me`);
    return unwrap<FacultyProfileResponse>(data);
  },
  /** Readable by any signed-in user — this is the visitor's host picker. */
  async list(query: DirectoryQuery = {}): Promise<Paged<FacultyProfileResponse>> {
    const { data } = await userClient.get<unknown>(FACULTY, { params: query });
    return unwrapPage<FacultyProfileResponse>(data);
  },
  async count(campusId?: number): Promise<number> {
    const { data } = await userClient.get<unknown>(`${FACULTY}/count`, { params: { campusId } });
    return unwrapScalar<number>(data, SCALAR_KEYS.profileCount);
  },
  async update(id: number, body: FacultyProfileUpdateRequest): Promise<FacultyProfileResponse> {
    const { data } = await userClient.put<unknown>(`${FACULTY}/${id}`, body);
    return unwrap<FacultyProfileResponse>(data);
  },
  async uploadPhoto(id: number, file: File): Promise<FacultyProfileResponse> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await userClient.post<unknown>(`${FACULTY}/${id}/photo`, form);
    return unwrap<FacultyProfileResponse>(data);
  },
  async photoUrl(id: number): Promise<PresignedUrlResponse> {
    const { data } = await userClient.get<unknown>(`${FACULTY}/${id}/photo-url`);
    return unwrap<PresignedUrlResponse>(data);
  },
  async removePhoto(id: number): Promise<FacultyProfileResponse> {
    const { data } = await userClient.delete<unknown>(`${FACULTY}/${id}/photo`);
    return unwrap<FacultyProfileResponse>(data);
  },
};

export const departmentApi = {
  async create(body: DepartmentCreateRequest): Promise<DepartmentResponse> {
    const { data } = await userClient.post<unknown>(DEPARTMENTS, body);
    return unwrap<DepartmentResponse>(data);
  },
  async getOne(id: number, campusId?: number): Promise<DepartmentResponse> {
    const { data } = await userClient.get<unknown>(`${DEPARTMENTS}/${id}`, { params: { campusId } });
    return unwrap<DepartmentResponse>(data);
  },
  async list(campusId?: number, activeOnly = true): Promise<DepartmentResponse[]> {
    const { data } = await userClient.get<unknown>(DEPARTMENTS, { params: { campusId, activeOnly } });
    return unwrapList<DepartmentResponse>(data);
  },
  /** Deactivating retires it from new selections; it does not delete its users. */
  async update(id: number, body: DepartmentUpdateRequest, campusId?: number): Promise<DepartmentResponse> {
    const { data } = await userClient.put<unknown>(`${DEPARTMENTS}/${id}`, body, { params: { campusId } });
    return unwrap<DepartmentResponse>(data);
  },
};

export const documentApi = {
  async upload(userId: number, docType: DocumentType, file: File): Promise<DocumentResponse> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await userClient.post<unknown>(DOCUMENTS, form, { params: { userId, docType } });
    return unwrap<DocumentResponse>(data);
  },
  async getOne(id: number): Promise<DocumentResponse> {
    const { data } = await userClient.get<unknown>(`${DOCUMENTS}/${id}`);
    return unwrap<DocumentResponse>(data);
  },
  async downloadUrl(id: number): Promise<PresignedUrlResponse> {
    const { data } = await userClient.get<unknown>(`${DOCUMENTS}/${id}/url`);
    return unwrap<PresignedUrlResponse>(data);
  },
  async forUser(userId: number, docType?: DocumentType): Promise<DocumentResponse[]> {
    const { data } = await userClient.get<unknown>(`${DOCUMENTS}/user/${userId}`, { params: { docType } });
    return unwrapList<DocumentResponse>(data);
  },
  async mine(): Promise<DocumentResponse[]> {
    const { data } = await userClient.get<unknown>(`${DOCUMENTS}/me`);
    return unwrapList<DocumentResponse>(data);
  },
  async pendingFor(userId: number): Promise<DocumentResponse[]> {
    const { data } = await userClient.get<unknown>(`${DOCUMENTS}/user/${userId}/pending`);
    return unwrapList<DocumentResponse>(data);
  },
  async decide(id: number, body: DocumentVerificationRequest): Promise<DocumentResponse> {
    const { data } = await userClient.patch<unknown>(`${DOCUMENTS}/${id}/verification`, body);
    return unwrap<DocumentResponse>(data);
  },
  /** A verified document cannot be deleted. */
  async remove(id: number): Promise<void> {
    const { data } = await userClient.delete<unknown>(`${DOCUMENTS}/${id}`);
    unwrap<void>(data);
  },
};
