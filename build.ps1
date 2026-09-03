param (
    [string]$Module = "all"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$m2Repo = Join-Path $env:USERPROFILE ".m2\repository"
$testServerPlugins = "C:\Users\User\Desktop\TestServer\plugins"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Building Minecraft Plugins (Java 21) " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$jars = (Get-ChildItem -Recurse -Filter *.jar $m2Repo).FullName
$cp = [string]::Join([System.IO.Path]::PathSeparator, $jars)

function Invoke-BuildPlugin {
    param (
        [string]$Name
    )

    Write-Host ""
    Write-Host ("=== [BUILDING] " + $Name + " ===") -ForegroundColor Yellow

    $moduleDir = Join-Path $root $Name
    $srcDir = Join-Path $moduleDir "src\main\java"
    $resDir = Join-Path $moduleDir "src\main\resources"
    $targetDir = Join-Path $moduleDir "target"
    $classesDir = Join-Path $targetDir "classes"
    $targetJar = Join-Path $targetDir ($Name + ".jar")
    $rootJar = Join-Path $root ($Name + ".jar")

    if (Test-Path $classesDir) {
        Remove-Item -Recurse -Force $classesDir
    }
    New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

    $javaFiles = (Get-ChildItem -Recurse -Filter *.java $srcDir).FullName
    Write-Host ("  [1/3] Compiling " + $javaFiles.Count + " Java source files...") -ForegroundColor Gray
    javac -cp $cp -d $classesDir -encoding UTF-8 $javaFiles

    Write-Host "  [2/3] Copying resource files..." -ForegroundColor Gray
    if (Test-Path $resDir) {
        Copy-Item "$resDir\*" $classesDir -Recurse -Force
    }

    Write-Host "  [3/3] Packaging JAR file..." -ForegroundColor Gray
    jar cf $targetJar -C $classesDir .
    Copy-Item $targetJar $rootJar -Force

    $jarItem = Get-Item $rootJar
    Write-Host ("  [SUCCESS] " + $Name + ".jar (" + $jarItem.Length + " bytes) generated successfully!") -ForegroundColor Green

    if (Test-Path $testServerPlugins) {
        $destTestJar = Join-Path $testServerPlugins ($Name + ".jar")
        Copy-Item $rootJar $destTestJar -Force
        Write-Host ("  [DEPLOYED] Copied to TestServer: " + $destTestJar) -ForegroundColor Magenta
    }
}

if ($Module -eq "all" -or $Module -eq "voidscape") {
    Invoke-BuildPlugin -Name "voidscape"
}

if ($Module -eq "all" -or $Module -eq "afterdeath") {
    Invoke-BuildPlugin -Name "afterdeath"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Build Finished Successfully! " -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
