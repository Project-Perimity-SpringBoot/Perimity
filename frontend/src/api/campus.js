import { campusApi } from './client';

/** campus-service. */
export const campus = {
  list:       (params = {}) => campusApi.get('/api/campus/campuses', { params }),
  get:        (id)          => campusApi.get(`/api/campus/campuses/${id}`),
  byCode:     (code)        => campusApi.get(`/api/campus/campuses/by-code/${code}`),
  stats:      ()            => campusApi.get('/api/campus/campuses/stats'),
  create:     (body)        => campusApi.post('/api/campus/campuses', body),
  update:     (id, body)    => campusApi.put(`/api/campus/campuses/${id}`, body),
  setStatus:  (id, active)  => campusApi.patch(`/api/campus/campuses/${id}/status`, { active }),

  gates:      (campusId)    => campusApi.get(`/api/campus/campuses/${campusId}/gates`),
  gate:       (campusId, id) => campusApi.get(`/api/campus/campuses/${campusId}/gates/${id}`),
  createGate: (campusId, body) => campusApi.post(`/api/campus/campuses/${campusId}/gates`, body),
  updateGate: (campusId, id, body) => campusApi.put(`/api/campus/campuses/${campusId}/gates/${id}`, body),

  config:        (campusId) => campusApi.get(`/api/campus/campuses/${campusId}/config`),
  configValue:   (campusId, key) => campusApi.get(`/api/campus/campuses/${campusId}/config/${key}`),
  setConfig:     (campusId, key, value) =>
    campusApi.put(`/api/campus/campuses/${campusId}/config/${key}`, { value }),
  setConfigBulk: (campusId, body) => campusApi.put(`/api/campus/campuses/${campusId}/config`, body),
  restoreDefaults: (campusId) =>
    campusApi.post(`/api/campus/campuses/${campusId}/config/restore-defaults`),

  logoUrl:    (campusId)    => `${import.meta.env.VITE_CAMPUS_URL}/api/campus/campuses/${campusId}/logo`,
  uploadLogo: (campusId, file) => {
    const form = new FormData();
    form.append('file', file);
    return campusApi.post(`/api/campus/campuses/${campusId}/logo`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } });
  },
};

/**
 * The six config keys, spelled as campus-service stores them.
 *
 * `repeat_entry_result` is an enum GREEN/AMBER, not a boolean. An earlier
 * draft called it `repeat.entry.allowed` and treated it as true/false; guard
 * reads the enum. Getting this wrong means the setting silently never applies.
 */
export const CONFIG_KEYS = {
  REPEAT_ENTRY_RESULT: 'repeat_entry_result',
  VISITOR_APPROVAL_REQUIRED: 'visitor_approval_required',
  PASS_VALIDITY_DAYS: 'pass_validity_days',
  GATE_OPEN_TIME: 'gate_open_time',
  GATE_CLOSE_TIME: 'gate_close_time',
  BULK_MAX_ROWS: 'bulk_max_rows',
};
