param([switch]$SkipPacks)
$ErrorActionPreference = 'Stop'
$moduleRoot = $PSScriptRoot
$workspaceRoot = Split-Path $moduleRoot -Parent
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
$builtJar = Join-Path $outputDir 'advance-magic.jar'
& jar --create --file $builtJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
$distDir = Join-Path $moduleRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $distDir 'advance-magic-1.0.0.jar') -Force
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $workspaceRoot 'advance-magic.jar') -Force
if (!$SkipPacks) {
    & python (Join-Path $moduleRoot 'tools/build_packs.py')
    if ($LASTEXITCODE -ne 0) { throw 'Resource pack build failed.' }
}
Write-Output ('Built: ' + (Join-Path $distDir 'advance-magic-1.0.0.jar'))
