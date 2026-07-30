# crm-service

Generic, business-agnostic CRM bounded context for SugamFlow.

- **Standalone** SaaS CRM and **ERP-integrated** via events (Phase 2+)
- **No** dependency on product/stock/order/school domain tables
- API prefix: `/api/v1/crm/**` (gateway route reserved in Phase 0)
- Does **not** own `/api/v1/leads` (Field Force)

## Phase 1–2 scope

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/crm/status` | Health-ish status (phase `2`) |
| `GET /api/v1/crm/templates` | Industry template codes |
| `POST /api/v1/crm/workspaces/bootstrap` | Workspace + lead + opportunity pipelines from template |
| `GET /api/v1/crm/pipelines` | List pipelines |
| `GET /api/v1/crm/pipelines/{id}/stages` | List stages |
| `POST/GET/PUT/PATCH/DELETE /api/v1/crm/leads` | Lead CRUD |
| `POST /api/v1/crm/leads/{id}/assign` | MANUAL or ROUND_ROBIN assign |
| `POST /api/v1/crm/leads/import` | CSV / XLSX multipart import |
| `POST/GET/DELETE /api/v1/crm/assignment/members` | Team members for round-robin |
| `POST/GET/PUT /api/v1/crm/opportunities/**` | Opportunity CRUD + stage move |
| `POST/GET /api/v1/crm/quotations/**` | GST quotation create/list/send/accept/pdf/payment-link |
| `GET/POST /api/v1/crm/sequences/**` | Comms sequences + enroll + process-due |
| `GET /api/v1/crm/analytics/summary` | Funnel / sources / overdue tasks |
| `GET/POST /api/v1/crm/tasks/**` | SLA aging + open tasks |
| `GET/POST /api/v1/crm/campaigns` | Campaign CRUD + public capture key |
| `POST /api/v1/crm/public/capture/{publicKey}` | Public lead ingest with UTM (no tenant header) |

Templates: `GENERIC`, `EDUCATION`, `RETAIL`, `MEDICAL_DISTRIBUTOR` (classpath `crm-templates/*.json`).

Quote send: `POST /quotations/{id}/send` with `{ "channel":"WHATSAPP","recipient":"91…" }` queues via notification-service when `crm.notification.enabled=true` (`CRM_NOTIFICATION_ENABLED`).

### CSV / Excel columns

`title` (or `name`) required. Optional: `display_name`, `company_name`, `email`, `phone`, `source_code`, `priority`, `score`, `owner_user_id`, `team_id`, `amount`, `currency`. Unknown columns → `attributes` JSON.

### Round-robin

1. `POST /api/v1/crm/assignment/members` with `{ "userId": "u1", "displayName": "Asha" }`  
2. `POST /api/v1/crm/leads/{id}/assign` with `{ "mode": "ROUND_ROBIN" }`  
3. Import: `assignRoundRobin=true` query param

## Local run

```powershell
# One-time: create role + database (postgres superuser password required)
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -f scripts\create-crmdb.sql

cd D:\sugamFlow\crm-service
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Defaults: `jdbc:postgresql://localhost:5432/crmdb` · user/pass **`crmdb`/`crmdb`**  
Override with `CRM_DB_URL` / `CRM_DB_USERNAME` / `CRM_DB_PASSWORD`.

Port: **8095** · Eureka name: `crm-service`

Local profile **disables Eureka** by default (avoids `localhost:8761` noise).  
crm-ui proxies to `:8095`. Gateway: set `GATEWAY_CRM_URI=http://localhost:8095` (or `host.docker.internal:8095`).

To register with discovery: `EUREKA_CLIENT_ENABLED=true` and start `discovery-service` on `:8761`.

Headers required (except `/status` and actuator):

```
X-Tenant-Id: <org-or-shop-id>
```

Optional: `X-User-Id` (default owner on create)

## Entitlements

`crm.entitlement.enabled=false` in local profile.

When enabled, calls subscription-service:

`GET /api/subscription/feature-flags/FEATURE_CRM`

Assign plan `crm-starter` (or Pro/Enterprise) to the tenant first.

## Smoke

```bash
curl http://localhost:8095/api/v1/crm/status

curl -X POST http://localhost:8095/api/v1/crm/workspaces/bootstrap \
  -H "X-Tenant-Id: demo-crm" -H "Content-Type: application/json" \
  -d "{\"name\":\"Demo\",\"templateCode\":\"GENERIC\"}"

curl -X POST http://localhost:8095/api/v1/crm/leads \
  -H "X-Tenant-Id: demo-crm" -H "Content-Type: application/json" \
  -d "{\"title\":\"Website lead\",\"phone\":\"9876543210\",\"sourceCode\":\"WEBSITE\"}"
```

Via gateway (`:9090`) once Eureka has `crm-service` registered.
