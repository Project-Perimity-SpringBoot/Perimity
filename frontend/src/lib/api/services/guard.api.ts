import { guardClient } from '../client';
import { unwrap, unwrapList, unwrapPage } from '../normalize';
import type { Paged, PageRequest } from '@/types/api';
import type {
  EntryLogFilterRequest, EntryLogResponse, EntryStatsResponse, EventAttendanceQuery,
  EventAttendanceResponse, ScanRequest, ScanResponse, ScanSessionResponse, ScanSessionStartRequest,
} from '@/types/guard.types';

const SESSIONS = '/api/guard/sessions';
const LOGS = '/api/guard/entry-logs';

export const scanApi = {
  /**
   * Always resolves on a decided scan — a DENY is not an HTTP error, it is the
   * answer. Only "no open session" and "not a guard" reject.
   */
  async scan(body: ScanRequest): Promise<ScanResponse> {
    const { data } = await guardClient.post<unknown>('/api/guard/scan', body);
    return unwrap<ScanResponse>(data);
  },
};

export const sessionApi = {
  async start(body: ScanSessionStartRequest): Promise<ScanSessionResponse> {
    const { data } = await guardClient.post<unknown>(SESSIONS, body);
    return unwrap<ScanSessionResponse>(data);
  },
  async end(id: string): Promise<ScanSessionResponse> {
    const { data } = await guardClient.post<unknown>(`${SESSIONS}/${id}/end`);
    return unwrap<ScanSessionResponse>(data);
  },
  async current(): Promise<ScanSessionResponse> {
    const { data } = await guardClient.get<unknown>(`${SESSIONS}/current`);
    return unwrap<ScanSessionResponse>(data);
  },
  /** Supervision view: every guard on duty at this campus right now. */
  async open(): Promise<ScanSessionResponse[]> {
    const { data } = await guardClient.get<unknown>(`${SESSIONS}/open`);
    return unwrapList<ScanSessionResponse>(data);
  },
  async history(): Promise<ScanSessionResponse[]> {
    const { data } = await guardClient.get<unknown>(`${SESSIONS}/history`);
    return unwrapList<ScanSessionResponse>(data);
  },
};

export const entryLogApi = {
  /** POST because the filter is a body, not a query string. Range capped at 90 days. */
  async search(filter: EntryLogFilterRequest, page: PageRequest = {}): Promise<Paged<EntryLogResponse>> {
    const { data } = await guardClient.post<unknown>(`${LOGS}/search`, filter, { params: page });
    return unwrapPage<EntryLogResponse>(data);
  },
  async stats(filter: EntryLogFilterRequest): Promise<EntryStatsResponse> {
    const { data } = await guardClient.post<unknown>(`${LOGS}/stats`, filter);
    return unwrap<EntryStatsResponse>(data);
  },
  async byHolder(holderUserId: number, page: PageRequest = {}): Promise<Paged<EntryLogResponse>> {
    const { data } = await guardClient.get<unknown>(`${LOGS}/holder/${holderUserId}`, { params: page });
    return unwrapPage<EntryLogResponse>(data);
  },
  async byPass(passId: number): Promise<EntryLogResponse[]> {
    const { data } = await guardClient.get<unknown>(`${LOGS}/pass/${passId}`);
    return unwrapList<EntryLogResponse>(data);
  },
  async bySession(sessionId: string): Promise<EntryLogResponse[]> {
    const { data } = await guardClient.get<unknown>(`${LOGS}/session/${sessionId}`);
    return unwrapList<EntryLogResponse>(data);
  },
  /**
   * Counted on attributedEventId, so a student who scanned their daily QR during
   * the event still appears. registeredCount must be threaded in from the
   * gatepass attendance summary or every percentage reads zero.
   */
  async eventAttendance(eventId: number, query: EventAttendanceQuery): Promise<EventAttendanceResponse> {
    const { data } = await guardClient.get<unknown>(`${LOGS}/events/${eventId}/attendance`, { params: query });
    return unwrap<EventAttendanceResponse>(data);
  },
};
