@echo off
setlocal enabledelayedexpansion

REM Device Owner Setup Script for react-native-kiosk-manager (Windows)
REM This script helps you set up Device Owner via ADB

title Device Owner Setup Script

echo.
echo 🔧 Device Owner Setup Script
echo ==============================
echo.

REM Check if ADB is installed
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ ADB is not installed or not in PATH
    echo.
    echo Please install Android SDK Platform Tools:
    echo 1. Download from: https://developer.android.com/studio/releases/platform-tools
    echo 2. Extract and add to your PATH
    echo 3. Restart command prompt after adding to PATH
    pause
    exit /b 1
)
echo ✅ ADB is available

REM Check device connection
for /f "skip=1 tokens=*" %%i in ('adb devices 2^>nul') do (
    if not "%%i"=="" (
        set device_count=1
    )
)

if not defined device_count (
    echo ❌ No devices connected
    echo.
    echo Please ensure:
    echo 1. Device is connected via USB
    echo 2. USB debugging is enabled
    echo 3. You've authorized the computer on the device
    pause
    exit /b 1
)
echo ✅ Device is connected

REM Get package name from user
echo.
echo ℹ️  Enter your app's package name:
echo Examples:
echo   - com.kioskmanager.example (for the example app)
echo   - com.yourcompany.yourapp (for your production app)
echo.
set /p PACKAGE_NAME="Package name: "

if "%PACKAGE_NAME%"=="" (
    echo ❌ Package name cannot be empty
    pause
    exit /b 1
)

echo ✅ Package name: %PACKAGE_NAME%

REM Check if app is installed
echo.
echo ℹ️  Checking if app is installed...
adb shell pm list packages | findstr "package:%PACKAGE_NAME%" >nul
if %errorlevel% neq 0 (
    echo ❌ App %PACKAGE_NAME% is not installed on the device
    echo.
    echo Please install your app first:
    echo 1. Build and install via React Native: npx react-native run-android
    echo 2. Or install APK: adb install your-app.apk
    pause
    exit /b 1
)
echo ✅ App is installed

REM Warning and confirmation
echo.
echo ⚠️  IMPORTANT: This should only be done on factory reset devices!
echo.
set /p CONFIRM="Do you want to continue? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo Setup cancelled.
    pause
    exit /b 0
)

REM Set Device Owner
echo.
echo ℹ️  Setting Device Owner...
set ADMIN_COMPONENT=%PACKAGE_NAME%/.DeviceAdminReceiver

adb shell dpm set-device-owner "%ADMIN_COMPONENT%" 2>temp_error.txt
if %errorlevel% neq 0 (
    echo ❌ Failed to set Device Owner
    echo.
    echo Error details:
    type temp_error.txt
    echo.
    echo Common causes:
    echo 1. Device has existing users (factory reset required)
    echo 2. Another app is already Device Owner
    echo 3. Device is not in the correct state
    echo 4. DeviceAdminReceiver not properly configured
    del temp_error.txt
    pause
    exit /b 1
)

del temp_error.txt
echo ✅ Device Owner set successfully!

REM Verify Device Owner
echo.
echo ℹ️  Verifying Device Owner status...
adb shell dpm list-owners | findstr "%PACKAGE_NAME%" >nul
if %errorlevel% neq 0 (
    echo ❌ Verification failed. Device Owner was not set properly.
    echo.
    echo Current owners:
    adb shell dpm list-owners
) else (
    echo ✅ Verification successful! Your app is now Device Owner.
    echo.
    echo Device Owner details:
    adb shell dpm list-owners
)

echo.
echo ✅ Device Owner setup complete!
echo.
echo Next steps:
echo 1. Test kiosk functionality in your app
echo 2. Configure additional device policies as needed
echo 3. Deploy to production devices using similar process

pause 