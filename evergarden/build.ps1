param([switch]$SkipPacks, [switch]$SkipDeploy)
$ErrorActionPreference = 'Stop'
$moduleRoot = $PSScriptRoot
$workspaceRoot = Split-Path $moduleRoot -Parent
$outputDir = Join-Path $moduleRoot ('target/build-' + [guid]::NewGuid().ToString('N'))
$classesDir = Join-Path $outputDir 'classes'
$distDir = Join-Path $moduleRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

if (!$SkipPacks) {
    & python (Join-Path $moduleRoot 'tools/build_packs.py')
    if ($LASTEXITCODE -ne 0) { throw 'Resource pack build failed.' }
}

$dependencyRoot = Join-Path $env:USERPROFILE '.m2/repository'
$dependencyJars = (Get-ChildItem -LiteralPath $dependencyRoot -Recurse -Filter '*.jar').FullName
if (!$dependencyJars) { throw 'No cached Java dependencies found. Run Maven dependency resolution first.' }
$advanceMagicJar = Join-Path $workspaceRoot 'advance-magic.jar'
if (Test-Path $advanceMagicJar) {
    $dependencyJars = @($advanceMagicJar) + $dependencyJars
}
$classPath = [string]::Join([System.IO.Path]::PathSeparator, $dependencyJars)
$sources = (Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src/main/java') -Recurse -Filter '*.java').FullName
& javac --release 21 -proc:none -encoding UTF-8 -cp $classPath -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; existing JAR has not been replaced.' }
Copy-Item -Path (Join-Path $moduleRoot 'src/main/resources/*') -Destination $classesDir -Recurse

# Embed resource packs inside the JAR
$packDir = Join-Path $classesDir 'resource-packs'
$geyserDir = Join-Path $classesDir 'geyser'
New-Item -ItemType Directory -Force -Path $packDir | Out-Null
New-Item -ItemType Directory -Force -Path $geyserDir | Out-Null
foreach ($asset in @('evergarden-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json')) {
    $srcPath = Join-Path $distDir $asset
    if (Test-Path $srcPath) {
        Copy-Item -LiteralPath $srcPath -Destination $packDir -Force
    }
}
if (Test-Path (Join-Path $distDir 'evergarden-bedrock.mcpack')) {
    Copy-Item -LiteralPath (Join-Path $distDir 'evergarden-bedrock.mcpack') -Destination $geyserDir -Force
}
if (Test-Path (Join-Path $distDir 'geyser-mappings.json')) {
    Copy-Item -LiteralPath (Join-Path $distDir 'geyser-mappings.json') -Destination $geyserDir -Force
}

$builtJar = Join-Path $outputDir 'evergarden.jar'
& jar --create --file $builtJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }

Copy-Item -LiteralPath $builtJar -Destination (Join-Path $distDir 'evergarden-3.0.0.jar') -Force
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $workspaceRoot 'evergarden.jar') -Force

$destinations = @(
    'C:\Users\User\Desktop\TestServer\plugins\evergarden.jar',
    (Join-Path $workspaceRoot 'output\compatibility-20260915\evergarden.jar'),
    (Join-Path $workspaceRoot '.audit-plugins\garden-balance-server\plugins\evergarden.jar'),
    (Join-Path $workspaceRoot '.audit-plugins\gardens-server\plugins\evergarden.jar')
)
foreach ($dst in $(if ($SkipDeploy) { @() } else { $destinations })) {
    $parent = Split-Path $dst -Parent
    if (Test-Path $parent) {
        Copy-Item -LiteralPath $builtJar -Destination $dst -Force
        Write-Output ("Deployed to: " + $dst)
    }
}

Write-Output ('Built Evergarden 3.0 (with embedded resource-packs): ' + (Join-Path $distDir 'evergarden-3.0.0.jar'))
Write-Output ('Classes: ' + $classesDir)

# ─── Auto-update resource-pack URL & SHA1 in deployed configs ────────────────
$hashesFile = Join-Path $distDir 'pack-hashes.json'
if ((Test-Path $hashesFile) -and !$SkipDeploy) {
    $hashes   = Get-Content $hashesFile | ConvertFrom-Json
    $newSha1  = $hashes.'evergarden-java.zip'
    $gitHash  = (& git -C $workspaceRoot rev-parse --short=8 HEAD 2>$null).Trim()
    if ($gitHash -and $newSha1) {
        $newUrl = "https://raw.githubusercontent.com/40000years/Afterdeath/$gitHash/evergarden/dist/evergarden-java.zip"
        $configPaths = @(
            'C:\Users\User\Desktop\TestServer\plugins\Evergarden\config.yml'
        )
        foreach ($cfg in $configPaths) {
            if (Test-Path $cfg) {
                $content = Get-Content $cfg -Raw -Encoding UTF8
                # Update only the top-level resource-pack url: line (github raw URLs)
                $content = $content -replace '(?m)^(  url:\s*)https://raw\.githubusercontent\.com/[^\r\n]+', "`${1}$newUrl"
                # Update only the top-level resource-pack sha1: (exactly 2-space indent, not aeternum nested sha1)
                $content = $content -replace '(?m)^(  sha1:\s*)[^\r\n]+', "`${1}'$newSha1'"
                # Update Aeternum pack URL to stable GitHub raw CDN
                $aeternumUrl = "https://raw.githubusercontent.com/40000years/Afterdeath/$gitHash/evergarden/dist/Aeternum-Foods-26.x.zip"
                $content = $content -replace '(?m)^(\s+url:\s*)(?:https://cdn\.modrinth\.com/|https://raw\.githubusercontent\.com/)[^\r\n]*Aeternum[^\r\n]*', "`${1}$aeternumUrl"
                [System.IO.File]::WriteAllText($cfg, $content, [System.Text.Encoding]::UTF8)
                Write-Output ("Updated resource-pack config: $cfg  (sha1=$newSha1, commit=$gitHash)")
            }
        }
    }
}
