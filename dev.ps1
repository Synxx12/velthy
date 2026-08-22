# Interactive Dev Runner for Musique (Flutter-like experience)
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
        if ($line -match "^([^\s]+)\s+device\s+(.*)$") {
            $id = $matches[1].Trim()
            $info = $matches[2].Trim()
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
    return $devices
}

$Global:SelectedDevice = $TargetDevice

function Select-TargetDevice {
    $devices = Get-ConnectedDevices
    if ($devices.Count -eq 0) {
        Write-Host "[!] Tidak ada perangkat/emulator ADB yang terdeteksi." -ForegroundColor Red
        $Global:SelectedDevice = $null
        return $null
    }
    if ($devices.Count -eq 1) {
        $Global:SelectedDevice = $devices[0].Id
        $name = if ($devices[0].IsEmulator) { "Emulator ($($devices[0].Id))" } else { "$($devices[0].Model) ($($devices[0].Id))" }
        Write-Host "[*] Target Device: $name" -ForegroundColor Green
        return $Global:SelectedDevice
    }

    # Jika ada lebih dari 1 perangkat dan belum dipilih
    if (-not $Global:SelectedDevice) {
        # Prioritaskan Emulator jika aktif
        $emu = $devices | Where-Object { $_.IsEmulator } | Select-Object -First 1
        if ($emu) {
            $Global:SelectedDevice = $emu.Id
            Write-Host "[*] Otomatis memilih Emulator: $($emu.Id)" -ForegroundColor Green
        } else {
            $Global:SelectedDevice = $devices[0].Id
            Write-Host "[*] Target Device: $($devices[0].Model) ($($devices[0].Id))" -ForegroundColor Green
        }
    } else {
        # Validasi apakah device yang dipilih masih tersambung
        $match = $devices | Where-Object { $_.Id -eq $Global:SelectedDevice }
        if (-not $match) {
            $Global:SelectedDevice = $devices[0].Id
            Write-Host "[!] Device sebelumnya terputus. Beralih ke: $($devices[0].Id)" -ForegroundColor Yellow
        }
    }
    return $Global:SelectedDevice
}

function Show-DeviceMenu {
    $devices = Get-ConnectedDevices
    if ($devices.Count -eq 0) {
        Write-Host "[!] Tidak ada perangkat tersambung." -ForegroundColor Red
        return
    }
    Write-Host "`n--- Pilih Target Perangkat ---" -ForegroundColor Cyan
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $d = $devices[$i]
        $tag = if ($d.IsEmulator) { "[EMULATOR]" } else { "[HP FISIK]" }
        $current = if ($d.Id -eq $Global:SelectedDevice) { " (AKTIF)" } else { "" }
        Write-Host "  [$($i + 1)] $tag $($d.Model) - $($d.Id)$current" -ForegroundColor $(if ($current) { "Green" } else { "White" })
    }
    $choice = Read-Host "Pilih nomor perangkat (1-$($devices.Count))"
    $idx = [int]$choice - 1
    if ($idx -ge 0 -and $idx -lt $devices.Count) {
        $Global:SelectedDevice = $devices[$idx].Id
        Write-Host "[OK] Target dialihkan ke: $($devices[$idx].Model) ($($devices[$idx].Id))`n" -ForegroundColor Green
    } else {
        Write-Host "[!] Pilihan tidak valid. Tetap menggunakan: $Global:SelectedDevice" -ForegroundColor Yellow
    }
}

function Run-BuildAndLaunch {
    $device = Select-TargetDevice
    Write-Host "`n[+] Mengompilasi dev build..." -ForegroundColor Green
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    
    .\gradlew.bat assembleDevDebug
    if ($LASTEXITCODE -eq 0) {
        $apkPath = Get-ChildItem -Path "app\build\outputs\apk\dev\debug\*.apk" | Select-Object -First 1
        
        if ($apkPath -and (Test-Path $apkPath.FullName)) {
            if ($device) {
                Write-Host "[+] Memasang APK ($($apkPath.Name)) ke $device..." -ForegroundColor Cyan
                adb -s $device install -r -d -t $apkPath.FullName
                adb -s $device shell am start -n com.musique.client.dev/com.music.bitchord.MainActivity | Out-Null
            } else {
                Write-Host "[+] Memasang APK ($($apkPath.Name)) via ADB..." -ForegroundColor Cyan
                adb install -r -d -t $apkPath.FullName
                adb shell am start -n com.musique.client.dev/com.music.bitchord.MainActivity | Out-Null
            }
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

# Jalankan build pertama kali saat script dibuka
Run-BuildAndLaunch

while ($true) {
    Write-Host "`n----------------------------------------" -ForegroundColor DarkGray
    Write-Host "Target saat ini: $($Global:SelectedDevice)" -ForegroundColor Magenta
    Write-Host "Ketik 'r' (atau tekan Enter) untuk Reload ke target" -ForegroundColor Cyan
    Write-Host "Ketik 'd' untuk Ganti Target Device (Emulator / HP)" -ForegroundColor Green
    Write-Host "Ketik 'l' untuk melihat Logcat terbaru (40 baris)" -ForegroundColor Yellow
    Write-Host "Ketik 'q' untuk keluar" -ForegroundColor Gray
    Write-Host "----------------------------------------" -ForegroundColor DarkGray
    
    $key = Read-Host "Pilihan"
    
    if ($key -eq "q" -or $key -eq "exit") {
        Write-Host "Keluar dari dev runner. Sampai jumpa!" -ForegroundColor Yellow
        break
    } elseif ($key -eq "d" -or $key -eq "device") {
        Show-DeviceMenu
    } elseif ($key -eq "l" -or $key -eq "logs") {
        $device = Select-TargetDevice
        Write-Host "`n--- Logcat Terbaru (40 Baris) dari $device ---" -ForegroundColor Yellow
        if ($device) {
            adb -s $device logcat -d -t 40 -s BitChord:V AndroidRuntime:E
        } else {
            adb logcat -d -t 40 -s BitChord:V AndroidRuntime:E
        }
    } else {
        # Default (Enter atau 'r') -> Rebuild & Deploy
        Run-BuildAndLaunch
    }
}
