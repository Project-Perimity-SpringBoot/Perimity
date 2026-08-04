#!/usr/bin/env bash
#
# Perimity — Day 12 service check.
#
#   ./check-services.sh
#
# Answers three questions in order, because a later one is meaningless if an
# earlier one fails:
#   1. Is the infrastructure up?
#   2. Is each service listening and answering?
#   3. Do the service-to-service contracts actually line up?
#
# Read-only. Creates nothing, changes nothing.

set -uo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}PASS${NC}  $1"; }
fail() { echo -e "  ${RED}FAIL${NC}  $1"; FAILURES=$((FAILURES+1)); }
warn() { echo -e "  ${YELLOW}WARN${NC}  $1"; }
FAILURES=0

KEY=$(grep '^INTERNAL_API_KEY=' .env 2>/dev/null | cut -d= -f2- || echo "")

echo ""
echo "=============================================="
echo " 1. INFRASTRUCTURE"
echo "=============================================="
for c in perimity-postgres perimity-rabbitmq perimity-mongo perimity-redis perimity-mailhog; do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null \
             || docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo "missing")
    case "$status" in
        healthy|running) pass "$c ($status)" ;;
        missing)         fail "$c is not running - docker compose up -d" ;;
        *)               warn "$c is $status" ;;
    esac
done

echo ""
echo " Databases (all five must exist or a service will not start):"
for db in authdb userdb gatepassdb campusdb qrdb; do
    if docker exec perimity-postgres psql -U perimity -lqt 2>/dev/null | cut -d\| -f1 | grep -qw "$db"; then
        pass "$db"
    else
        fail "$db missing - check docker/postgres/init-databases.sql ran"
    fi
done

echo ""
echo "=============================================="
echo " 2. SERVICES — is each one listening?"
echo "=============================================="
declare -a SERVICES=(
    "auth:8081:/api/auth/ping"
    "user:8082:/api/user/ping"
    "gatepass:8083:/api/gatepass/ping"
    "campus:8084:/api/campus/ping"
    "guard:8085:/api/guard/ping"
    "qr:8086:/api/qr/ping"
)
UP=0
for entry in "${SERVICES[@]}"; do
    IFS=: read -r name port path <<< "$entry"
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:$port$path" 2>/dev/null)
    case "$code" in
        200)     pass "$name-service  :$port  ping 200"; UP=$((UP+1)) ;;
        401|403) warn "$name-service  :$port  ping $code (up, but ping is behind auth)"; UP=$((UP+1)) ;;
        000)     fail "$name-service  :$port  NOT RUNNING" ;;
        *)       warn "$name-service  :$port  ping returned $code" ;;
    esac
done
echo ""
echo "  $UP of 6 services reachable."

if [ "$UP" -lt 6 ]; then
    echo ""
    echo "  Start a missing one with:"
    echo "    cd <name>-service && mvn spring-boot:run"
    echo ""
    echo "  Skipping contract checks - they need every service up."
    exit 1
fi

echo ""
echo "=============================================="
echo " 3. CONTRACTS — do the services agree?"
echo "=============================================="
echo ""
echo " These are the joins where one service calls another. Each one is a"
echo " place a 404 hides until a demo."
echo ""

# --- guard -> qr : the scan path, hop 1 ---
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST "http://localhost:8086/api/qr/internal/decrypt" \
    -H "Content-Type: application/json" -H "X-Internal-Api-Key: $KEY" \
    -d '{"token":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","gateId":1}' 2>/dev/null)
[ "$code" = "200" ] && pass "guard -> qr    POST /api/qr/internal/decrypt  ($code)" \
                    || fail "guard -> qr    POST /api/qr/internal/decrypt  ($code, want 200)"

# --- guard -> gatepass : the scan path, hop 2 ---
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8083/api/gatepass/internal/passes/1" \
    -H "X-Internal-Api-Key: $KEY" 2>/dev/null)
[ "$code" = "200" ] || [ "$code" = "404" ] \
    && pass "guard -> gatepass  GET /api/gatepass/internal/passes/{id}  ($code)" \
    || fail "guard -> gatepass  GET /api/gatepass/internal/passes/{id}  ($code)"

# REGRESSION CHECK - this assertion is now INVERTED, on purpose.
#
# /api/internal/gatepass/passes/{id} is the path Team Guide section 5 documents
# and gatepass-service has never served. guard-service called it for three days
# and 404'd on every scan, which surfaced as a 503 outage card because a 404
# from a hop is indistinguishable from that hop being down.
#
# HttpPassVerificationClient now calls /api/gatepass/internal/passes/{id} - the
# line checked immediately above - so a 404 HERE is the correct and expected
# result. It means nobody has re-introduced the documented-but-wrong prefix.
#
# If this ever returns 200, gatepass-service has started serving both spellings
# and the ambiguity that caused the original bug is back.
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8083/api/internal/gatepass/passes/1" \
    -H "X-Internal-Api-Key: $KEY" 2>/dev/null)
if [ "$code" = "404" ]; then
    pass "old wrong path /api/internal/gatepass/passes/1 is absent (404) - as it should be"
else
    warn "old wrong path /api/internal/gatepass/passes/1 answered $code - two spellings now exist"
fi

# --- gatepass -> auth ---
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8081/api/internal/auth/users/by-email?email=nobody@example.com" \
    -H "X-Internal-Api-Key: $KEY" 2>/dev/null)
[ "$code" = "200" ] || [ "$code" = "404" ] \
    && pass "gatepass -> auth   GET /api/internal/auth/users/by-email  ($code)" \
    || fail "gatepass -> auth   GET /api/internal/auth/users/by-email  ($code)"

# --- gatepass -> user ---
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8082/api/user/internal/profiles/1/summary" \
    -H "X-Internal-Api-Key: $KEY" 2>/dev/null)
[ "$code" = "200" ] || [ "$code" = "404" ] \
    && pass "gatepass -> user   GET /api/user/internal/profiles/{id}/summary  ($code)" \
    || fail "gatepass -> user   GET /api/user/internal/profiles/{id}/summary  ($code)"

# --- anyone -> campus ---
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8084/api/campus/internal/campuses/1" \
    -H "X-Internal-Api-Key: $KEY" 2>/dev/null)
[ "$code" = "200" ] || [ "$code" = "404" ] \
    && pass "* -> campus        GET /api/campus/internal/campuses/{id}  ($code)" \
    || fail "* -> campus        GET /api/campus/internal/campuses/{id}  ($code)"

echo ""
echo "=============================================="
echo " 4. THE INTERNAL KEY ACTUALLY GUARDS THINGS"
echo "=============================================="
echo ""
for entry in "8086:/api/qr/internal/emails/undelivered" \
             "8083:/api/gatepass/internal/passes/1" \
             "8084:/api/campus/internal/campuses/1"; do
    IFS=: read -r port path <<< "$entry"
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:$port$path" 2>/dev/null)
    [ "$code" = "401" ] || [ "$code" = "403" ] \
        && pass "no key -> $path  ($code)" \
        || fail "no key -> $path  ($code) - internal endpoint is OPEN"
done

echo ""
echo "=============================================="
echo " 5. QUEUES"
echo "=============================================="
echo ""
docker exec perimity-rabbitmq rabbitmqctl list_queues name messages consumers 2>/dev/null \
    | grep -E "qr\.|name" || warn "could not read queues"
echo ""
echo "  qr.generate.request should have >= 1 consumer."
echo "  qr.generate.result   should have >= 1 consumer (gatepass)."
echo "  qr.generate.dlq      should be 0 messages on a clean run."

echo ""
echo "=============================================="
[ "$FAILURES" -eq 0 ] \
    && echo -e " ${GREEN}All checks passed.${NC}" \
    || echo -e " ${RED}$FAILURES check(s) failed.${NC}"
echo "=============================================="
echo ""
exit $FAILURES
