# FortyCalendar release rules.
#
# Shrink and optimise, but keep the real class and method names: the in-app error report
# ("Relatar um problema") then shows exactly where something broke, with no mapping file needed.
-dontobfuscate

# Keep line numbers in error reports.
-keepattributes SourceFile,LineNumberTable

# Room finds the generated database by name; its library rules cover this, kept here to be sure.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
