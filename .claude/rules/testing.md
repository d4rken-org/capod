---
description: Unit test conventions — JUnit 5, kotest assertions, mockk, BaseTest, and which Gradle task runs which source set
paths:
  - "app/src/test/**"
  - "app/src/testFoss/**"
  - "app/src/testGplay/**"
  - "app/build.gradle.kts"
  - "buildSrc/src/main/java/Dependencies.kt"
---

# Testing

The stack here is not the Android default — check this before reaching for a familiar library.

## Libraries

- **JUnit 5** (`org.junit.jupiter.api.Test`). Gradle sets `useJUnitPlatform()`.
- **kotest** for assertions: `io.kotest.matchers.shouldBe`, `shouldBeNull`, `shouldBeInstanceOf`,
  `shouldContainExactly`, `io.kotest.assertions.throwables.shouldThrow`. Use kotest for new
  assertions — `MediaControlTest` still uses JUnit `Assertions.*` and is a legacy exception.
- **mockk** for mocking. Not Mockito.
- **Turbine is not a dependency.** `testhelpers.flow.FlowTest` provides a `Flow<T>.test()` helper —
  use it rather than adding one.

## Base classes

Extend `testhelpers.BaseTest`, or the applicable specialized base that already extends it:

- `BaseBlePodsTest` — BLE advertisement parsing per pod model
- `BaseAapSessionTest` — AAP protocol/session tests

`BaseTest` installs a `JUnitLogger` and calls `unmockkAll()` in `@AfterAll`. Skipping it can leave
global mockk and logging state behind for later test classes.

The only exceptions are the two Robolectric-backed Compose UI tests
(`UpgradeScreenFossComposeTest`, `UpgradeScreenComposeTest`), which use JUnit 4 `@RunWith`/`@Rule`
via `junit-vintage-engine`. Don't copy that pattern for a plain unit test.

## Source sets and Gradle tasks

Each task compiles and runs only its own flavor — running the wrong one silently skips your test.

| Test location | Task |
|---|---|
| `app/src/test/` (shared) | either; run both before pushing |
| `app/src/testFoss/` | `./gradlew testFossDebugUnitTest` |
| `app/src/testGplay/` | `./gradlew testGplayDebugUnitTest` |

CI runs both. Flavor-specific tests are for code that only exists in that flavor — billing in
`gplay`, the sponsor-based upgrade flow in `foss`.

## Helpers that already exist

- `runTest2(autoCancel, context, expectedError, testBody)` in `testhelpers/coroutine/TestExtensions.kt` —
  use `expectedError = SomeException::class` instead of hand-rolling a throws-assertion around `runTest`
- `FakeDataStoreValue<T>(initial)` in `testhelpers/datastore/` — a working fake with a real backing
  `MutableStateFlow`; read/write it through `.value` and pass `.mock` to the code under test

## Mocking `DataStoreValue`

`DataStoreValue.value()` and `.value(T)` are **extension functions** (`DataStoreValue.kt:54,56`), not
members, so MockK cannot stub them. They delegate to `flow.first()` and `update { }` — stub those:

```kotlin
every { someSetting.flow } returns flowOf(value)   // covers .value() reads
coVerify { someSetting.update(any()) }             // verifies .value(x) writes
```

`UpgradeRepoGplayTest` uses this shape. Prefer `FakeDataStoreValue` when you need reads and writes to
actually round-trip.

## Reading ViewModel state

`ViewModel2.asLiveState()` is `stateIn(..., initialValue = null).filterNotNull()` with
`SharingStarted.WhileSubscribed(5_000)` — so `vm.state` is a `Flow`, not a `StateFlow`, and has no
`.value` to read. Collect it: `vm.state.first()` is the established pattern across the existing
ViewModel tests. Because the upstream only runs while subscribed, a test that never collects sees
nothing happen at all.
