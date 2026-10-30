# One-step Keycloak setup for the tender app (Windows PowerShell).
# Put this file and tender-track-realm.json in the same folder (the project's "infra" folder), then run:
#   powershell -ExecutionPolicy Bypass -File .\infra\setup-keycloak.ps1
# It: logs in as the Keycloak admin -> deletes realm "tender_track" if it exists -> imports it fresh ->
#     enables custom attributes -> sets tenant ids -> tests the login for "viswa" and shows the token contents.
param([string]$AdminUser = "admin", [string]$AdminPass = "admin", [string]$Kc = "http://localhost:8080")
$ErrorActionPreference = "Stop"
$realm = "tender_track"; $client = "tendertrack-web"
$realmFile = Join-Path $PSScriptRoot "tender-track-realm.json"
$root = Split-Path $PSScriptRoot -Parent

function Step($m) { Write-Host ""; Write-Host "== $m" -ForegroundColor Cyan }
function Fail($m) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 }
function Err($e) { if ($e.ErrorDetails -and $e.ErrorDetails.Message) { $e.ErrorDetails.Message } else { $e.Exception.Message } }
function Get-Token {
  (Invoke-RestMethod -Method Post -Uri "$Kc/realms/master/protocol/openid-connect/token" `
    -Body @{ client_id = "admin-cli"; grant_type = "password"; username = $AdminUser; password = $AdminPass }).access_token
}
function AuthHeaders { @{ Authorization = "Bearer " + (Get-Token) } }

Step "1. Logging in to Keycloak admin at $Kc"
try { Get-Token | Out-Null } catch { Fail "Cannot log in as '$AdminUser'. Is Keycloak running, and are the admin name and password right? Details: $(Err $_)" }
Write-Host "OK"

Step "2. Checking for an existing '$realm' realm"
$exists = $false
try { Invoke-RestMethod -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm" | Out-Null; $exists = $true }
catch {
  $code = 0; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
  if ($code -ne 404) { Fail "Could not check for the realm (HTTP $code): $(Err $_)" }
}
if ($exists) {
  Write-Host "Found it. Deleting it to start clean (its users are removed)." -ForegroundColor Yellow
  Invoke-RestMethod -Method Delete -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm" | Out-Null
} else { Write-Host "None found." }

Step "3. Importing $realmFile"
if (-not (Test-Path $realmFile)) { Fail "tender-track-realm.json must be in the same folder as this script ($PSScriptRoot)." }
try {
  Invoke-RestMethod -Method Post -Headers (AuthHeaders) -ContentType "application/json" -Uri "$Kc/admin/realms" -Body ([IO.File]::ReadAllText($realmFile)) | Out-Null
} catch { Fail "Keycloak rejected the realm file: $(Err $_)" }
Write-Host "Realm '$realm' imported (client '$client', users viswa, marcus, elena)."

Step "4. Enabling custom attributes and setting tenant ids"
try {
  $profile = Invoke-RestMethod -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm/users/profile"
  $profile | Add-Member -NotePropertyName unmanagedAttributePolicy -NotePropertyValue "ADMIN_EDIT" -Force
  Invoke-RestMethod -Method Put -Headers (AuthHeaders) -ContentType "application/json" -Uri "$Kc/admin/realms/$realm/users/profile" -Body ($profile | ConvertTo-Json -Depth 20) | Out-Null
  $orgA = "11111111-1111-1111-1111-111111111111"; $orgB = "22222222-2222-2222-2222-222222222222"
  $tenants = @{ viswa = $orgA; marcus = $orgA; elena = $orgB }
  foreach ($name in $tenants.Keys) {
    $user = (Invoke-RestMethod -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm/users?username=$name&exact=true")[0]
    $user | Add-Member -NotePropertyName attributes -NotePropertyValue @{ tenant_id = @($tenants[$name]) } -Force
    Invoke-RestMethod -Method Put -Headers (AuthHeaders) -ContentType "application/json" -Uri "$Kc/admin/realms/$realm/users/$($user.id)" -Body ($user | ConvertTo-Json -Depth 10) | Out-Null
    Write-Host "tenant_id set for $name"
  }
} catch { Fail "Could not set tenant ids: $(Err $_)" }

Step "5. Testing the login for viswa"
try {
  $r = Invoke-RestMethod -Method Post -Uri "$Kc/realms/$realm/protocol/openid-connect/token" `
    -Body @{ client_id = $client; grant_type = "password"; username = "viswa"; password = "Viswa@987" }
} catch { Fail "Login failed: $(Err $_)" }
$p = $r.access_token.Split(".")[1].Replace("-", "+").Replace("_", "/"); while ($p.Length % 4) { $p += "=" }
$claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
Write-Host "user: $($claims.preferred_username)"
Write-Host "tenant_id: $($claims.tenant_id)"
Write-Host "roles: $($claims.realm_access.roles -join ', ')"
Write-Host "issuer: $($claims.iss)"
if (-not $claims.tenant_id) { Fail "Login works but the token has no tenant_id." }
if ($claims.realm_access.roles -notcontains "admin") { Fail "Login works but the token has no 'admin' role." }
Write-Host "LOGIN OK" -ForegroundColor Green

Step "6. Checking the project files match"
$api = Join-Path $root "frontend\src\api.js"
if (Test-Path $api) {
  $a = Get-Content $api -Raw
  if ($a -match "client_id:\s*'([^']+)'") { $found = $Matches[1] } else { $found = "(not found)" }
  if ($found -ne $client) { Write-Host "FIX NEEDED: frontend\src\api.js uses client '$found' but must use '$client'." -ForegroundColor Yellow } else { Write-Host "api.js client: OK" }
  if ($a -notmatch "realms/$realm/") { Write-Host "FIX NEEDED: frontend\src\api.js does not point at realm '$realm'." -ForegroundColor Yellow }
}
Get-ChildItem $root -Recurse -File -Include application.yml, application.yaml | Where-Object { $_.FullName -notmatch '\\(node_modules|target)\\' } | ForEach-Object {
  $c = Get-Content $_.FullName -Raw
  if ($c -match "issuer-uri:\s*\S*realms/([A-Za-z0-9_\-]+)") {
    if ($Matches[1] -ne $realm) { Write-Host "FIX NEEDED: $($_.FullName) uses realm '$($Matches[1])', must be '$realm'." -ForegroundColor Yellow }
  }
}
Write-Host ""
Write-Host "Done. Open http://localhost:5173 and sign in as viswa / Viswa@987" -ForegroundColor Green
