#!/usr/bin/env bash
#
# Day 10 load test. Publishes N generation jobs through the RabbitMQ HTTP API,
# all sharing one batchId, each with its own jobId.
#
#   ./db/loadtest-publish.sh 600 900
#
# Exists because the real bulk engine is Tushar's and this side needs proving
# before it lands. Six hundred hand-published messages is not a test anyone runs
# twice.
set -euo pipefail

COUNT="${1:-100}"
BATCH_ID="${2:-900}"
HOST="${RABBIT_HTTP:-http://localhost:15672}"
USER="${RABBIT_USER:-guest}"
PASS="${RABBIT_PASS:-guest}"
RUN="lt-$(date +%s)"

echo "Publishing $COUNT jobs, batchId $BATCH_ID, run $RUN"
START=$(date +%s)

for i in $(seq 1 "$COUNT"); do
  # passId is unique per row. Reusing one pass id would make every message a
  # re-issue of the same pass, which serialises on the row lock and measures
  # lock contention rather than throughput.
  PASS_ID=$((100000 + i))

  PAYLOAD=$(cat <<JSON
{"jobId":"$RUN-$i","passId":$PASS_ID,"campusId":1,"batchId":$BATCH_ID,
 "holderName":"Load Test $i","holderEmail":"load$i@example.com",
 "validFrom":"2026-07-01","validTo":"2026-12-31",
 "emailSubject":"Your gate pass","emailGreeting":"Hi Load Test $i, your pass is attached."}
JSON
)
  ESCAPED=$(printf '%s' "$PAYLOAD" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')

  curl -s -u "$USER:$PASS" -H "Content-Type: application/json" \
    -X POST "$HOST/api/exchanges/%2F/perimity.qr/publish" \
    -d "{\"properties\":{\"content_type\":\"application/json\"},\"routing_key\":\"qr.generate\",\"payload\":$ESCAPED,\"payload_encoding\":\"string\"}" \
    > /dev/null

  if (( i % 50 == 0 )); then echo "  published $i"; fi
done

echo "Published $COUNT in $(( $(date +%s) - START ))s. Watch progress with:"
echo "  curl -s http://localhost:8086/api/qr/jobs/batch/$BATCH_ID/progress | python3 -m json.tool"
