# crm-service

Generic, business-agnostic CRM bounded context for SugamFlow.

- **Standalone** SaaS CRM and **ERP-integrated** via events (Phase 2+)
- **No** dependency on product/stock/order/school domain tables
- API prefix: `/api/v1/crm/**` (gateway route reserved in Phase 0)
- Does **not** own `/api/v1/leads` (Field Force)

## Phase 1 scope

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/crm/status` | Health-ish status (no tenant) |
| `POST /api/v1/crm/workspaces/bootstrap` | Create workspace + default Sales pipeline |
| `GET /api/v1/crm/pipelines` | List pipelines |
| `GET /api/v1/crm/pipelines/{id}/stages` | List stages |
| `POST/GET/PUT/PATCH/DELETE /api/v1/crm/leads` | Lead CRUD |
| `POST /api/v1/crm/leads/{id}/assign` | MANUAL or ROUND_ROBIN assign |
| `POST /api/v1/crm/leads/import` | CSV / XLSX multipart import |
| `POST/GET/DELETE /api/v1/crm/assignment/members` | Team members for round-robin |

### CSV / Excel columns

`title` (or `name`) required. Optional: `display_name`, `company_name`, `email`, `phone`, `source_code`, `priority`, `score`, `owner_user_id`, `team_id`, `amount`, `currency`. Unknown columns → `attributes` JSON.

### Round-robin

1. `POST /api/v1/crm/assignment/members` with `{ "userId": "u1", "displayName": "Asha" }`  
2. `POST /api/v1/crm/leads/{id}/assign` with `{ "mode": "ROUND_ROBIN" }`  
3. Import: `assignRoundRobin=true` query param

## Local run

```bash
# Create DB
createdb crmdb   # user/pass crmdb/crmdb by default

cd D:\sugamFlow\crm-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Port: **8095** · Eureka name: `crm-service`

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
