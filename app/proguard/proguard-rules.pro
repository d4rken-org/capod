-keep class eu.darken.capod.BuildConfig { *; }
-dontobfuscate

# Glance pulls work-runtime:2.7.1 → room-runtime:2.2.5 as transitive deps.
# Room 2.2.5 finds WorkDatabase_Impl via Class.forName() reflection, but its
# consumer ProGuard rules don't keep _Impl classes, so R8 full mode (AGP 9+) strips it.
# Remove when Glance upgrades to Room 2.7+ (which uses RoomDatabaseConstructor instead of reflection).
# See: https://issuetracker.google.com/issues/243257364
-keep class androidx.work.impl.WorkDatabase_Impl { *; }