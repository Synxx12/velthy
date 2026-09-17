package com.velthy.client.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.velthy.client.MainActivity
import com.velthy.client.R
import com.velthy.client.data.settings.AppSettings

/**
 * The "a new version is out" notification.
 *
 * Separate from [AppUpdater]'s progress notification on purpose: those are two
 * different moments in a user's life. This one is news, arriving unbidden and
 * possibly while the phone is in someone's hand; that one is the progress of a
 * download they asked for. One channel cannot mean both, and Android freezes a
 * channel's importance at creation — so getting it wrong here is permanent on a
 * given install.
 *
 * ### Three channels, not one
 *
 * A channel's importance is fixed once created, so an urgent release cannot be
 * made to interrupt on a channel that was made quiet for routine ones. Two
 * channels carry the split — [CRITICAL_CHANNEL] for releases that have to be
 * seen, [ROUTINE_CHANNEL] for the rest — and the severity decides which.
 *
 * ### Once per version
 *
 * [AppSettings.updateNotifiedVersion] records the last version this said
 * anything about. Without it the periodic worker, the on-launch check and the
 * Settings check would each raise the same banner within minutes of one another,
 * which is how a helpful notification becomes the reason someone turns
 * notifications off. The record lives in the device-local set, so a restore
 * onto a new phone re-announces the pending update once, exactly as it should.
 */
object UpdateNotifier {

    private const val NOTIFICATION_ID = 9901

    private const val ROUTINE_CHANNEL = "app_updates_available"
    private const val CRITICAL_CHANNEL = "app_updates_critical"

    /**
     * Posts the update notification for [info] unless it has already been posted
     * for this version, notifications are switched off, or the permission has
     * not been granted.
     *
     * Returns whether anything was posted, which the caller does not need to act
     * on — it exists so the decision is testable without a running notification
     * service.
     */
    fun notifyIfNeeded(context: Context, info: AppUpdateChecker.UpdateInfo): Boolean {
        if (!AppSettings.updateNotifications.value) return false
        if (!canNotify(context)) return false
        if (AppSettings.updateNotifiedVersion.value == info.version) return false

        val severity = info.severity
        val channelId = if (severity.interrupts) CRITICAL_CHANNEL else ROUTINE_CHANNEL
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_update", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0x9901,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(
                when (severity) {
                    UpdateSeverity.CRITICAL -> "Critical update available"
                    UpdateSeverity.IMPORTANT -> "Important update available"
                    UpdateSeverity.NORMAL -> "Velthy v${info.version} is available"
                },
            )
            .setContentText(severity.blurb)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append("Velthy v")
                        append(info.version)
                        append(" — ")
                        append(severity.blurb)
                        if (info.releaseNotes.isNotBlank()) {
                            append("\n\n")
                            append(info.releaseNotes.take(400))
                        }
                    },
                ),
            )
            .setPriority(
                if (severity.interrupts) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW,
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val posted = runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        }.getOrDefault(false)

        if (posted) {
            AppSettings.setUpdateNotifiedVersion(info.version)
        }
        return posted
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                ROUTINE_CHANNEL,
                "Update available",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Tells you when a new version of Velthy is available."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CRITICAL_CHANNEL,
                context.getString(R.string.update_channel_critical_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.update_channel_critical_description)
            },
        )
    }
}
