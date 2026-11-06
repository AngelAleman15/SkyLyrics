# SkyLyrics GSMTC bridge. Windows PowerShell 5.1, Windows 10 1809+.
# Read-only media-session access. No HTTP, credentials or playback control.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and
    $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
function Await($operation, $resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    if (-not $task.Wait(5000)) { throw 'WinRT timeout' }
    return $task.Result
}
function Emit($value) {
    [Console]::WriteLine(($value | ConvertTo-Json -Compress -Depth 3))
    [Console]::Out.Flush()
}
try {
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    while ($true) {
        try {
            $sessions = @($manager.GetSessions() | Where-Object { $_.SourceAppUserModelId -match '(?i)spotify' })
            $session = $sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1
            if ($null -eq $session) { $session = $sessions | Select-Object -First 1 }
            if ($null -eq $session) {
                Emit @{ state='NO_SESSION' }
            } else {
                $media = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $timeline = $session.GetTimelineProperties()
                $playback = $session.GetPlaybackInfo()
                $now = [DateTimeOffset]::UtcNow
                $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
                $rate = 1.0
                if ($null -ne $playback.PlaybackRate) { $rate = [double]$playback.PlaybackRate }
                $position = ($timeline.Position - $timeline.StartTime).TotalMilliseconds
                $duration = ($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
                # Position is anchored at LastUpdatedTime, not at the polling instant.
                $age = ($now - $timeline.LastUpdatedTime).TotalMilliseconds
                if ($playing -and $age -ge 0 -and $age -lt 86400000) { $position += $age * $rate }
                $position = [Math]::Max(0, $position)
                if ($duration -gt 0) { $position = [Math]::Min($duration, $position) }
                Emit @{
                    state='SESSION'; title=$media.Title; artist=$media.Artist
                    album=$media.AlbumTitle; app=$session.SourceAppUserModelId
                    durationMs=[long][Math]::Max(0,$duration); positionMs=[long]$position
                    playing=$playing; rate=$rate; capturedAtMs=$now.ToUnixTimeMilliseconds()
                }
            }
        } catch {
            # Do not leak media metadata or exception payloads into logs.
            Emit @{ state='READ_ERROR' }
        }
        Start-Sleep -Milliseconds 500
    }
} catch {
    Emit @{ state='UNAVAILABLE' }
    exit 1
}
