param(
    [string]$JavaHome = '',
    [string]$MavenPath = '',
    [switch]$Rebuild,
    [switch]$UseMySql
)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    # Explicit parameters win. These fallbacks also support the current classroom PC.
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
    } elseif (Test-Path -LiteralPath 'D:/jdk-17.0.2/bin/java.exe') {
        $env:JAVA_HOME = 'D:/jdk-17.0.2'
    }
    $javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    if (-not (Get-Command $javaCommand -ErrorAction SilentlyContinue)) {
        throw 'Java was not found. Install JDK 17+ and set JAVA_HOME, or pass -JavaHome.'
    }

    $jarPath = Join-Path $PSScriptRoot 'target/wardrobe-account-server-1.0.0.jar'
    if (-not $Rebuild -and -not (Test-Path -LiteralPath $jarPath)) {
        $bundledJar = Join-Path $PSScriptRoot ../releases/wardrobe-account-server-1.0.0.jar
        if (Test-Path -LiteralPath $bundledJar) { $jarPath = $bundledJar }
    }
    if ($Rebuild -or -not (Test-Path -LiteralPath $jarPath)) {
        if (-not $MavenPath) {
            $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
            if ($mavenCommand) {
                $MavenPath = $mavenCommand.Source
            } elseif (Test-Path -LiteralPath 'D:/apache-maven-3.9.11/bin/mvn.cmd') {
                $MavenPath = 'D:/apache-maven-3.9.11/bin/mvn.cmd'
            } else {
                throw 'Maven was not found. Install Maven 3.6.3+ and add it to PATH, or pass -MavenPath.'
            }
        }
        Write-Host 'Building and testing the account server. The first build may download dependencies.'
        & $MavenPath -s tools/central-settings.xml -gs tools/central-settings.xml package
        if ($LASTEXITCODE -ne 0) { throw 'Build failed. Read the Maven error above; the server was not started.' }
    }

    Write-Host 'Starting Wardrobe Account Server. Press Ctrl+C to stop.'
    if ($UseMySql) {
        & $javaCommand -jar $jarPath --spring.profiles.active=mysql
    } else {
        & $javaCommand -jar $jarPath
    }
    if ($LASTEXITCODE -ne 0) { throw "Server exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
