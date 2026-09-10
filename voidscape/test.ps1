$ErrorActionPreference = 'Stop'
$moduleRoot=$PSScriptRoot
$testClasses=Join-Path $moduleRoot 'target/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$dependencyJars=(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName
$jar=(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'dist') -Filter 'voidscape-*.jar' | Select-Object -Last 1).FullName
if(!$jar){throw 'No voidscape JAR found in dist.'}
$classPath=$jar+[System.IO.Path]::PathSeparator+[string]::Join([System.IO.Path]::PathSeparator,$dependencyJars)
$sources=(Join-Path $moduleRoot 'tests/GeometryChecks.java')
& javac -proc:none -encoding UTF-8 -cp $classPath -d $testClasses $sources
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& java -cp ($testClasses+[System.IO.Path]::PathSeparator+$classPath) GeometryChecks
if($LASTEXITCODE -ne 0){throw 'Geometry or placement tests failed'}
Write-Output 'Geometry checks passed. Use tests/build_gardens.ps1 for the isolated Paper integration suite.'
