#!/usr/bin/env python3
"""
Script to fix remaining R import issues.
This script finds files that use R.* but don't have R imports and adds them.
"""

import os
import re
from pathlib import Path

def needs_r_import(file_path):
    """Check if file uses R.* but doesn't import R."""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Check if file uses R.something
    uses_r = re.search(r'\bR\.[a-zA-Z_]', content)
    if not uses_r:
        return False
    
    # Check if file already imports R
    has_import = re.search(r'import.*\.R$', content, re.MULTILINE)
    if has_import:
        return False
    
    return True

def add_r_import(file_path):
    """Add R import to a file."""
    print(f"Adding R import to: {file_path}")
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find the package line
    package_match = re.search(r'^package\s+[^\n]+$', content, re.MULTILINE)
    if not package_match:
        print(f"  Error: No package declaration found in {file_path}")
        return False
    
    # Find existing imports
    import_pattern = r'^import\s+[^\n]+$'
    existing_imports = list(re.finditer(import_pattern, content, re.MULTILINE))
    
    if existing_imports:
        # Insert after the last import
        last_import = existing_imports[-1]
        insert_pos = last_import.end()
        updated_content = (content[:insert_pos] + 
                         "\nimport eu.darken.capod.R" + 
                         content[insert_pos:])
    else:
        # Insert after package line
        insert_pos = package_match.end()
        updated_content = (content[:insert_pos] + 
                         "\n\nimport eu.darken.capod.R" + 
                         content[insert_pos:])
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(updated_content)
    
    print(f"  ✓ Added R import to {file_path}")
    return True

def main():
    """Main function to fix all R import issues."""
    project_root = Path.cwd()
    app_src = project_root / "app" / "src"
    
    # Find all Kotlin files that need R import
    files_to_fix = []
    for kt_file in app_src.rglob("*.kt"):
        if needs_r_import(kt_file):
            files_to_fix.append(kt_file)
    
    print(f"Found {len(files_to_fix)} files that need R imports")
    
    success_count = 0
    for file_path in files_to_fix:
        if add_r_import(file_path):
            success_count += 1
    
    print(f"\nAdded R imports to {success_count} files")
    return 0

if __name__ == "__main__":
    exit(main())