#!/usr/bin/env python3
"""
Script to merge strings.xml files from app-common module to app module.
This script merges all string entries from app-common into the corresponding app module files.
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

def merge_strings_xml(app_common_file, app_file):
    """Merge strings from app-common file into app file."""
    print(f"Processing: {app_common_file.name} -> {app_file.name}")
    
    # Read the app-common strings.xml content
    with open(app_common_file, 'r', encoding='utf-8') as f:
        common_content = f.read()
    
    # Read the app strings.xml content
    with open(app_file, 'r', encoding='utf-8') as f:
        app_content = f.read()
    
    # Extract string entries from app-common (including comments)
    # Find everything between <resources> and </resources> tags, excluding the tags themselves
    common_match = re.search(r'<resources[^>]*>(.*?)</resources>', common_content, re.DOTALL)
    if not common_match:
        print(f"  Warning: No resources found in {app_common_file}")
        return False
    
    common_strings = common_match.group(1).strip()
    
    if not common_strings:
        print(f"  Warning: No string content found in {app_common_file}")
        return False
    
    # Find the insertion point in the app file (before </resources>)
    app_match = re.search(r'(.*?)(\s*</resources>)', app_content, re.DOTALL)
    if not app_match:
        print(f"  Error: Invalid XML structure in {app_file}")
        return False
    
    # Merge the content
    before_closing = app_match.group(1)
    closing_tag = app_match.group(2)
    
    # Add the common strings with proper spacing
    merged_content = f"{before_closing}\n\n    <!-- Strings from app-common -->\n{common_strings}\n{closing_tag}"
    
    # Write the merged content back to the app file
    with open(app_file, 'w', encoding='utf-8') as f:
        f.write(merged_content)
    
    print(f"  ✓ Merged successfully")
    return True

def main():
    """Main function to merge all strings.xml files."""
    project_root = Path.cwd()
    app_common_res = project_root / "app-common" / "src" / "main" / "res"
    app_res = project_root / "app" / "src" / "main" / "res"
    
    if not app_common_res.exists():
        print(f"Error: app-common resources directory not found: {app_common_res}")
        return 1
    
    if not app_res.exists():
        print(f"Error: app resources directory not found: {app_res}")
        return 1
    
    # Find all strings.xml files in app-common
    common_strings_files = list(app_common_res.glob("*/strings.xml"))
    
    if not common_strings_files:
        print("Error: No strings.xml files found in app-common")
        return 1
    
    print(f"Found {len(common_strings_files)} strings.xml files to merge")
    
    success_count = 0
    error_count = 0
    
    for common_file in sorted(common_strings_files):
        # Determine the corresponding app file
        locale_dir = common_file.parent.name
        app_file = app_res / locale_dir / "strings.xml"
        
        if not app_file.exists():
            print(f"  Error: Corresponding app file does not exist: {app_file}")
            error_count += 1
            continue
        
        if merge_strings_xml(common_file, app_file):
            success_count += 1
        else:
            error_count += 1
    
    print(f"\nMerge complete:")
    print(f"  ✓ Successfully merged: {success_count}")
    print(f"  ✗ Errors: {error_count}")
    
    return 0 if error_count == 0 else 1

if __name__ == "__main__":
    exit(main())