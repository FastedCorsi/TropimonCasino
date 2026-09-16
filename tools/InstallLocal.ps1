param(
    [string]$Source,
    [string]$TargetDirectory = (Join-Path $env:APPDATA '.tropimon\mods')
)

# By FastedCorsi. Point d'entrée manuel vers l'installateur différé et vérifié.
$ErrorActionPreference = 'Stop'
if (-not $Source) {
    $deliveryDirectory = Join-Path $PSScriptRoot '..\build\delivery\local'
    $releases = @(Get-ChildItem -LiteralPath $deliveryDirectory -Filter 'TropimonCasino-*-LOCAL.jar' -File)
    if ($releases.Count -ne 1) { throw 'Livraison locale absente ou ambiguë.' }
    $Source = $releases[0].FullName
}
$resolvedSource = (Resolve-Path -LiteralPath $Source).Path
if (-not (Test-Path -LiteralPath $TargetDirectory)) {
    New-Item -ItemType Directory -Path $TargetDirectory | Out-Null
}
$resolvedTargetDirectory = (Resolve-Path -LiteralPath $TargetDirectory).Path
$launcherDirectory = Split-Path -Parent $resolvedTargetDirectory
$stream = [IO.File]::OpenRead($resolvedSource)
try {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $expectedHash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose() }
} finally { $stream.Dispose() }
$installer = Join-Path $PSScriptRoot 'InstallWhenClosed.ps1'

& $installer `
    -Source $resolvedSource `
    -ExpectedHash $expectedHash `
    -LauncherDirectory $launcherDirectory `
    -Once

$statusFile = Join-Path (Split-Path -Parent $resolvedSource) 'install-status.json'
$status = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
switch ($status.state) {
    'installed-verified' { Write-Output "Installation vérifiée dans $resolvedTargetDirectory" }
    'waiting-for-game' { Write-Output 'Installation préparée : elle attend la fermeture de Minecraft.' }
    default { throw "Installation non terminée : $($status.state)" }
}
