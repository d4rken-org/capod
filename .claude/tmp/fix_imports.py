#!/usr/bin/env python3
"""
Script to fix import statements after removing app-common module.
This script fixes R class imports from app-common to app module.
"""

import os
import re
from pathlib import Path

def fix_r_import(file_path):
    """Fix R import in a single file."""
    print(f"Fixing R import in: {file_path}")
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace the import statement
    updated_content = re.sub(
        r'import eu\.darken\.capod\.common\.R$',
        'import eu.darken.capod.R',
        content,
        flags=re.MULTILINE
    )
    
    if updated_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(updated_content)
        print(f"  ✓ Updated import in {file_path}")
        return True
    else:
        print(f"  - No changes needed in {file_path}")
        return False

def main():
    """Main function to fix all import statements."""
    project_root = Path.cwd()
    app_src = project_root / "app" / "src"
    
    # Find all Kotlin files that import eu.darken.capod.common.R
    files_to_fix = []
    for kt_file in app_src.rglob("*.kt"):
        with open(kt_file, 'r', encoding='utf-8') as f:
            content = f.read()
            if re.search(r'import eu\.darken\.capod\.common\.R$', content, re.MULTILINE):
                files_to_fix.append(kt_file)
    
    print(f"Found {len(files_to_fix)} files with incorrect R imports")
    
    success_count = 0
    for file_path in files_to_fix:
        if fix_r_import(file_path):
            success_count += 1
    
    print(f"\nFixed imports in {success_count} files")
    return 0

if __name__ == "__main__":
    exit(main())