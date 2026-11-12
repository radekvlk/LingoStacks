package cz.slovickaspeak.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cz.slovickaspeak.app.MainActivity
import cz.slovickaspeak.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object StudyReminderScheduler {
    private const val CHANNEL_ID = "lingowords_study_reminder"
    private const val NOTIFICATION_ID = 1042
    private const val REQUEST_CODE = 4201
    private const val ACTION_REMINDER = "cz.slovickaspeak.app.ACTION_STUDY_REMINDER"

    private fun isCzechLocale(): Boolean {
        val lang = Locale.getDefault().language.lowercase(Locale.US)
        return lang in listOf("cs", "sk", "ces", "cze")
    }

    fun ensureNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val isCs = isCzechLocale()
        val channel = NotificationChannel(
            CHANNEL_ID,
            if (isCs) "Denní připomenutí učení" else "Daily Study Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = if (isCs) {
                "Připomíná denní procvičování slovíček v nastavený čas."
            } else {
                "Reminds you to practice your daily vocabulary goal at your chosen time."
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun computeNextTriggerMillis(hour: Int, minute: Int, forceTomorrow: Boolean = false): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceTomorrow || timeInMillis <= now.timeInMillis + 10_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis
    }

    fun scheduleOrCancel(
        context: Context,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        forceTomorrow: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StudyReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        ensureNotificationChannel(context)
        val triggerAtMillis = computeNextTriggerMillis(hour, minute, forceTomorrow)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    internal fun showReminderNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val prefs = context.getSharedPreferences("slovicka_speak_prefs", Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val savedDate = prefs.getString("today_date", null)
        val todayDone = if (savedDate == todayKey) prefs.getInt("today_completed", 0) else 0
        val dailyGoal = prefs.getInt("daily_goal", 20).coerceIn(5, 200)

        // Do not nag if user already completed today's daily goal!
        if (todayDone >= dailyGoal) return

        ensureNotificationChannel(context)
        val isCs = isCzechLocale()
        val remaining = (dailyGoal - todayDone).coerceAtLeast(1)

        val title = if (isCs) "🗣️ Čas na slovíčka!" else "🗣️ Time to practice LingoStacks!"
        val body = if (todayDone > 0) {
            if (isCs) {
                "Dnes už máš $todayDone slov. Stačí ještě $remaining do splnění denního cíle!"
            } else {
                "You've done $todayDone words today. Just $remaining more to hit your daily goal!"
            }
        } else {
            if (isCs) {
                "Dnešní cíl čeká ($dailyGoal slovíček). Dej si rychlou 2minutovou sérii!"
            } else {
                "Your daily goal ($dailyGoal words) is waiting. Take a quick 2-minute speaking session!"
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("slovicka_speak_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("study_reminder_enabled", false)
        val hour = prefs.getInt("study_reminder_hour", 19).coerceIn(0, 23)
        val minute = prefs.getInt("study_reminder_minute", 0).coerceIn(0, 59)

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (enabled) {
                StudyReminderScheduler.scheduleOrCancel(context, enabled = true, hour = hour, minute = minute)
            }
            return
        }

        if (enabled) {
            StudyReminderScheduler.showReminderNotification(context)
            // Schedule next day's reminder reliably
            StudyReminderScheduler.scheduleOrCancel(
                context = context,
                enabled = true,
                hour = hour,
                minute = minute,
                forceTomorrow = true
            )
        }
    }
}
