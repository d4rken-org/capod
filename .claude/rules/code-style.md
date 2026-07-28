---
description: Kotlin and Compose conventions — logging, ViewModel base classes, the ScreenHost/Screen split, DataStore settings
paths:
  - "app/src/main/**/*.kt"
  - "app/src/foss/**/*.kt"
  - "app/src/gplay/**/*.kt"
  - "app/src/debug/**/*.kt"
---

# Code Style

## Logging

`logTag()` builds the tag; `log()` takes a lambda so the message is only built if it's emitted.

```kotlin
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.logging.Logging.Priority.*

companion object {
    private val TAG = logTag("Profiles", "Repo")   // multi-part tags are the norm
}

log(TAG) { "Processing $item" }              // DEBUG is the default
log(TAG, VERBOSE) { "Devices changed" }
log(TAG, ERROR) { "Failed: ${e.asLog()}" }   // asLog() for stacktraces
```

Never suppress protocol logging — downgrading a level is fine, removing the call is not.

## ViewModel base classes

Four exist. Use **`ViewModel4`** for new work — it's the current one (12 subclasses) and wires
`NavigationEventSource` + `ErrorEventSource2`.

- `ViewModel4` — current, use this
- `ViewModel2` — plain base, no nav/error event sources (4 subclasses)
- `ViewModel1` — legacy (1 subclass)
- `ViewModel3` — **dead, zero subclasses.** It's the `ViewModel4` shape against the older
  `NavEventSource`/`ErrorEventSource` interfaces. Don't extend it.

## Compose: the Host/Screen split

Every screen is two composables.

**`<Feature>ScreenHost`** — the only place that touches `hiltViewModel()`, installs the event
handlers, and collects state.

**`<Feature>Screen`** — presentation only. Takes a plain state object plus `on*` callbacks, so it
previews without Hilt.

```kotlin
@Composable
fun SettingsScreenHost(vm: SettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    state?.let {
        SettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onWiki = { vm.openUrl("https://github.com/d4rken-org/capod/wiki") },
        )
    }
}

@Composable
fun SettingsScreen(
    state: SettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onWiki: () -> Unit,
    modifier: Modifier = Modifier,   // last, after the required params
) { ... }
```

- `modifier: Modifier = Modifier` goes after the required parameters — i.e. it is the first
  *optional* one, per the Compose API guidelines. capod is not fully consistent here (roughly 10
  composables put it after required params, 3 put it genuinely first); match the file you're in
  rather than reformatting neighbours
- The Host null-guards state; `collectAsStateWithLifecycle(initialValue = null)` is the usual shape
- Wrap previews in `PreviewWrapper` (`common/compose/PreviewWrapper.kt`), which applies `CapodTheme`
  plus a background `Surface`
- Trailing commas on multi-line parameter lists and argument lists

## DataStore settings

`createValue()` is overloaded. Primitives need no serializer:

```kotlin
val monitorMode = dataStore.createValue("core.monitor.mode", MonitorMode.AUTOMATIC)
```

`@Serializable` types take a `Json`, and optionally fall back instead of throwing on corrupt or
legacy stored JSON:

```kotlin
val config = dataStore.createValue("some.config", SomeConfig(), json, onErrorFallbackToDefault = true)
```

Read and write via `.value()` / `.value(x)` (suspend) or `.flow` (reactive). Both `value` functions
are **extension functions**, not members — see `.claude/rules/testing.md` for what that means when
mocking.

## General

- Package by feature, not by layer
- Prefer adding to an existing file over creating a new one
- Prefer flow-based, cancellable solutions
- No comments for self-evident code
- Place `@Suppress` as close to the affected code as possible — on the function or constructor,
  not the whole class
