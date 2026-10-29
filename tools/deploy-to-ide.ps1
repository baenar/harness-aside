# Swaps the built plugin into the running JetBrains IDE and restarts it.
# Started detached by the `deployToIde` Gradle task, which defines $Zip and $IdeProcess above this line.
# Everything it does is logged to %TEMP%\harness-aside-deploy.log.
$ErrorActionPreference = 'Stop'
Start-Transcript -Path (Join-Path $env:TEMP 'harness-aside-deploy.log') -Force | Out-Null
try {
    $ide = @(Get-Process -Name $IdeProcess -ErrorAction SilentlyContinue)
    if ($ide.Count -eq 0) { throw "$IdeProcess is not running; start the IDE once so its location can be found." }

    # Resolve everything before touching the IDE, so a bad setup never closes it.
    $exe = $ide[0].Path
    $info = Get-Content (Join-Path (Split-Path (Split-Path $exe)) 'product-info.json') -Raw | ConvertFrom-Json
    $plugins = Join-Path $env:APPDATA "JetBrains\$($info.dataDirectoryName)\plugins"
    if (-not (Test-Path $Zip)) { throw "Plugin zip not found: $Zip" }
    Write-Output "IDE: $exe", "Plugins: $plugins", "Zip: $Zip"

    $ide | ForEach-Object { [void]$_.CloseMainWindow() }
    $ide | Wait-Process -Timeout 300 -ErrorAction SilentlyContinue
    if ($ide | Where-Object { -not $_.HasExited }) {
        throw "$IdeProcess did not close within 5 minutes; nothing was changed. Close it and run deployToIde again."
    }

    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    Remove-Item (Join-Path $plugins 'harness-aside') -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $Zip -DestinationPath $plugins -Force
    Start-Process $exe
    Write-Output 'Deployed and restarted.'
} catch {
    Write-Output "FAILED: $_"
} finally {
    Stop-Transcript | Out-Null
}
