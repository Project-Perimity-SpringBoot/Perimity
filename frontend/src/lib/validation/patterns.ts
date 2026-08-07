/**
 * Transcribed verbatim from each service's ValidationPatterns.java.
 *
 * A stricter client rule rejects input the server would accept. A looser one
 * produces a 400 the user cannot act on. Neither is acceptable, so these are
 * copies, not approximations.
 */
export const RX = {
  /** Identical in all six services. */
  EMAIL:
    /^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\.[A-Za-z]{2,24}$/,

  /** E.164-ish: optional +, no leading zero, 7–15 digits. */
  PHONE: /^\+?[1-9]\d{6,14}$/,

  /**
   * Unicode letters and marks. 2–120 characters.
   *
   * LITERAL SPACE, not \s. This used to be \s, mirroring the server before the
   * server was fixed: \s inside a character class also matches \n, \r and \t,
   * and a name carrying a line break splits one field into two log lines with
   * the second one attacker-controlled. All six services now use a literal
   * space, so this does too.
   *
   * Had this been left as \s it would be the LOOSER of the two rules — a tabbed
   * name would sail past the form and come back as a 400 pointing at a field
   * the user cannot see anything wrong with.
   */
  PERSON_NAME: /^[\p{L}\p{M}][\p{L}\p{M} .'-]{1,119}$/u,

  /**
   * ONE part of a name — first, middle or last on its own. Mirrors
   * ValidationPatterns.PERSON_NAME_PART in user-service.
   *
   * Same character set as PERSON_NAME, shorter: 60 rather than 120, matching
   * the column. Spaces are still allowed inside a single part because "van der
   * Berg" and "Del Toro" are single surnames.
   *
   * Matches the empty string, as the server's does — required-ness is a
   * separate rule, and doubling it up here would produce two error messages for
   * one blank field.
   */
  PERSON_NAME_PART: /^$|^[\p{L}\p{M}][\p{L}\p{M} .'-]{0,59}$/u,

  /** Country dialling code: + followed by 1–4 digits, no leading zero. */
  PHONE_COUNTRY_CODE: /^\+[1-9][0-9]{0,3}$/,

  /** Subscriber number WITHOUT the country code. Digits only. */
  PHONE_NATIONAL: /^[0-9]{4,15}$/,

  /** An Indian mobile: exactly 10 digits starting 6–9. Applied only when the code is +91. */
  PHONE_NATIONAL_IN: /^[6-9][0-9]{9}$/,

  /**
   * 8–72, one lowercase, one uppercase, one digit. NO symbol is required and
   * the minimum is 8, not 12 — the design mockup's checklist is wrong.
   */
  PASSWORD_POLICY: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/,

  CAMPUS_CODE: /^[A-Za-z0-9][A-Za-z0-9-]{1,31}$/,

  /** Literal space, not \s — campus-service's DISPLAY_NAME was fixed the same way. */
  DISPLAY_NAME: /^[\p{L}\p{N}][\p{L}\p{N} .,'&()/-]{1,149}$/u,
  CONFIG_KEY: /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)*$/,
  IDENTIFIER_CODE: /^[A-Za-z0-9][A-Za-z0-9/-]{0,31}$/,
  DEPARTMENT_CODE: /^[A-Z0-9]{4}$/,

  /**
   * gatepass TITLE is 3–180; user-service TITLE is 1–150. They genuinely differ.
   * Both use a literal space, not \s, for the reason given on PERSON_NAME.
   */
  TITLE_GATEPASS: /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .,'&()+/-]{2,179}$/u,
  TITLE_USER: /^[\p{L}\p{N}][\p{L}\p{N} .,'&()/-]{0,149}$/u,

  GOV_ID: /^$|^\d{12}$/,
  OTP_CODE: /^\d{6}$/,
  SHA256_HEX: /^[a-f0-9]{64}$/i,
  SPREADSHEET_FILENAME: /^[^\\/:*?"<>|\r\n]{1,255}\.(xlsx|xls|csv)$/i,

  /** ScanRequestDto.token */
  SCAN_TOKEN: /^[A-Za-z0-9+/=_.:-]+$/,

  OBJECT_KEY_300: /^(?!.*\.\.)[A-Za-z0-9][A-Za-z0-9!_.*'()/-]{0,299}$/,
  OBJECT_KEY_512: /^(?!.*\.\.)(?!\/)[A-Za-z0-9!_.*'()/-]{1,512}$/,
} as const;

/** @Size bounds, so a Zod schema never guesses a number. */
export const LIMITS = {
  email: { max: 180 },
  personName: { min: 2, max: 120 },
  password: { min: 8, max: 72 },
  purpose: { min: 5, max: 500 },
  reason: { min: 3, max: 500 },
  blocklistReason: { min: 5, max: 500 },
  eventName: { min: 3, max: 180 },
  eventDescription: { max: 1000 },
  campusName: { max: 150 },
  campusCode: { min: 2, max: 32 },
  address: { max: 250 },
  departmentName: { max: 150 },
  departmentCode: { min: 4, max: 4 },
  identifierCode: { max: 32 },
  /** One part of a name — matches the 60-char columns on student_profiles. */
  personNamePart: { max: 60 },
  /** Subscriber number without the country code. */
  phoneNational: { min: 4, max: 15 },
  verificationRemarks: { max: 500 },
  designation: { max: 100 },
  qualification: { max: 150 },
  gateName: { max: 100 },
  gateLocation: { max: 150 },
  configKey: { max: 100 },
  configValue: { max: 2000 },
  fileName: { max: 255 },
  scanToken: { max: 2048 },
} as const;

/** Enforced client-side so the user gets the good message, not the servlet's. */
export const UPLOAD_RULES = {
  photo: {
    maxBytes: 2 * 1024 * 1024,
    accept: ['image/png', 'image/jpeg', 'image/webp'] as const,
    label: 'PNG, JPEG or WebP, up to 2 MB',
  },
  document: {
    maxBytes: 5 * 1024 * 1024,
    accept: ['application/pdf', 'image/png', 'image/jpeg'] as const,
    label: 'PDF, PNG or JPEG, up to 5 MB',
  },
  campusLogo: {
    maxBytes: 2 * 1024 * 1024,
    accept: ['image/png', 'image/jpeg', 'image/webp'] as const,
    label: 'PNG, JPEG or WebP, up to 2 MB',
  },
  /*
   * XLSX ONLY, and the label has to say so.
   *
   * This offered XLS and CSV as well, but ResponseSheetParser reads a real
   * workbook and nothing else - its own error text names "a .csv renamed, an
   * .xls" as the case it rejects. So the dropzone invited a file the server
   * was always going to refuse, and the failure then read "That does not look
   * like an .xlsx workbook", which blames the person's file rather than the
   * label that told them to pick it.
   *
   * Narrowing `accept` also makes the OS file picker grey the wrong files out,
   * which stops the mistake before the upload rather than after it.
   */
  bulkSheet: {
    maxBytes: 5 * 1024 * 1024,
    accept: [
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ] as const,
    label: 'XLSX only, up to 5 MB',
  },
} as const;

/** OTP behaviour, from auth-service application.properties. */
export const OTP_RULES = {
  length: 6,
  expiryMinutes: 10,
  maxAttempts: 5,
  /** UI cooldown only. The server's real limit is 3 per email per hour. */
  resendCooldownSeconds: 60,
} as const;

/**
 * Columns the STUDENT intake sheet must carry.
 *
 * ======================================================================
 *  A DIFFERENT SHEET FROM BULK_COLUMNS, NOT A LONGER ONE
 * ======================================================================
 * The event sheet needs a name and an email; everything else is a bonus,
 * because a guest attending a lecture has no roll number and never will.
 * The student sheet is the opposite: it becomes somebody's campus record, so
 * date of birth, address, roll number, department and a passport photo are
 * all required, and a sheet missing any of them is rejected BEFORE any row is
 * read - naming the column, not producing two hundred identical row errors.
 *
 * The photo is required for a reason worth remembering: a student with no
 * photo cannot hold a pass, because a guard would have no face to check
 * against the person at the gate.
 *
 * Kept in step with FormColumn in user-service. That enum is the authority;
 * this copy exists only to tell faculty what to upload.
 */
export const STUDENT_IMPORT_COLUMNS = {
  required: [
    'email address',
    'full name',
    'first name',
    'last name',
    'date of birth',
    'gender',
    'address',
    'phone number',
    'roll number',
    'department',
    'passport photo',
  ] as const,
  optional: ['middle name', 'phone country code'] as const,
} as const;

/**
 * Bulk sheet columns, matched by header name. Position is irrelevant.
 *
 * The optional list is long because faculty running an event reuse the Google
 * Form they already have - usually the student intake form. Every column it
 * asks for is read if present and ignored if not, so the responses sheet can
 * be uploaded exactly as Forms exported it. Only name and email are required.
 *
 * Kept in step with SheetParser.HEADER_ALIASES in gatepass-service. This copy
 * exists to tell the faculty member what to upload, not to validate anything -
 * the server does that and its answer is the one that counts.
 */
export const BULK_COLUMNS = {
  required: ['name', 'email'] as const,
  optional: [
    'phone',
    'purpose',
    'first name',
    'middle name',
    'last name',
    'date of birth',
    'gender',
    'address',
    'roll number',
    'department',
    'passport photo',
  ] as const,
  aliases: {
    name: ['name', 'fullName', 'attendeeName', 'visitorName', 'studentName'],
    email: ['email', 'emailAddress', 'emailId'],
    phone: ['phone', 'phoneNo', 'phoneNumber'],
    purpose: ['purpose', 'purposeOfVisit'],
  },
  maxRows: 1000,
} as const;
