#!/usr/bin/env bash
# Pruebas de fuego cruzadas contra la URL pública.
#   A = dueños de la app (tienen APP_USER/APP_PASSWORD)
#   B = la otra pareja (no tiene la contraseña)
# Uso: APP_USER=... APP_PASSWORD=... bash scripts/prueba-de-fuego-cruzada.sh https://<app>.onrender.com
# Todo lo que crea lo borra al final.

API="${1:?Falta la URL del backend}"; API="${API%/}/api/todos"
: "${APP_USER:?Falta APP_USER}" "${APP_PASSWORD:?Falta APP_PASSWORD}"
A="$APP_USER:$APP_PASSWORD"
J="Content-Type: application/json"
pasa=0; falla=0
check() { if [[ "$3" == "$2" ]]; then echo "PASA  $1"; pasa=$((pasa+1)); else echo "FALLA $1 (esperado: $2 | obtenido: $3)"; falla=$((falla+1)); fi; }
crear() { curl -s -u "$A" -X POST "$API" -H "$J" -d "{\"title\":\"$1\"}" | grep -o '"id":[0-9]*' | grep -o '[0-9]*'; }
# Lanza N peticiones al mismo tiempo (curl --parallel) y devuelve los códigos HTTP ordenados
simultaneas() { local n=$1 metodo=$2 url=$3 args=(); for ((i=0;i<n;i++)); do args+=(-o /dev/null "$url"); done
  curl -s -Z --parallel-immediate --parallel-max "$n" -u "$A" -X "$metodo" -w "%{http_code}\n" "${args[@]}" | sort | uniq -c | awk '{printf "%s×%s ", $1, $2}'; }

echo "Despertando el backend..."; curl -s -o /dev/null --retry 12 --retry-all-errors --retry-delay 10 --max-time 90 "$API"

echo "--- 1. Doble eliminación al mismo tiempo (como la doble reserva)"
id=$(crear "Concurrencia borrar")
r=$(simultaneas 10 DELETE "$API/$id")
echo "      10 DELETE simultáneos sobre la tarea $id: $r"
check "exactamente uno borra (204) y el resto responde 404" "1×204 9×404 " "$r"
check "la tarea ya no existe" "404" "$(curl -s -o /dev/null -w "%{http_code}" "$API/$id")"

echo "--- 2. Diez toggles al mismo tiempo (no se puede perder ninguno)"
id=$(crear "Concurrencia toggle")
r=$(simultaneas 10 PATCH "$API/$id/toggle")
echo "      10 PATCH simultáneos sobre la tarea $id: $r"
check "los 10 responden 200" "10×200 " "$r"
final=$(curl -s "$API/$id" | grep -o '"completed":[a-z]*' | cut -d: -f2)
check "10 cambios = número par → vuelve a completed=false" "false" "$final"
curl -s -o /dev/null -u "$A" -X DELETE "$API/$id"

echo "--- 3. Dos creaciones con el mismo título al mismo tiempo"
cuerpo='{"title":"Concurrencia crear"}'
ids=$(curl -s -Z --parallel-immediate -u "$A" -H "$J" -d "$cuerpo" "$API" --next -u "$A" -H "$J" -d "$cuerpo" "$API" | grep -o '"id":[0-9]*' | grep -o '[0-9]*' | sort -u)
check "se crean 2 tareas con ids distintos (títulos repetidos permitidos)" "2" "$(echo "$ids" | grep -c .)"
for i in $ids; do curl -s -o /dev/null -u "$A" -X DELETE "$API/$i"; done

echo "--- 4. B intenta cancelar (eliminar) la tarea de A"
id=$(crear "Tarea de A")
antes=$(curl -s "$API/$id")
check "B sin credenciales no puede eliminar → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API/$id")"
check "B con una contraseña inventada → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -u "$APP_USER:adivinando-la-clave" -X DELETE "$API/$id")"
check "B con otro usuario → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -u "pareja-b:$APP_PASSWORD" -X DELETE "$API/$id")"
check "B no puede editarla → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$API/$id" -H "$J" -d '{"title":"hackeada"}')"
check "B no puede marcarla → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$API/$id/toggle")"
check "B no puede crear tareas → 401" "401" "$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API" -H "$J" -d '{"title":"de B"}')"
check "B desde su propio sitio web: el navegador lo bloquea (CORS) → 403" "403" \
  "$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$API/$id" -H 'Origin: https://pareja-b.vercel.app' -H 'Access-Control-Request-Method: DELETE')"
check "la tarea de A sigue igual después de los ataques" "$antes" "$(curl -s "$API/$id")"
check "B sí puede verla (leer es público, por diseño)" "200" "$(curl -s -o /dev/null -w "%{http_code}" "$API/$id")"
curl -s -o /dev/null -u "$A" -X DELETE "$API/$id"

echo "--- Resultado: $pasa pasan, $falla fallan"
[[ $falla -eq 0 ]]
