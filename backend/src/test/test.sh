#!/usr/bin/env bash
set -eu
BACKEND_HOST="localhost"
BACKEND_PORT=8080
PROXY_HOST="localhost"
PROXY_PORT=8081
REDIS_HOST="127.0.0.1"
REDIS_PORT=6379

echo "1) Proxy events (GET)"
curl -sS "http://${PROXY_HOST}:${PROXY_PORT}/internal/kafka/events?eventoId=1" | jq -C . || true
echo "----"

echo "2) Send webhook (JSON)"
# JSON sin escapes: usar comillas simples alrededor para no tener que escapar comillas internas
WEBHOOK_PAYLOAD='{"eventoId":"1","seatId":"r2cX","status":"BLOQUEADO","holder":"aluX","updatedAt":"2025-12-02T00:00:00Z"}'
curl -s -w "\nHTTP_CODE:%{http_code}\n" -X POST "http://${BACKEND_HOST}:${BACKEND_PORT}/internal/proxy/webhook" \
  -H "Content-Type: application/json" -d "$WEBHOOK_PAYLOAD" || true
echo "----"

echo "3) Check idempotency key in Redis"
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} GET "backend:proxy:webhook:1:r2cX" || true
echo "----"

echo "4) dryRun reconcile"
curl -s -X POST "http://${BACKEND_HOST}:${BACKEND_PORT}/internal/proxy/evento/1/reconcile?dryRun=true" | jq -C . || true
echo "----"

echo "5) apply reconcile"
curl -s -X POST "http://${BACKEND_HOST}:${BACKEND_PORT}/internal/proxy/evento/1/reconcile?apply=true" | jq -C . || true
echo "----"

echo "6) Check audit in Redis"
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} LLEN backend:reconciliation:applied || true
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} LRANGE backend:reconciliation:applied 0 20 || true
echo "----"

echo "Idempotency: re-send same webhook"
curl -s -w "\nHTTP_CODE:%{http_code}\n" -X POST "http://${BACKEND_HOST}:${BACKEND_PORT}/internal/proxy/webhook" \
  -H "Content-Type: application/json" -d "$WEBHOOK_PAYLOAD" || true
echo "Check retry queue"
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} LLEN backend:webhook:retry || true
echo "DONE"