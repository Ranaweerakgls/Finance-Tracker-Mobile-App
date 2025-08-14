package com.example.budgetlyst.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.budgetlyst.R
import com.example.budgetlyst.data.PreferencesManager
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.ui.MainActivity
import java.util.Calendar

class NotificationManager(private val context: Context) {
    private val notificationManagerCompat = NotificationManagerCompat.from(context)
    private val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferencesManager = PreferencesManager(context)
    private val transactionRepository = TransactionRepository(context)

    companion object {
        private const val CHANNEL_ID_BUDGET = "budget_channel"
        private const val CHANNEL_ID_REMINDER = "reminder_channel"
        private const val NOTIFICATION_ID_BUDGET = 1001
        private const val NOTIFICATION_ID_REMINDER = 1002
        private const val REMINDER_REQUEST_CODE = 2001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val budgetChannel = NotificationChannel(
                CHANNEL_ID_BUDGET,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for budget alerts"
                enableLights(true)
                enableVibration(true)
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDER,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to record expenses"
            }

            systemNotificationManager.createNotificationChannel(budgetChannel)
            systemNotificationManager.createNotificationChannel(reminderChannel)
        }
    }

    fun checkBudgetAndNotify() {
        if (!preferencesManager.isNotificationEnabled()) return

        val budget = preferencesManager.getBudget()
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        if (budget.month == currentMonth && budget.year == currentYear && budget.amount > 0) {
            val totalExpenses = transactionRepository.getTotalExpensesForMonth(currentMonth, currentYear)
            val budgetPercentage = (totalExpenses / budget.amount) * 100

            // Log for debugging
            android.util.Log.d("NotificationManager", "Budget check: $totalExpenses / ${budget.amount} = $budgetPercentage%")

            if (budgetPercentage >= 90) {
                showBudgetNotification(budgetPercentage, budget.amount, totalExpenses)
            }
        }
    }

    private fun showBudgetNotification(percentage: Double, budgetAmount: Double, spentAmount: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val message = if (percentage >= 100) {
            "You've exceeded your monthly budget of ${preferencesManager.getCurrency()} $budgetAmount"
        } else {
            "You're at ${percentage.toInt()}% of your monthly budget (${preferencesManager.getCurrency()} $spentAmount / $budgetAmount)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budget Alert")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Log for debugging
        android.util.Log.d("NotificationManager", "Showing notification: $message")

        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                notificationManagerCompat.notify(NOTIFICATION_ID_BUDGET, notification)
            } else {
                android.util.Log.e("NotificationManager", "Notification permission not granted")
                // You might want to request permission here or inform the user
            }
        } else {
            notificationManagerCompat.notify(NOTIFICATION_ID_BUDGET, notification)
        }
    }

    fun scheduleDailyReminder() {
        if (!preferencesManager.isReminderEnabled()) {
            cancelDailyReminder()
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Set reminder for 8 PM every day
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If it's already past 8 PM, schedule for tomorrow
            if (Calendar.getInstance().after(this)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelDailyReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        alarmManager.cancel(pendingIntent)
    }

    fun showReminderNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Reminder")
            .setContentText("Don't forget to record your expenses for today!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                notificationManagerCompat.notify(NOTIFICATION_ID_REMINDER, notification)
            }
        } else {
            notificationManagerCompat.notify(NOTIFICATION_ID_REMINDER, notification)
        }
    }
}