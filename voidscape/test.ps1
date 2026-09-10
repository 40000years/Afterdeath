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
Copy-Item -LiteralPath (Join-Path $moduleRoot 'tests/plugin.yml') -Destination $testClasses -Force
& jar --create --file (Join-Path $moduleRoot 'target/integration-checks.jar') -C $testClasses .
if($LASTEXITCODE -ne 0){throw 'Test packaging failed'}
Write-Output 'Integration test plugin ready. Install ONLY in an isolated test server.'
