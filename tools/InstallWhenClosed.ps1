param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$ExpectedHash,
    [string]$LauncherDirectory = $env:TROPIMON_HOME,
    [switch]$Once
)

# By FastedCorsi. Installateur local externe, absent du JAR partageable.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceFile = (Resolve-Path -LiteralPath $Source).Path
$statusFile = Join-Path (Split-Path -Parent $sourceFile) 'install-status.json'
$version = $null

function Set-InstallStatus([string]$State) {
    @{ state = $State; version = $version; updatedUtc = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
}

function Read-Mod([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            $stream = $entry.Open()
            try { $stream.CopyTo([IO.Stream]::Null) } finally { $stream.Dispose() }
        }
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { throw 'Métadonnées absentes.' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}

function Assert-Release([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $actualHash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
        finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
    if ($actualHash -ne $ExpectedHash) { throw 'Intégrité inattendue.' }
    $meta = Read-Mod $Path
    if ($meta.id -ne 'tropimon_casino' -or @($meta.authors).Count -ne 1 -or
            $meta.authors[0] -cne 'By FastedCorsi' -or $meta.version -notmatch '^[0-9A-Za-z.+_-]+$') {
        throw 'Identité du mod inattendue.'
    }
    return $meta.version
}

function Test-GameBusy {
    foreach ($process in Get-CimInstance Win32_Process) {
        if ($process.Name -notmatch '^java(w)?\.exe$') { continue }
        if (-not $process.CommandLine) { return $true }
        if ($process.CommandLine -match '(?i)KnotClient|net\.minecraft|--gameDir') { return $true }
        $gameProcess = Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
        if ($gameProcess -and $gameProcess.MainWindowTitle -match '(?i)^(Minecraft\b|Tropimon\s+\d)') { return $true }
    }
    return $false
}

$lock = $null
$stage = $null
try {
    $version = Assert-Release $sourceFile
    if (-not $LauncherDirectory) { $LauncherDirectory = Join-Path $env:APPDATA '.tropimon' }
    $instance = (Resolve-Path -LiteralPath $LauncherDirectory).Path
    $mods = (Resolve-Path -LiteralPath (Join-Path $instance 'mods')).Path
    if ((Get-Item -LiteralPath $mods).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Dossier mods redirigé.' }
    if ($sourceFile.StartsWith($mods + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Source dans les mods chargés.' }
    $backupDirectory = Join-Path $instance 'mod-archive\TropimonCasino'
    New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
    $lock = [IO.File]::Open((Join-Path $backupDirectory 'install.lock'),
        [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $target = Join-Path $mods "TropimonCasino-$version+1.21.1-LOCAL.jar"

    while (Test-GameBusy) {
        Set-InstallStatus 'waiting-for-game'
        if ($Once) { return }
        Start-Sleep -Seconds 5
    }

    $installed = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Where-Object {
        try { (Read-Mod $_.FullName).id -eq 'tropimon_casino' } catch { $false }
    })
    if ($installed.Count -gt 1) { throw 'Plusieurs JAR Casino sont chargés.' }
    if ($installed.Count -eq 1 -and $installed[0].FullName -eq $target -and
            (Assert-Release $target) -eq $version) {
        Set-InstallStatus 'installed-verified'
        return
    }

    $stage = Join-Path $mods ('.tropimon-casino-' + [Guid]::NewGuid().ToString('N') + '.pending')
    Copy-Item -LiteralPath $sourceFile -Destination $stage
    $null = Assert-Release $stage
    if (Test-GameBusy) { throw 'Minecraft a redémarré pendant la préparation.' }
    $old = if ($installed.Count -eq 1) { $installed[0].FullName } else { $null }
    $backup = $null
    if ($old) {
        $probe = [IO.File]::Open($old, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $probe.Dispose()
        $backup = Join-Path $backupDirectory ((Split-Path -Leaf $old) + '.' + [Guid]::NewGuid().ToString('N') + '.bak')
        Move-Item -LiteralPath $old -Destination $backup
    }
    try {
        Move-Item -LiteralPath $stage -Destination $target
        $stage = $null
        $null = Assert-Release $target
    } catch {
        if (Test-Path -LiteralPath $target) {
            Move-Item -LiteralPath $target -Destination (Join-Path $backupDirectory ([Guid]::NewGuid().ToString('N') + '.failed'))
        }
        if ($backup) { Move-Item -LiteralPath $backup -Destination $old }
        throw
    }
    Set-InstallStatus 'installed-verified'
} catch {
    Set-InstallStatus 'blocked-review-required'
    throw
} finally {
    if ($stage -and (Test-Path -LiteralPath $stage)) { Remove-Item -LiteralPath $stage -Force }
    if ($lock) { $lock.Dispose() }
}
