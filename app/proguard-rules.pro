# Keep source line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable

# Keep notification BroadcastReceiver and scheduler symbols invoked by AlarmManager
-keep class cz.slovickaspeak.app.notifications.** { *; }

# Strip verbose/debug logs in optimized release builds to reduce binary size
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
