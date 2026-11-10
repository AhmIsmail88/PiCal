$ErrorActionPreference = 'Stop'
$taskProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskSigningDir = Join-Path $taskProjectRoot '.signing'
$taskStore = Join-Path $taskSigningDir 'pical-release.p12'
$taskProperties = Join-Path $taskSigningDir 'keystore.properties'
if ((Test-Path -LiteralPath $taskStore) -or (Test-Path -LiteralPath $taskProperties)) {
    throw 'Signing material already exists. Reuse it; this script never replaces a release key.'
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or 21 first.' }
$taskKeytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $taskKeytool)) { throw 'keytool was not found under JAVA_HOME.' }
New-Item -ItemType Directory -Force -Path $taskSigningDir | Out-Null
$taskAcl = Get-Acl -LiteralPath $taskSigningDir
$taskAcl.SetAccessRuleProtection($true, $false)
$taskInheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
$taskUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
foreach ($taskPrincipal in @($taskUser, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $taskRule = [Security.AccessControl.FileSystemAccessRule]::new($taskPrincipal, 'FullControl', $taskInheritance,
        [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow)
    $taskAcl.AddAccessRule($taskRule)
}
Set-Acl -LiteralPath $taskSigningDir -AclObject $taskAcl
$taskPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:PICAL_KEY_GENERATION_PASSWORD = $taskPassword
try {
    & $taskKeytool -genkeypair -keystore $taskStore -storetype PKCS12 -alias pical-release -keyalg RSA -keysize 3072 `
        -validity 10000 -dname 'CN=PiCal Release' -storepass:env PICAL_KEY_GENERATION_PASSWORD `
        -keypass:env PICAL_KEY_GENERATION_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
    $taskText = "storeFile=pical-release.p12`nstoreType=PKCS12`nkeyAlias=pical-release`nstorePassword=$taskPassword`nkeyPassword=$taskPassword`n"
    [IO.File]::WriteAllText($taskProperties, $taskText, [Text.UTF8Encoding]::new($false))
    Write-Output 'Release signing key created in .signing; credentials were not printed.'
} finally {
    [Environment]::SetEnvironmentVariable('PICAL_KEY_GENERATION_PASSWORD', $null, 'Process')
    $taskPassword = $null
    $taskText = $null
}
