import type { IdType } from '@/types/enums';

/**
 * Mirrors gatepass-service's IdDocumentValidator, including the Verhoeff
 * checksum. Kept in step with it deliberately: a rule the browser enforces and
 * the server does not is theatre, and one the server enforces and the browser
 * does not means a round trip to learn about a typo.
 *
 * The server remains the authority - this only moves the answer earlier.
 */
const SHAPES: Record<IdType, RegExp> = {
  AADHAAR: /^[0-9]{12}$/,
  PAN: /^[A-Z]{5}[0-9]{4}[A-Z]$/,
  PASSPORT: /^[A-Z][0-9]{7}$/,
  VOTER_ID: /^[A-Z]{3}[0-9]{7}$/,
};

export const ID_HINTS: Record<IdType, string> = {
  AADHAAR: '12 digits, e.g. 2341 2341 2346',
  PAN: '5 letters, 4 digits, 1 letter — e.g. ABCDE1234F',
  PASSPORT: 'A letter then 7 digits — e.g. A1234567',
  VOTER_ID: '3 letters then 7 digits — e.g. ABC1234567',
};

export const ID_MESSAGES: Record<IdType, string> = {
  AADHAAR: 'An Aadhaar number is 12 digits and must pass its checksum',
  PAN: 'A PAN is 5 letters, 4 digits and a letter, e.g. ABCDE1234F',
  PASSPORT: 'A passport number is a letter followed by 7 digits, e.g. A1234567',
  VOTER_ID: 'A voter ID is 3 letters followed by 7 digits, e.g. ABC1234567',
};

const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
  [2, 3, 4, 0, 1, 7, 8, 9, 5, 6], [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
  [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
  [8, 7, 6, 5, 9, 3, 2, 1, 0, 4], [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
];

const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
  [5, 8, 0, 3, 7, 9, 6, 1, 4, 2], [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
  [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
];

/** Valid when the running product over the digits, read right to left, is 0. */
export function verhoeff(digits: string): boolean {
  let c = 0;
  const reversed = digits.split('').reverse().map(Number);
  for (let i = 0; i < reversed.length; i += 1) {
    const digit = reversed[i] ?? 0;
    const permuted = P[i % 8]?.[digit] ?? 0;
    c = D[c]?.[permuted] ?? 0;
  }
  return c === 0;
}

/**
 * Blank is valid here — the field is optional, and "did you supply one" is a
 * separate question the form's pairing rule answers.
 */
export function isValidIdNumber(type: IdType | '' | undefined, value: string | undefined): boolean {
  if (!type || !value || !value.trim()) return true;

  const normalised = value.trim().toUpperCase();
  if (!SHAPES[type].test(normalised)) return false;

  if (type === 'AADHAAR') {
    // 111111111111 has the right shape and is not an Aadhaar.
    if (new Set(normalised).size === 1) return false;
    return verhoeff(normalised);
  }
  return true;
}
