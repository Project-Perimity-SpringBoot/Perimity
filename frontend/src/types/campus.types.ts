import type { ConfigValueType } from './enums';

export interface CampusResponse {
  id: number;
  /** Immutable after creation — baked into storage prefixes and pass URLs. */
  code: string;
  name: string;
  address: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  logoS3Key: string | null;
  adminUserId: number | null;
  active: boolean;
  /** 0 unless the handler chose the enriching overload. */
  activeGateCount: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface CampusStatsResponse {
  totalCampuses: number;
  activeCampuses: number;
  inactiveCampuses: number;
}

/**
 * No type, no active hours, no approvalRequired, no assigned guards exist on
 * the entity. Mockup elements depending on those are unbuildable today.
 */
export interface CampusGateResponse {
  id: number;
  campusId: number;
  name: string;
  location: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface CampusConfigResponse {
  id: number;
  campusId: number;
  configKey: string;
  configValue: string | null;
  valueType: ConfigValueType;
  description: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/* ── Requests ── */

export interface CampusCreateRequest {
  code: string;
  name: string;
  address?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  logoS3Key?: string | null;
  adminUserId?: number | null;
}

/** `code` is rejected on update. */
export type CampusUpdateRequest = Omit<CampusCreateRequest, 'code'>;

export interface CampusStatusUpdateRequest {
  active: boolean;
  reason: string;
  changedBy: number;
}

export interface CampusGateCreateRequest {
  name: string;
  location?: string | null;
}

export interface CampusGateUpdateRequest {
  name: string;
  location?: string | null;
  active: boolean;
}

export interface CampusConfigUpsertRequest {
  configKey: string;
  /** Must parse as valueType — checked by @AssertTrue server-side. */
  configValue?: string | null;
  valueType: ConfigValueType;
  description?: string | null;
}

export interface CampusConfigBulkUpsertRequest {
  /** 1–200 settings, applied all-or-nothing. */
  settings: CampusConfigUpsertRequest[];
}

/**
 * The six keys CampusConfigDefaults actually seeds. The policy screen renders
 * exactly these — inventing others would create controls that do nothing.
 * `consumed` records whether any service actually reads the key today (B7).
 */
export const CAMPUS_CONFIG_KEYS = [
  { key: 'visitor_approval_required', type: 'BOOLEAN', default: 'true', consumed: false },
  { key: 'repeat_entry_result', type: 'STRING', default: 'AMBER', choices: ['GREEN', 'AMBER'], consumed: false },
  { key: 'daily_pass_validity_days', type: 'INTEGER', default: '365', min: 1, max: 3650, consumed: false },
  { key: 'max_visitor_duration_days', type: 'INTEGER', default: '7', min: 1, max: 365, consumed: false },
  { key: 'otp_expiry_minutes', type: 'INTEGER', default: '10', min: 1, max: 60, consumed: false },
  { key: 'photo_required_for_pass', type: 'BOOLEAN', default: 'true', consumed: false },
] as const;
