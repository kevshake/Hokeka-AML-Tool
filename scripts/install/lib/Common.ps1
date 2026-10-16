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

# Resolve the docker.exe path from PATH or standard Docker Desktop install locations.
function Get-HokekaDockerCommand {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path ${env:ProgramFiles} "Docker\Docker\resources\bin\docker.exe")
        (Join-Path ${env:ProgramFiles} "Docker\Docker\resources\docker.exe")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

# Ensure Docker Desktop CLI directories are on PATH for this PowerShell session (pwsh often omits them).
function Add-HokekaDockerToPath {
    $binDir = Join-Path ${env:ProgramFiles} "Docker\Docker\resources\bin"
    if (-not (Test-Path -LiteralPath $binDir)) { return $false }
    $pathParts = $env:Path -split ';'
    if ($pathParts -contains $binDir) { return $false }
    $env:Path = "$binDir;$env:Path"
    Write-HokekaLog "Prepended Docker CLI to PATH for this session: $binDir"
    return $true
}

# Return $true when Docker CLI is installed and the daemon responds (same probe as Linux docker info).
function Test-HokekaDockerReady {
    $dockerExe = Get-HokekaDockerCommand
    if (-not $dockerExe) { return $false }
    & $dockerExe info 1>$null 2>$null
    return $LASTEXITCODE -eq 0
}

# Try to start Docker-related Windows services when the CLI is present but the daemon is still down.
function Start-HokekaDockerDaemonIfInstalled {
    foreach ($serviceName in @('com.docker.service', 'docker')) {
        $svc = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
        if (-not $svc) { continue }
        if ($svc.Status -eq 'Running') { continue }
        Write-HokekaLog "Starting Windows service '$serviceName' (Docker engine)…"
        try {
            Start-Service -Name $serviceName -ErrorAction Stop
        } catch {
            Write-HokekaWarn "Could not start service '$serviceName': $($_.Exception.Message)"
        }
    }
}

# Wait up to MaxWaitSeconds for Docker CLI and daemon readiness, with bounded retries and clear logs.
function Wait-HokekaDockerReady {
    param(
        [int]$MaxWaitSeconds = 120
    )
    if ($env:HOKEKA_DOCKER_WAIT_SECONDS -match '^\d+$') {
        $MaxWaitSeconds = [int]$env:HOKEKA_DOCKER_WAIT_SECONDS
    }
    $deadline = (Get-Date).AddSeconds($MaxWaitSeconds)
    $attempt = 0
    $loggedWait = $false
    while ((Get-Date) -lt $deadline) {
        $attempt++
        Add-HokekaDockerToPath | Out-Null
        if (Test-HokekaDockerReady) { return $true }
        if (-not $loggedWait) {
            Write-HokekaLog "Docker not ready yet; waiting up to ${MaxWaitSeconds}s for CLI and daemon (GitHub Actions runners and fresh boots often need a moment)…"
            $loggedWait = $true
        }
        Start-HokekaDockerDaemonIfInstalled
        $sleepSec = [Math]::Min(10, 2 + [Math]::Floor($attempt / 2))
        Start-Sleep -Seconds $sleepSec
    }
    return $false
}

# Log actionable guidance when Docker is still missing after the wait window (never prints secrets).
function Write-HokekaDockerMissingGuidance {
    $cli = Get-HokekaDockerCommand
    if ($cli) {
        Write-HokekaWarn "Docker CLI found at '$cli' but the daemon did not respond in time."
        Write-HokekaWarn "Start Docker Desktop from the Start menu, wait until it reports Running, then re-run."
        Write-HokekaWarn "Or install/repair: winget install -e --id Docker.DockerDesktop (requires WSL2 on Windows)."
    } else {
        Write-HokekaWarn "Docker CLI not on PATH and not under Program Files\Docker\Docker\resources\bin."
        Write-HokekaWarn "Install Docker Desktop: https://docs.docker.com/desktop/setup/install/windows-install/"
        Write-HokekaWarn "Or run: winget install -e --id Docker.DockerDesktop"
    }
}

# Install Docker Desktop via winget when Docker is missing (Windows Edge / Console roles).
function Install-HokekaDockerDesktop {
    if (Wait-HokekaDockerReady) {
        Write-HokekaOk "Docker Desktop running"
        return
    }
    Write-HokekaDockerMissingGuidance
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
