#!/bin/bash
# Script to verify Google Play Services and Firebase configuration

echo "Verifying Google Play Services and Firebase Configuration"
echo "========================================================="

# Extract application ID from build.gradle.kts
APP_ID=$(grep -o 'applicationId\s*=\s*"[^"]*"' app/build.gradle.kts | grep -o '"[^"]*"' | tr -d '"')
echo "Application ID from build.gradle.kts: $APP_ID"

# Extract package name from google-services.json
FIREBASE_PKG=$(grep -o '"package_name": "[^"]*"' app/google-services.json | grep -o '"[^"]*"$' | tr -d '"')
echo "Package name from google-services.json: $FIREBASE_PKG"

# Extract FileProvider authorities from AndroidManifest.xml
AUTHORITIES=$(grep -o 'android:authorities="[^"]*"' app/src/main/AndroidManifest.xml | grep -o '"[^"]*"' | tr -d '"')
echo "FileProvider authorities from AndroidManifest.xml: $AUTHORITIES"

# Verify matching configuration
if [ "$APP_ID" == "$FIREBASE_PKG" ]; then
    echo "✅ Application ID matches Firebase package name"
else
    echo "❌ Application ID does NOT match Firebase package name"
    echo "   This can cause SecurityException with Google Play Services"
fi

# Check for potential issues with authorities
if [[ $AUTHORITIES == *"$APP_ID.service"* ]]; then
    echo "❌ WARNING: FileProvider authorities include '$APP_ID.service' which can cause issues"
    echo "   This may lead to com.example.tareamov.service.service.* package conflicts"
fi

echo ""
echo "Recommendations:"
echo "1. Ensure application ID matches Firebase package name"
echo "2. FileProvider authorities should not duplicate parts of the package name"
echo "3. If you see SecurityException related to Google Play Services, rebuild the app after fixing these issues"
