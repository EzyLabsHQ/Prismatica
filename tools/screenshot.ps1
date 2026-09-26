<#
  Screenshots the running Minecraft window to a PNG.

  There is no in-game screenshot tooling that reaches the host desktop, and the
  things worth looking at here are one or two pixels wide at a time: a stray
  column on a button edge, a missing corner row. Reading a log cannot find
  those, so the screen has to be looked at.

  Captures the window rect rather than the whole desktop, so the result is
  exactly the game at native resolution and can be inspected pixel by pixel.

  Usage:
      .\tools\screenshot.ps1 -Name button-edges
#>
[CmdletBinding()]
param(
    [string] $Name = 'shot',
    [string] $OutDir = (Join-Path $env:TEMP 'prismatica-shots')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class Win {
    public delegate bool CB(IntPtr h, IntPtr l);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool EnumWindows(CB cb, IntPtr l);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);

    // Every top level window of one process, as "handle~area~left~top~w~h".
    public static List<string> ForPid(uint target) {
        var res = new List<string>();
        EnumWindows((h, l) => {
            uint p; GetWindowThreadProcessId(h, out p);
            if (p != target) return true;
            RECT r;
            if (!GetWindowRect(h, out r)) return true;
            int w = r.Right - r.Left, ht = r.Bottom - r.Top;
            res.Add(h.ToInt64() + "~" + (w * ht) + "~" + r.Left + "~" + r.Top + "~" + w + "~" + ht);
            return true;
        }, IntPtr.Zero);
        return res;
    }
}
'@

$proc = Get-Process java -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowHandle -ne 0 } |
    Select-Object -First 1

if (-not $proc) { throw "no java process with a window; is the game running?" }

# MainWindowHandle is the window to use. Picking the largest window belonging to
# the process instead, which looks like the obvious heuristic, is wrong: this JVM
# also owns a couple of invisible 1440x753 helper windows of class "N" that are
# always larger than the real one, so a largest-area search reliably lands on a
# helper and captures whatever is on screen at its coordinates, which here is the
# editor. The cost of getting this wrong is a screenshot of the wrong program.
$h = $proc.MainWindowHandle
if ($h -eq [IntPtr]::Zero) { throw "pid $($proc.Id) has no main window" }

# SW_RESTORE, then temporarily TOPMOST, then back to normal.
#
# SW_RESTORE alone is not enough: a minimised window is parked at
# (-32000,-32000), and grabbing that rectangle yields whatever happens to sit at
# the top left of the desktop, which is how a screenshot ends up showing
# something that is not the game at all.
#
# SetForegroundWindow is also not enough, and fails silently: Windows refuses it
# when the caller is not the foreground process, so the game stayed behind this
# window and CopyFromScreen captured this window instead. TOPMOST goes through
# SetWindowPos, which is not subject to that rule, so it actually raises the game
# above whatever else is on screen. It is undone below.
[void][Win]::ShowWindow($h, 9)
Start-Sleep -Milliseconds 250
[void][Win]::SetWindowPos($h, [IntPtr](-1), 0, 0, 0, 0, 0x0003)  # TOPMOST, NOSIZE|NOMOVE
Start-Sleep -Milliseconds 250
[void][Win]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 800

# Measured only after restoring: a minimised window reports (-32000,-32000) and
# an arbitrary 160x28, which is not the size of anything worth capturing.
$rect = New-Object Win+RECT
if (-not [Win]::GetWindowRect($h, [ref]$rect)) { throw "GetWindowRect failed" }
$winX = $rect.Left
$winY = $rect.Top
$w = $rect.Right - $rect.Left
$hgt = $rect.Bottom - $rect.Top

if ($winX -le -30000 -or $w -lt 200 -or $hgt -lt 200) {
    throw ("window did not come back: pos=({0},{1}) size={2}x{3}" -f $winX, $winY, $w, $hgt)
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$file = Join-Path $OutDir ("{0}-{1}.png" -f $Name, (Get-Date -Format 'HHmmss'))

$bmp = New-Object Drawing.Bitmap $w, $hgt
$g = [Drawing.Graphics]::FromImage($bmp)
try {
    $g.CopyFromScreen($winX, $winY, 0, 0, $bmp.Size)
    $bmp.Save($file, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $g.Dispose()
    $bmp.Dispose()
}

Write-Host ("saved {0}  ({1}x{2} from pid {3} '{4}')" -f $file, $w, $hgt, $proc.Id, $proc.MainWindowTitle)
Write-Host $file

# Drop the window back out of TOPMOST. Leaving it there would put the game above
# everything for the rest of the session, including over the user's own windows.
[void][Win]::SetWindowPos($h, [IntPtr](-2), 0, 0, 0, 0, 0x0003)  # NOTOPMOST, NOSIZE|NOMOVE
