# Run manually on the Windows account that deployed the dashboard.
$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot '..\reference\sync-owner-key.dpapi'
if (!(Test-Path -LiteralPath $keyFile)) { throw 'The owner key is only available on the computer/account that deployed this dashboard.' }
$secure = Get-Content -LiteralPath $keyFile | ConvertTo-SecureString
$key = [Net.NetworkCredential]::new('', $secure).Password
Set-Clipboard -Value $key
Remove-Variable key
Write-Host 'Owner access key copied. Paste it into the dashboard. This key grants access to group/device names; keep it private.'
Start-Process 'https://streamnest-watch-sync.streamnest-shaurya609.workers.dev/admin' -WindowStyle Hidden
