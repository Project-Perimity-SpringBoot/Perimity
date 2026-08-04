import { gatepassClient } from '../client';
import { unwrap, unwrapList, unwrapPage, unwrapScalar, SCALAR_KEYS } from '../normalize';
import { fetchFile } from '../download';
import type { DownloadedFile, Paged, PageRequest } from '@/types/api';
import type { PassStatus, PassType, RequestStatus } from '@/types/enums';
import type {
  BulkConfirmRequest, BulkRetryResult, BulkUploadBatchResponse, BulkValidationSummaryResponse,
  EventAttendanceSummaryResponse, EventCreateRequest, EventResponse, EventUpdateRequest,
  GatePassCreateRequest, GatePassResponse, GatePassStatusUpdateRequest,
  VisitorRequestCreateRequest, VisitorRequestDecisionRequest, VisitorRequestResponse,
} from '@/types/gatepass.types';

const PASSES = '/api/gatepass/passes';
const REQUESTS = '/api/gatepass/visitor-requests';
const EVENTS = '/api/gatepass/events';
const BULK = '/api/gatepass/bulk';

export const passApi = {
  async issue(body: GatePassCreateRequest): Promise<GatePassResponse> {
    const { data } = await gatepassClient.post<unknown>(PASSES, body);
    return unwrap<GatePassResponse>(data);
  },
  async changeStatus(id: number, body: GatePassStatusUpdateRequest): Promise<GatePassResponse> {
    const { data } = await gatepassClient.patch<unknown>(`${PASSES}/${id}/status`, body);
    return unwrap<GatePassResponse>(data);
  },
  /** Re-queue a pass stuck at PENDING because QR generation never finished. */
  async republish(id: number): Promise<GatePassResponse> {
    const { data } = await gatepassClient.post<unknown>(`${PASSES}/${id}/republish`);
    return unwrap<GatePassResponse>(data);
  },
  async mine(): Promise<GatePassResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/mine`);
    return unwrapList<GatePassResponse>(data);
  },
  async mineActive(): Promise<GatePassResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/mine/active`);
    return unwrapList<GatePassResponse>(data);
  },
  async getOne(id: number): Promise<GatePassResponse> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/${id}`);
    return unwrap<GatePassResponse>(data);
  },
  async byHolder(holderUserId: number): Promise<GatePassResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/holder/${holderUserId}`);
    return unwrapList<GatePassResponse>(data);
  },
  async byEvent(eventId: number): Promise<GatePassResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/event/${eventId}`);
    return unwrapList<GatePassResponse>(data);
  },
  /** The response map keys on the enum NAME, not "count". */
  async count(status: PassStatus): Promise<number> {
    const { data } = await gatepassClient.get<unknown>(`${PASSES}/count`, { params: { status } });
    return unwrapScalar<number>(data, SCALAR_KEYS.passCount(status));
  },
};

export const visitorRequestApi = {
  async submit(body: VisitorRequestCreateRequest): Promise<VisitorRequestResponse> {
    const { data } = await gatepassClient.post<unknown>(REQUESTS, body);
    return unwrap<VisitorRequestResponse>(data);
  },
  async decide(id: number, body: VisitorRequestDecisionRequest): Promise<VisitorRequestResponse> {
    const { data } = await gatepassClient.patch<unknown>(`${REQUESTS}/${id}/decision`, body);
    return unwrap<VisitorRequestResponse>(data);
  },
  async cancel(id: number): Promise<VisitorRequestResponse> {
    const { data } = await gatepassClient.patch<unknown>(`${REQUESTS}/${id}/cancel`);
    return unwrap<VisitorRequestResponse>(data);
  },
  async getOne(id: number): Promise<VisitorRequestResponse> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/${id}`);
    return unwrap<VisitorRequestResponse>(data);
  },
  async passFor(id: number): Promise<GatePassResponse> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/${id}/pass`);
    return unwrap<GatePassResponse>(data);
  },
  /** Campus queue. Oldest first server-side, because it is a queue. */
  async queue(status: RequestStatus, page: PageRequest = {}): Promise<Paged<VisitorRequestResponse>> {
    const { data } = await gatepassClient.get<unknown>(REQUESTS, { params: { status, ...page } });
    return unwrapPage<VisitorRequestResponse>(data);
  },
  async myQueue(status: RequestStatus, page: PageRequest = {}): Promise<Paged<VisitorRequestResponse>> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/mine`, { params: { status, ...page } });
    return unwrapPage<VisitorRequestResponse>(data);
  },
  async myHistory(): Promise<VisitorRequestResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/my-history`);
    return unwrapList<VisitorRequestResponse>(data);
  },
  async byEmail(email: string): Promise<VisitorRequestResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/by-email`, { params: { email } });
    return unwrapList<VisitorRequestResponse>(data);
  },
  async pendingCount(): Promise<number> {
    const { data } = await gatepassClient.get<unknown>(`${REQUESTS}/pending-count`);
    return unwrapScalar<number>(data, SCALAR_KEYS.pendingRequests);
  },
};

export const eventApi = {
  async create(body: EventCreateRequest): Promise<EventResponse> {
    const { data } = await gatepassClient.post<unknown>(EVENTS, body);
    return unwrap<EventResponse>(data);
  },
  async getOne(id: number): Promise<EventResponse> {
    const { data } = await gatepassClient.get<unknown>(`${EVENTS}/${id}`);
    return unwrap<EventResponse>(data);
  },
  async list(page: PageRequest = {}): Promise<Paged<EventResponse>> {
    const { data } = await gatepassClient.get<unknown>(EVENTS, { params: page });
    return unwrapPage<EventResponse>(data);
  },
  async runningToday(): Promise<EventResponse[]> {
    const { data } = await gatepassClient.get<unknown>(`${EVENTS}/running`);
    return unwrapList<EventResponse>(data);
  },
  async update(id: number, body: EventUpdateRequest): Promise<EventResponse> {
    const { data } = await gatepassClient.put<unknown>(`${EVENTS}/${id}`, body);
    return unwrap<EventResponse>(data);
  },
  /** Never deletes. Revokes every pass issued for the event. */
  async cancel(id: number): Promise<EventResponse> {
    const { data } = await gatepassClient.patch<unknown>(`${EVENTS}/${id}/cancel`);
    return unwrap<EventResponse>(data);
  },
  async attendanceSummary(id: number): Promise<EventAttendanceSummaryResponse> {
    const { data } = await gatepassClient.get<unknown>(`${EVENTS}/${id}/attendance-summary`);
    return unwrap<EventAttendanceSummaryResponse>(data);
  },
  attendeeCsv: (id: number): Promise<DownloadedFile> =>
    fetchFile(gatepassClient, `${EVENTS}/${id}/attendees.csv`),
};

export const bulkApi = {
  /** Fast path. Creates nothing; returns counts and row errors to confirm. */
  async validate(file: File, passType: PassType, eventId?: number): Promise<BulkValidationSummaryResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('passType', passType);
    if (eventId !== undefined) form.append('eventId', String(eventId));
    const { data } = await gatepassClient.post<unknown>(`${BULK}/validate`, form);
    return unwrap<BulkValidationSummaryResponse>(data);
  },
  async confirm(batchId: number, body: BulkConfirmRequest): Promise<BulkUploadBatchResponse> {
    const { data } = await gatepassClient.post<unknown>(`${BULK}/${batchId}/confirm`, body);
    return unwrap<BulkUploadBatchResponse>(data);
  },
  async retry(batchId: number): Promise<BulkRetryResult> {
    const { data } = await gatepassClient.post<unknown>(`${BULK}/${batchId}/retry`);
    return unwrap<BulkRetryResult>(data);
  },
  async getBatch(batchId: number): Promise<BulkUploadBatchResponse> {
    const { data } = await gatepassClient.get<unknown>(`${BULK}/${batchId}`);
    return unwrap<BulkUploadBatchResponse>(data);
  },
  async history(page: PageRequest = {}): Promise<Paged<BulkUploadBatchResponse>> {
    const { data } = await gatepassClient.get<unknown>(BULK, { params: page });
    return unwrapPage<BulkUploadBatchResponse>(data);
  },
  async errorReportUrl(batchId: number): Promise<string> {
    const { data } = await gatepassClient.get<unknown>(`${BULK}/${batchId}/errors`);
    return unwrapScalar<string>(data, SCALAR_KEYS.downloadUrl);
  },
  template: (passType: PassType): Promise<DownloadedFile> =>
    fetchFile(gatepassClient, `${BULK}/template`, { passType }),
};
