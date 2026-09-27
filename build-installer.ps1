# EntropyLab Native Packaging Script for Windows
# Uses Maven, JDK 17+ (jpackage), and WiX Toolset 3.11+
param(
    [string]$PackageType = "all" # options: "app-image", "exe", "msi", "all"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  EntropyLab Native Build & Packaging   " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Verify Java / JDK
$javaVersion = java -version 2>&1 | Out-String
Write-Host "[1/6] Verifying Java Runtime..." -ForegroundColor Yellow
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
    $jpackageExe = "$env:JAVA_HOME\bin\jpackage.exe"
} else {
    $jpackageExe = (Get-Command jpackage.exe -ErrorAction SilentlyContinue)?.Source
}

if (-not $jpackageExe) {
    Write-Error "jpackage not found. Please ensure JDK 17+ is installed and JAVA_HOME points to it."
}
Write-Host "Using jpackage: $jpackageExe" -ForegroundColor Green

# 2. Check/Configure WiX Toolset
Write-Host "[2/6] Verifying WiX Toolset for Windows Installers..." -ForegroundColor Yellow
$wixDir = "$env:USERPROFILE\.wix311"
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    if (Test-Path "$wixDir\candle.exe") {
        $env:PATH = "$wixDir;" + $env:PATH
        Write-Host "Added $wixDir to PATH." -ForegroundColor Green
    } else {
        Write-Host "Downloading portable WiX 3.11 binaries to $wixDir..." -ForegroundColor Yellow
        New-Item -ItemType Directory -Path $wixDir -Force | Out-Null
        $zip = "$env:TEMP\wix311-binaries.zip"
        Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip" -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $wixDir -Force
        Remove-Item $zip -Force
        $env:PATH = "$wixDir;" + $env:PATH
    }
}
Write-Host "WiX Toolset verified: $(Get-Command candle.exe).Source" -ForegroundColor Green

# Configure Temp Directory with ample space
$tempDir = "target/tmp"
if (-not (Test-Path $tempDir)) { New-Item -ItemType Directory -Path $tempDir -Force | Out-Null }
$tempAbs = (Resolve-Path $tempDir).Path
$env:TEMP = $tempAbs
$env:TMP = $tempAbs
$env:WIX_TEMP = $tempAbs

# 3. Ensure App Icon
Write-Host "[3/6] Verifying Icons..." -ForegroundColor Yellow
$iconIco = "src/main/resources/icon.ico"
if (-not (Test-Path $iconIco)) {
    Write-Host "Generating application icons..." -ForegroundColor Yellow
    mvn compile -q
    & "$env:JAVA_HOME\bin\java.exe" -cp "target\classes" com.entropylab.ui.IconGenerator
}
Write-Host "Icons verified: $iconIco" -ForegroundColor Green

# 4. Maven Build & Dependency Staging
Write-Host "[4/6] Building project and staging dependencies..." -ForegroundColor Yellow
mvn clean package -DskipTests

# Stage main jar into target/installer-input
$mainJarSource = "target/entropylab-1.0.0.jar"
$installerInput = "target/installer-input"
Copy-Item $mainJarSource "$installerInput/entropylab-1.0.0.jar" -Force
Write-Host "Staged application JARs in $installerInput" -ForegroundColor Green

# 5. Build Native App Image
Write-Host "[5/6] Building Native Application Image via jpackage..." -ForegroundColor Yellow
$distDir = "target/dist"
if (Test-Path "$distDir/EntropyLab") {
    Remove-Item -Path "$distDir/EntropyLab" -Recurse -Force
}

& $jpackageExe `
    --type app-image `
    --dest $distDir `
    --name "EntropyLab" `
    --app-version "1.0.0" `
    --input $installerInput `
    --main-jar "entropylab-1.0.0.jar" `
    --main-class "com.entropylab.Launcher" `
    --icon $iconIco `
    --temp $tempAbs

Write-Host "App Image built: $distDir\EntropyLab\EntropyLab.exe" -ForegroundColor Green

# 6. Build Installers (EXE / MSI)
Write-Host "[6/6] Building Windows Installers ($PackageType)..." -ForegroundColor Yellow

if ($PackageType -eq "exe" -or $PackageType -eq "all") {
    Write-Host "Building EXE Installer..." -ForegroundColor Cyan
    & $jpackageExe `
        --type exe `
        --dest $distDir `
        --temp $tempAbs `
        --app-image "$distDir/EntropyLab" `
        --name "EntropyLab" `
        --app-version "1.0.0" `
        --icon $iconIco `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut
    Write-Host "Created: $distDir\EntropyLab-1.0.0.exe" -ForegroundColor Green
}

if ($PackageType -eq "msi" -or $PackageType -eq "all") {
    Write-Host "Building MSI Installer..." -ForegroundColor Cyan
    & $jpackageExe `
        --type msi `
        --dest $distDir `
        --temp $tempAbs `
        --app-image "$distDir/EntropyLab" `
        --name "EntropyLab" `
        --app-version "1.0.0" `
        --icon $iconIco `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut
    Write-Host "Created: $distDir\EntropyLab-1.0.0.msi" -ForegroundColor Green
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  BUILD & PACKAGING COMPLETE!            " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Get-ChildItem -Path $distDir | Select-Object Name, Length, LastWriteTime
