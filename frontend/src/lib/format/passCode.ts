import type { GatePassResponse } from '@/types/gatepass.types';

/**
 * GatePass carries only an IDENTITY bigint — there is no passCode column in the
 * backend, and the design specifies human-readable codes on every pass surface.
 * Until that field exists this derives a stable display code from the id and
 * type so the UI is consistent, and it is NOT sent to the server or used for
 * lookup: only the numeric id addresses a pass.
 */
export function displayPassCode(pass: Pick<GatePassResponse, 'id' | 'passType' | 'visitorRequestId'>): string {
  const prefix = pass.passType === 'EVENT' ? 'EV' : pass.visitorRequestId !== null ? 'PM' : 'S';
  return `${prefix}-${String(pass.id).padStart(4, '0')}`;
}
