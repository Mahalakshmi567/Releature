# Releature

Releature lets you release features on your terms: a Spring Boot API with PostgreSQL and Redis, and a React dashboard for creating and managing feature flags.

## Run it

Requirements: Java 17+, Maven, Node 18+, PostgreSQL 13+ and Redis 6+ installed locally.

```bash
# 1. Database: create the user and database (one time)
psql -U postgres -f database/setup.sql

# 2. Redis (cache for flag evaluation; the API still works without it)
redis-server        # on Windows, start the Memurai service instead

# 3. API (http://localhost:8080) - Flyway creates the table on first start
cd backend
mvn spring-boot:run

# 4. Dashboard (http://localhost:5173) - Vite proxies /api to the backend
cd frontend
npm install
npm run dev
```

The API connects to `jdbc:postgresql://localhost:5432/flags` as user `flags` / password `flags`.
To use different settings, set `DB_URL`, `DB_USER` and `DB_PASSWORD` environment variables
(or edit `backend/src/main/resources/application.yml`).

## Signing in

All `/api` endpoints except login need a token. The first start creates one account: username `admin`, password
`admin123` (set `ADMIN_USERNAME` and `ADMIN_PASSWORD` before the first start to choose your own; they are only used
when no users exist yet). Open the dashboard and sign in. That first account is an admin; change its password from the dashboard.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl localhost:8080/api/flags -H "Authorization: Bearer $TOKEN"
```

Tokens last 8 hours (`app.auth.token-hours`). Only a hash of the token is stored. Passwords are stored as bcrypt hashes.
Client apps that evaluate flags currently need such a token, since the evaluate endpoint is protected like the rest.

## Creating an account

The sign-in page links to "Create an account". Anyone who can reach the dashboard can register with a username and a
password (10 to 72 characters). New accounts are **viewers**, who can look but not change anything; an admin gives
them more access on the Users tab, where new sign-ups show with their creation date. Registering signs the new user in.
Sign out returns to the sign-in page, so a different account can sign in straight away. Each browser tab keeps its own
sign-in, so two accounts can also be open side by side in two tabs.

- **Turn it off:** set `REGISTRATION_ENABLED=false` before starting the backend. The "Create an account" link then
  disappears and the endpoint answers 403. Because a viewer can read every flag's configuration, switch registration off
  for shared or internet-facing installs and create accounts from the Users tab instead.
- **Abuse limits (in memory, per client address):** 10 failed sign-ins in 15 minutes block that address for the rest of
  the window; successful sign-ins never count. At most `app.registration.max-per-hour-per-ip` (default 10) accounts per
  address per hour. Behind a reverse proxy the backend sees the proxy's address, so configure forwarded headers before
  relying on this. The limits reset when the backend restarts.
- **API:** `POST /api/auth/register` with `{"username": "...", "password": "..."}` (public, returns a token like login)
  and `GET /api/auth/registration` (public, `{"enabled": true}`).

## Roles and permissions

Every user has one role, and each role can do everything the roles before it can.

| What you can do | Viewer | Developer | Release manager | Admin |
|---|:-:|:-:|:-:|:-:|
| See flags, audit history, evaluation results | yes | yes | yes | yes |
| See target user lists, cache stats | | yes | yes | yes |
| Create flags, edit a flag's name and description | | yes | yes | yes |
| Change value, rollout and targeting in development and testing, and roll those changes back | | yes | yes | yes |
| Change production (value, rollout, targeting, rollbacks) | | | yes | yes |
| Change a flag's default value, delete a flag | | | yes | yes |
| Manage users and roles | | | | yes |

Why the default value and deletion need release manager: the default value is what users outside a rollout get in
every environment, and deleting removes the flag from production. Creating a flag does put it in production with its
default value, but no code reads it yet.

How it is enforced (the dashboard also hides what you can't do, but the server decides):

1. `SecurityConfig` requires a valid token for every `/api` call and limits `/api/users` to admins.
2. `@PreAuthorize` on each controller method requires the role for that endpoint (`@EnableMethodSecurity`).
3. The services check production access themselves (`AccessControl`), so a production change is refused whichever way
   it arrives, including through a rollback.
4. Roles are read from the database on every request, so a role change or disabling an account applies immediately.
   Changing or resetting a password signs that user out everywhere.

Denied requests get `403` with a message such as "Changing production requires the release manager role."

Users: the Users tab (admins only) adds users, changes roles, switches accounts off, and resets passwords. At least one
active admin must always remain. Everyone can change their own password with "Change password" (`POST /api/auth/password`).
Passwords need 10 to 72 characters. Existing accounts became admins when this version was installed.

## Audit history

Every change to a flag is recorded with who made it, when, the reason, and the previous and new configuration:
creating, editing or deleting a flag, changing a value or rollout in an environment, and changing targeting.
Every change request must include a non-empty `reason` (up to 500 characters); the dashboard asks for it.

- View it on the "Audit history" tab, or `GET /api/audit?flagKey=new_checkout&environment=production&page=0&size=20`
  (newest first).
- The change and its history entry are saved in one transaction, so one never exists without the other.
- The history is append-only: the API has no way to edit or delete entries, and a database trigger rejects
  UPDATE and DELETE on `audit_log`. History is kept after a flag is deleted.
- A change that leaves the configuration identical is not recorded.
- `DELETE /api/flags/{id}?reason=...` takes the reason as a query parameter.

## Redis caching

Flag evaluation (`/api/evaluate`) reads a cached copy of each flag's configuration for the environment: value, rollout
percentage, target users and rules. A cache hit needs no database query. The admin screens (flag list, targeting
dialog, audit history) always read PostgreSQL, so you never see stale data there.

- **Keys:** one entry per flag and environment, `flags:cfg:{environment}:{flagKey}`, stored as JSON.
- **Invalidation:** when an administrator creates, edits or deletes a flag, changes an environment's value or rollout,
  or changes targeting, the affected entries are deleted right after the database transaction commits. The next
  evaluation reloads from PostgreSQL and caches the result. Editing a flag's default value clears all its environments;
  changing one environment clears only that one.
- **Safety net:** every entry expires after `app.cache.ttl-seconds` (default 60), so even a missed invalidation corrects
  itself within that time.
- **Redis down:** the cache is optional. On any Redis error the API reads PostgreSQL instead and skips Redis for
  10 seconds, so evaluations don't each wait for a timeout. Evictions Redis could not confirm are retried before the
  next cache read.
- **Not cached:** unknown flags or environments (404s).
- **Check it works:** `GET /api/cache/stats` returns hits, misses, Redis errors and pending evictions.
- **Windows:** Memurai works as the Redis server. With it on the same machine set `SPRING_DATA_REDIS_HOST=127.0.0.1`
  and `SPRING_DATA_REDIS_PORT=6379`.
- **Settings:** `CACHE_ENABLED=false` turns caching off. Point at another Redis with `SPRING_DATA_REDIS_HOST`,
  `SPRING_DATA_REDIS_PORT` and `SPRING_DATA_REDIS_PASSWORD`.

Known limits: if Redis rejects the delete after an admin change and the backend restarts or another instance serves
the next read, the old value can be served for up to the TTL. Instances do not share the retry list for failed deletes.

## Change notifications

When anyone changes a flag, every open dashboard updates by itself: the flag list and the audit history refresh, and a
pop-up says who changed what and why (for changes made by other people). A small "Live" indicator in the navigation
shows the connection; it turns amber and says "Reconnecting..." if the connection drops, and the dashboard reads
everything again once it is back, so nothing is missed. No page reload and no restart is needed.

- **Server-sent events, not WebSockets:** updates only flow from the server to the browser, and SSE needs nothing but a
  normal HTTP connection. `GET /api/events` is the stream (any signed-in user; viewers included). The browser's
  `EventSource` can't send the sign-in token in a header, so the dashboard reads the stream with `fetch` and parses
  the events itself (`changeFeed.js`) rather than putting the token in the URL.
- **What is sent:** a `flag-changed` event with the flag name and key, environment, action, who, the reason and whether
  it was a rollback. It never contains configuration values; the dashboard reads those again from the API. A `ready`
  event arrives when a stream opens, and a keep-alive comment every 25 seconds.
- **Order:** the notification goes out after the change is committed and after the Redis cache entries for it were
  dropped, so a dashboard reacting to it never reads the old state. Every recorded change sends one, rollbacks included.
- **Refreshing without a restart:** the Redis cache already refreshes itself when a flag changes; notifications extend that
  to the dashboards. Nothing about flags is read at startup, so no restart is ever needed for a flag change.
- **Limits:** streams are held in memory, so a dashboard hears about changes handled by the backend instance it is
  connected to; with several backend instances you would add Redis pub/sub. A stream ends after 10 minutes and the browser
  reconnects, which checks its token again, so a user who is signed out or disabled stops receiving updates within
  that time. At most 5 streams per user (extra tabs close the oldest). Behind nginx or another proxy, keep response
  buffering off for `/api/events` (the response already sends `X-Accel-Buffering: no`).

## One-click rollback

Every audit entry for a flag edit, an environment change or a targeting change has a "Restore previous configuration"
button. It puts the configuration back as it was before that change.

- **Preview first:** the dialog shows current -> restored values. If the configuration has changed since that entry, it
  warns that the restore also undoes those later changes, because it replaces the whole configuration (value and rollout
  together for an environment, users and rules together for targeting).
- **Audit trail kept:** the restore is recorded as a NEW audit entry (who, when, your reason, previous and new
  configuration) marked as a rollback of the original entry. Nothing in the history is edited or deleted, and you can
  roll back a rollback to re-apply a change.
- **Same rules as any edit:** it needs a reason, is validated, and clears the Redis cache for the affected flag.
- **Configuration only:** it does not undo anything the feature already did while it was on, such as records created
  for users or messages sent. That needs its own cleanup.
- **Not restorable:** created and deleted flags. A delete also removes targeting, which the history doesn't keep, so a
  flag can't be faithfully brought back from it. Entries for a flag that no longer exists, or whose configuration
  already equals the restored one, are refused.
- **API:** `GET /api/audit/{id}/rollback` previews it, `POST /api/audit/{id}/rollback` with `{"reason": "..."}` does it.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/login` | Sign in, returns a token and your role |
| POST | `/api/auth/register` | Create a viewer account and sign in (public) |
| GET | `/api/auth/me` | Current user and role |
| POST | `/api/auth/password` | Change your own password |
| GET / POST | `/api/users` | List / create users (admin) |
| PUT | `/api/users/{id}` | Change role or active state (admin) |
| POST | `/api/users/{id}/password` | Reset a user's password (admin) |
| GET | `/api/audit` | Audit history |
| GET / POST | `/api/audit/{id}/rollback` | Preview / perform a rollback |
| GET | `/api/cache/stats` | Cache hits, misses and errors |
| GET | `/api/events` | Server-sent event stream of flag changes |
| GET | `/api/flags` | List flags |
| GET | `/api/flags/{id}` | Get one flag |
| POST | `/api/flags` | Create a flag |
| PUT | `/api/flags/{id}` | Update name, description, default value |
| PUT | `/api/flags/{id}/environments/{envKey}` | Set the value in one environment |
| DELETE | `/api/flags/{id}` | Delete a flag |
| GET | `/api/environments` | List environments |
| GET | `/api/evaluate/{envKey}/{flagKey}?userId=...` | Decide what one user gets |
| POST | `/api/evaluate/{envKey}/{flagKey}` | Same, with `{userId, attributes}` in the body |
| GET / PUT | `/api/flags/{id}/environments/{envKey}/targeting` | Read / replace target users and rules |

```bash
curl -X POST localhost:8080/api/flags -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "name": "New Checkout", "key": "new_checkout",
  "dataType": "BOOLEAN", "defaultValue": "false", "reason": "Prepare the checkout rewrite"
}'
```

## Rules the MVP enforces

- Key: lowercase letters, numbers, underscores; starts with a letter; unique (409 on duplicates).
- Data types: Boolean, String, Number. The default value is validated against the type.
- Key and data type are locked after creation, since application code depends on them.

## Environments

Three environments are seeded: `development`, `testing`, `production`. Every flag has its own value in each,
starting from the flag's default value. Changing a value in one environment never changes the others, and the
dashboard asks for confirmation before changing a value in production.

## Percentage rollout

Each flag has a rollout percentage (0-100) in each environment, 100 by default. Users inside the rollout get the
environment's value; everyone else gets the flag's default value.

- A user's bucket (0-99) is a SHA-256 hash of `flagKey:userId`, so the same user always gets the same decision for
  a flag, on any server, across restarts and across environments.
- A user is inside an N% rollout when their bucket is below N. Raising the percentage only adds users, so nobody who
  already has the feature loses it. Lowering it removes the users with the highest buckets first.
- The flag key is part of the hash, so a 10% rollout of one flag does not hit the same 10% of users as another flag.
- Evaluate a user: `curl "localhost:8080/api/evaluate/production/new_checkout?userId=user-42"`
  (`userId` is optional only at 0% and 100%).
- Set the percentage: `PUT /api/flags/{id}/environments/production` with `{"rolloutPercentage": 25, "reason": "Start the canary"}`.
- Run the bucketing tests with `mvn test`.

## User targeting

Per flag and environment you can target explicit user IDs and attribute rules such as `plan is one of pro, enterprise`.
Targeted users always get the environment's value.

How targeting interacts with the percentage rollout (first match wins):

1. User ID is on the target list: environment value (`EXPLICIT_USER`).
2. A rule matches the user's attributes: environment value (`ATTRIBUTE_RULE`).
3. Everyone else follows the percentage rollout: inside gets the environment value, outside gets the flag's default.

So targeting overrides the rollout, even at 0%, and the rollout only decides for users who are not targeted.

- Rules are ORed: a user matching any rule is targeted. A rule is one attribute and a list of allowed values;
  matching ignores case. Attribute names are lowercase (`plan`, `country`).
- A user without the attribute never matches the rule.
- Limits: 1000 user IDs, 20 rules, 50 values per rule, per environment.
- Evaluate with attributes: `curl "localhost:8080/api/evaluate/production/new_checkout?userId=u1&attr.plan=pro"`
  or POST `{"userId": "u1", "attributes": {"plan": "pro"}}`.

## Not in the MVP yet

API keys for client apps (the evaluate endpoint currently needs a user's token, and each call costs one database lookup
to check it), email verification or admin approval of new accounts, audit entries for user and role changes (they are only logged for now), and approval
workflows for production changes.
