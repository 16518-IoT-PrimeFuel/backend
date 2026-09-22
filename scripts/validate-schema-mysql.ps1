# Validates the Flyway migrations and Hibernate schema validation against a real MySQL server,
# with NO credentials hardcoded and NO destructive DROP of any pre-existing database.
#
# It never targets an existing schema: each run uses a unique, disposable database name
# (fulltank_schema_check_<timestamp>) created on the fly via createDatabaseIfNotExist=true.
#
# Usage (PowerShell):
#   $env:MYSQL_USER = 'your-user'
#   $env:MYSQL_PASSWORD = 'your-password'
#   ./scripts/validate-schema-mysql.ps1
#
# Optional environment: MYSQL_HOST (default localhost), MYSQL_PORT (default 3306), MYSQL_DB_PREFIX.
param(
    [string]$DatabasePrefix = $(if ($env:MYSQL_DB_PREFIX) { $env:MYSQL_DB_PREFIX } else { 'fulltank_schema_check' }),
    [string]$HostName = $(if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { 'localhost' }),
    [int]$Port = $(if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3306 }),
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$user = $env:MYSQL_USER
$password = $env:MYSQL_PASSWORD
if (-not $user -or -not $password) {
    throw 'Set MYSQL_USER and MYSQL_PASSWORD in the environment before running this script.'
}
if ($DatabasePrefix -notmatch '^[A-Za-z0-9_]+$') {
    throw 'MYSQL_DB_PREFIX must contain only letters, digits and underscores.'
}

$repo = Split-Path -Parent $PSScriptRoot
$database = "{0}_{1}" -f $DatabasePrefix, (Get-Date -Format 'yyyyMMddHHmmss')
$log = Join-Path $repo 'target\schema-validation.log'
New-Item -ItemType Directory -Force -Path (Join-Path $repo 'target') | Out-Null

$propertiesFile = Join-Path $repo 'target\schema-validation.properties'
@"
spring.datasource.url=jdbc:mysql://${HostName}:${Port}/${database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=$user
spring.datasource.password=$password
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.profiles.active=test
authorization.jwt.secret=0123456789abcdef0123456789abcdef
"@ | Set-Content -Path $propertiesFile -Encoding UTF8

Write-Host "Validating migrations against $HostName`:$Port/$database (disposable)..."
$stdout = Join-Path $repo 'target\schema-validation.out.log'
$stderr = Join-Path $repo 'target\schema-validation.err.log'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Users\crama\.jdks\openjdk-26.0.2' }
$runArguments = '--spring.config.additional-location=file:target/schema-validation.properties'
$process = Start-Process -FilePath (Join-Path $repo 'mvnw.cmd') `
    -ArgumentList @('-q', 'spring-boot:run', "-Dspring-boot.run.arguments=$runArguments") `
    -WorkingDirectory $repo -NoNewWindow -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$applied = $false
$failed = $false
while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
    Start-Sleep -Seconds 2
    $text = (Get-Content $stdout -Raw -ErrorAction SilentlyContinue) + (Get-Content $stderr -Raw -ErrorAction SilentlyContinue)
    if ($text -match 'Successfully applied|now at version') { $applied = $true }
    if ($text -match 'SchemaManagementException|APPLICATION FAILED TO START|Exception in thread') { $failed = $true; break }
    if ($applied -and $text -match 'Started FullTankPlatformApplication') { break }
}

if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
Copy-Item -Path $stdout -Destination $log -Force -ErrorAction SilentlyContinue
Remove-Item -Force $propertiesFile -ErrorAction SilentlyContinue

if ($failed -or -not $applied) {
    Write-Host 'SCHEMA VALIDATION FAILED. See target\schema-validation.log' -ForegroundColor Red
    exit 1
}
Write-Host "SCHEMA VALIDATION OK ($database). Flyway applied and Hibernate validate passed." -ForegroundColor Green

# Best-effort cleanup of the disposable database only, if the MySQL CLI is available.
if (Get-Command mysql -ErrorAction SilentlyContinue) {
    & mysql -h $HostName -P $Port -u $user "-p$password" -e "DROP DATABASE IF EXISTS ``$database``;" 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Host "Dropped disposable database $database." }
}
