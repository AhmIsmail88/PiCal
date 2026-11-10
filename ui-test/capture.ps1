param([string]$Name = 'current')
$adb = 'F:/SDK/platform-tools/adb.exe'
& $adb -s AUYFVB5905066578 shell uiautomator dump /sdcard/flowtrack-ui.xml | Out-Null
& $adb -s AUYFVB5905066578 pull /sdcard/flowtrack-ui.xml "$PSScriptRoot/current.xml" 2>&1 | Out-Null
& $adb -s AUYFVB5905066578 shell screencap -p /sdcard/flowtrack-ui.png
& $adb -s AUYFVB5905066578 pull /sdcard/flowtrack-ui.png "$PSScriptRoot/$Name.png" 2>&1 | Out-Null
[xml]$ui = Get-Content "$PSScriptRoot/current.xml"
$ui.SelectNodes('//node') | Where-Object { $_.text -ne '' -or $_.'content-desc' -ne '' -or $_.class -eq 'android.widget.EditText' } | ForEach-Object { '{0} | {1} | {2} | {3}' -f $_.text, $_.'content-desc', $_.bounds, $_.class }
