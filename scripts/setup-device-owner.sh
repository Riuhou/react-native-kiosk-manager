#!/bin/bash

# Device Owner Setup Script for react-native-kiosk-manager
# This script helps you set up Device Owner via ADB

set -e

echo "🔧 Device Owner Setup Script"
echo "=============================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if ADB is installed
check_adb() {
    if ! command -v adb &> /dev/null; then
        print_error "ADB is not installed or not in PATH"
        echo ""
        echo "Please install Android SDK Platform Tools:"
        echo "1. Download from: https://developer.android.com/studio/releases/platform-tools"
        echo "2. Extract and add to your PATH"
        echo "3. Or install via package manager:"
        echo "   - macOS: brew install android-platform-tools"
        echo "   - Ubuntu: sudo apt install android-tools-adb"
        echo "   - Windows: Download from link above"
        exit 1
    fi
    print_success "ADB is available"
}

# Check device connection
check_device() {
    local devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    if [ $devices -eq 0 ]; then
        print_error "No devices connected"
        echo ""
        echo "Please ensure:"
        echo "1. Device is connected via USB"
        echo "2. USB debugging is enabled"
        echo "3. You've authorized the computer on the device"
        exit 1
    elif [ $devices -gt 1 ]; then
        print_warning "Multiple devices connected. This script works with single device only."
        echo ""
        echo "Connected devices:"
        adb devices
        exit 1
    fi
    print_success "Device is connected"
}

# Check if device is ready for Device Owner setup
check_device_state() {
    print_info "Checking device state..."
    
    # Check if setup wizard is complete
    local setup_complete=$(adb shell getprop ro.setupwizard.mode 2>/dev/null || echo "UNKNOWN")
    if [ "$setup_complete" != "DISABLED" ] && [ "$setup_complete" != "UNKNOWN" ]; then
        print_warning "Setup wizard may not be complete"
        echo "Setup wizard mode: $setup_complete"
    fi
    
    # Check if there are existing users
    local user_count=$(adb shell pm list users 2>/dev/null | grep "UserInfo" | wc -l || echo "0")
    if [ $user_count -gt 1 ]; then
        print_error "Multiple users detected. Device Owner can only be set on devices with no additional users."
        echo ""
        echo "Current users:"
        adb shell pm list users 2>/dev/null || echo "Could not list users"
        echo ""
        echo "Please factory reset the device and run this script again."
        exit 1
    fi
    
    print_success "Device state looks good"
}

# Get package name from user
get_package_name() {
    echo ""
    print_info "Enter your app's package name:"
    echo "Examples:"
    echo "  - com.kioskmanager.example (for the example app)"
    echo "  - com.yourcompany.yourapp (for your production app)"
    echo ""
    read -p "Package name: " PACKAGE_NAME
    
    if [ -z "$PACKAGE_NAME" ]; then
        print_error "Package name cannot be empty"
        get_package_name
        return
    fi
    
    # Basic validation
    if [[ ! $PACKAGE_NAME =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
        print_warning "Package name format looks unusual. Continue anyway? (y/N)"
        read -p "" CONTINUE
        if [[ ! $CONTINUE =~ ^[Yy]$ ]]; then
            get_package_name
            return
        fi
    fi
    
    print_success "Package name: $PACKAGE_NAME"
}

# Check if app is installed
check_app_installed() {
    print_info "Checking if app is installed..."
    
    if ! adb shell pm list packages | grep -q "package:$PACKAGE_NAME"; then
        print_error "App $PACKAGE_NAME is not installed on the device"
        echo ""
        echo "Please install your app first:"
        echo "1. Build and install via React Native: npx react-native run-android"
        echo "2. Or install APK: adb install your-app.apk"
        exit 1
    fi
    
    print_success "App is installed"
}

# Set Device Owner
set_device_owner() {
    print_info "Setting Device Owner..."
    
    local admin_component="$PACKAGE_NAME/.DeviceAdminReceiver"
    
    # Try to set device owner
    local result=$(adb shell dpm set-device-owner "$admin_component" 2>&1)
    
    if echo "$result" | grep -q "Success"; then
        print_success "Device Owner set successfully!"
    else
        print_error "Failed to set Device Owner"
        echo ""
        echo "Error details:"
        echo "$result"
        echo ""
        echo "Common causes:"
        echo "1. Device has existing users (factory reset required)"
        echo "2. Another app is already Device Owner"
        echo "3. Device is not in the correct state"
        echo "4. DeviceAdminReceiver not properly configured"
        exit 1
    fi
}

# Verify Device Owner
verify_device_owner() {
    print_info "Verifying Device Owner status..."
    
    local owners=$(adb shell dpm list-owners 2>/dev/null)
    
    if echo "$owners" | grep -q "$PACKAGE_NAME"; then
        print_success "Verification successful! Your app is now Device Owner."
        echo ""
        echo "Device Owner details:"
        echo "$owners"
    else
        print_error "Verification failed. Device Owner was not set properly."
        echo ""
        echo "Current owners:"
        echo "$owners"
    fi
}

# Main execution
main() {
    echo "This script will help you set up Device Owner for your Android app."
    echo ""
    print_warning "IMPORTANT: This should only be done on factory reset devices or devices without user accounts!"
    echo ""
    read -p "Do you want to continue? (y/N): " CONFIRM
    
    if [[ ! $CONFIRM =~ ^[Yy]$ ]]; then
        echo "Setup cancelled."
        exit 0
    fi
    
    echo ""
    print_info "Starting Device Owner setup process..."
    echo ""
    
    check_adb
    check_device
    check_device_state
    get_package_name
    check_app_installed
    
    echo ""
    print_warning "Ready to set Device Owner for: $PACKAGE_NAME"
    read -p "Proceed? (y/N): " FINAL_CONFIRM
    
    if [[ ! $FINAL_CONFIRM =~ ^[Yy]$ ]]; then
        echo "Setup cancelled."
        exit 0
    fi
    
    echo ""
    set_device_owner
    verify_device_owner
    
    echo ""
    print_success "Device Owner setup complete!"
    echo ""
    echo "Next steps:"
    echo "1. Test kiosk functionality in your app"
    echo "2. Configure additional device policies as needed"
    echo "3. Deploy to production devices using similar process"
}

# Run main function
main "$@" 