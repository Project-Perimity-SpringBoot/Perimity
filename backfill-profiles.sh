#!/usr/bin/env bash
#
# Perimity — create the missing profiles for accounts that predate
# event-driven provisioning.
#
#   ADMIN_EMAIL=... ADMIN_PASSWORD=... ./backfill-profiles.sh          # dry run
#   ADMIN_EMAIL=... ADMIN_PASSWORD=... ./backfill-profiles.sh --apply  # do it
#
# WHY THIS EXISTS
# ---------------
# An account lives in auth-service and a STUDENT's or FACULTY's profile lives in
# user-service. Until user.created existed, the only thing that ever created the
# second record was the React Add Student screen, making a second API call after
# the first returned. Any account created any other way - or whose second call
# never happened - could sign in with no profile, and nothing in the product
# could create the missing row afterwards.
#
# From now on the queue handles it. This is for everything already broken:
# students seeing "No student profile exists for account 21", and every faculty
# account, whose absence makes the visitor host dropdown empty and the whole
# visitor journey unreachable.
#
# Teammates whose local databases have the same gap can run this too.
#
# HOW
# ---
# Through the real API, as a Super Admin, so the same validation and the same
# campus rules apply. It reads which accounts exist by asking auth-service, not
# by querying anyone's database directly.
#
# Idempotent: create is create-or-fill server-side, so running it twice is
# harmless. Dry run by default, because a script that writes on first contact is
# a script nobody trusts.

set -uo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'

AUTH=http://localhost:8081
USER=http://localhost:8082

APPLY=false
[[ "${1:-}" == "--apply" ]] && APPLY=true

envval() { grep "^$1=" .env 2>/dev/null | cut -d= -f2- | tr -d '\r'; }
ADMIN_EMAIL="${ADMIN_EMAIL:-$(envval SUPER_ADMIN_EMAIL)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(envval SUPER_ADMIN_PASSWORD)}"

command -v jq >/dev/null || { echo "This needs jq."; exit 2; }

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
  echo "Set ADMIN_EMAIL and ADMIN_PASSWORD (a Super Admin), or put"
  echo "SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD in .env."
  exit 2
fi

TOKEN=$(curl -s -X POST "$AUTH/api/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email:$e,password:$p}')" \
  | jq -r '.data.token // empty')

[[ -n "$TOKEN" ]] || { echo -e "${RED}Could not sign in as $ADMIN_EMAIL${NC}"; exit 1; }

echo ""
echo "=============================================="
if $APPLY; then
  echo " BACKFILL — writing"
else
  echo " BACKFILL — dry run (pass --apply to write)"
fi
echo "=============================================="

CREATED=0; SKIPPED=0; FAILED=0; NOCAMPUS=0

# Only these two roles have a profile entity. GUARD, CAMPUS_ADMIN, SUPER_ADMIN
# and VISITOR have none by design - a visitor is identified by their pass.
for ROLE in STUDENT FACULTY; do
  echo ""
  echo "--- $ROLE"

  # Paged deliberately: a campus with 2000 students should not be pulled in one
  # request just because this is a one-off script.
  PAGE=0
  while :; do
    RESP=$(curl -s "$AUTH/api/auth/users?role=$ROLE&page=$PAGE&size=100" \
             -H "Authorization: Bearer $TOKEN")

    ROWS=$(jq -r '.data.items // .data.content // [] | length' <<<"$RESP" 2>/dev/null || echo 0)
    [[ "$ROWS" =~ ^[0-9]+$ ]] || { echo -e "  ${RED}Unexpected response from auth-service${NC}"; break; }
    [[ "$ROWS" -eq 0 ]] && break

    while IFS=$'\t' read -r ID EMAIL CAMPUS; do
      [[ -z "$ID" ]] && continue

      # A STUDENT or FACULTY with no campus cannot have a profile - campus_id is
      # NOT NULL on both tables. Reported rather than guessed at.
      if [[ -z "$CAMPUS" || "$CAMPUS" == "null" ]]; then
        echo -e "  ${YELLOW}SKIP${NC}  $EMAIL (id $ID) has no campus"
        NOCAMPUS=$((NOCAMPUS+1))
        continue
      fi

      if [[ "$ROLE" == "STUDENT" ]]; then
        EXISTS=$(curl -s -o /dev/null -w '%{http_code}' \
                   "$USER/api/user/students/by-user/$ID" -H "Authorization: Bearer $TOKEN")
        PATH_SEG="students"
        BODY=$(jq -nc --argjson u "$ID" --argjson c "$CAMPUS" '{userId:$u, campusId:$c}')
      else
        EXISTS=$(curl -s -o /dev/null -w '%{http_code}' \
                   "$USER/api/user/faculty/by-user/$ID" -H "Authorization: Bearer $TOKEN")
        PATH_SEG="faculty"
        # employeeId is left unset. Inventing one would put a fabricated
        # identifier on a record staff are meant to own.
        BODY=$(jq -nc --argjson u "$ID" --argjson c "$CAMPUS" '{userId:$u, campusId:$c}')
      fi

      if [[ "$EXISTS" == "200" ]]; then
        SKIPPED=$((SKIPPED+1))
        continue
      fi

      if ! $APPLY; then
        echo -e "  ${YELLOW}WOULD CREATE${NC}  $EMAIL (id $ID, campus $CAMPUS)"
        CREATED=$((CREATED+1))
        continue
      fi

      OUT=$(curl -s -w '\n%{http_code}' -X POST "$USER/api/user/$PATH_SEG" \
              -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
              -d "$BODY")
      CODE=$(tail -n1 <<<"$OUT")

      if [[ "$CODE" == "201" || "$CODE" == "200" ]]; then
        echo -e "  ${GREEN}CREATED${NC}  $EMAIL (id $ID, campus $CAMPUS)"
        CREATED=$((CREATED+1))
      else
        echo -e "  ${RED}FAILED${NC}   $EMAIL (id $ID) HTTP $CODE"
        sed '$d' <<<"$OUT" | jq -r '.message // empty' 2>/dev/null | sed 's/^/           /'
        FAILED=$((FAILED+1))
      fi
    # tr -d '\r' is load-bearing on Windows. jq.exe writes CRLF line endings, so
    # `read` strips the \n and leaves the \r on the LAST field - campusId became
    # "2\r". The visible symptom is cosmetic (the closing bracket wraps to the
    # start of the line, because \r returns the cursor), but the same value goes
    # into the request body and the URL, and a stray control character there
    # fails in ways that look nothing like their cause.
    done < <(jq -r '(.data.items // .data.content // [])[]
                    | [.id, .email, (.campusId // "")] | @tsv' <<<"$RESP" | tr -d '\r')

    [[ "$ROWS" -lt 100 ]] && break
    PAGE=$((PAGE+1))
  done
done

echo ""
echo "=============================================="
if $APPLY; then
  echo -e " Created ${GREEN}$CREATED${NC}   already had one $SKIPPED   failed ${RED}$FAILED${NC}"
else
  echo -e " Would create ${YELLOW}$CREATED${NC}   already have one $SKIPPED"
  echo " Nothing was written. Re-run with --apply."
fi
[[ $NOCAMPUS -gt 0 ]] && echo -e " ${YELLOW}$NOCAMPUS account(s) have no campus and were skipped${NC}"
echo "=============================================="
exit $((FAILED > 0))
