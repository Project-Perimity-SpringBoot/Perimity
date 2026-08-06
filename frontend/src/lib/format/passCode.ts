import type { GatePassResponse } from '@/types/gatepass.types';

/**
 * GatePass carries an IDENTITY bigint — derives a human-readable pass code
 * (e.g. GP-000007, EV-000007, PM-000007) for every display surface.
 */
export function displayPassCode(pass: Pick<GatePassResponse, 'id' | 'passType' | 'visitorRequestId'>): string {
  const prefix = pass.passType === 'EVENT' ? 'EV' : pass.visitorRequestId !== null ? 'PM' : 'GP';
  return `${prefix}-${String(pass.id).padStart(6, '0')}`;
}
