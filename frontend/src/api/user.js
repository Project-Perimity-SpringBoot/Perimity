import { userApi } from './client';

/** user-service. */
export const user = {
  myStudentProfile: () => userApi.get('/api/user/students/me'),
  myFacultyProfile: () => userApi.get('/api/user/faculty/me'),
  student:          (id) => userApi.get(`/api/user/students/${id}`),
  studentByUser:    (userId) => userApi.get(`/api/user/students/by-user/${userId}`),
  faculty:          (id) => userApi.get(`/api/user/faculty/${id}`),
  facultyByUser:    (userId) => userApi.get(`/api/user/faculty/by-user/${userId}`),
  students:         (params = {}) => userApi.get('/api/user/students', { params }),
  studentCount:     (params = {}) => userApi.get('/api/user/students/count', { params }),
  facultyCount:     (params = {}) => userApi.get('/api/user/faculty/count', { params }),
  updateStudent:    (id, body) => userApi.put(`/api/user/students/${id}`, body),
  updateFaculty:    (id, body) => userApi.put(`/api/user/faculty/${id}`, body),

  /** A signed, short-lived URL. Do NOT cache it — it expires. */
  studentPhotoUrl:  (id) => userApi.get(`/api/user/students/${id}/photo-url`),
  facultyPhotoUrl:  (id) => userApi.get(`/api/user/faculty/${id}/photo-url`),

  departments:      () => userApi.get('/api/user/departments'),
  createDepartment: (body) => userApi.post('/api/user/departments', body),
  updateDepartment: (id, body) => userApi.put(`/api/user/departments/${id}`, body),

  myDocuments:      () => userApi.get('/api/user/documents/me'),
  documentsFor:     (userId) => userApi.get(`/api/user/documents/user/${userId}`),
  pendingDocuments: (userId) => userApi.get(`/api/user/documents/user/${userId}/pending`),
  documentUrl:      (id) => userApi.get(`/api/user/documents/${id}/url`),
  verifyDocument:   (id, status, reason) =>
    userApi.patch(`/api/user/documents/${id}/verification`, { status, reason }),
  uploadDocument:   (form) =>
    userApi.post('/api/user/documents', form,
      { headers: { 'Content-Type': 'multipart/form-data' } }),
};
