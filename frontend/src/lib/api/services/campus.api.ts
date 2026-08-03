import { campusClient } from '../client';
import { unwrap, unwrapList, unwrapScalar, SCALAR_KEYS } from '../normalize';
import type {
  CampusConfigBulkUpsertRequest, CampusConfigResponse, CampusConfigUpsertRequest,
  CampusCreateRequest, CampusGateCreateRequest, CampusGateResponse, CampusGateUpdateRequest,
  CampusResponse, CampusStatsResponse, CampusStatusUpdateRequest, CampusUpdateRequest,
} from '@/types/campus.types';

const CAMPUSES = '/api/campus/campuses';

export const campusApi = {
  async create(body: CampusCreateRequest): Promise<CampusResponse> {
    const { data } = await campusClient.post<unknown>(CAMPUSES, body);
    return unwrap<CampusResponse>(data);
  },
  /** `code` is immutable — it is baked into storage prefixes and pass URLs. */
  async update(id: number, body: CampusUpdateRequest): Promise<CampusResponse> {
    const { data } = await campusClient.put<unknown>(`${CAMPUSES}/${id}`, body);
    return unwrap<CampusResponse>(data);
  },
  async changeStatus(id: number, body: CampusStatusUpdateRequest): Promise<CampusResponse> {
    const { data } = await campusClient.patch<unknown>(`${CAMPUSES}/${id}/status`, body);
    return unwrap<CampusResponse>(data);
  },
  async getOne(id: number): Promise<CampusResponse> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${id}`);
    return unwrap<CampusResponse>(data);
  },
  async byCode(code: string): Promise<CampusResponse> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/by-code/${code}`);
    return unwrap<CampusResponse>(data);
  },
  async list(includeInactive = false): Promise<CampusResponse[]> {
    const { data } = await campusClient.get<unknown>(CAMPUSES, { params: { includeInactive } });
    return unwrapList<CampusResponse>(data);
  },
  async stats(): Promise<CampusStatsResponse> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/stats`);
    return unwrap<CampusStatsResponse>(data);
  },
  async logoUrl(campusId: number): Promise<string> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${campusId}/logo`);
    return unwrapScalar<string>(data, SCALAR_KEYS.downloadUrl);
  },
  async uploadLogo(campusId: number, file: File): Promise<CampusResponse> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await campusClient.post<unknown>(`${CAMPUSES}/${campusId}/logo`, form);
    return unwrap<CampusResponse>(data);
  },
  async removeLogo(campusId: number): Promise<CampusResponse> {
    const { data } = await campusClient.delete<unknown>(`${CAMPUSES}/${campusId}/logo`);
    return unwrap<CampusResponse>(data);
  },
};

export const gateApi = {
  async create(campusId: number, body: CampusGateCreateRequest): Promise<CampusGateResponse> {
    const { data } = await campusClient.post<unknown>(`${CAMPUSES}/${campusId}/gates`, body);
    return unwrap<CampusGateResponse>(data);
  },
  async update(campusId: number, id: number, body: CampusGateUpdateRequest): Promise<CampusGateResponse> {
    const { data } = await campusClient.put<unknown>(`${CAMPUSES}/${campusId}/gates/${id}`, body);
    return unwrap<CampusGateResponse>(data);
  },
  async getOne(campusId: number, id: number): Promise<CampusGateResponse> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${campusId}/gates/${id}`);
    return unwrap<CampusGateResponse>(data);
  },
  async list(campusId: number, includeClosed = false): Promise<CampusGateResponse[]> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${campusId}/gates`, {
      params: { includeClosed },
    });
    return unwrapList<CampusGateResponse>(data);
  },
};

export const campusConfigApi = {
  async list(campusId: number): Promise<CampusConfigResponse[]> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${campusId}/config`);
    return unwrapList<CampusConfigResponse>(data);
  },
  async get(campusId: number, key: string): Promise<CampusConfigResponse> {
    const { data } = await campusClient.get<unknown>(`${CAMPUSES}/${campusId}/config/${key}`);
    return unwrap<CampusConfigResponse>(data);
  },
  async upsert(campusId: number, key: string, body: CampusConfigUpsertRequest): Promise<CampusConfigResponse> {
    const { data } = await campusClient.put<unknown>(`${CAMPUSES}/${campusId}/config/${key}`, body);
    return unwrap<CampusConfigResponse>(data);
  },
  /** Saves the whole settings screen. All or nothing, server-side. */
  async upsertAll(campusId: number, body: CampusConfigBulkUpsertRequest): Promise<CampusConfigResponse[]> {
    const { data } = await campusClient.put<unknown>(`${CAMPUSES}/${campusId}/config`, body);
    return unwrapList<CampusConfigResponse>(data);
  },
  async restoreDefaults(campusId: number): Promise<CampusConfigResponse[]> {
    const { data } = await campusClient.post<unknown>(`${CAMPUSES}/${campusId}/config/restore-defaults`);
    return unwrapList<CampusConfigResponse>(data);
  },
};
