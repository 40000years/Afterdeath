$ErrorActionPreference = 'Stop'
$moduleRoot = $PSScriptRoot
$testClasses = Join-Path $moduleRoot 'target/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$dependencyJars = (Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName
$classPath = (Join-Path $moduleRoot 'dist/advance-magic-1.0.0.jar') + [System.IO.Path]::PathSeparator + [string]::Join([System.IO.Path]::PathSeparator,$dependencyJars)
& javac --release 21 -proc:none -encoding UTF-8 -cp $classPath -d $testClasses (Join-Path $moduleRoot 'tests/AccountingChecks.java') (Join-Path $moduleRoot 'tests/PackHttpChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& java -cp ($testClasses + [System.IO.Path]::PathSeparator + $classPath) AccountingChecks
if ($LASTEXITCODE -ne 0) { throw 'Accounting or collision tests failed.' }
& java -cp ($testClasses + [System.IO.Path]::PathSeparator + $classPath) PackHttpChecks (Join-Path $moduleRoot 'dist/advance-magic-java.zip')
if ($LASTEXITCODE -ne 0) { throw 'HTTP resource pack tests failed.' }
& python (Join-Path $moduleRoot 'tests/check_packs.py')
if ($LASTEXITCODE -ne 0) { throw 'Resource pack checks failed.' }
