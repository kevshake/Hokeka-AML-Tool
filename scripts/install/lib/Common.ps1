# Shared helpers for hokeka-install.ps1 (logging, retries, detection).

# Write a cyan progress line tagged hokeka-install.
function Write-HokekaLog {
    param([string]$Message)
    Write-Host "[hokeka-install] $Message" -ForegroundColor Cyan
}

# Write a green success line with an OK prefix.
function Write-HokekaOk {
    param([string]$Message)
    Write-Host "  [OK] $Message" -ForegroundColor Green
}

# Write a yellow warning line.
function Write-HokekaWarn {
    param([string]$Message)
    Write-Host "  [!] $Message" -ForegroundColor Yellow
}

# Write a red error line and exit with code 1.
function Stop-HokekaInstall {
    param([string]$Message)
    Write-Host "[error] $Message" -ForegroundColor Red
    exit 1
}

# Resolve repository root from HOKEKA_REPO_ROOT or relative to this script.
function Get-HokekaRepoRoot {
    if ($env:HOKEKA_REPO_ROOT) { return $env:HOKEKA_REPO_ROOT }
    $installDir = Split-Path -Parent $PSScriptRoot
    $root = (Resolve-Path (Join-Path $installDir "../..")).Path
    return $root
}

# Run a script block up to MaxAttempts times with exponential backoff.
function Invoke-HokekaRetry {
    param(
        [int]$MaxAttempts = 5,
        [scriptblock]$Action
    )
    $delay = 2
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            & $Action
            return
        } catch {
            if ($attempt -eq $MaxAttempts) { throw }
            Write-HokekaWarn "Attempt $attempt failed, retrying in ${delay}s…"
            Start-Sleep -Seconds $delay
            $delay = [Math]::Min(60, $delay * 2)
        }
    }
}

# Return $true when Docker CLI is installed and the daemon responds.
function Test-HokekaDockerReady {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    try {
        docker version --format '{{.Server.Version}}' 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

# Install Docker Desktop via winget when Docker is missing (Windows Edge / Console roles).
function Install-HokekaDockerDesktop {
    if (Test-HokekaDockerReady) {
        Write-HokekaOk "Docker Desktop running"
        return
    }
    if ($script:CheckOnly) {
        $script:Missing += @('docker')
        return
    }
    if ($script:DryRun) {
        Write-HokekaLog "[dry-run] would install Docker Desktop via winget"
        return
    }
    Write-HokekaLog "Installing Docker Desktop via winget…"
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Stop-HokekaInstall "winget not available. Install Docker Desktop manually, then re-run."
    }
    winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements
    Write-HokekaWarn "Start Docker Desktop, wait until running, then re-run this installer."
    Stop-HokekaInstall "Docker Desktop requires a reboot or first launch before continuing."
}

# Install Node.js LTS via winget when node is missing or older than v20.
function Install-HokekaNodeLts {
    if (Get-Command node -ErrorAction SilentlyContinue) {
        $major = [int](node -p "process.versions.node.split('.')[0]")
        if ($major -ge 20) {
            Write-HokekaOk "Node.js present ($(node -v))"
            return
        }
    }
    if ($script:CheckOnly) {
        $script:Missing += @('node-lts')
        return
    }
    if ($script:DryRun) {
        Write-HokekaLog "[dry-run] would install Node.js LTS via winget"
        return
    }
    Write-HokekaLog "Installing Node.js LTS via winget…"
    winget install -e --id OpenJS.NodeJS.LTS --accept-source-agreements --accept-package-agreements
    Write-HokekaOk "Node.js installed — open a new shell if node is not on PATH"
}

# Install Visual Studio C++ Build Tools required to compile Rust edge-engine on Windows.
function Install-HokekaVsBuildTools {
    if ($script:CheckOnly) {
        $script:Missing += @('msvc-build-tools')
        return
    }
    if ($script:DryRun) {
        Write-HokekaLog "[dry-run] would install VS C++ Build Tools via winget"
        return
    }
    Write-HokekaLog "Installing Microsoft Visual C++ Build Tools (Rust linker)…"
    winget install -e --id Microsoft.VisualStudio.2022.BuildTools `
        --override "--quiet --wait --add Microsoft.VisualStudio.Workload.VCTools" `
        --accept-source-agreements --accept-package-agreements
    Write-HokekaOk "VS Build Tools installed"
}

# Install the Rust toolchain pin from edge-engine/rust-toolchain.toml using rustup.
function Install-HokekaRustToolchain {
    param([string]$RepoRoot)
    $toolchainFile = Join-Path $RepoRoot "edge-engine/rust-toolchain.toml"
    $pin = "1.85.0"
    if (Test-Path $toolchainFile) {
        $pin = (Select-String -Path $toolchainFile -Pattern '^channel = "(.+)"' | ForEach-Object { $_.Matches.Groups[1].Value } | Select-Object -First 1)
    }
    if (Get-Command rustc -ErrorAction SilentlyContinue) {
        $ver = (rustc --version)
        if ($ver -match [regex]::Escape($pin)) {
            Write-HokekaOk "Rust $pin present"
            return
        }
    }
    if ($script:CheckOnly) {
        $script:Missing += @("rust-$pin")
        return
    }
    if ($script:DryRun) {
        Write-HokekaLog "[dry-run] would install Rust $pin via rustup"
        return
    }
    Install-HokekaVsBuildTools
    Invoke-HokekaRetry -Action {
        Invoke-WebRequest -UseBasicParsing -Uri "https://win.rustup.rs/x86_64" -OutFile "$env:TEMP\rustup-init.exe"
        & "$env:TEMP\rustup-init.exe" -y --default-toolchain $pin --component clippy,rustfmt
    }
    Write-HokekaOk "Rust $pin installed"
}
