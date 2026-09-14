param([string]$PaperTestRoot = (Join-Path $PSScriptRoot '../../.audit-plugins/review-20260914'))
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$classes = Join-Path $PSScriptRoot 'target/probe-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$deps = @((Get-ChildItem (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName)
$deps += @((Get-ChildItem (Join-Path $PaperTestRoot 'libraries') -Recurse -Filter '*.jar').FullName)
$deps += @((Join-Path $PaperTestRoot 'versions/26.2/paper-26.2.jar'),(Join-Path $workspaceRoot 'evergarden.jar'),(Join-Path $workspaceRoot 'advance-magic.jar'))
& javac --release 21 -proc:none -encoding UTF-8 -cp ([string]::Join([IO.Path]::PathSeparator,$deps)) -d $classes (Join-Path $PSScriptRoot 'ReviewChecks.java')
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
@('name: ReviewChecks','version: 1.0','main: ReviewChecks',"api-version: '26.2'",'depend: [Evergarden, advance-magic]') | Set-Content -Encoding ASCII (Join-Path $classes 'plugin.yml')
& jar --create --file (Join-Path $PSScriptRoot 'target/review-checks.jar') -C $classes .
if($LASTEXITCODE -ne 0){throw 'Probe packaging failed'}
Write-Output 'Built target/review-checks.jar. Test-only: install ONLY in a disposable Paper 26.2 server with both plugins. It edits worlds, writes review-result.txt, and shuts down.'
