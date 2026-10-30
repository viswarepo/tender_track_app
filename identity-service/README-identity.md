# identity-service: users, roles and sign-in (replaces Keycloak)

Port 8089. Issues signed login tokens (RS256). Other services only verify them, using the public keys at
`http://localhost:8089/.well-known/jwks.json`. Tokens carry the same claims the services already read:
`tenant_id`, `preferred_username` and `realm_access.roles` (admin, coordinator or viewer).

## Setup (once)
1. Database: `psql -U postgres -f db-setup-identity.sql` (creates tender_identity_db; DEV ONLY, it drops an existing one).
2. Add `<module>identity-service</module>` to the root pom and put this folder next to the other services.
3. Point every other service at the new key set. In each service's application.yaml (gateway, flow, tenant, audit, notification, document)
   replace the `issuer-uri: ...` line with:
       jwk-set-uri: http://localhost:8089/.well-known/jwks.json
4. Gateway: add a route (same indentation as the others, under `routes:`):
       - id: identity
         uri: http://127.0.0.1:8089
         predicates:
           - Path=/api/users/**,/api/me/**
5. Start identity-service first. On an empty database it creates demo users (identity.bootstrap-demo):
       viswa / Viswa@987 (admin), marcus / Tender@123 (coordinator)   organization 11111111-...
       elena / Tender@123 (admin)                                      organization 22222222-...
6. Replace frontend/src/api.js and App.jsx, restart `npm run dev`. Keycloak is no longer needed.

## How it works
- /auth/login and /auth/register are public and called by the browser directly. Everything else goes through the gateway.
- Roles: admin (everything, including users and settings), coordinator (creates and edits tenders and documents), viewer (read only).
- Passwords: BCrypt. 8 to 72 characters with a letter and a number. 5 wrong attempts lock the account for 15 minutes.
- Users an admin creates, or whose password an admin resets, must choose a new password at their next sign-in.
- An organization always keeps at least one active admin.
- Logins last 30 minutes (identity.token-minutes). Role or status changes apply when the person signs in again.

## Before production
- Set identity.signup-enabled=false (or put sign-up behind an invitation or approval) and identity.bootstrap-demo=false.
- Protect and back up the signing key file (identity.key-file). Anyone with it can mint logins. Use a secret manager.
- Serve everything over HTTPS and set identity.issuer and identity.cors-origin to the real addresses.
- Missing for now: password-reset by email, refresh tokens (so sessions are short), audit events for user changes, MFA, login rate limiting beyond account lockout.
