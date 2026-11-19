# Keeps the game windows of test runs off the screen, so they take neither the screen, the focus nor the mouse while
# someone uses the computer. selftest.sh starts this for each run. Only games started from this project are touched:
# a Minecraft you are playing yourself is left alone. Hidden windows keep drawing, so screenshots still work.
#
# Usage: test-window-guard.ps1 <file that, once it exists, ends this script>
param([string]$StopFile)

Add-Type @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class TestWindows {
	delegate bool Visit(IntPtr window, IntPtr data);
	[DllImport("user32.dll")] static extern bool EnumWindows(Visit visit, IntPtr data);
	[DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
	[DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
	[DllImport("user32.dll")] static extern bool ShowWindow(IntPtr window, int command);
	public static int Hide(HashSet<uint> processes) {
		int hidden = 0;
		EnumWindows((window, data) => {
			uint process;
			GetWindowThreadProcessId(window, out process);
			if (processes.Contains(process) && IsWindowVisible(window)) {
				ShowWindow(window, 0);
				hidden++;
			}
			return true;
		}, IntPtr.Zero);
		return hidden;
	}
}
"@

$games = New-Object 'System.Collections.Generic.HashSet[uint32]'
$round = 0
# A run is over long before this, even if whoever started the guard could not stop it.
$giveUp = (Get-Date).AddMinutes(40)
while (-not (Test-Path -LiteralPath $StopFile) -and (Get-Date) -lt $giveUp) {
	# Looking up processes is slow, hiding is not: new games are looked for every half second, windows every 30 ms.
	if ($round % 16 -eq 0) {
		$games.Clear()
		Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
			Where-Object { $_.CommandLine -match 'ragdolldeaths|Ragdoll Deaths' -and $_.CommandLine -notmatch 'GradleDaemon|GradleWrapperMain' } |
			ForEach-Object { [void]$games.Add([uint32]$_.ProcessId) }
	}
	if ($games.Count -gt 0) {
		[void][TestWindows]::Hide($games)
	}
	$round++
	Start-Sleep -Milliseconds 30
}
Remove-Item -LiteralPath $StopFile -ErrorAction SilentlyContinue
