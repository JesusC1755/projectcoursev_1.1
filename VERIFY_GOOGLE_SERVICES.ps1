# PowerShell script to verify Google Play Services and Firebase configuration

Write-Host "Verifying Google Play Services and Firebase Configuration"
Write-Host "========================================================="

# Extract application ID from build.gradle.kts
$buildGradleContent = Get-Content -Path "app\build.gradle.kts" -Raw
$appIdMatch = [regex]::Match($buildGradleContent, 'applicationId\s*=\s*"([^"]*)"')
$APP_ID = $appIdMatch.Groups[1].Value
Write-Host "Application ID from build.gradle.kts: $APP_ID"

# Extract package name from google-services.json
$googleServicesContent = Get-Content -Path "app\google-services.json" -Raw
$packageMatch = [regex]::Match($googleServicesContent, '"package_name": "([^"]*)"')
$FIREBASE_PKG = $packageMatch.Groups[1].Value
Write-Host "Package name from google-services.json: $FIREBASE_PKG"

# Extract FileProvider authorities from AndroidManifest.xml
$manifestContent = Get-Content -Path "app\src\main\AndroidManifest.xml" -Raw
$authoritiesMatch = [regex]::Match($manifestContent, 'android:authorities="([^"]*)"')
$AUTHORITIES = $authoritiesMatch.Groups[1].Value
Write-Host "FileProvider authorities from AndroidManifest.xml: $AUTHORITIES"

# Verify matching configuration
if ($APP_ID -eq $FIREBASE_PKG) {
    Write-Host "✓ Application ID matches Firebase package name" -ForegroundColor Green
} else {
    Write-Host "✗ Application ID does NOT match Firebase package name" -ForegroundColor Red
    Write-Host "   This can cause SecurityException with Google Play Services" -ForegroundColor Yellow
}

# Check for potential issues with authorities
if ($AUTHORITIES -like "*$APP_ID.service*") {
    Write-Host "✗ WARNING: FileProvider authorities include '$APP_ID.service' which can cause issues" -ForegroundColor Red
    Write-Host "   This may lead to com.example.tareamov.service.service.* package conflicts" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Recommendations:" -ForegroundColor Cyan
Write-Host "1. Ensure application ID matches Firebase package name"
Write-Host "2. FileProvider authorities should not duplicate parts of the package name"
Write-Host "3. If you see SecurityException related to Google Play Services, rebuild the app after fixing these issues"
