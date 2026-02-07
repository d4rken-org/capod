---
description: Guidelines for adding and naming Android string resources
globs:
  - "**/res/values*/strings.xml"
---

# Localization Guidelines

When adding new user-facing strings:

- **Always use string resources**: Never hardcode user-facing text in layouts or code
- **Follow naming conventions**: Use descriptive, hierarchical naming (e.g., `profiles_name_default`, `settings_bluetooth_enabled`)
- **Provide context**: String names should indicate usage and location
- **Consider pluralization**: Use Android plural resources (`<plurals>`) when quantities vary

## Naming Examples

- `profiles_create_title` (screen title)
- `profiles_name_label` (form field label)
- `profiles_delete_confirmation` (dialog message)
- `error_network_unavailable` (error message)
