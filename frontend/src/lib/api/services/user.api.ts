import { userClient } from '../client';
import { unwrap, unwrapList, unwrapPage, unwrapScalar, SCALAR_KEYS } from '../normalize';
import type { Paged, PageRequest } from '@/types/api';
import type { DocumentType } from '@/types/enums';
import type {
  DepartmentCreateRequest, DepartmentResponse, DepartmentUpdateRequest,
  DocumentResponse, DocumentVerificationRequest,
  FacultyProfileCreateRequest, FacultyProfileResponse, FacultyProfileUpdateRequest,
  PresignedUrlResponse,
  StudentProfileCreateRequest, StudentProfileResponse, StudentProfileUpdateRequest,
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
