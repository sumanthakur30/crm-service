# CRM Retail pilot smoke (PowerShell)
# Prereq: crm-service on :8095 with profile local,pilot-retail (or local), Postgres crmdb.
# Usage: .\scripts\pilot-smoke.ps1 -TenantId demo-crm

param(
  [string]$BaseUrl = "http://localhost:8095",
  [string]$TenantId = "demo-crm"
)

$ErrorActionPreference = "Stop"
$headers = @{ "X-Tenant-Id" = $TenantId; "Content-Type" = "application/json" }

function Step($name, $script) {
  Write-Host "`n==> $name" -ForegroundColor Cyan
  & $script
}

Step "status" {
  $s = Invoke-RestMethod -Uri "$BaseUrl/api/v1/crm/status" -Headers $headers
  Write-Host ($s | ConvertTo-Json -Compress)
  if ($s.phase -notmatch "5") { Write-Warning "Expected phase 5-pilot, got $($s.phase)" }
}

Step "bootstrap RETAIL" {
  $ws = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/workspaces/bootstrap" -Headers $headers -Body (@{
    name = "Pilot Retail"; templateCode = "RETAIL"
  } | ConvertTo-Json)
  Write-Host "workspace=$($ws.name) template=$($ws.templateCode)"
}

Step "campaign" {
  $code = "PILOT-" + (Get-Date -Format "HHmmss")
  $c = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/campaigns" -Headers $headers -Body (@{
    code = $code; name = "Pilot campaign"; status = "ACTIVE"; channel = "WEB"; utmSource = "pilot"; utmMedium = "smoke"
  } | ConvertTo-Json)
  $script:PublicKey = $c.publicKey
  Write-Host "campaign=$($c.id) publicKey=$($c.publicKey)"
}

Step "public capture" {
  $lead = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/public/capture/$($script:PublicKey)" -Body (@{
    title = "Smoke lead"; displayName = "Smoke Tester"; phone = "9876543210"; email = "smoke@example.com"
  } | ConvertTo-Json) -ContentType "application/json"
  $script:LeadId = $lead.id
  Write-Host "lead=$($lead.id) score=$($lead.score)"
  if (-not $lead.score -or $lead.score -le 0) { throw "Expected score > 0 after capture" }
}

Step "opportunity + quote" {
  $opp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities" -Headers $headers -Body (@{
    name = "Smoke deal"; leadId = $script:LeadId; amount = 10000; currency = "INR"
  } | ConvertTo-Json)
  $script:OppId = $opp.id
  $q = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/quotations" -Headers $headers -Body (@{
    opportunityId = $opp.id
    customerName = "Smoke Tester"
    placeOfSupply = "KA"
    sellerStateCode = "29"
    buyerStateCode = "29"
    lines = @(@{ description = "Pilot SKU"; hsn = "9983"; quantity = 1; unitPrice = 10000; gstRate = 18 })
  } | ConvertTo-Json -Depth 5)
  $script:QuoteId = $q.id
  $sent = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/send" -Headers $headers -Body (@{
    channel = "WHATSAPP"; recipient = "9876543210"
  } | ConvertTo-Json)
  Write-Host "quote=$($sent.id) status=$($sent.status)"
}

Step "convert SHOP_CUSTOMER" {
  $conv = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/leads/$($script:LeadId)/convert?targetSystem=SHOP_CUSTOMER" -Headers $headers
  Write-Host "convert status=$($conv.status) externalId=$($conv.externalId)"
}

Step "audit export" {
  $job = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/crm/enterprise/audit-exports" -Headers $headers -Body (@{ format = "JSON" } | ConvertTo-Json)
  Write-Host "export #$($job.id) status=$($job.status) rows=$($job.rowCount)"
  if ($job.status -ne "DONE") { throw "Audit export not DONE" }
}

Write-Host "`nPilot smoke PASS" -ForegroundColor Green
