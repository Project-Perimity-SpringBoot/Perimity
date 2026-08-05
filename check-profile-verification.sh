#!/usr/bin/env bash
#
# Perimity — student profile verification check.
#
#   STUDENT_EMAIL=... STUDENT_PASSWORD=... \
#   FACULTY_EMAIL=... FACULTY_PASSWORD=... ./check-profile-verification.sh
#
# Or put those four in .env and just run it.
#
# NOT read-only. It writes to one student's profile and leaves it VERIFIED.
# Use a throwaway student account, not a real one.
#
# The point of this script is the REFUSALS. Any endpoint can return 200 on the
# happy path; what makes a state machine worth having is that it says no at the
# right moments. Roughly half the checks below assert a failure.

set -uo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}PASS${NC}  $1"; }
fail() { echo -e "  ${RED}FAIL${NC}  $1"; FAILURES=$((FAILURES+1)); }
info() { echo -e "  ${YELLOW}····${NC}  $1"; }
FAILURES=0

AUTH=http://localhost:8081
USER=http://localhost:8082

# Credentials come from the environment or .env, never from this file.
envval() { grep "^$1=" .env 2>/dev/null | cut -d= -f2- | tr -d '\r'; }
STUDENT_EMAIL="${STUDENT_EMAIL:-$(envval STUDENT_EMAIL)}"
STUDENT_PASSWORD="${STUDENT_PASSWORD:-$(envval STUDENT_PASSWORD)}"
FACULTY_EMAIL="${FACULTY_EMAIL:-$(envval FACULTY_EMAIL)}"
FACULTY_PASSWORD="${FACULTY_PASSWORD:-$(envval FACULTY_PASSWORD)}"

if [[ -z "$STUDENT_EMAIL" || -z "$FACULTY_EMAIL" ]]; then
  echo "Set STUDENT_EMAIL/STUDENT_PASSWORD and FACULTY_EMAIL/FACULTY_PASSWORD"
  echo "as environment variables or in .env, then run this again."
  exit 2
fi

# jq keeps this readable; without it every assertion becomes a grep.
command -v jq >/dev/null || { echo "This needs jq. apt/choco install jq"; exit 2; }

# --------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------

# Prints "HTTP_STATUS<newline>BODY" so callers can assert on both.
req() { # req METHOD URL TOKEN [BODY]
  local method=$1 url=$2 token=$3 body=${4:-}
  if [[ -n "$body" ]]; then
    curl -s -w '\n%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -w '\n%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $token"
  fi
}
code() { tail -n1 <<<"$1"; }
body() { sed '$d' <<<"$1"; }

# Writes the token to stdout. On failure writes nothing to stdout and puts the
# server's own explanation on stderr — "sign-in failed" with no reason is
# useless, and a wrong password and a locked account need different responses.
login() { # login EMAIL PASSWORD -> token on stdout
  local r code payload
  r=$(curl -s -w '\n%{http_code}' -X POST "$AUTH/api/auth/login" \
        -H 'Content-Type: application/json' \
        -d "$(jq -nc --arg e "$1" --arg p "$2" '{email:$e,password:$p}')")
  code=$(tail -n1 <<<"$r"); payload=$(sed '$d' <<<"$r")

  local token; token=$(jq -r '.data.token // empty' <<<"$payload" 2>/dev/null)
  if [[ -n "$token" ]]; then
    echo "$token"
    if [[ "$(jq -r '.data.user.mustChangePassword // false' <<<"$payload" 2>/dev/null)" == "true" ]]; then
      echo "    note: $1 has mustChangePassword set - some endpoints may refuse" >&2
    fi
    return 0
  fi

  {
    echo "    HTTP $code for $1"
    jq -r '.message // empty' <<<"$payload" 2>/dev/null | sed 's/^/    /'
    jq -r '.errors[]? // empty' <<<"$payload" 2>/dev/null | sed 's/^/    /'
  } >&2
  return 1
}

expect() { # expect DESCRIPTION EXPECTED_CODE ACTUAL_RESPONSE
  local want=$2 got; got=$(code "$3")
  if [[ "$got" == "$want" ]]; then
    pass "$1"
  else
    fail "$1 — wanted HTTP $want, got $got"
    jq -r '.message // .errors // empty' <<<"$(body "$3")" 2>/dev/null | head -2 | sed 's/^/          /'
  fi
}

# A refusal is any 4xx. Which 4xx depends on how the exception handler is wired,
# and asserting the exact code here would make this script fail for a reason
# that has nothing to do with the state machine.
expect_refused() { # expect_refused DESCRIPTION RESPONSE
  local got; got=$(code "$2")
  if [[ "$got" =~ ^4 ]]; then
    pass "$1 (HTTP $got)"
  else
    fail "$1 — wanted a 4xx refusal, got $got"
  fi
}

DETAILS='{
  "firstName":"Anjali","middleName":"Sunil","lastName":"Rao",
  "dateOfBirth":"2004-08-19","gender":"FEMALE",
  "address":"12 Example Road",
  "phoneCountryCode":"+91","phoneNumber":"9876543210"
}'

echo ""
echo "=============================================="
echo " 0. SIGN IN"
echo "=============================================="
S_TOKEN=$(login "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
F_TOKEN=$(login "$FACULTY_EMAIL" "$FACULTY_PASSWORD")
[[ -n "$S_TOKEN" ]] && pass "student signed in" || { fail "student sign-in failed"; exit 1; }
[[ -n "$F_TOKEN" ]] && pass "faculty signed in" || { fail "faculty sign-in failed"; exit 1; }

PROFILE=$(body "$(req GET "$USER/api/user/students/me" "$S_TOKEN")")
PROFILE_ID=$(jq -r '.data.id // empty' <<<"$PROFILE")
[[ -n "$PROFILE_ID" ]] && info "student profile id $PROFILE_ID" \
  || { fail "student has no profile row — create one via the faculty screen first"; exit 1; }

echo ""
echo "=============================================="
echo " 1. VALIDATION — the DTO should refuse bad input"
echo "=============================================="

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" \
      "$(jq -c '.phoneNumber="12345"' <<<"$DETAILS")")
expect_refused "a 5-digit +91 mobile is refused" "$r"

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" \
      "$(jq -c '.dateOfBirth="2025-01-01"' <<<"$DETAILS")")
expect_refused "a date of birth 1 year ago is refused" "$r"

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" \
      "$(jq -c '.altPhoneNumber="9876543211"' <<<"$DETAILS")")
expect_refused "an alt number with no country code is refused" "$r"

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" \
      "$(jq -c '.firstName="Ravi\nINFO Entry GRANTED"' <<<"$DETAILS")")
expect_refused "a newline in a name is refused (log forgery)" "$r"

echo ""
echo "=============================================="
echo " 2. THE HAPPY PATH"
echo "=============================================="

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" "$DETAILS")
expect "student saves their details" 200 "$r"
[[ "$(jq -r '.data.verificationStatus' <<<"$(body "$r")")" == "DRAFT" ]] \
  && pass "status is DRAFT after saving" || fail "status should be DRAFT after saving"

r=$(req POST "$USER/api/user/students/me/details/submit" "$S_TOKEN")
expect "student submits for verification" 200 "$r"
[[ "$(jq -r '.data.verificationStatus' <<<"$(body "$r")")" == "SUBMITTED" ]] \
  && pass "status is SUBMITTED" || fail "status should be SUBMITTED"

echo ""
echo "=============================================="
echo " 3. THE LOCK — SUBMITTED must be untouchable"
echo "=============================================="

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" "$DETAILS")
expect_refused "student cannot edit while faculty are reviewing" "$r"

r=$(req POST "$USER/api/user/students/me/details/submit" "$S_TOKEN")
expect_refused "student cannot submit twice (would reset queue position)" "$r"

echo ""
echo "=============================================="
echo " 4. THE QUEUE"
echo "=============================================="

r=$(req GET "$USER/api/user/students/pending" "$F_TOKEN")
expect "faculty can read the pending queue" 200 "$r"
jq -e --arg id "$PROFILE_ID" '.data.content[]? | select(.id == ($id|tonumber))' \
   <<<"$(body "$r")" >/dev/null 2>&1 \
  && pass "the submitted student appears in it" || fail "student missing from the queue"

r=$(req GET "$USER/api/user/students/pending" "$S_TOKEN")
expect_refused "a student cannot read the queue" "$r"

echo ""
echo "=============================================="
echo " 5. THE DECISION"
echo "=============================================="

r=$(req PATCH "$USER/api/user/students/$PROFILE_ID/verification" "$F_TOKEN" \
      '{"approved":false}')
expect_refused "rejecting with no remarks is refused" "$r"

r=$(req PATCH "$USER/api/user/students/$PROFILE_ID/verification" "$S_TOKEN" \
      '{"approved":true}')
expect_refused "a student cannot approve themselves" "$r"

r=$(req PATCH "$USER/api/user/students/$PROFILE_ID/verification" "$F_TOKEN" \
      '{"approved":true}')
expect "faculty approves" 200 "$r"
[[ "$(jq -r '.data.verificationStatus' <<<"$(body "$r")")" == "VERIFIED" ]] \
  && pass "status is VERIFIED" || fail "status should be VERIFIED"
[[ "$(jq -r '.data.verifiedBy' <<<"$(body "$r")")" != "null" ]] \
  && pass "verifiedBy was set from the token, not the body" || fail "verifiedBy is null"

r=$(req PATCH "$USER/api/user/students/$PROFILE_ID/verification" "$F_TOKEN" \
      '{"approved":true}')
expect_refused "deciding twice is refused (stops reviewers racing)" "$r"

echo ""
echo "=============================================="
echo " 6. EDITING A VERIFIED PROFILE CLEARS IT"
echo "=============================================="
info "the one that matters — a verified row must never describe unchecked details"

r=$(req PUT "$USER/api/user/students/me/details" "$S_TOKEN" \
      "$(jq -c '.address="99 Changed Street"' <<<"$DETAILS")")
expect "student edits their verified details" 200 "$r"
b=$(body "$r")
[[ "$(jq -r '.data.verificationStatus' <<<"$b")" == "DRAFT" ]] \
  && pass "status dropped back to DRAFT" || fail "status should be DRAFT after editing"
[[ "$(jq -r '.data.verifiedBy' <<<"$b")" == "null" ]] \
  && pass "verifiedBy was cleared" || fail "verifiedBy survived the edit — stale attestation"
[[ "$(jq -r '.data.verifiedAt' <<<"$b")" == "null" ]] \
  && pass "verifiedAt was cleared" || fail "verifiedAt survived the edit"

echo ""
echo "=============================================="
echo " 7. THE DIRECTORY MUST NOT LEAK CONTACT DETAILS"
echo "=============================================="

r=$(req GET "$USER/api/user/students?size=50" "$F_TOKEN")
expect "faculty can read the directory" 200 "$r"
b=$(body "$r")
row=$(jq -c --arg id "$PROFILE_ID" '.data.content[]? | select(.id == ($id|tonumber))' <<<"$b")
if [[ -z "$row" ]]; then
  info "student not on this page — widen ?size= if this matters"
else
  for f in address dateOfBirth phoneNumber altPhoneNumber; do
    [[ "$(jq -r ".$f" <<<"$row")" == "null" ]] \
      && pass "directory hides $f" || fail "directory LEAKS $f"
  done
  [[ "$(jq -r '.displayName' <<<"$row")" != "null" ]] \
    && pass "directory still shows displayName" || fail "displayName was blanked too"
fi

r=$(req GET "$USER/api/user/students/$PROFILE_ID" "$F_TOKEN")
[[ "$(jq -r '.data.phoneNumber' <<<"$(body "$r")")" != "null" ]] \
  && pass "the single-profile read still returns contact details" \
  || fail "single read lost its contact details — forDirectory used by mistake?"

echo ""
echo "=============================================="
if [[ $FAILURES -eq 0 ]]; then
  echo -e " ${GREEN}Everything passed.${NC}"
else
  echo -e " ${RED}$FAILURES check(s) failed.${NC}"
fi
echo " Left student profile $PROFILE_ID in DRAFT."
echo "=============================================="
exit $((FAILURES > 0))
