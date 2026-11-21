#!/usr/bin/env bash
# Pruebas de fuego contra el despliegue público.
# Uso: bash scripts/prueba-de-fuego.sh https://<app>.onrender.com https://<front>.vercel.app
# Crea una tarea de prueba y la borra al final: la tabla queda como estaba.

API="${1:?Falta la URL de Render}"; FRONT="${2:?Falta la URL de Vercel}"
API="${API%/}"; FRONT="${FRONT%/}"
pasa=0; falla=0

check() { # descripción, esperado, obtenido
  if [[ "$3" == *"$2"* ]]; then echo "PASA  $1"; pasa=$((pasa+1))
  else echo "FALLA $1 (esperado: $2 | obtenido: ${3:0:160})"; falla=$((falla+1)); fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "Despertando el backend (Render gratis puede tardar ~1 min)..."
curl -s -o /dev/null --retry 12 --retry-all-errors --retry-delay 10 --max-time 90 "$API/api/todos"

echo "--- Front (Vercel)"
check "el front responde 200"                 "200"            "$(code "$FRONT/")"
check "el front es la app de tareas"          "Mis tareas"     "$(curl -s "$FRONT/")"
check "config.js apunta al backend de Render" "$API"           "$(curl -s "$FRONT/config.js")"
check "cabecera X-Content-Type-Options"       "nosniff"        "$(curl -sI "$FRONT/" | tr -d '\r')"

echo "--- Backend (Render + Supabase)"
check "GET /api/todos responde 200"           "200"            "$(code "$API/api/todos")"
check "GET /api/todos devuelve una lista"     "["              "$(curl -s "$API/api/todos")"
check "GET tarea inexistente → 404"           "no existe"      "$(curl -s "$API/api/todos/999999")"
check "GET id no numérico → 400"              "El id debe ser un número" "$(curl -s "$API/api/todos/abc")"
check "POST título vacío → 400"               "El título es obligatorio" \
  "$(curl -s -X POST "$API/api/todos" -H 'Content-Type: application/json' -d '{"title":"   "}')"
check "JSON roto sin detalles internos"       "El cuerpo de la petición no es válido" \
  "$(curl -s -X POST "$API/api/todos" -H 'Content-Type: application/json' -d '{"title":')"

creada=$(curl -s -X POST "$API/api/todos" -H 'Content-Type: application/json' -H "Origin: $FRONT" \
  -d '{"title":"Prueba de fuego"}')
id=$(echo "$creada" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
check "POST crea en Supabase"                 '"title":"Prueba de fuego"' "$creada"
check "PATCH toggle"                          '"completed":true' "$(curl -s -X PATCH "$API/api/todos/$id/toggle")"
check "DELETE → 204"                          "204"            "$(code -X DELETE "$API/api/todos/$id")"
check "DELETE otra vez → 404"                 "404"            "$(code -X DELETE "$API/api/todos/$id")"

echo "--- CORS"
check "permite el origen de Vercel" "access-control-allow-origin: $FRONT" \
  "$(curl -s -i -X OPTIONS "$API/api/todos" -H "Origin: $FRONT" -H 'Access-Control-Request-Method: POST' | tr -d '\r' | tr 'A-Z' 'a-z')"
check "rechaza otros orígenes → 403" "403" \
  "$(code -X OPTIONS "$API/api/todos" -H 'Origin: https://sitio-malicioso.com' -H 'Access-Control-Request-Method: DELETE')"

echo "--- Resultado: $pasa pasan, $falla fallan"
[[ $falla -eq 0 ]]
