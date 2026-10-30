#!/usr/bin/env bash
# End-to-end check. Needs: docker compose up, all 4 services running, curl.
# Users (password "carelane"): priya=admin, marcus=coordinator, okafor=clinician (Northside); elena=admin (Riverbend).
KC=http://localhost:8080/realms/tender_track/protocol/openid-connect/token
GW=http://localhost:8090
tok(){ curl -s -d client_id=carelane-web -d grant_type=password -d username=$1 -d password=carelane $KC | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }
call(){ # token method path [json]
  curl -s -o /tmp/smoke.out -w "%{http_code}" -X $2 -H "Authorization: Bearer $1" -H "Content-Type: application/json" ${4:+-d "$4"} $GW$3; }
ok(){ [ "$2" = "$3" ] && echo "PASS  $1" || { echo "FAIL  $1 (expected $3, got $2)"; FAILED=1; }; }

PRIYA=$(tok priya); MARCUS=$(tok marcus); OKAFOR=$(tok okafor); ELENA=$(tok elena)

ok "admin creates a clinical task"          "$(call $PRIYA POST /api/tasks '{"title":"Review HbA1c","category":"Results review","clinical":true}')" 200
ID=$(sed -E 's/.*"id":"([^"]+)".*/\1/' /tmp/smoke.out)
ok "clinician cannot create tasks"          "$(call $OKAFOR POST /api/tasks '{"title":"x","category":"y"}')" 403
ok "coordinator cannot claim clinical task" "$(call $MARCUS POST /api/tasks/$ID/claim)" 403
ok "clinician claims clinical task"         "$(call $OKAFOR POST /api/tasks/$ID/claim)" 200
ok "clinician completes it"                 "$(call $OKAFOR POST /api/tasks/$ID/complete)" 200
call $PRIYA GET /api/tasks >/dev/null; grep -q "$ID" /tmp/smoke.out && echo "PASS  own tenant sees the task" || { echo "FAIL  own tenant sees the task"; FAILED=1; }
call $ELENA GET /api/tasks >/dev/null; grep -q "$ID" /tmp/smoke.out && { echo "FAIL  other tenant must NOT see it"; FAILED=1; } || echo "PASS  other tenant cannot see it"
ok "other tenant cannot act on it"          "$(call $ELENA POST /api/tasks/$ID/claim)" 404
sleep 2
ok "admin reads audit log"                  "$(call $PRIYA GET /api/audit)" 200
grep -q TASK_COMPLETED /tmp/smoke.out && echo "PASS  audit has TASK_COMPLETED" || { echo "FAIL  audit entry missing"; FAILED=1; }
ok "coordinator cannot read audit log"      "$(call $MARCUS GET /api/audit)" 403
exit ${FAILED:-0}
