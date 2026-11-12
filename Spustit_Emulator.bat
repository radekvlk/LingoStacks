@echo off
setlocal EnableExtensions
set "ANDROID_SDK_ROOT=C:\Users\radek.vlk\.gemini\antigravity\scratch\AndroidCLI\appdata\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "ANDROID_USER_HOME=C:\Users\radek.vlk\.gemini\antigravity\scratch\AndroidCLI\user_home"
set "ANDROID_AVD_HOME=%ANDROID_USER_HOME%\avd"
set "PATH=%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\emulator;%PATH%"
set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
set "EMU=%ANDROID_SDK_ROOT%\emulator\emulator.exe"

if not exist "%EMU%" (
    echo CHYBA: Nenalezen emulator.exe na ceste:
    echo %EMU%
    pause
    exit /b 1
)

adb start-server >nul 2>&1
adb devices | findstr /R "emulator-5554.*device" >nul
if errorlevel 1 (
    del /q "%ANDROID_AVD_HOME%\medium_phone.avd\*.lock" >nul 2>&1
    for /d %%D in ("%ANDROID_AVD_HOME%\medium_phone.avd\*.lock") do rd /s /q "%%D" >nul 2>&1
    echo Spoustim Android Emulator ^(medium_phone^) s mikrofonem...
    start "Android Emulator" "%EMU%" -avd medium_phone -allow-host-audio
) else (
    echo Emulator uz bezi!
)

echo Cekam na nabehnuti systemu...
adb wait-for-device
:waitboot
set "BOOT="
for /f "delims=" %%i in ('adb shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%i"
if not "%BOOT%"=="1" (
    timeout /t 2 /nobreak >nul
    goto waitboot
)

if exist "%~dp0LingoWords.apk" (
    echo Instaluji nejnovejsi LingoWords.apk...
    adb install -r "%~dp0LingoWords.apk"
    if errorlevel 1 (
        echo Odinstalovavam starsi podpis a instaluji nacisto...
        adb uninstall cz.slovickaspeak.app >nul 2>&1
        adb install "%~dp0LingoWords.apk"
    )
) else (
    echo UPOZORNENI: Soubor LingoWords.apk nebyl nalezen.
)

adb shell pm grant cz.slovickaspeak.app android.permission.RECORD_AUDIO >nul 2>&1
adb shell am start -n cz.slovickaspeak.app/.MainActivity
if errorlevel 1 (
    echo Nepodarilo se spustit aplikaci.
    pause
    exit /b 1
)
echo Hotovo! LingoWords bezi v emulatoru.
timeout /t 3 /nobreak >nul
