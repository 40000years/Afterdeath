param([switch]$SkipPacks)
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
Write-Output ('Built Evergarden 3.0 (with embedded resource-packs): ' + (Join-Path $distDir 'evergarden-3.0.0.jar'))
Write-Output ('Classes: ' + $classesDir)
