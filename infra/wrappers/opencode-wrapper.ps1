<#
.SYNOPSIS
    Wrapper for OpenCode.exe that normalizes exit code -1 to 1.
.DESCRIPTION
    Launches OpenCode.exe as a child process, captures output, normalizes
    negative exit codes to 1, and logs all exit codes with timestamps.
.PARAMETER openCodePath
    Path to OpenCode.exe.
.PARAMETER arguments
    Array of arguments to forward to OpenCode.exe.
.EXAMPLE
    .\opencode-wrapper.ps1 -openCodePath "C:\tools\opencode.exe" -arguments @("do", "task")
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$openCodePath,

    [Parameter(Mandatory = $false)]
    [string[]]$arguments = @()
)

$scriptName = "opencode-wrapper.ps1"
$logDir    = Join-Path -Path $PSScriptRoot -ChildPath "."
$logFile   = Join-Path -Path $logDir -ChildPath "opencode-wrapper.log"

# ----- Log rotation (max 5 MB) -----
if (Test-Path -LiteralPath $logFile) {
    $size = (Get-Item -LiteralPath $logFile).Length
    $max  = 5 * 1024 * 1024   # 5 MB
    if ($size -ge $max) {
        $rotated = "{0}.{1:yyyyMMddHHmmss}.bak" -f $logFile, (Get-Date)
        Move-Item -LiteralPath $logFile -Destination $rotated -Force
    }
}

# ----- Timestamp helper -----
function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "{0} | {1}" -f $timestamp, $Message
    Add-Content -Path $logFile -Value $line -Encoding UTF8
}

Write-Log "Wrapper started | openCodePath='$openCodePath' arguments='$($arguments -join ' ')'"

# ----- Launch child process -----
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName               = $openCodePath
$psi.Arguments              = $arguments -join ' '
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError  = $true
$psi.UseShellExecute        = $false
$psi.CreateNoWindow         = $true

try {
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $null = $proc.Start()

    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()

    $proc.WaitForExit()

    $exitCode = $proc.ExitCode

    # ----- Normalise negative exit codes -----
    $normalized = $exitCode
    if ($exitCode -lt 0) {
        $normalized = 1
        Write-Log "Exit code $exitCode normalized to $normalized"
    }

    Write-Log "Exit code=$exitCode normalized=$normalized"

    # Forward captured output to the caller's streams
    if ($stdout) { Write-Output $stdout }
    if ($stderr) { Write-Error $stderr }

    Write-Log "Wrapper finished | exitCode=$normalized"
    exit $normalized
}
catch {
    Write-Log "Wrapper exception: $_"
    Write-Error "Wrapper exception: $_"
    exit 1
}
