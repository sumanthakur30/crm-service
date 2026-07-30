# Extended CRM backlog smoke (PowerShell)
# Prereq: crm-service on :8095 with local,pilot-retail and Flyway >= V15
# Usage: .\scripts\pilot-smoke-backlog.ps1 -TenantId demo-crm

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
  if ($null -eq $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
  }
  return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -Body ($Body | ConvertTo-Json -Depth 8)
}

Step "status + entitlements snapshot" {
  $s = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/status"
  Write-Host ($s | ConvertTo-Json -Compress)
  $e = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/entitlements"
  Write-Host ("checksEnabled=" + $e.checksEnabled)
}

Step "bootstrap RETAIL" {
  $ws = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/workspaces/bootstrap" -Body @{
    name = "Pilot Retail"; templateCode = "RETAIL"
  }
  Write-Host "workspace=$($ws.name) template=$($ws.templateCode)"
}

Step "lead + deal" {
  $pipes = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/pipelines"
  $leadPipe = $pipes | Where-Object { $_.objectType -eq 'LEAD' -or $_.code -match 'RETAIL|SALES|ADMISSION' } | Select-Object -First 1
  $dealPipe = $pipes | Where-Object { $_.objectType -eq 'OPPORTUNITY' -or $_.code -match 'DEAL' } | Select-Object -First 1
  $leadStages = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/pipelines/$($leadPipe.id)/stages"
  $dealStages = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/pipelines/$($dealPipe.id)/stages"
  $script:LeadStageDemo = ($leadStages | Where-Object { $_.code -eq 'DEMO' } | Select-Object -First 1)
  $script:DealStageNeg = ($dealStages | Where-Object { $_.code -eq 'NEGOTIATION' } | Select-Object -First 1)
  $script:DealStageWon = ($dealStages | Where-Object { $_.won -eq $true } | Select-Object -First 1)
  $lead = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/leads" -Body @{
    title = "Backlog smoke lead"; displayName = "Smoke"; phone = "9876501234"; email = "smoke2@example.com"; sourceCode = "WEB"
  }
  $script:LeadId = $lead.id
  Write-Host "lead=$($lead.id)"
  if ($script:LeadStageDemo) {
    $moved = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/leads/$($lead.id)/stage/$($script:LeadStageDemo.id)"
    Write-Host "lead stage -> $($script:LeadStageDemo.code)"
  }
  $opp = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities" -Body @{
    name = "Backlog smoke deal"; leadId = $lead.id; amount = 25000; currency = "INR"
  }
  $script:OppId = $opp.id
  Write-Host "opp=$($opp.id)"
}

Step "stage automation (NEGOTIATION task)" {
  $rules = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/automation/stage-rules"
  Write-Host "rules=$($rules.Count)"
  if (-not $script:DealStageNeg) { throw "NEGOTIATION stage missing" }
  $moved = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities/$($script:OppId)/stage/$($script:DealStageNeg.id)" -Body @{}
  Write-Host "opp status=$($moved.status) stage=$($moved.stageId)"
  $tasks = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/tasks"
  $hit = $tasks | Where-Object { $_.relatedId -eq $script:OppId -and $_.sourceCode -eq 'STAGE_AUTOMATION' } | Select-Object -First 1
  if (-not $hit) { Write-Warning "No STAGE_AUTOMATION task yet (rule may not match stage code)" }
  else { Write-Host "automation task=$($hit.id) $($hit.title)" }
}

Step "won/lost close reason" {
  $reasons = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/close-reasons?outcome=WON"
  $code = $reasons[0].code
  if (-not $script:DealStageWon) { throw "Won stage missing" }
  $won = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities/$($script:OppId)/stage/$($script:DealStageWon.id)" -Body @{
    closeReasonCode = $code; closeReasonNote = "smoke"
  }
  Write-Host "won reason=$($won.closeReasonCode) status=$($won.status)"
  if ($won.closeReasonCode -ne $code) { throw "close reason not persisted" }
}

Step "quote revise + discount approval + send" {
  # reopen deal for quote work: create fresh open opp
  $opp2 = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/opportunities" -Body @{
    name = "Quote smoke deal"; leadId = $script:LeadId; amount = 10000; currency = "INR"
  }
  $q = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations" -Body @{
    opportunityId = $opp2.id
    customerName = "Smoke Tester"
    placeOfSupply = "KA"
    sellerStateCode = "29"
    buyerStateCode = "29"
    discountAmount = 2000
    lines = @(@{ description = "Pilot SKU"; hsn = "9983"; qty = 1; unitPrice = 10000; gstRate = 18 })
  }
  Write-Host "quote v$($q.versionNo) discount=$($q.discountAmount) approval=$($q.approvalStatus)"
  $blocked = $false
  try {
    Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/send" -Body @{}
  } catch {
    $blocked = $true
    Write-Host "send blocked as expected (discount gate)"
  }
  if (-not $blocked) { Write-Warning "Expected discount approval 409; send may have passed if threshold off" }
  $q2 = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)"
  if ($q2.approvalStatus -eq 'PENDING' -and $q2.approvalId) {
    $dec = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/ops/approvals/$($q2.approvalId)/decide?approve=true"
    Write-Host "approved=$($dec.status)"
  } else {
    Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/request-discount-approval" | Out-Null
    $q2 = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)"
    if ($q2.approvalId) {
      Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/ops/approvals/$($q2.approvalId)/decide?approve=true" | Out-Null
    }
  }
  $sent = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/send" -Body @{
    channel = "WHATSAPP"; recipient = "9876501234"
  }
  Write-Host "sent status=$($sent.status)"
  $rev = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/quotations/$($q.id)/revise"
  Write-Host "revised v$($rev.versionNo) parent=$($rev.parentQuotationId) status=$($rev.status)"
  if ($rev.versionNo -lt 2) { throw "revise did not bump version" }
}

Step "sequence builder enroll" {
  $seq = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/sequences/ensure-welcome"
  $script:SeqId = $seq.id
  $saved = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/sequences" -Body @{
    code = $seq.code
    name = $seq.name
    channelDefault = "WHATSAPP"
    steps = @(
      @{ sortOrder = 10; delayHours = 0; channel = "WHATSAPP"; bodyTemplate = "Hi {{name}}, smoke step 1" },
      @{ sortOrder = 20; delayHours = 1; channel = "WHATSAPP"; bodyTemplate = "Hi {{name}}, smoke step 2" }
    )
  }
  Write-Host "sequence steps=$($saved.steps.Count)"
  $enr = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/sequences/enrollments" -Body @{
    sequenceId = $saved.id; leadId = $script:LeadId; recipient = "9876501234"; channel = "WHATSAPP"
  }
  Write-Host "enrollment=$($enr.id) status=$($enr.status)"
  $proc = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/sequences/process-due?limit=5"
  Write-Host "process-due processed=$($proc.processed)"
}

Step "FF embed config" {
  $ff = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/field-force/embed-config"
  Write-Host ($ff | ConvertTo-Json -Compress)
  if (-not $ff.enabled) { throw "FF embed disabled" }
  if ($ff.visitUrlTemplate -notmatch 'leadId') { throw "visitUrlTemplate missing leadId placeholder" }
}

Step "tags + attachments" {
  $tags = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/tags"
  $tagId = $tags[0].id
  $assigned = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/tags/assignments/LEAD/$($script:LeadId)" -Body @{ tagId = $tagId }
  Write-Host "lead tags=$($assigned.Count)"
  $att = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/attachments/LEAD/$($script:LeadId)" -Body @{
    fileName = "smoke-note.txt"; note = "pilot smoke attachment"
  }
  Write-Host "attachment=$($att.id)"
  $list = Invoke-Crm -Uri "$BaseUrl/api/v1/crm/attachments/LEAD/$($script:LeadId)"
  if ($list.Count -lt 1) { throw "attachment missing" }
}

Step "convert SHOP_CUSTOMER" {
  $conv = Invoke-Crm -Method Post -Uri "$BaseUrl/api/v1/crm/leads/$($script:LeadId)/convert?targetSystem=SHOP_CUSTOMER"
  Write-Host "convert status=$($conv.status) externalId=$($conv.externalId)"
}

Write-Host ""
Write-Host "PWA install: open crm-ui :4500 and use Install app / Add to Home Screen (manual UI check)." -ForegroundColor Yellow
Write-Host "Backlog pilot smoke PASS" -ForegroundColor Green
