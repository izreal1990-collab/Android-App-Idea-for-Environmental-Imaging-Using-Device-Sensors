#!/bin/bash

# Script to add developer headers to all Kotlin files
# Developer: Jovan Blango
# Date: October 27, 2025

HEADER="/*
 * Environmental Imaging App
 * 
 * Developer: Jovan Blango
 * Copyright (c) 2025 Jovan Blango
 * 
 * An advanced Android application for environmental imaging and spatial mapping
 * using device sensors including camera, WiFi RTT, Bluetooth, acoustics, and IMU.
 * 
 * GitHub: https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors
 */

"

# Find all Kotlin files and add header if not present
find ./app/src/main/java -name "*.kt" | while read file; do
    # Check if file already has the header
    if ! grep -q "Developer: Jovan Blango" "$file"; then
        # Create temporary file with header + original content
        echo "$HEADER" > temp_file
        cat "$file" >> temp_file
        mv temp_file "$file"
        echo "Added header to: $file"
    else
        echo "Header already exists in: $file"
    fi
done

echo "Done adding headers to all Kotlin files!"
