# Building EntropyLab

This document outlines the prerequisites and exact steps to build **EntropyLab** from source, run it in development mode, and package it into native Windows application images and installers (`.exe` and `.msi`).

---

## 1. Prerequisites

Before building, verify that the following tools are installed and present in your system `PATH`:

| Requirement | Supported Versions | Verification Command | Notes |
| :--- | :--- | :--- | :--- |
| **Operating System** | Windows 10 or 11 (x64) | `[System.Environment]::OSVersion` | Native packaging targets Windows. |
| **Java Development Kit** | JDK 17 or JDK 21+ | `javac -version`<br>`jpackage --version` | Ensure `JAVA_HOME` is set to the JDK root. `jpackage` is included in JDK 17+. |
| **Apache Maven** | Maven 3.8+ | `mvn -version` | Used to manage dependencies and build JARs. |
| **WiX Toolset** | WiX 3.11+ | `candle.exe -?`<br>`light.exe -?` | Required by `jpackage` for building `.msi` and `.exe` installers. |

> **WiX Setup Note:** If WiX is not already installed on your system, the included `build-installer.ps1` script will automatically download the portable WiX 3.11 binaries to `%USERPROFILE%\.wix311` and configure your environment without requiring administrator privileges.

---

## 2. Quickstart: Running in Development

To compile and launch the application directly from source:

```powershell
mvn clean compile javafx:run
```

Alternatively, you can run the bootstrap launcher directly:

```powershell
mvn clean compile exec:java -Dexec.mainClass="com.entropylab.Launcher"
```

---

## 3. Building the Application JAR

To compile, process resources, and package the standard application JAR along with all runtime dependencies:

```powershell
mvn clean package -DskipTests
```

This generates:
- `target/entropylab-1.0.0.jar` — The compiled application JAR with `Main-Class: com.entropylab.Launcher`.
- `target/installer-input/` — Staging directory containing all runtime dependencies (JavaFX controls, Jackson, SQLite JDBC, ByteBuddy, etc.).

---

## 4. One-Click Native Installer Build

An automated PowerShell script is provided to perform the complete end-to-end build from scratch:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-installer.ps1
```

To build only a specific package format:

```powershell
# Build only the native portable app-image directory
.\build-installer.ps1 -PackageType app-image

# Build only the Windows EXE installer
.\build-installer.ps1 -PackageType exe

# Build only the Windows MSI installer
.\build-installer.ps1 -PackageType msi
```

---

## 5. Step-by-Step Manual Packaging (`jpackage`)

If you prefer to run the `jpackage` tool manually:

### Step A: Stage Dependencies and App JAR
```powershell
mvn clean package -DskipTests
Copy-Item target/entropylab-1.0.0.jar target/installer-input/entropylab-1.0.0.jar -Force
```

### Step B: Build Portable Native Application Image
```powershell
jpackage `
  --type app-image `
  --dest target/dist `
  --name "EntropyLab" `
  --app-version "1.0.0" `
  --input target/installer-input `
  --main-jar "entropylab-1.0.0.jar" `
  --main-class "com.entropylab.Launcher" `
  --icon src/main/resources/icon.ico
```
This produces a fully self-contained folder: `target/dist/EntropyLab/` with `EntropyLab.exe` and a custom stripped JRE in `target/dist/EntropyLab/runtime/`.

### Step C: Build Windows EXE Installer
```powershell
jpackage `
  --type exe `
  --dest target/dist `
  --app-image target/dist/EntropyLab `
  --name "EntropyLab" `
  --app-version "1.0.0" `
  --icon src/main/resources/icon.ico `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut
```

### Step D: Build Windows MSI Installer
```powershell
jpackage `
  --type msi `
  --dest target/dist `
  --app-image target/dist/EntropyLab `
  --name "EntropyLab" `
  --app-version "1.0.0" `
  --icon src/main/resources/icon.ico `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut
```

---

## 6. Output Artifacts

All final distribution artifacts are placed in `target/dist/`:

| Artifact | Type | Description |
| :--- | :--- | :--- |
| `target/dist/EntropyLab/` | Directory | **Self-contained Portable App**: Contains `EntropyLab.exe` and bundled JRE. Runs anywhere without requiring Java installed on the host. |
| `target/dist/EntropyLab-1.0.0.exe` | Executable | **Windows Setup Bootstrapper**: Installs EntropyLab with custom directory chooser, Start Menu shortcut, and Desktop icon. |
| `target/dist/EntropyLab-1.0.0.msi` | MSI Package | **Windows Installer Package**: Standard enterprise/Windows installer package for managed deployments. |

---

## 7. Troubleshooting

### WiX Toolset Missing (`candle.exe` or `light.exe`)
- Verify WiX 3.11 is installed and its `bin/` folder is on your `PATH`.
- When using `build-installer.ps1`, the script will automatically download portable binaries into `%USERPROFILE%\.wix311`.

### Temp Disk Space Issues
- Compressing large runtime images during MSI generation can require several gigabytes of temporary space.
- If your system drive (`C:`) is low on disk space, redirect the temporary working directory:
  ```powershell
  $env:WIX_TEMP = "E:\temp"
  $env:TEMP = "E:\temp"
  ```
  The `build-installer.ps1` script automatically sets `--temp target/tmp` inside the workspace drive.
