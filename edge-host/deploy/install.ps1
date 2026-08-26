#Requires -RunAsAdministrator
<#
    Hokeka Edge Engine - Windows installer.

    Installs prerequisites and brings up the edge + its feature store as an isolated container stack,
    with the database reachable ONLY by the edge app (private container network, no published port).

    Aerospike has no native Windows build, so on Windows the database runs in a container via Docker
    Desktop / WSL2 - this script handles that. For a native Linux install use install.sh --native.

    It also generates the TLS keystore the transaction API terminates on - the edge refuses to start
    without one and never falls back to plain HTTP.

    Usage:
      .\install.ps1 -PspId acme -EdgeId acme-eu-1 -ControlPlane https://edge.hokeka.com -EnrollmentCode ABCD-1234
#>
param(
    [string]$EdgeImage      = "registry.hokeka.com/hokeka/edge-engine:0.1.0",
    [string]$Bind           = "127.0.0.1",
    [string]$PspId          = "acme",
    [string]$EdgeId         = "acme-1",
    [string]$ControlPlane   = "https://edge.hokeka.com",
    [string]$AllowSubnet    = "",
    [string]$EnrollmentCode = "",
    [ValidateSet("none","want","need")]
    [string]$TlsClientAuth  = "none"
)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Log($m)  { Write-Host "[hokeka] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  [OK] $m"   -ForegroundColor Green }
function Warn($m) { Write-Host "  [!] $m"    -ForegroundColor Yellow }
function Die($m)  { Write-Host "[error] $m"  -ForegroundColor Red; exit 1 }

# --- ensure a container runtime -------------------------------------------------------------------
function Ensure-Docker {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        try { docker version --format '{{.Server.Version}}' 1>$null 2>$null; Ok "Docker present and running"; return }
        catch { Warn "Docker installed but the daemon is not running - start Docker Desktop, then re-run." ; Die "Docker daemon not available" }
    }
    Log "Docker not found - attempting install via winget (Docker Desktop)..."
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements
        Warn "Docker Desktop installed. It requires WSL2 and a sign-in/reboot on first launch."
        Warn "Start Docker Desktop, wait until it reports 'running', then re-run this script."
        Die "Re-run after Docker Desktop is running."
    } else {
        Die "winget not available. Install Docker Desktop manually (docker.com/products/docker-desktop), then re-run."
    }
}

# --- directories with restricted ACLs (config/secrets readable by SYSTEM + Administrators only) ----
function Setup-Dirs {
    foreach ($d in @("config","secrets","data")) {
        $p = Join-Path $Here $d
        New-Item -ItemType Directory -Force -Path $p | Out-Null
    }
    foreach ($d in @("config","secrets")) {
        $p = Join-Path $Here $d
        # Remove inherited perms; grant only SYSTEM and Administrators full control.
        icacls $p /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "Administrators:(OI)(CI)F" | Out-Null
    }
    Ok "Created config/secrets/data with restricted ACLs"
}

# --- TLS material for the transaction API ---------------------------------------------------------
# The edge terminates TLS itself and REFUSES to start without a keystore. Generate a self-signed
# PKCS#12 so a fresh install is encrypted from the first request; replace it with a CA-issued cert
# before going live (see the summary printed at the end).
function New-RandomPassword {
    $chars = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    -join (1..32 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

function Ensure-TlsCert {
    $secrets  = Join-Path $Here "secrets"
    $store    = Join-Path $secrets "edge-tls.p12"
    $passFile = Join-Path $secrets "edge-tls.pass"

    if ((Test-Path $store) -and (Test-Path $passFile)) {
        $script:TlsPassword = (Get-Content $passFile -Raw).Trim()
        Ok "TLS keystore already present - leaving it untouched"
        return
    }

    $script:TlsPassword = New-RandomPassword
    $dname = "CN=hokeka-edge,O=Hokeka Edge,OU=$PspId"
    $san   = "SAN=dns:localhost,ip:127.0.0.1"
    if ($Bind -ne "127.0.0.1") { $san = "$san,ip:$Bind" }

    Log "Generating a self-signed TLS keystore for the transaction API..."
    $keytool = $null
    if (Get-Command keytool -ErrorAction SilentlyContinue) {
        $keytool = (Get-Command keytool).Source
    } elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) {
        $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    }

    if ($keytool) {
        & $keytool -genkeypair -alias edge -keyalg EC -groupname secp384r1 -sigalg SHA384withECDSA `
            -validity 825 -storetype PKCS12 -keystore $store -storepass $script:TlsPassword `
            -keypass $script:TlsPassword -dname $dname -ext $san
    } else {
        # No JDK on the host - borrow keytool from the edge image itself.
        docker run --rm --entrypoint keytool -v "${secrets}:/secrets" $EdgeImage `
            -genkeypair -alias edge -keyalg EC -groupname secp384r1 -sigalg SHA384withECDSA `
            -validity 825 -storetype PKCS12 -keystore /secrets/edge-tls.p12 `
            -storepass $script:TlsPassword -keypass $script:TlsPassword -dname $dname -ext $san
    }
    if (-not (Test-Path $store)) { Die "Could not generate the TLS keystore at $store" }

    Set-Content -Path $passFile -Value $script:TlsPassword -Encoding ascii -NoNewline
    icacls $store    /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" | Out-Null
    icacls $passFile /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" | Out-Null
    Ok "TLS keystore created (self-signed, P-384, 825 days) with restricted ACLs"
}

function Write-Env {
    $env = @"
EDGE_IMAGE=$EdgeImage
EDGE_BIND=$Bind
EDGE_PSPID=$PspId
EDGE_EDGEID=$EdgeId
CONTROLPLANE_URL=$ControlPlane
AEROSPIKE_TAG=6.4
# TLS on the transaction API - the edge refuses to start without these.
EDGE_TLS_KEYSTORE_PASSWORD=$script:TlsPassword
EDGE_TLS_KEY_ALIAS=edge
EDGE_TLS_CLIENT_AUTH=$TlsClientAuth
# First-boot activation + pinned control-plane keys (from portal.hokeka.com).
EDGE_ENROLLMENT_CODE=$EnrollmentCode
CONTROLPLANE_ED25519_PUBLIC_KEY=
CONTROLPLANE_X25519_PUBLIC_KEY=
# mTLS client identity issued to this edge (drop the PKCS#12 into secrets\ and set the password).
EDGE_MTLS_KEYSTORE=/opt/hokeka/secrets/edge-client.p12
EDGE_MTLS_KEYSTORE_PASSWORD=
# Optional: custom trust anchors for the control plane. Empty = the JDK default trust store.
EDGE_MTLS_TRUSTSTORE=
EDGE_MTLS_TRUSTSTORE_PASSWORD=
"@
    $envFile = Join-Path $Here ".env"
    Set-Content -Path $envFile -Value $env -Encoding utf8
    icacls $envFile /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" | Out-Null
    Ok "Wrote .env with restricted ACLs"
}

function Firewall-Api {
    if ([string]::IsNullOrWhiteSpace($AllowSubnet)) {
        Warn "API bound to ${Bind}:8443. Pass -AllowSubnet <cidr> to add a Windows Firewall rule."
        return
    }
    New-NetFirewallRule -DisplayName "Hokeka Edge 8443 ($AllowSubnet)" -Direction Inbound -Protocol TCP `
        -LocalPort 8443 -RemoteAddress $AllowSubnet -Action Allow | Out-Null
    New-NetFirewallRule -DisplayName "Hokeka Edge 8443 deny-other" -Direction Inbound -Protocol TCP `
        -LocalPort 8443 -Action Block | Out-Null
    Ok "Windows Firewall: 8443 limited to $AllowSubnet"
}

function Compose-Up {
    Log "Starting the isolated stack (Aerospike has NO published port - reachable only by the edge)..."
    Push-Location $Here
    try { docker compose up -d } finally { Pop-Location }
}

function Health-Check {
    Log "Waiting for health..."
    for ($i = 0; $i -lt 30; $i++) {
        try {
            docker exec hokeka-edge curl -fsk https://localhost:8443/actuator/health 1>$null 2>$null
            if ($LASTEXITCODE -eq 0) { Ok "edge is HEALTHY"; return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    Warn "edge not healthy yet - check: docker compose logs edge"
}

function Summary {
    Write-Host ""
    Log "---------------- Installation summary (Windows/container) ----------------"
    Ok  "Edge transaction API : https://${Bind}:8443  (PSP API nodes only)"
    Ok  "Database access      : Aerospike is reachable ONLY by the edge app"
    Write-Host "                         (private container network, no published port)"
    Ok  "Control plane        : $ControlPlane  (outbound only, mTLS + HSE-1)"
    Ok  "Transport security   : TLS 1.3 only, self-signed keystore at secrets\edge-tls.p12"
    Write-Host ""
    Warn "The generated certificate is SELF-SIGNED. Before production, replace it with one from your"
    Write-Host "      own CA (same alias 'edge'), keeping the same path and updating EDGE_TLS_KEYSTORE_PASSWORD:"
    Write-Host "        keytool -importkeystore -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 ``"
    Write-Host "                -destkeystore secrets\edge-tls.p12 -deststoretype PKCS12 -alias edge"
    Write-Host "      The edge refuses to start if the keystore is missing - it never serves plain HTTP."
    Write-Host ""
    if ([string]::IsNullOrWhiteSpace($EnrollmentCode)) {
        Warn "No -EnrollmentCode given: this node stays UNAUTHORIZED and will only return HOLD."
    }
    Log "Next: request an enrollment code at portal.hokeka.com, set EDGE_ENROLLMENT_CODE plus the pinned"
    Log "      CONTROLPLANE_*_PUBLIC_KEY values in .env, then approve the node in the portal."
    Log "Manage: cd `"$Here`"; docker compose ps | logs | down"
}

# --- Rust native core -----------------------------------------------------------------------------
# System.loadLibrary("edge_engine") resolves the platform filename automatically, so on Windows the
# JVM looks for edge_engine.dll (libedge_engine.so on Linux). In container mode the library already
# ships inside the image; this staging matters when running the host directly on Windows.
# Optional: without it the edge runs on its Java interpreter fallback - functionally equivalent,
# just slower.
function Stage-NativeCore {
    $lib = "edge_engine.dll"
    $libDir = Join-Path $Here "lib"
    New-Item -ItemType Directory -Force -Path $libDir | Out-Null
    $src = Join-Path $Here $lib
    if (Test-Path $src) {
        Copy-Item $src (Join-Path $libDir $lib) -Force
        Ok "Staged the native core: $libDir\$lib"
    } else {
        Warn "$lib not found next to the installer - the edge will use the Java interpreter fallback."
        Warn "Download it from the CI artifact 'edge_engine-windows-x86_64' and place it in $libDir."
    }
}

# --- run ------------------------------------------------------------------------------------------
$script:TlsPassword = ""
Ensure-Docker
Setup-Dirs
Stage-NativeCore
Ensure-TlsCert
Write-Env
Compose-Up
Firewall-Api
Health-Check
Summary
