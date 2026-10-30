# Seeds sample data into every service through the gateway API. Windows PowerShell.
# Needs: Keycloak, Kafka, the gateway and flow/tenant/notification/document/audit services all running,
# and the demo users from setup-keycloak.ps1 (viswa and marcus in one organization, elena in another).
#   powershell -ExecutionPolicy Bypass -File .\infra\seed-sample-data.ps1
param(
  [string]$Kc = "http://localhost:8080", [string]$Gateway = "http://localhost:8090",
  [string]$AdminPass = "Viswa@987", [string]$OtherPass = "Tender@123",
  [switch]$Force
)
$ErrorActionPreference = "Stop"
$realm = "tender_track"; $client = "tendertrack-web"
Add-Type -AssemblyName System.Net.Http

function Step($m) { Write-Host ""; Write-Host "== $m" -ForegroundColor Cyan }
function Fail($m) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 }
function Err($e) { if ($e.ErrorDetails -and $e.ErrorDetails.Message) { $e.ErrorDetails.Message } else { $e.Exception.Message } }
function HttpCode($e) { if ($e.Exception.Response) { [int]$e.Exception.Response.StatusCode } else { 0 } }
function Login($u, $p) {
  (Invoke-RestMethod -Method Post -Uri "$Kc/realms/$realm/protocol/openid-connect/token" `
    -Body @{ client_id = $client; grant_type = "password"; username = $u; password = $p }).access_token
}
function Call($tok, $method, $path, $body) {
  $req = @{ Method = $method; Uri = "$Gateway$path"; Headers = @{ Authorization = "Bearer $tok" } }
  if ($null -ne $body) {
    $req.ContentType = "application/json"
    if ($body -is [string]) { $req.Body = $body } else { $req.Body = ($body | ConvertTo-Json -Depth 6) }
  }
  Invoke-RestMethod @req
}
function ToDay($days) { (Get-Date).AddDays($days).ToString("yyyy-MM-dd") }
function Data($title, $auth, $value, $dep, $pre, $clar, $sub, $checks) {
  @{ title = $title; authority = $auth; estimatedValue = $value; currency = "INR"; depositAmount = $dep
     preBidMeeting = (ToDay $pre); clarificationDeadline = (ToDay $clar); submissionDeadline = (ToDay $sub); checklist = @($checks) }
}
function New-Tender($tok, $data) { (Call $tok POST "/api/flow/cases" @{ typeKey = "tender"; data = $data }).id }
function Advance($tok, $id, $to) { Call $tok POST "/api/flow/cases/$id/transition" @{ to = $to } | Out-Null }
function Upload-Doc($tok, $caseId, $path, $item) {
  $http = New-Object System.Net.Http.HttpClient
  $http.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $tok)
  $form = New-Object System.Net.Http.MultipartFormDataContent
  $form.Add((New-Object System.Net.Http.StringContent($caseId)), "caseId")
  if ($item) { $form.Add((New-Object System.Net.Http.StringContent($item)), "checklistItem") }
  $file = New-Object System.Net.Http.ByteArrayContent(, [IO.File]::ReadAllBytes($path))
  $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
  $form.Add($file, "file", [IO.Path]::GetFileName($path))
  $resp = $http.PostAsync("$Gateway/api/documents", $form).Result
  if (-not $resp.IsSuccessStatusCode) { throw "Upload of $([IO.Path]::GetFileName($path)) failed: $($resp.StatusCode) $($resp.Content.ReadAsStringAsync().Result)" }
}

Step "1. Signing in as viswa (admin), marcus (coordinator), elena (admin of the other organization)"
try { $viswa = Login "viswa" $AdminPass; $marcus = Login "marcus" $OtherPass; $elena = Login "elena" $OtherPass }
catch { Fail "Login failed: $(Err $_). Run .\infra\setup-keycloak.ps1 first, or pass -AdminPass / -OtherPass." }
Write-Host "OK"

Step "2. Checking that every service answers through the gateway"
$checks = [ordered]@{
  "flow-service"         = "/api/flow/types"
  "tenant-service"       = "/api/tenant/settings"
  "notification-service" = "/api/notifications/settings"
  "document-service"     = "/api/documents?caseId=00000000-0000-0000-0000-000000000000"
  #"audit-service"        = "/api/audit"
}
$bad = @()
foreach ($name in $checks.Keys) {
  try { Call $viswa GET $checks[$name] $null | Out-Null; Write-Host ("{0,-22} OK" -f $name) }
  catch { $c = HttpCode $_; Write-Host ("{0,-22} FAILED (HTTP {1})" -f $name, $c) -ForegroundColor Red; $bad += $name }
}
if ($bad.Count -gt 0) { Fail "Not reachable: $($bad -join ', '). 404 = missing gateway route, 503 = service not running, 401/403 = token problem." }

Step "3. Organization settings and alert emails (tenant-service, notification-service)"
Call $viswa PUT "/api/tenant/settings" '{"displayName":"Apex Builders Pvt Ltd","timeZone":"Asia/Kolkata","queues":["General"],"sites":[]}' | Out-Null
Call $elena PUT "/api/tenant/settings" '{"displayName":"Riverstone Infra Works","timeZone":"Asia/Kolkata","queues":["General"],"sites":[]}' | Out-Null
Call $viswa PUT "/api/notifications/settings" @{ escalationEmail = "bids@apexbuilders.example" } | Out-Null
Call $elena PUT "/api/notifications/settings" @{ escalationEmail = "bids@riverstone.example" } | Out-Null
Write-Host "Apex Builders Pvt Ltd and Riverstone Infra Works configured"

Step "4. Installing the tender template for both organizations (flow-service)"
foreach ($t in @($viswa, $elena)) {
  try { Call $t POST "/api/flow/templates/tender/install" $null | Out-Null }
  catch { if ((HttpCode $_) -ne 409) { Fail "Template install failed: $(Err $_)" } }
}
Write-Host "Installed (or already present)"

#Step "5. Checking for existing tenders"
#if ((@(Call $viswa GET "/api/flow/cases?type=tender" $null).Count -gt 0) -and -not $Force) {
#  Fail "Apex Builders already has tenders. Running again would duplicate them. Use -Force to add more anyway."
#}

#Step "6. Creating tenders for Apex Builders (created by marcus, approved by viswa)"
$all = @("Tender fee paid", "Deposit arranged", "Company registration documents", "Experience certificates", "Technical bid", "Financial bid")
try {
  # Identified
  $a1 = New-Tender $marcus (Data "Drainage upgrade, Zone 4" "Public Works Department" 18500000 370000 5 10 21 @())
  # Waiting for go/no-go approval
  $a2 = New-Tender $marcus (Data "Bridge rehabilitation, NH-48 stretch" "State Highways Department" 92000000 1840000 4 8 14 @("Tender fee paid"))
  Advance $marcus $a2 "GO_NO_GO"
  # Preparing, deadline in 2 days (triggers the deadline alert)
  $a3 = New-Tender $marcus (Data "Municipal road resurfacing, Ward 12" "City Municipal Corporation" 24600000 492000 -3 -1 2 $all[0..4])
  Advance $marcus $a3 "GO_NO_GO"; Advance $viswa $a3 "PREPARING"
  # Preparing
  $a4 = New-Tender $marcus (Data "Water pipeline replacement, Phase 2" "District Water Board" 41200000 824000 2 5 9 @($all[0], $all[2]))
  Advance $marcus $a4 "GO_NO_GO"; Advance $viswa $a4 "PREPARING"
  # Submitted
  $a5 = New-Tender $marcus (Data "School building, Block B" "Education Infrastructure Board" 31800000 636000 -12 -8 5 $all)
  Advance $marcus $a5 "GO_NO_GO"; Advance $viswa $a5 "PREPARING"; Advance $marcus $a5 "SUBMITTED"
  # Won
  $a6 = New-Tender $marcus (Data "Community hall construction" "Housing Development Authority" 12700000 254000 -40 -35 -28 $all)
  Advance $marcus $a6 "GO_NO_GO"; Advance $viswa $a6 "PREPARING"; Advance $marcus $a6 "SUBMITTED"; Advance $marcus $a6 "WON"
  # Lost
  $a7 = New-Tender $marcus (Data "Stormwater drain package" "City Municipal Corporation" 15900000 318000 -55 -50 -45 $all)
  Advance $marcus $a7 "GO_NO_GO"; Advance $viswa $a7 "PREPARING"; Advance $marcus $a7 "SUBMITTED"; Advance $marcus $a7 "LOST"
  # Declined at go/no-go
  $a8 = New-Tender $marcus (Data "Airport apron extension" "Airport Authority" 156000000 3120000 7 12 30 @())
  Advance $marcus $a8 "GO_NO_GO"; Advance $viswa $a8 "NO_GO"
} catch { Fail "Creating tenders for Apex Builders failed: $(Err $_)" }
Write-Host "8 tenders created: identified, in review, 2 preparing, submitted, won, lost, declined"

Step "7. Creating tenders for Riverstone (elena, a separate organization)"
try {
  $b1 = New-Tender $elena (Data "Riverside embankment works" "Irrigation Department" 27400000 548000 6 10 20 @())
  $b2 = New-Tender $elena (Data "Rural road package, Phase 1" "Rural Development Agency" 36800000 736000 3 7 12 @("Tender fee paid", "Deposit arranged"))
  Advance $elena $b2 "GO_NO_GO"
} catch { Fail "Creating tenders for Riverstone failed: $(Err $_)" }
Write-Host "2 tenders created (a single admin cannot approve their own go/no-go, so these stop at review)"

Step "8. Uploading sample documents (document-service)"
$tmp = Join-Path $env:TEMP "tender-seed"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
  $files = @(
    @{ id = $a3; name = "technical-bid-draft.txt"; item = "Technical bid"; text = "Sample technical bid draft for road resurfacing, Ward 12. Demo file." },
    @{ id = $a3; name = "tender-fee-receipt.txt"; item = "Tender fee paid"; text = "Receipt: tender fee paid to City Municipal Corporation. Demo file." },
    @{ id = $a4; name = "company-registration-copy.txt"; item = "Company registration documents"; text = "Copy of company registration certificate. Demo file." },
    @{ id = $a5; name = "boq-summary.csv"; item = "Financial bid"; text = "item,quantity,rate`nExcavation,1200,450`nPipe laying,800,1250" }
  )
  foreach ($f in $files) {
    $p = Join-Path $tmp $f.name
    Set-Content -Path $p -Value $f.text -Encoding UTF8
    Upload-Doc $marcus $f.id $p $f.item
    Write-Host "Uploaded $($f.name)"
  }
} catch { Fail "Document upload failed: $($_.Exception.Message)" }
finally { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }

Step "9. Tidying checklist ticks for the road resurfacing tender"
Call $marcus PUT "/api/flow/cases/$a3/data" @{ checklist = @($all[0..4]) } | Out-Null
Write-Host "Done"

Write-Host ""
Write-Host "Sample data is in place." -ForegroundColor Green
Write-Host "Sign in at http://localhost:5173 as viswa / $AdminPass (admin), marcus / $OtherPass (coordinator), or elena / $OtherPass (other organization)."
Write-Host "The Audit log (admin) now lists every step. For the deadline email, restart flow-service: it alerts about 45 seconds after startup, then hourly. Read it at http://localhost:8025."
