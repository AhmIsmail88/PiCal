$ErrorActionPreference = 'Stop'
$reviewRoot = Split-Path $PSScriptRoot -Parent
$reviewJdk = 'C:\Users\PC\.jdks\jbr-21.0.11'
$reviewStdlib = Get-ChildItem 'C:\Users\PC\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\1.9.24' -Recurse -Filter 'kotlin-stdlib-1.9.24.jar' | Select-Object -First 1 -ExpandProperty FullName
$reviewClasspath = "$reviewRoot\app\build\tmp\kotlin-classes\debug;$reviewStdlib"
& "$reviewJdk\bin\java.exe" --class-path $reviewClasspath "$PSScriptRoot\CalcReviewRepro.java"
if ($LASTEXITCODE -ne 0) { throw 'Review reproduction failed.' }
