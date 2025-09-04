#!/usr/bin/env python3
"""
Script to fix fully qualified R references in code.
This script finds and replaces eu.darken.capod.common.R with R.
"""

import os
import re
from pathlib import Path

def fix_qualified_r_references(file_path):
    """Fix fully qualified R references in a single file."""
    print(f"Fixing qualified R references in: {file_path}")
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace fully qualified references
    updated_content = re.sub(
        r'\beu\.darken\.capod\.common\.R\.',
        'R.',
        content
    )
    
    if updated_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(updated_content)
        print(f"  ✓ Fixed qualified R references in {file_path}")
        return True
    else:
        print(f"  - No changes needed in {file_path}")
        return False

def main():
    """Main function to fix all qualified R references."""
    project_root = Path.cwd()
    app_src = project_root / "app" / "src"
    
    # Find all Kotlin files that have qualified R references
    files_to_fix = []
    for kt_file in app_src.rglob("*.kt"):
        with open(kt_file, 'r', encoding='utf-8') as f:
            content = f.read()
            if re.search(r'\beu\.darken\.capod\.common\.R\.', content):
                files_to_fix.append(kt_file)
    
    print(f"Found {len(files_to_fix)} files with qualified R references")
    
    success_count = 0
    for file_path in files_to_fix:
        if fix_qualified_r_references(file_path):
            success_count += 1
    
    print(f"\nFixed qualified R references in {success_count} files")
    return 0

if __name__ == "__main__":
    exit(main())