# Carelane: multi-tenant clinical ops (Spring Boot microservices)

Gateway :8090 -> workqueue-service :8081, audit-service :8082, scheduling-service :8083, case-service :8085,
flow-service :8087 (configurable cases), tenant-service :8086, notification-service :8084 (emails land in
MailHog, http://localhost:8025). Kafka carries task events to audit.

## Run

1. `docker compose up -d`
2. Keycloak imports `infra/carelane-realm.json` automatically (realm `carelane`, client `carelane-web`, roles, and
   `tenant_id` + role claims).
   Test users, password `carelane`: priya (admin), marcus (coordinator), okafor (clinician) in Northside; elena (admin)
   in Riverbend.
3. `mvn clean install`
4. Start each service: `mvn -pl workqueue-service spring-boot:run` (same for audit-service, gateway)
5. Run `scripts/smoke.sh` for an end-to-end check of roles, tenant isolation and audit, or call
   `POST http://localhost:8090/api/tasks` with a Bearer token.

## How tenant isolation works

- `TenantFilter` reads `tenant_id` from the verified JWT only.
- `TenantScope.apply()` sets `app.tenant_id` inside each transaction; Postgres row-level security filters every query.
- The app connects as a non-superuser (`app_user`); superusers bypass RLS, so never run the services as one.
- Migrations run as the owner (`carelane`) via Flyway; each service owns its own database.

## Next

Notification service, per-tenant time zones, Dockerfiles, Kubernetes manifests, React front end.

## Front end

`cd frontend && npm install && npm run dev` then open http://localhost:5173 and sign in as priya, marcus, okafor or
elena (password `carelane`).
The token is held in memory only. Sign-in uses the password grant for local development; switch to authorization code +
PKCE before production.
Task queue names are hard-coded in `src/App.jsx` for now; load them per tenant from the API later.

## SLA escalation and notifications

`SlaJob` (workqueue-service) runs every 5 minutes. Tasks past their due date are marked escalated and an event is
published.
`notification-service` emails the address an admin sets under Settings. Emails carry no patient information.
Run several workqueue instances only after adding ShedLock to the job.

## Phase 2: cases and tenant settings

`case-service` tracks referrals (sent, scheduled, completed or lost) and prior auths (submitted, approved or denied,
then appealed or closed).
Open cases untouched for 7 days are flagged stalled; open cases past their due date are flagged overdue.
`tenant-service` stores each organization's display name, time zone, queue names and sites. The front end reads them at
sign-in.

## Configurable core (flow-service)

Case types live in the database as JSON: fields, states, transitions with allowed roles, an optional approval flag and a
deadline field.
Install the tender template from Settings (admin). A transition marked `approval` must be done by a different person
than the one who created the case.
An hourly job alerts once for open cases due within 3 days; notification-service emails the escalation address.
Add a new industry by dropping a JSON file in `flow-service/src/main/resources/templates/`, no code needed.
The older case-service (referrals and prior auths) can later be re-created as a template and retired.

## Messaging (Kafka)

Services publish events to the `carelane.events` topic (3 partitions), keyed by tenant id so each tenant's events stay
in order.
audit-service and notification-service each read the topic with their own consumer group.
Delivery is at-least-once: a redelivered event could be recorded twice, so add an event id and de-duplicate in
audit-service before production.
Failed events are retried 3 times, then skipped. Add a dead-letter topic before production.
