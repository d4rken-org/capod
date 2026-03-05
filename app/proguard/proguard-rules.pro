-keep class eu.darken.capod.BuildConfig { *; }
-dontobfuscate

# work-runtime 2.7.1 (pulled by Glance) uses Class.newInstance() reflection throughout.
# R8 full mode strips no-arg constructors not reachable by static analysis.
# Keep all constructors in the work package. Remove when Glance upgrades to work-runtime 2.10+.
# See: https://issuetracker.google.com/issues/243257364
-keep class androidx.work.** { <init>(...); }