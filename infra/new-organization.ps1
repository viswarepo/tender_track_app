# Creates a new organization (tenant) or adds a user to an existing one. Windows PowerShell.
# New organization:
#   powershell -ExecutionPolicy Bypass -File .\infra\new-organization.ps1 -OrgName "Acme Builders" -User acme_admin -Password "Acme@12345"
# Add a coordinator to an existing organization (use the TenantId printed when it was created):
#   powershell -ExecutionPolicy Bypass -File .\infra\new-organization.ps1 -AddToExisting -TenantId <uuid> -User ravi -Password "Ravi@12345" -Role coordinator
param(
  [string]$OrgName,
  [Parameter(Mandatory = $true)][string]$User,
  [Parameter(Mandatory = $true)][string]$Password,
  [ValidateSet("admin", "coordinator")][string]$Role = "admin",
  [string]$TenantId,
  [switch]$AddToExisting,
  [string]$Email, [string]$FirstName, [string]$LastName,
  [string]$KcAdminUser = "admin", [string]$KcAdminPass = "admin",
  [string]$Kc = "http://localhost:8080", [string]$Gateway = "http://localhost:8090"
)
$ErrorActionPreference = "Stop"
$realm = "tender_track"; $client = "tendertrack-web"

function Step($m) { Write-Host ""; Write-Host "== $m" -ForegroundColor Cyan }
function Fail($m) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 }
function Warn($m) { Write-Host "WARNING: $m" -ForegroundColor Yellow }
function Err($e) { if ($e.ErrorDetails -and $e.ErrorDetails.Message) { $e.ErrorDetails.Message } else { $e.Exception.Message } }
function Get-AdminToken {
  (Invoke-RestMethod -Method Post -Uri "$Kc/realms/master/protocol/openid-connect/token" `
    -Body @{ client_id = "admin-cli"; grant_type = "password"; username = $KcAdminUser; password = $KcAdminPass }).access_token
}
function AuthHeaders { @{ Authorization = "Bearer " + (Get-AdminToken) } }

# ---- inputs ----
if ($AddToExisting) {
  if (-not $TenantId) { Fail "With -AddToExisting you must pass -TenantId <uuid of the organization>." }
} else {
  if (-not $OrgName) { Fail "Pass -OrgName ""Your company name"" to create a new organization." }
  if (-not $TenantId) { $TenantId = [guid]::NewGuid().ToString() }
}
try { $TenantId = [guid]::Parse($TenantId).ToString() } catch { Fail "TenantId '$TenantId' is not a valid UUID." }
if (-not $Email) { $Email = "$User@example.com" }
if (-not $FirstName) { $FirstName = $User }
if (-not $LastName) { $LastName = "User" }

Step "1. Creating Keycloak user '$User' (role: $Role) in realm '$realm'"
try { Get-AdminToken | Out-Null } catch { Fail "Cannot log in to Keycloak as '$KcAdminUser': $(Err $_)" }
$body = @{
  username = $User; enabled = $true; emailVerified = $true; email = $Email; firstName = $FirstName; lastName = $LastName
  attributes = @{ tenant_id = @($TenantId) }
  credentials = @(@{ type = "password"; value = $Password; temporary = $false })
} | ConvertTo-Json -Depth 6
try {
  Invoke-RestMethod -Method Post -Headers (AuthHeaders) -ContentType "application/json" -Uri "$Kc/admin/realms/$realm/users" -Body $body | Out-Null
} catch {
  $code = 0; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
  if ($code -eq 409) { Fail "A user named '$User' already exists. Choose another username." }
  Fail "Could not create the user (HTTP $code): $(Err $_)"
}
$newUser = (Invoke-RestMethod -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm/users?username=$User&exact=true")[0]
$roleRep = Invoke-RestMethod -Headers (AuthHeaders) -Uri "$Kc/admin/realms/$realm/roles/$Role"
Invoke-RestMethod -Method Post -Headers (AuthHeaders) -ContentType "application/json" `
  -Uri "$Kc/admin/realms/$realm/users/$($newUser.id)/role-mappings/realm" -Body (ConvertTo-Json -InputObject @($roleRep) -Depth 5) | Out-Null
Write-Host "User created with role '$Role' and tenant_id $TenantId"

Step "2. Testing the new login"
try {
  $r = Invoke-RestMethod -Method Post -Uri "$Kc/realms/$realm/protocol/openid-connect/token" `
    -Body @{ client_id = $client; grant_type = "password"; username = $User; password = $Password }
} catch { Fail "The new user cannot log in: $(Err $_)" }
$p = $r.access_token.Split(".")[1].Replace("-", "+").Replace("_", "/"); while ($p.Length % 4) { $p += "=" }
$claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
if ($claims.tenant_id -ne $TenantId) { Fail "Login works but the token's tenant_id is '$($claims.tenant_id)'. Re-run .\infra\setup-keycloak.ps1 to enable custom attributes." }
Write-Host "Login OK. Roles: $($claims.realm_access.roles -join ', ')" -ForegroundColor Green

if (-not $AddToExisting) {
  Step "3. Setting up the organization in the app (needs the gateway and services running)"
  $ah = @{ Authorization = "Bearer $($r.access_token)" }
  try {
    Invoke-RestMethod -Method Put -Headers $ah -ContentType "application/json" -Uri "$Gateway/api/tenant/settings" `
      -Body (@{ displayName = $OrgName; timeZone = "UTC"; queues = @("General"); sites = @() } | ConvertTo-Json) | Out-Null
    Write-Host "Display name set to '$OrgName'"
  } catch { Warn "Could not set the display name ($(Err $_)). Do it later under Settings." }
  try {
    Invoke-RestMethod -Method Post -Headers $ah -Uri "$Gateway/api/flow/templates/tender/install" | Out-Null
    Write-Host "Tender tracking installed"
  } catch { Warn "Could not install the tender template ($(Err $_)). Do it later under Settings." }
}

Write-Host ""
Write-Host "Organization tenant id: $TenantId" -ForegroundColor Green
Write-Host "Sign in at http://localhost:5173 as $User. Keep the tenant id: you need it to add more users to this organization."
