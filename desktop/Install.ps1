param(
  [string]$InstallDir = (Join-Path $env:LOCALAPPDATA 'Popcorn-Time'),
  [string]$BackupRoot = (Join-Path $env:LOCALAPPDATA 'StreamNest\desktop-backups')
)
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'Popcorn-Time' -ErrorAction SilentlyContinue) {
  throw 'Close Popcorn Time before applying this patch.'
}
$installRoot = [IO.Path]::GetFullPath($InstallDir)
$package = Get-Content -LiteralPath (Join-Path $installRoot 'package.json') -Raw | ConvertFrom-Json
if ($package.version -ne '0.5.1') { throw 'This patch supports Popcorn Time desktop 0.5.1 only.' }
$targetRoot = [IO.Path]::GetFullPath((Join-Path $installRoot 'src\app'))
$overlayRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'overlay'))
$backupFolder = Join-Path ([IO.Path]::GetFullPath($BackupRoot)) (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
foreach ($entry in $manifest.files) {
  $source = [IO.Path]::GetFullPath((Join-Path $overlayRoot $entry.path))
  $target = [IO.Path]::GetFullPath((Join-Path $targetRoot $entry.path))
  if (!$source.StartsWith($overlayRoot + [IO.Path]::DirectorySeparatorChar) -or !$target.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Patch path is outside the intended app folder.'
  }
  if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLower() -ne $entry.sha256) {
    throw ('Patch checksum mismatch: ' + $entry.path)
  }
}
foreach ($entry in $manifest.files) {
  $target = [IO.Path]::GetFullPath((Join-Path $targetRoot $entry.path))
  $backup = [IO.Path]::GetFullPath((Join-Path $backupFolder $entry.path))
  if (!$backup.StartsWith($backupFolder + [IO.Path]::DirectorySeparatorChar)) { throw 'Invalid backup path.' }
  if (Test-Path -LiteralPath $target) {
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($backup)) | Out-Null
    Copy-Item -LiteralPath $target -Destination $backup
  }
  New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
  Copy-Item -LiteralPath (Join-Path $overlayRoot $entry.path) -Destination $target
}
Write-Output ('Installed StreamNest desktop patch 0.11.0. Backup: ' + $backupFolder)
