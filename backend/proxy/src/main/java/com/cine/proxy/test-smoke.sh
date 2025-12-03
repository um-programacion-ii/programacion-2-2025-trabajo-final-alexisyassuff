#!/usr/bin/env bash
set -euo pipefail
BASE_PROXY="http://localhost:8081"
EVENTO_ID=1
TOKEN_IN_REDIS=$(curl -s $BASE_PROXY/internal/catedra/token || true)

echo "Token en proxy (GET /internal/catedra/token):"
curl -s -S $BASE_PROXY/internal/catedra/token || true
echo -e "\n\n---- GET list eventos ----"
curl -s -D - $BASE_PROXY/eventos | sed -n '1,20p'
echo -e "\n\n---- GET detalle evento ----"
curl -s -D - $BASE_PROXY/eventos/$EVENTO_ID | sed -n '1,20p'
echo -e "\n\n---- POST bloquear (smoke) ----"
curl -s -D - -X POST $BASE_PROXY/eventos/$EVENTO_ID/bloquear \
  -H "Content-Type: application/json" \
  -d '{"asientos":[{"fila":2,"columna":3},{"fila":2,"columna":4}]}' | sed -n '1,40p'
echo -e "\n\n---- POST vender (smoke) ----"
curl -s -D - -X POST $BASE_PROXY/eventos/$EVENTO_ID/vender \
  -H "Content-Type: application/json" \
  -d '{"fecha":"2025-08-17T20:00:00.000Z","precioVenta":1400.10,"asientos":[{"fila":2,"columna":3,"persona":"Fernando Galvez"},{"fila":2,"columna":4,"persona":"Carlos Perez"}]}' | sed -n '1,60p'