---
description: Guidelines for adding and naming Android string resources
globs:
  - "**/res/values*/strings.xml"
---

# Localization Guidelines

When adding new user-facing strings:

- **Always use string resources**: Never hardcode user-facing text in layouts or code
- **Follow naming conventions**: Use descriptive, hierarchical naming (e.g., `profiles_name_default`, `settings_monitor_mode_label`)
- **Provide context**: String names should indicate usage and location
- **Consider pluralization**: Use Android plural resources (`<plurals>`) when quantities vary

## Naming Examples

- `profiles_create_title` (screen title)
- `profiles_name_label` (form field label)
- `profiles_name_default` (default value)
- `troubleshooter_ble_result_failure_title` (status/error title)

Common prefixes currently in use: `device_`, `settings_`, `support_`, `profiles_`, `press_`, `general_`, `pods_`, `upgrade_`, `widget_`, `debug_`, `permission_`, `troubleshooter_`, `overview_`, `anc_`, `onboarding_`. There is no `error_*` prefix — error labels live under the relevant feature (e.g. `general_error_label`, `troubleshooter_*_failure_*`).
