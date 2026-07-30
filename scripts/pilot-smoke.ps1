# CRM Retail pilot smoke (PowerShell)
# Prereq: crm-service on :8095. For local smoke without subscription plan:
#   $env:CRM_ENTITLEMENT_ENABLED='false'
#   mvn spring-boot:run "-Dspring-boot.run.profiles=local,pilot-retail"
# Usage: .\scripts\pilot-smoke.ps1 -TenantId demo-crm

param(
  [string]$BaseUrl = "http://localhost:8095",
  [string]$TenantId = "demo-crm"
)

$ErrorActionPreference = "Stop"
$headers = @{ "X-Tenant-Id" = $TenantId; "Content-Type" = "application/json" }

function Step($name, $script) {
  Write-Host ""
  Write-Host "==> $name" -ForegroundColor Cyan
  & $script
}

function Invoke-Crm {
  param([string]$Method = "Get", [string]$Uri, [object]$Body = $null)
  try {
    if ($null -eq $Body) {
      return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
    }
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -Body ($Body | ConvertTo-Json -Depth 6)
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) {
      Write-Host "Cannot reach $BaseUrl. Start crm-service first:" -ForegroundColor Red
      Write-Host '  $env:CRM_ENTITLEMENT_ENABLED=''false'''
      Write-Host '  mvn spring-boot:run "-Dspring-boot.run.profiles=local,pilot-retail"'
      throw "crm-service not reachable on $BaseUrl"
    }
    $code = [int]$resp.StatusCode
    if ($code -eq 403) {
      Write-Host "HTTP 403 - FEATURE_CRM not enabled for tenant '$TenantId'." -ForegroundColor Red
      Write-Host 'For local smoke: $env:CRM_ENTITLEMENT_ENABLED=''false'' then restart crm-service'
      Write-Host 'Or assign crm-professional in subscription-service.'
      throw "Forbidden for tenant $TenantId"
    }
    throw
  }
}

Step "status" {
  $s = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/status"
  Write-Host ($s | ConvertTo-Json -Compress)
  if ($s.phase -notmatch "5") { Write-Warning "Expected phase 5-pilot, got $($s.phase)" }
  if ($s.entitlementCheckEnabled) {
    Write-Host "NOTE: entitlement check is ON." -ForegroundColor Yellow
  }
}

Step "bootstrap RETAIL" {
  $ws = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/workspaces/bootstrap" -Body @{
    name = "Pilot Retail"; templateCode = "RETAIL"
  }
  Write-Host "workspace=$($ws.name) template=$($ws.templateCode)"
}

Step "campaign" {
  $code = "PILOT-" + (Get-Date -Format "HHmmss")
  $c = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/campaigns" -Body @{
    code = $code; name = "Pilot campaign"; status = "ACTIVE"; channel = "WEB"; utmSource = "pilot"; utmMedium = "smoke"
  }
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
  $opp = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities" -Body @{
    name = "Smoke deal"; leadId = $script:LeadId; amount = 10000; currency = "INR"
  }
  $q = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations" -Body @{
    opportunityId = $opp.id
    customerName = "Smoke Tester"
    placeOfSupply = "KA"
    sellerStateCode = "29"
    buyerStateCode = "29"
    lines = @(@{ description = "Pilot SKU"; hsn = "9983"; quantity = 1; unitPrice = 10000; gstRate = 18 })
  }
  $sent = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/send" -Body @{
    channel = "WHATSAPP"; recipient = "9876543210"
  }
  Write-Host "quote=$($sent.id) status=$($sent.status)"
}

Step "convert SHOP_CUSTOMER" {
  $conv = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/leads/$($script:LeadId)/convert?targetSystem=SHOP_CUSTOMER"
  Write-Host "convert status=$($conv.status) externalId=$($conv.externalId)"
}

Step "audit export" {
  $job = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/enterprise/audit-exports" -Body @{ format = "JSON" }
  Write-Host "export #$($job.id) status=$($job.status) rows=$($job.rowCount)"
  if ($job.status -ne "DONE") { throw "Audit export not DONE" }
}

Write-Host ""
Write-Host "Pilot smoke PASS" -ForegroundColor Green
