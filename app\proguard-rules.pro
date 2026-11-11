# Keep source line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable

# Keep notification BroadcastReceiver and scheduler symbols invoked by AlarmManager
-keep class cz.slovickaspeak.app.notifications.** { *; }
