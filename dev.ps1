# Interactive Dev Runner for Velthy (Single-Key Responsive)
param(
    [string]$TargetDevice = ""
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Velthy Dev Runner (Live Dev)         " -ForegroundColor Yellow
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

    try {
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
    } catch {
        return $null
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
            
            # Bersihkan file staging APK sementara di HP agar tidak menumpuk di internal storage
            adb -s "$device" shell "rm -f /data/local/tmp/*.apk" 2>$null
            
            adb -s "$device" shell am start -n com.velthy.client.dev/com.velthy.client.MainActivity | Out-Null
            
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

function Take-DeviceScreenshot {
    if (-not $Global:SelectedDevice) {
        Write-Host "`n[!] Tidak ada target perangkat yang aktif." -ForegroundColor Red
        return
    }

    $device = $Global:SelectedDevice
    $screenshotDir = Join-Path $PSScriptRoot "screenshots"
    if (-not (Test-Path $screenshotDir)) {
        New-Item -ItemType Directory -Path $screenshotDir -Force | Out-Null
    }

    $timestamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
    $fileName = "velthy_${timestamp}.png"
    $localPath = Join-Path $screenshotDir $fileName
    $remotePath = "/data/local/tmp/velthy_shot_${timestamp}.png"

    Write-Host "`n[+] Mengambil screenshot layar dari $device..." -ForegroundColor Cyan
    adb -s "$device" shell screencap -p "$remotePath"
    if ($LASTEXITCODE -eq 0) {
        adb -s "$device" pull "$remotePath" "$localPath" | Out-Null
        adb -s "$device" shell rm -f "$remotePath" 2>$null

        if (Test-Path $localPath) {
            Write-Host "[OK] 📸 Screenshot tersimpan: $localPath" -ForegroundColor Green
            # Buka otomatis di image viewer bawaan OS
            Start-Process "$localPath"
        } else {
            Write-Host "[X] Gagal mengunduh screenshot ke PC." -ForegroundColor Red
        }
    } else {
        Write-Host "[X] Gagal mengeksekusi screencap di perangkat." -ForegroundColor Red
    }
}

function Restart-AppOnly {
    if (-not $Global:SelectedDevice) {
        Write-Host "`n[!] Tidak ada target perangkat yang aktif." -ForegroundColor Red
        return
    }
    $device = $Global:SelectedDevice
    Write-Host "`n[+] Me-restart aplikasi Velthy di $device..." -ForegroundColor Cyan
    adb -s "$device" shell am force-stop com.velthy.client.dev
    adb -s "$device" shell am start -n com.velthy.client.dev/com.velthy.client.MainActivity | Out-Null
    Write-Host "[OK] ⚡ Aplikasi dibuka kembali tanpa compile ulang.`n" -ForegroundColor Green
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

# 3. Main interactive loop dengan penanganan aman Ctrl+C dan shortcut instan
try {
    while ($true) {
        Write-Host "`n----------------------------------------------------------------" -ForegroundColor DarkGray
        Write-Host "Target: $($Global:SelectedDevice)" -ForegroundColor Magenta
        Write-Host " [r] Reload (Build)  [o] Quick Restart  [s] Screenshot  [k] Clean" -ForegroundColor Cyan
        Write-Host " [x] Clear Cache     [l] Logcat         [d] Ganti Dev   [q] Keluar" -ForegroundColor Cyan
        Write-Host " (Tekan hurufnya langsung | Ctrl+C / [q] untuk keluar)" -ForegroundColor DarkGray
        Write-Host "----------------------------------------------------------------" -ForegroundColor DarkGray

        try {
            $keyInfo = [System.Console]::ReadKey($true)
        } catch {
            Write-Host "`nKeluar dari dev runner. 👋" -ForegroundColor Yellow
            break
        }

        $keyChar = $keyInfo.KeyChar.ToString().ToLower()
        $keyCode = $keyInfo.Key
        $isCtrlC = ($keyCode -eq [System.ConsoleKey]::C -and ($keyInfo.Modifiers -band [System.ConsoleModifiers]::Control))

        if ($keyChar -eq "q" -or $keyCode -eq [System.ConsoleKey]::Escape -or $isCtrlC) {
            Write-Host "`nKeluar dari dev runner. Sampai jumpa! 👋" -ForegroundColor Yellow
            break
        } elseif ($keyChar -eq "s") {
            Take-DeviceScreenshot
        } elseif ($keyChar -eq "o") {
            Restart-AppOnly
        } elseif ($keyChar -eq "d" -or $keyCode -eq [System.ConsoleKey]::Tab) {
            Select-TargetDevice -Interactive $true
        } elseif ($keyChar -eq "k") {
            Write-Host "`n[+] Menjalankan Gradle Clean..." -ForegroundColor Yellow
            .\gradlew.bat clean
            if ($LASTEXITCODE -eq 0) {
                Write-Host "[OK] Folder build dan cache intermediate berhasil dibersihkan!`n" -ForegroundColor Green
            }
        } elseif ($keyChar -eq "x") {
            if ($Global:SelectedDevice) {
                Write-Host "`n[+] Membersihkan data & cache aplikasi di $Global:SelectedDevice..." -ForegroundColor Yellow
                adb -s "$Global:SelectedDevice" shell pm clear com.velthy.client.dev
                Write-Host "[OK] Data & disk cache aplikasi berhasil dikosongkan.`n" -ForegroundColor Green
            }
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
            Write-Host "   Velthy Dev Runner (Live Dev)         " -ForegroundColor Yellow
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
} finally {
    # Pastikan kursor konsol kembali normal saat keluar
    [System.Console]::CursorVisible = $true
}
