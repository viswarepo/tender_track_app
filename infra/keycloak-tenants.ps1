# Run AFTER importing infra/carelane-realm-lite.json. Windows PowerShell.
# 1) lets the realm store a custom "tenant_id" attribute (editable by admins only)
# 2) sets tenant_id on the four demo users
# Change $adminUser / $adminPass if yours differ. Keycloak must be running on http://localhost:8080.
$kc = "http://localhost:8080"; $realm = "tendertrack"
$adminUser = "admin"; $adminPass = "admin"
$ErrorActionPreference = "Stop"

$tok = (Invoke-RestMethod -Method Post -Uri "$kc/realms/master/protocol/openid-connect/token" `
  -Body @{ client_id = "admin-cli"; grant_type = "password"; username = $adminUser; password = $adminPass }).access_token
$h = @{ Authorization = "Bearer $tok" }

# 1) allow custom attributes
$profile = Invoke-RestMethod -Headers $h -Uri "$kc/admin/realms/$realm/users/profile"
$profile | Add-Member -NotePropertyName unmanagedAttributePolicy -NotePropertyValue "ADMIN_EDIT" -Force
Invoke-RestMethod -Method Put -Headers $h -ContentType "application/json" `
  -Uri "$kc/admin/realms/$realm/users/profile" -Body ($profile | ConvertTo-Json -Depth 20) | Out-Null
Write-Host "Custom attributes enabled."

# 2) tenant ids (same UUID = same organization)
$northside = "11111111-1111-1111-1111-111111111111"
$riverbend = "22222222-2222-2222-2222-222222222222"
$tenants = @{ viswa = $northside; marcus = $northside; okafor = $northside; elena = $riverbend }

foreach ($name in $tenants.Keys) {
  $found = Invoke-RestMethod -Headers $h -Uri "$kc/admin/realms/$realm/users?username=$name&exact=true"
  if (-not $found) { Write-Host "User $name not found, skipping."; continue }
  $user = $found[0]
  $user | Add-Member -NotePropertyName attributes -NotePropertyValue @{ tenant_id = @($tenants[$name]) } -Force
  Invoke-RestMethod -Method Put -Headers $h -ContentType "application/json" `
    -Uri "$kc/admin/realms/$realm/users/$($user.id)" -Body ($user | ConvertTo-Json -Depth 10) | Out-Null
  Write-Host "Set tenant_id for $name"
}
Write-Host "Done. Sign in at http://localhost:5173"
