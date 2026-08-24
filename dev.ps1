# Interactive Dev Runner for Musique (Single-Key Responsive)
param(
    [string]$TargetDevice = ""
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Musique Dev Runner (Live Dev)        " -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

function Get-ConnectedDevices {
    $raw = adb devices -l
    $devices = @()
    foreach ($line in $raw) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not ($trimmed -match "^List of devices attached")) {
            if ($trimmed -match "^(.+?)\s+device(\s+(.*))?$") {
                $id = $matches[1].Trim()
                $info = if ($matches[3]) { $matches[3].Trim() } else { "" }
                $model = if ($info -match "model:([^\s]+)") { $matches[1] } else { $id }
                $isEmulator = $id -match "^emulator-"
                $devices += [PSCustomObject]@{
                    Id = $id
                    Model = $model
                    IsEmulator = $isEmulator
                    Info = $info
                }
            }
        }
    }
    return $devices
}

$Global:SelectedDevice = $TargetDevice

function Select-TargetDevice ([bool]$Interactive = $false) {
    $devices = Get-ConnectedDevices
    
    if ($devices.Count -eq 0) {
        Write-Host "`n[!] Tidak ada perangkat/emulator ADB yang aktif." -ForegroundColor Red
        Write-Host "    Pastikan Emulator atau HP dengan USB/Wireless Debugging sudah menyala.`n" -ForegroundColor Yellow
        $Global:SelectedDevice = $null
        return $null
    }

    # Jika hanya ada 1 perangkat dan tidak diminta memilih ulang
    if ($devices.Count -eq 1 -and -not $Interactive) {
        $d = $devices[0]
        $tag = if ($d.IsEmulator) { "[EMULATOR]" } else { "[HP FISIK]" }
        $Global:SelectedDevice = $d.Id
        Write-Host "[*] Target Terdeteksi: $tag $($d.Model) ($($d.Id))`n" -ForegroundColor Green
        return $Global:SelectedDevice
    }

    # Jika ada lebih dari 1 perangkat (atau dipanggil interaktif)
    Write-Host "`n--- Pilih Target Perangkat ---" -ForegroundColor Cyan
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $d = $devices[$i]
        $tag = if ($d.IsEmulator) { "[EMULATOR]" } else { "[HP FISIK]" }
        $current = if ($d.Id -eq $Global:SelectedDevice) { " (AKTIF)" } else { "" }
        Write-Host "  [$($i + 1)] $tag $($d.Model) - $($d.Id)$current" -ForegroundColor $(if ($current) { "Green" } else { "White" })
    }
    Write-Host "Tekan angka pilihan (1-$($devices.Count)): " -NoNewline -ForegroundColor Yellow

    $key = [System.Console]::ReadKey($true)
    $char = $key.KeyChar.ToString()
    if ($char -match "^[1-9]$") {
        $idx = [int]$char - 1
        if ($idx -ge 0 -and $idx -lt $devices.Count) {
            $Global:SelectedDevice = $devices[$idx].Id
            $tag = if ($devices[$idx].IsEmulator) { "[EMULATOR]" } else { "[HP FISIK]" }
            Write-Host "$char" -ForegroundColor Green
            Write-Host "[OK] Target dipilih: $tag $($devices[$idx].Model) ($($devices[$idx].Id))`n" -ForegroundColor Green
            return $Global:SelectedDevice
        }
    }

    # Fallback default ke perangkat pertama jika input lain
    $Global:SelectedDevice = $devices[0].Id
    Write-Host "1 (Default)`n" -ForegroundColor DarkGray
    return $Global:SelectedDevice
}

function Run-BuildAndLaunch {
    if (-not $Global:SelectedDevice) {
        $Global:SelectedDevice = Select-TargetDevice
        if (-not $Global:SelectedDevice) {
            Write-Host "[X] Build dibatalkan: tidak ada target device." -ForegroundColor Red
            return
        }
    }

    $device = $Global:SelectedDevice
    Write-Host "[+] Mengompilasi dev build..." -ForegroundColor Green
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    
    .\gradlew.bat assembleDevDebug
    if ($LASTEXITCODE -eq 0) {
        $apkPath = Get-ChildItem -Path "app\build\outputs\apk\dev\debug\*.apk" | Select-Object -First 1
        
        if ($apkPath -and (Test-Path $apkPath.FullName)) {
            Write-Host "[+] Memasang APK ($($apkPath.Name)) ke $device..." -ForegroundColor Cyan
            adb -s "$device" install -r -d -t $apkPath.FullName
            adb -s "$device" shell am start -n com.musique.client.dev/com.music.musique.MainActivity | Out-Null
            
            $stopwatch.Stop()
            Write-Host "[OK] Berhasil dipasang dan dibuka dalam $($stopwatch.Elapsed.TotalSeconds.ToString("0.0"))s!" -ForegroundColor Green
        } else {
            $stopwatch.Stop()
            Write-Host "[X] APK file tidak ditemukan di app\build\outputs\apk\dev\debug" -ForegroundColor Red
        }
    } else {
        $stopwatch.Stop()
        Write-Host "[X] Build gagal. Cek error di atas." -ForegroundColor Red
    }
}

# 1. Pilih target device di awal jika ada lebih dari 1 perangkat
$devices = Get-ConnectedDevices
if ($devices.Count -gt 1) {
    Select-TargetDevice -Interactive $true | Out-Null
} else {
    Select-TargetDevice | Out-Null
}

# 2. Jalankan build & launch pertama kali
Run-BuildAndLaunch

# 3. Main interactive loop
while ($true) {
    Write-Host "`n----------------------------------------" -ForegroundColor DarkGray
    Write-Host "Target: $($Global:SelectedDevice)" -ForegroundColor Magenta
    Write-Host " [r] Reload  [d] Ganti Device  [l] Logcat  [c] Clear  [q] Keluar" -ForegroundColor Cyan
    Write-Host " (Tekan hurufnya langsung tanpa perlu Enter)" -ForegroundColor DarkGray
    Write-Host "----------------------------------------" -ForegroundColor DarkGray

    $keyInfo = [System.Console]::ReadKey($true)
    $keyChar = $keyInfo.KeyChar.ToString().ToLower()
    $keyCode = $keyInfo.Key

    if ($keyChar -eq "q" -or $keyCode -eq [System.ConsoleKey]::Escape) {
        Write-Host "`nKeluar dari dev runner. Sampai jumpa! 👋" -ForegroundColor Yellow
        break
    } elseif ($keyChar -eq "d" -or $keyCode -eq [System.ConsoleKey]::Tab) {
        Select-TargetDevice -Interactive $true
    } elseif ($keyChar -eq "l") {
        Write-Host "`n--- Logcat Terbaru (40 Baris) dari $Global:SelectedDevice ---" -ForegroundColor Yellow
        if ($Global:SelectedDevice) {
            adb -s "$Global:SelectedDevice" logcat -d -t 40 -s Musique:V AndroidRuntime:E
        } else {
            adb logcat -d -t 40 -s Musique:V AndroidRuntime:E
        }
    } elseif ($keyChar -eq "c") {
        Clear-Host
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "   Musique Dev Runner (Live Dev)        " -ForegroundColor Yellow
        Write-Host "========================================" -ForegroundColor Cyan
    } elseif ($keyChar -eq "r" -or $keyCode -eq [System.ConsoleKey]::Enter -or $keyCode -eq [System.ConsoleKey]::Spacebar) {
        Run-BuildAndLaunch
    } elseif ($keyChar -match "^[1-9]$") {
        $devs = Get-ConnectedDevices
        $idx = [int]$keyChar - 1
        if ($idx -ge 0 -and $idx -lt $devs.Count) {
            $Global:SelectedDevice = $devs[$idx].Id
            $tag = if ($devs[$idx].IsEmulator) { "[EMULATOR]" } else { "[HP FISIK]" }
            Write-Host "`n[OK] ⚡ Beralih langsung ke: $tag $($devs[$idx].Model) ($($devs[$idx].Id))`n" -ForegroundColor Green
        }
    }
}
