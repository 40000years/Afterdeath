$ErrorActionPreference = 'Stop'
$moduleRoot=$PSScriptRoot
$testClasses=Join-Path $moduleRoot 'target/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$dependencyJars=(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName
$classPath=(Join-Path $moduleRoot 'dist/voidscape-2.0.0.jar')+[System.IO.Path]::PathSeparator+[string]::Join([System.IO.Path]::PathSeparator,$dependencyJars)
$sources=(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'tests') -Filter '*.java').FullName
& javac -proc:none -encoding UTF-8 -cp $classPath -d $testClasses $sources
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& java -cp ($testClasses+[System.IO.Path]::PathSeparator+$classPath) GeometryChecks
if($LASTEXITCODE -ne 0){throw 'Geometry or placement tests failed'}
Copy-Item -LiteralPath (Join-Path $moduleRoot 'tests/plugin.yml') -Destination $testClasses -Force
& jar --create --file (Join-Path $moduleRoot 'target/integration-checks.jar') -C $testClasses .
if($LASTEXITCODE -ne 0){throw 'Test packaging failed'}
Write-Output 'Integration test plugin ready. Install ONLY in an isolated test server.'
