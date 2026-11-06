package co.overthrow.slackink

import android.content.Context
import org.json.JSONArray

/** Private app storage. The Slack token lives here in plain text (app-private, not backed up). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("slackink", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()

    /** Your own Slack user id, used to spot mentions and to ignore your own messages. */
    var me: String
        get() = sp.getString("me", "") ?: ""
        set(v) = sp.edit().putString("me", v).apply()

    var hideQuietDms: Boolean
        get() = sp.getBoolean("hideQuietDms", false)
        set(v) = sp.edit().putBoolean("hideQuietDms", v).apply()

    /** Slack ts of the newest mention-in-unfollowed-channel you've cleared. */
    var lastMentionSeen: String?
        get() = sp.getString("lastMentionSeen", null)
        set(v) = sp.edit().putString("lastMentionSeen", v).apply()

    var notifyOn: Boolean
        get() = sp.getBoolean("notifyOn", false)
        set(v) = sp.edit().putBoolean("notifyOn", v).apply()

    var quietOn: Boolean
        get() = sp.getBoolean("quietOn", true)
        set(v) = sp.edit().putBoolean("quietOn", v).apply()

    /** Quiet hours, as hours on the 24h clock (default 22:00 to 07:00). */
    var quietFrom: Int
        get() = sp.getInt("quietFrom", 22)
        set(v) = sp.edit().putInt("quietFrom", v).apply()

    var quietTo: Int
        get() = sp.getInt("quietTo", 7)
        set(v) = sp.edit().putInt("quietTo", v).apply()

    /** Slack ts of the newest message already alerted. */
    var lastNotified: String?
        get() = sp.getString("lastNotified", null)
        set(v) = sp.edit().putString("lastNotified", v).apply()

    /** Channels you chose to follow. DMs are always included automatically. */
    var channels: List<Convo>
        get() {
            val arr = JSONArray(sp.getString("channels", "[]"))
            return (0 until arr.length()).map { Convo.fromJson(arr.getJSONObject(it)) }
        }
        set(v) = sp.edit().putString("channels", JSONArray(v.map { it.toJson() }).toString()).apply()
}
