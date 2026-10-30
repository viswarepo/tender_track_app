# Makes the login token carry tenant_id. Windows PowerShell.
#   powershell -ExecutionPolicy Bypass -File .\infra\fix-tenant-claim.ps1
# It (1) allows custom user attributes, (2) sets tenant_id on the demo users,
# (3) adds or repairs the tenant_id mapper on the client, (4) logs in and shows the result.
param(
  [string]$VerifyUser = "viswa", [string]$Password = "Viswa@987",
  [string]$Realm = "tender_track", [string]$Client = "tendertrack-web",
  [string]$Kc = "http://localhost:8080", [string]$Gateway = "http://localhost:8090",
  [string]$KcAdminUser = "admin", [string]$KcAdminPass = "admin"
)
$ErrorActionPreference = "Stop"
$orgA = "11111111-1111-1111-1111-111111111111"; $orgB = "22222222-2222-2222-2222-222222222222"
$tenants = @{ viswa = $orgA; marcus = $orgA; elena = $orgB }

function Step($m) { Write-Host ""; Write-Host "== $m" -ForegroundColor Cyan }
function Fail($m) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 }
function Err($e) {
  if ($e.ErrorDetails -and $e.ErrorDetails.Message) { return $e.ErrorDetails.Message }
  return $e.Exception.Message
}
function AdminToken {
  (Invoke-RestMethod -Method Post -Uri "$Kc/realms/master/protocol/openid-connect/token" `
    -Body @{ client_id = "admin-cli"; grant_type = "password"; username = $KcAdminUser; password = $KcAdminPass }).access_token
}
function AuthHdr { @{ Authorization = "Bearer " + (AdminToken) } }

Step "1. Logging in to Keycloak admin"
try { AdminToken | Out-Null } catch { Fail "Cannot log in as '$KcAdminUser': $(Err $_). Pass -KcAdminUser and -KcAdminPass if yours differ." }
Write-Host "OK"

Step "2. Allowing custom user attributes in realm '$Realm'"
try {
  $profile = Invoke-RestMethod -Headers (AuthHdr) -Uri "$Kc/admin/realms/$Realm/users/profile"
  $profile | Add-Member -NotePropertyName unmanagedAttributePolicy -NotePropertyValue "ADMIN_EDIT" -Force
  Invoke-RestMethod -Method Put -Headers (AuthHdr) -ContentType "application/json" -Uri "$Kc/admin/realms/$Realm/users/profile" -Body ($profile | ConvertTo-Json -Depth 20) | Out-Null
  Write-Host "Custom attributes enabled"
} catch { Write-Host "WARNING: could not change the user profile ($(Err $_)). Continuing." -ForegroundColor Yellow }

Step "3. Setting tenant_id on the users"
foreach ($name in $tenants.Keys) {
  $found = @(Invoke-RestMethod -Headers (AuthHdr) -Uri "$Kc/admin/realms/$Realm/users?username=$name&exact=true")
  if ($found.Count -eq 0) { Write-Host "User '$name' not found, skipping."; continue }
  $user = $found[0]
  $now = ""; if ($user.attributes -and $user.attributes.tenant_id) { $now = ($user.attributes.tenant_id -join ",") }
  $user | Add-Member -NotePropertyName attributes -NotePropertyValue @{ tenant_id = @($tenants[$name]) } -Force
  Invoke-RestMethod -Method Put -Headers (AuthHdr) -ContentType "application/json" -Uri "$Kc/admin/realms/$Realm/users/$($user.id)" -Body ($user | ConvertTo-Json -Depth 10) | Out-Null
  Write-Host ("{0,-8} tenant_id was [{1}], now {2}" -f $name, $now, $tenants[$name])
}

Step "4. Checking the tenant_id mapper on client '$Client'"
$cl = @(Invoke-RestMethod -Headers (AuthHdr) -Uri "$Kc/admin/realms/$Realm/clients?clientId=$Client")
if ($cl.Count -eq 0) { Fail "Client '$Client' does not exist in realm '$Realm'. Run .\infra\setup-keycloak.ps1 to create it." }
$cid = $cl[0].id
$maps = @(Invoke-RestMethod -Headers (AuthHdr) -Uri "$Kc/admin/realms/$Realm/clients/$cid/protocol-mappers/models")
Write-Host ("Mappers found: " + ($(if ($maps.Count) { ($maps | ForEach-Object { $_.name }) -join ", " } else { "(none)" })))
$tm = $maps | Where-Object { $_.config.'claim.name' -eq "tenant_id" } | Select-Object -First 1
$cfg = @{ "user.attribute" = "tenant_id"; "claim.name" = "tenant_id"; "jsonType.label" = "String"; "access.token.claim" = "true"; "id.token.claim" = "true" }
$base = "$Kc/admin/realms/$Realm/clients/$cid/protocol-mappers/models"
if (-not $tm) {
  $body = @{ name = "tenant_id"; protocol = "openid-connect"; protocolMapper = "oidc-usermodel-attribute-mapper"; config = $cfg } | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Method Post -Headers (AuthHdr) -ContentType "application/json" -Uri $base -Body $body | Out-Null
  Write-Host "Mapper was missing: added 'tenant_id'" -ForegroundColor Green
} elseif ($tm.config.'access.token.claim' -ne "true" -or $tm.config.'user.attribute' -ne "tenant_id") {
  $body = @{ id = $tm.id; name = $tm.name; protocol = "openid-connect"; protocolMapper = "oidc-usermodel-attribute-mapper"; config = $cfg } | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Method Put -Headers (AuthHdr) -ContentType "application/json" -Uri "$base/$($tm.id)" -Body $body | Out-Null
  Write-Host "Mapper '$($tm.name)' existed but was not adding tenant_id to the access token: repaired" -ForegroundColor Green
} else { Write-Host "Mapper '$($tm.name)' is correct" }

Step "5. Logging in as '$VerifyUser' and reading the token"
try {
  $r = Invoke-RestMethod -Method Post -Uri "$Kc/realms/$Realm/protocol/openid-connect/token" -Body @{ client_id = $Client; grant_type = "password"; username = $VerifyUser; password = $Password }
} catch { Fail "Login failed: $(Err $_)" }
$p = $r.access_token.Split(".")[1].Replace("-", "+").Replace("_", "/"); while ($p.Length % 4) { $p += "=" }
$claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
Write-Host "tenant_id: $($claims.tenant_id)"
Write-Host "roles:     $($claims.realm_access.roles -join ', ')"
if (-not $claims.tenant_id) { Fail "The token still has no tenant_id. Send me the output above." }
Write-Host "Token carries tenant_id." -ForegroundColor Green

Step "6. Asking the services"
$hd = @{ Authorization = "Bearer $($r.access_token)" }
foreach ($target in @(@("flow-service direct", "http://127.0.0.1:8087/api/flow/types"), @("through the gateway", "$Gateway/api/flow/types"))) {
  try { Invoke-RestMethod -Headers $hd -Uri $target[1] | Out-Null; Write-Host ("{0,-22} 200 OK" -f $target[0]) -ForegroundColor Green }
  catch { $code = 0; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }; Write-Host ("{0,-22} HTTP {1} {2}" -f $target[0], $code, (Err $_)) -ForegroundColor Yellow }
}
Write-Host ""
Write-Host "Done. In the app: SIGN OUT, then sign in again (the old token has no tenant), then click Install tender tracking." -ForegroundColor Green
