# Google Play scores the obfuscation share of every uploaded bundle and restricts listings that
# stay below its threshold, so the Play flavor obfuscates. The FOSS flavor does not
# (proguard-rules-foss.pro).

# Keep stack traces retraceable with the mapping file that the bundle embeds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AAP session logs identify settings and commands by their simple class name
# (AapSessionEngine, AapAncController, AapOutboundController). Names only; unused classes are
# still removed.
-keepnames class eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
-keepnames class * extends eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
-keepnames class eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
-keepnames class * extends eu.darken.capod.pods.core.apple.aap.protocol.AapCommand

# ViewModel1 derives its log tag from the subclass name (VM:OverviewViewModel).
-keepnames class * extends eu.darken.capod.common.uix.ViewModel1

# Error dialogs and log summaries show exceptions by class name (LocalizedError, asLogSummary).
-keepnames class * extends java.lang.Throwable
