import { authApi } from './client';

/** auth-service. */
export const auth = {
  me:            () => authApi.get('/api/auth/me'),
  login:         (email, password) => authApi.post('/api/auth/login', { email, password }),
  requestOtp:    (email, purpose = 'LOGIN', campusId = null) =>
    authApi.post('/api/auth/otp/request', { email, purpose, campusId }),
  verifyOtp:     (email, code, purpose = 'LOGIN') =>
    authApi.post('/api/auth/otp/verify', { email, code, purpose }),
  logout:        () => authApi.post('/api/auth/logout'),
  changePassword: (currentPassword, newPassword, confirmPassword) =>
    authApi.post('/api/auth/password/change', { currentPassword, newPassword, confirmPassword }),
  registerVisitor: (body) => authApi.post('/api/auth/visitors/register', body),

  users:         (params = {}) => authApi.get('/api/auth/users', { params }),
  user:          (id) => authApi.get(`/api/auth/users/${id}`),
  createUser:    (body) => authApi.post('/api/auth/users', body),
  updateUser:    (id, body) => authApi.put(`/api/auth/users/${id}`, body),
  setUserStatus: (id, active) => authApi.patch(`/api/auth/users/${id}/status`, { active }),

  blocklist:     (params = {}) => authApi.get('/api/auth/blocklist', { params }),
  blocklistCount: () => authApi.get('/api/auth/blocklist/count'),
  block:         (body) => authApi.post('/api/auth/blocklist', body),
  unblock:       (id) => authApi.delete(`/api/auth/blocklist/${id}`),

  audit:         (params = {}) => authApi.get('/api/auth/audit', { params }),
  auditByActor:  (actorUserId, params = {}) =>
    authApi.get(`/api/auth/audit/actor/${actorUserId}`, { params }),
  auditRange:    (from, to, params = {}) =>
    authApi.get('/api/auth/audit/range', { params: { from, to, ...params } }),
};
