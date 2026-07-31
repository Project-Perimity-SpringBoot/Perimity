/**
 * The six roles, exactly as auth-service spells them in the JWT `role` claim.
 * A typo here is a silent authorisation bug: an unknown role matches nothing
 * and the user simply sees an empty app.
 */
export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  CAMPUS_ADMIN: 'CAMPUS_ADMIN',
  FACULTY: 'FACULTY',
  STUDENT: 'STUDENT',
  VISITOR: 'VISITOR',
  GUARD: 'GUARD',
};

/** Login is NOT the same for everyone - this mirrors Role.canLoginWithOtp(). */
export const OTP_LOGIN_ROLES = [ROLES.FACULTY, ROLES.STUDENT, ROLES.VISITOR];

export const ROLE_LABEL = {
  SUPER_ADMIN: 'Super Admin',
  CAMPUS_ADMIN: 'Campus Admin',
  FACULTY: 'Faculty',
  STUDENT: 'Student',
  VISITOR: 'Visitor',
  GUARD: 'Guard',
};

/** Where each role lands after login. Claim your own in the routes table. */
export const HOME_FOR_ROLE = {
  SUPER_ADMIN: '/platform',
  CAMPUS_ADMIN: '/today',
  FACULTY: '/approvals',
  STUDENT: '/student',
  VISITOR: '/visitor',
  // A guard lands on gate selection, not the scanner. Scanning before choosing
  // a gate would log entries against the wrong one, and nothing downstream
  // could tell.
  GUARD: '/guard/start-shift',
};
