package co.overthrow.slackink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Calendar

private data class Alert(
    val where: String,
    val sender: String,
    val text: String,
    val ts: String,
    val mention: Boolean,
)

/**
 * Optional background alerts. Android runs a periodic job (at best every ~15 minutes, and it may
 * be delayed while the phone is idle). Each run checks Slack and posts one notification if there
 * are new DMs or direct @mentions since the last alert.
 */
object Notifier {
    private const val JOB_ID = 4201
    private const val CHANNEL_ID = "alerts"
    private const val NOTIF_ID = 1

    fun schedule(ctx: Context, force: Boolean = false) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (!force && js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, NotifyJob::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        js.schedule(info)
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }

    fun inQuietHours(prefs: Prefs, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean {
        if (!prefs.quietOn) return false
        val from = prefs.quietFrom
        val to = prefs.quietTo
        return when {
            from == to -> false
            from < to -> hour in from until to
            else -> hour >= from || hour < to
        }
    }

    /** Runs on a background thread (from the job). Throws SlackException on Slack errors. */
    fun check(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.notifyOn || prefs.token.isEmpty()) return
        // During quiet hours nothing is checked or advanced, so overnight messages alert once at the end.
        if (inQuietHours(prefs)) return

        val mark = prefs.lastNotified?.toDoubleOrNull()
        if (mark == null) {
            prefs.lastNotified = nowTs()
            return
        }

        Unread.load(prefs) {}

        val alerts = ArrayList<Alert>()
        for (s in Unread.states) {
            for (m in s.unread) {
                if (s.convo.isDm || m.mentionsMe) {
                    alerts.add(Alert(s.convo.name, m.sender, m.text, m.ts, m.mentionsMe))
                }
            }
        }
        for (m in Unread.elsewhere) {
            alerts.add(Alert(m.channelName, m.sender, m.text, m.ts, true))
        }

        val fresh = alerts
            .filter { (it.ts.toDoubleOrNull() ?: 0.0) > mark }
            .sortedBy { it.ts.toDoubleOrNull() ?: 0.0 }
        if (fresh.isEmpty()) return

        post(ctx, fresh)
        prefs.lastNotified = fresh.last().ts
    }

    private fun post(ctx: Context, fresh: List<Alert>) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "DMs and mentions", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val mentions = fresh.count { it.mention }
        val dms = fresh.size - mentions
        val parts = ArrayList<String>()
        if (dms > 0) parts.add("$dms new message${if (dms == 1) "" else "s"}")
        if (mentions > 0) parts.add("$mentions mention${if (mentions == 1) "" else "s"}")

        val inbox = Notification.InboxStyle()
        for (a in fresh.takeLast(5)) {
            val who = if (a.where == a.sender) a.sender else "${a.sender} in ${a.where}"
            inbox.addLine("$who: ${a.text.take(80)}")
        }

        val pi = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Slack Ink")
            .setContentText(parts.joinToString(", "))
            .setStyle(inbox)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
