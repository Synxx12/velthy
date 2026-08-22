# Interactive Dev Runner for Musique (Flutter-like experience)
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Musique Dev Runner (Live Dev)        " -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

function Get-AdbDevice {
    $raw = adb devices
    $lines = $raw | Where-Object { $_ -match "\tdevice$" }
    if ($lines) {
        $first = ($lines | Select-Object -First 1).Split("`t")[0].Trim()
        return $first
    }
    return $null
}

function Run-BuildAndLaunch {
    Write-Host "`n[+] Mengompilasi dev build..." -ForegroundColor Green
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    
    .\gradlew.bat assembleDevDebug
    if ($LASTEXITCODE -eq 0) {
        $apkPath = Get-ChildItem -Path "app\build\outputs\apk\dev\debug\*.apk" | Select-Object -First 1
        $targetDevice = Get-AdbDevice
        
        if ($apkPath -and (Test-Path $apkPath.FullName)) {
            Write-Host "[+] Memasang APK ($($apkPath.Name)) ke HP via ADB..." -ForegroundColor Cyan
            if ($targetDevice) {
                adb -s $targetDevice install -r -d -t $apkPath.FullName
                adb -s $targetDevice shell am start -n com.musique.client.dev/com.music.bitchord.MainActivity | Out-Null
            } else {
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
    Write-Host "Ketik 'r' (atau tekan Enter) untuk Reload ke HP" -ForegroundColor Cyan
    Write-Host "Ketik 'l' untuk melihat Logcat terbaru (40 baris)" -ForegroundColor Yellow
    Write-Host "Ketik 'q' untuk keluar" -ForegroundColor Gray
    Write-Host "----------------------------------------" -ForegroundColor DarkGray
    
    $key = Read-Host "Pilihan"
    
    if ($key -eq "q" -or $key -eq "exit") {
        Write-Host "Keluar dari dev runner. Sampai jumpa!" -ForegroundColor Yellow
        break
    } elseif ($key -eq "l" -or $key -eq "logs") {
        $targetDevice = Get-AdbDevice
        Write-Host "`n--- Logcat Terbaru (40 Baris) ---" -ForegroundColor Yellow
        if ($targetDevice) {
            adb -s $targetDevice logcat -d -t 40 -s BitChord:V AndroidRuntime:E
        } else {
            adb logcat -d -t 40 -s BitChord:V AndroidRuntime:E
        }
    } else {
        # Default (Enter atau 'r') -> Rebuild & Deploy
        Run-BuildAndLaunch
    }
}
