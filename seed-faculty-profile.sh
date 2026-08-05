#!/usr/bin/env bash
#
# Create a faculty profile for an account that already exists in auth-service.
#
#   ADMIN_EMAIL=... ADMIN_PASSWORD=... FACULTY_USER_ID=6 CAMPUS_ID=2 \
#     ./seed-faculty-profile.sh
#
# WHY THIS SCRIPT EXISTS
# ----------------------
# Nothing in the UI creates a faculty profile. Campus Admin creates the ACCOUNT
# in auth-service; the PROFILE in user-service is a second, separate record and
# no screen makes one. So every faculty account on this system has no profile.
#
# That is not cosmetic. The visitor "choose a host" dropdown is populated from
# faculty PROFILES, so with none, no visitor can name a host and the entire
# visitor journey is unreachable. Students have the same gap - four of five
# student accounts have no profile row either.
#
# This is a stopgap for testing. The real fix is for the account-creation screen
# to create both, the way the faculty Add Student screen already does.

set -uo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

AUTH=http://localhost:8081
USER=http://localhost:8082

envval() { grep "^$1=" .env 2>/dev/null | cut -d= -f2- | tr -d '\r'; }
ADMIN_EMAIL="${ADMIN_EMAIL:-$(envval SUPER_ADMIN_EMAIL)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(envval SUPER_ADMIN_PASSWORD)}"

: "${FACULTY_USER_ID:?Set FACULTY_USER_ID - the auth-service user id of the faculty account}"
: "${CAMPUS_ID:?Set CAMPUS_ID - the campus that account belongs to}"

EMPLOYEE_ID="${EMPLOYEE_ID:-EMP-$FACULTY_USER_ID}"
DESIGNATION="${DESIGNATION:-Lecturer}"

command -v jq >/dev/null || { echo "This needs jq."; exit 2; }

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
  echo "Set ADMIN_EMAIL and ADMIN_PASSWORD (a Super Admin or Campus Admin),"
  echo "or put SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD in .env."
  exit 2
fi

TOKEN=$(curl -s -X POST "$AUTH/api/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email:$e,password:$p}')" \
  | jq -r '.data.token // empty')

if [[ -z "$TOKEN" ]]; then
  echo -e "${RED}Could not sign in as $ADMIN_EMAIL${NC}"
  exit 1
fi
echo "Signed in as $ADMIN_EMAIL"

# campusId is sent because a SUPER_ADMIN has no campus of their own and the
# server needs to be told which one. A CAMPUS_ADMIN's own campus is enforced
# server-side regardless of what goes in here.
BODY=$(jq -nc \
  --argjson userId "$FACULTY_USER_ID" \
  --argjson campusId "$CAMPUS_ID" \
  --arg employeeId "$EMPLOYEE_ID" \
  --arg designation "$DESIGNATION" \
  '{userId:$userId, campusId:$campusId, employeeId:$employeeId, designation:$designation}')

RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "$USER/api/user/faculty" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$BODY")

CODE=$(tail -n1 <<<"$RESPONSE")
PAYLOAD=$(sed '$d' <<<"$RESPONSE")

if [[ "$CODE" == "201" || "$CODE" == "200" ]]; then
  echo -e "${GREEN}Created${NC} faculty profile $(jq -r '.data.id' <<<"$PAYLOAD") for user $FACULTY_USER_ID"
  echo "They will now appear in the visitor host dropdown."
else
  echo -e "${RED}HTTP $CODE${NC}"
  jq -r '.message // empty' <<<"$PAYLOAD" 2>/dev/null
  jq -r '.errors[]? // empty' <<<"$PAYLOAD" 2>/dev/null
  exit 1
fi
