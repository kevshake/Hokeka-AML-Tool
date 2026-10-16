#Requires -Version 5.1
<#
    Hokeka first-run bootstrap for Windows (Edge container path, Console dev tooling).

    Usage:
      .\scripts\install\hokeka-install.ps1 -Role edge|console|control-plane -Check
      .\scripts\install\hokeka-install.ps1 -Role edge -PspId 42 -EdgeId acme-1 -ControlPlane https://edge.hokeka.com

    Control Plane production stacks are Linux/Docker-first; on Windows this script validates Docker
    and delegates Edge installs to edge-host\deploy\install.ps1.
#>
param(
    [ValidateSet("control-plane", "edge", "console")]
    [string]$Role = "edge",
    [switch]$Check,
    [switch]$DryRun,
    [switch]$Yes,
    [switch]$BuildRust,
    [string]$InstallDir = "",
    [string]$PspId = "acme",
    [string]$EdgeId = "acme-1",
    [string]$ControlPlane = "https://edge.hokeka.com",
    [string]$EnrollmentCode = ""
)

$ErrorActionPreference = "Stop"
$script:CheckOnly = [bool]$Check
$script:DryRun = [bool]$DryRun
$script:Missing = @()

$Lib = Join-Path $PSScriptRoot "lib/Common.ps1"
. $Lib

$RepoRoot = Get-HokekaRepoRoot

# Confirm install when interactive and -Yes was not passed.
function Confirm-HokekaInstall {
    if ($script:CheckOnly -or $script:DryRun -or $Yes) { return }
    Write-HokekaWarn "This will install prerequisites for role: $Role"
    $ans = Read-Host "Continue? [y/N]"
    if ($ans -notmatch '^(y|yes)$') { Stop-HokekaInstall "Aborted by operator" }
}

# Audit or install Console developer dependencies on Windows.
function Invoke-HokekaConsoleRole {
    Install-HokekaNodeLts
    Install-HokekaDockerDesktop
    if ($script:CheckOnly) {
        if ($script:Missing.Count -gt 0) {
            Write-HokekaWarn "Missing: $($script:Missing -join ', ')"
            exit 1
        }
        Write-HokekaOk "Console prerequisites satisfied"
        return
    }
    $frontend = Join-Path $RepoRoot "FRONTEND"
    if (-not $script:DryRun -and (Test-Path (Join-Path $frontend "package.json"))) {
        Write-HokekaLog "Running npm ci in FRONTEND…"
        Push-Location $frontend
        try { npm ci } finally { Pop-Location }
        Write-HokekaLog "Installing Playwright chromium…"
        Push-Location $frontend
        try { npx playwright install chromium } finally { Pop-Location }
    }
    Write-HokekaOk "Console bootstrap complete — cd FRONTEND; npm run dev"
}

# Audit or install Edge via the canonical edge-host deploy script.
function Invoke-HokekaEdgeRole {
    Install-HokekaDockerDesktop
    if ($BuildRust) {
        Install-HokekaRustToolchain -RepoRoot $RepoRoot
        if (-not $script:CheckOnly -and -not $script:DryRun) {
            Push-Location (Join-Path $RepoRoot "edge-engine")
            try { cargo build --release -p edge-jni } finally { Pop-Location }
        }
    }
    if ($script:CheckOnly) {
        if ($script:Missing.Count -gt 0) {
            Write-HokekaWarn "Missing: $($script:Missing -join ', ')"
            exit 1
        }
        Write-HokekaOk "Edge prerequisites satisfied"
        return
    }
    $edgeInstaller = Join-Path $RepoRoot "edge-host/deploy/install.ps1"
    if (-not (Test-Path $edgeInstaller)) { Stop-HokekaInstall "Missing $edgeInstaller" }
    Write-HokekaLog "Delegating to edge-host/deploy/install.ps1…"
    & $edgeInstaller -PspId $PspId -EdgeId $EdgeId -ControlPlane $ControlPlane -EnrollmentCode $EnrollmentCode
}

# Control Plane on Windows: guide operators to Linux VPS bootstrap.
function Invoke-HokekaControlPlaneRole {
    Install-HokekaDockerDesktop
    Install-HokekaNodeLts
    if ($script:CheckOnly) {
        if ($script:Missing.Count -gt 0) {
            Write-HokekaWarn "Missing: $($script:Missing -join ', ')"
            exit 1
        }
        Write-HokekaOk "Docker/Node present — run scripts/install/hokeka-install.sh --role control-plane on Linux VPS"
        return
    }
    Write-HokekaWarn "Production Control Plane must run on Linux (Hostinger VPS)."
    Write-HokekaLog "Copy the repo to the server and run: sudo ./scripts/install/hokeka-install.sh --role control-plane --yes"
    Write-HokekaLog "See docs/install/03-control-plane.md"
}

Confirm-HokekaInstall
switch ($Role) {
    "console" { Invoke-HokekaConsoleRole }
    "edge" { Invoke-HokekaEdgeRole }
    "control-plane" { Invoke-HokekaControlPlaneRole }
    default { Stop-HokekaInstall "Unknown role: $Role" }
}
