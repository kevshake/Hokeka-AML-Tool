# OpenCode Wrapper — Paperclip Integration

## Overview

`opencode-wrapper.ps1` normalises OpenCode.exe exit code **-1** (SQLite lock / transient failure)
to **1** so the Paperclip adapter treats it as a regular failure, not an adapter-level crash.

## Configuration

### 1. Paperclip Adapter

In the Paperclip adapter config for this agent, replace the raw `OpenCode.exe` command with
the wrapper script. Example (JSON):

```json
{
  "agent_id": "fa26e1d7",
  "command": [
    "powershell.exe",
    "-NoProfile",
    "-File",
    "C:\\Users\\kevsh\\Documents\\AML system\\AML_FRAUD_DETECTOR\\infra\\wrappers\\opencode-wrapper.ps1",
    "-openCodePath",
    "C:\\Path\\To\\opencode.exe",
    "-arguments",
    "@('do', 'task')"
  ]
}
```

### 2. Environment Variables (optional)

| Variable                  | Purpose                         |
|---------------------------|---------------------------------|
| `OPENCODE_WRAPPER_LOG`    | Override log file path (default: `opencode-wrapper.log` in the same directory) |

### 3. Log Rotation

The log file automatically rotates at **5 MB**; the old file is renamed with a
`.yyyyMMddHHmmss.bak` timestamp suffix.

## Testing

```powershell
.\opencode-wrapper.ps1 -openCodePath "cmd.exe" -arguments @("/c", "exit -1")
# Expected: exit code 1
```

## Files

| File                 | Purpose                        |
|----------------------|--------------------------------|
| `opencode-wrapper.ps1` | PowerShell wrapper script     |
| `opencode-wrapper.log` | Rotating log (auto-created)   |
| `README.md`           | This document                 |
