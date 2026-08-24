# ============================================================================
#  DSH Remote Access over Tailscale — one-time setup for the "Harness" app
# ----------------------------------------------------------------------------
#  What it does (on this PC, the one running DeepSeek Harness):
#    1. Binds the DSH web GUI to 0.0.0.0 (via the web profile patch) so the
#       Tailnet peer (your phone) can reach it.
#    2. Adds a Windows Firewall rule allowing TCP 3080 ONLY from the Tailnet
#       range (100.64.0.0/10).
#    3. Prints the Tailnet address to type into the Harness app.
#  Run once as Administrator (the script re-launches elevated if needed).
# ============================================================================

$ErrorActionPreference = 'Stop'

# --- Self-elevate to Administrator (needed for the firewall rule) ----------
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Relaunching as administrator (UAC)..." -ForegroundColor Yellow
    Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}

Write-Host ""
Write-Host "=== DSH Remote Access setup (Harness app) ===" -ForegroundColor Cyan
Write-Host ""

# --- 1. Find DSH_HOME -------------------------------------------------------
$dshHome = $env:DSH_HOME
if (-not $dshHome) { $dshHome = Join-Path $env:USERPROFILE ".dsh" }
if (-not (Test-Path $dshHome)) {
    Write-Host "[ERROR] Could not find DSH at '$dshHome'." -ForegroundColor Red
    Write-Host "Install DeepSeek Harness first, or set the DSH_HOME variable." -ForegroundColor Yellow
    exit 1
}
Write-Host "DSH_HOME: $dshHome"

# --- 2. Web profile patch: bind host 0.0.0.0 --------------------------------
$profileDir = Join-Path $dshHome "profiles\web"
$patchFile  = Join-Path $profileDir "cordis.patch.yml"
if (-not (Test-Path $profileDir)) {
    Write-Host "[ERROR] Could not find the 'web' profile at '$profileDir'." -ForegroundColor Red
    Write-Host "Use the 'web' profile (dsh web / ollama launch dsh)." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $patchFile)) {
    Set-Content -Path $patchFile -Value "# Remote access (Harness app)" -Encoding utf8
}

$patchText = Get-Content $patchFile -Raw
if ($patchText -match 'host:\s*''0\.0\.0\.0''') {
    Write-Host "[ok] The web profile already has host 0.0.0.0" -ForegroundColor Green
} else {
    $append = @"

# --- Remote access for the Harness app (added by setup-dsh-remote.ps1) ---
- id: webserver
  config:
    host: '0.0.0.0'
    port: 3080
"@
    Add-Content -Path $patchFile -Value $append -Encoding utf8
    Write-Host "[ok] Added host 0.0.0.0 to the web profile" -ForegroundColor Green
}

# --- 3. Windows Firewall rule (Tailnet only) --------------------------------
$ruleName = "DSH Web tailnet"
$found = netsh advfirewall firewall show rule name="$ruleName" 2>$null | Select-String -SimpleMatch $ruleName
if ($found) {
    Write-Host "[ok] Firewall rule already exists" -ForegroundColor Green
} else {
    Write-Host "Adding firewall rule (allows 3080 only from the tailnet)..." -ForegroundColor Yellow
    netsh advfirewall firewall add rule name="$ruleName" dir=in action=allow protocol=TCP localport=3080 remoteip=100.64.0.0/10
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[ok] Firewall rule added" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Could not add the firewall rule." -ForegroundColor Red
    }
}

# --- 4. Print the Tailnet address for the app -------------------------------
Write-Host ""
Write-Host "=== Address for the Harness app ===" -ForegroundColor Cyan
$ip = (tailscale ip -4 2>$null | Select-Object -First 1)
$hostname = $null
try { $hostname = ((tailscale status --json 2>$null | ConvertFrom-Json).Self.DNSName).TrimEnd('.') } catch {}

if ($ip)      { Write-Host "  IP   :  http://$ip`:3080" }
if ($hostname){ Write-Host "  Host :  http://$hostname`:3080" }
if (-not $ip -and -not $hostname) {
    Write-Host "  Could not get a Tailscale address. Make sure Tailscale is connected." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== OPTIONAL: tailscale serve (HTTPS, port 443) ===" -ForegroundColor Cyan
Write-Host "  (Not needed if you use the method above; it is a more secure option.)"
$serveStatus = tailscale serve status 2>&1 | Out-String
if ($serveStatus -match 'Serve is not enabled') {
    Write-Host "  Serve is not enabled on your tailnet. Enable it once (in a browser):" -ForegroundColor Yellow
    Write-Host "    https://login.tailscale.com/f/serve" -ForegroundColor White
    Write-Host "  Then re-run this script (or run: tailscale serve --bg 3080)." -ForegroundColor Yellow
} else {
    Write-Host "  Enabling serve for DSH (HTTPS on 443)..." -ForegroundColor Yellow
    tailscale serve --bg 3080 2>&1 | Out-Null
    if ($hostname) { Write-Host "  HTTPS: https://$hostname" }
    else { Write-Host "  HTTPS: https://<hostname>.ts.net" }
    Write-Host "  NOTE: the phone must trust the tailnet certificate (Tailscale app)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Restart DSH:  Ctrl+C in its terminal, then  'ollama launch dsh'  (or  'dsh web')"
Write-Host "  2. Install the Harness app on your phone (see README)."
Write-Host "  3. In the app, enter one of the addresses above and tap 'Connect'."
Write-Host ""
