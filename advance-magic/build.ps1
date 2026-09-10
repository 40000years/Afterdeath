param([switch]$SkipPacks)
$ErrorActionPreference = 'Stop'
$moduleRoot = $PSScriptRoot
$workspaceRoot = Split-Path $moduleRoot -Parent
$distDir = Join-Path $moduleRoot 'dist'
if (!$SkipPacks) {
    & python (Join-Path $moduleRoot 'tools/build_packs.py')
    if ($LASTEXITCODE -ne 0) { throw 'Resource pack build failed; existing JAR has not been replaced.' }
}
& python (Join-Path $moduleRoot 'tests/check_packs.py') --assets-only
if ($LASTEXITCODE -ne 0) { throw 'Resource pack validation failed; existing JAR has not been replaced.' }
$outputDir = Join-Path $moduleRoot ('target/build-' + [guid]::NewGuid().ToString('N'))
$classesDir = Join-Path $outputDir 'classes'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
$dependencyJars = (Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName
if (!$dependencyJars) { throw 'No cached API dependencies. Run Maven dependency resolution first.' }
$classPath = [string]::Join([System.IO.Path]::PathSeparator, $dependencyJars)
$sources = (Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src/main/java') -Recurse -Filter '*.java').FullName
& javac --release 21 -proc:none -encoding UTF-8 -cp $classPath -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; existing JAR has not been replaced.' }
Copy-Item -Path (Join-Path $moduleRoot 'src/main/resources/*') -Destination $classesDir -Recurse
$packDir = Join-Path $classesDir 'resource-packs'
New-Item -ItemType Directory -Force -Path $packDir | Out-Null
foreach ($asset in @('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json','wand-preview.html','advance-magic-guide-th.png')) {
    Copy-Item -LiteralPath (Join-Path $distDir $asset) -Destination $packDir
}
$builtJar = Join-Path $outputDir 'advance-magic.jar'
& jar --create --file $builtJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
$distDir = Join-Path $moduleRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $distDir 'advance-magic-1.0.0.jar') -Force
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $workspaceRoot 'advance-magic.jar') -Force
Write-Output ('Built: ' + (Join-Path $distDir 'advance-magic-1.0.0.jar'))
