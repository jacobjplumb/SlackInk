package co.overthrow.slackink

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SlackException(message: String) : Exception(message)

data class Convo(val id: String, val name: String, val isDm: Boolean) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("dm", isDm)

    companion object {
        fun fromJson(o: JSONObject) = Convo(o.getString("id"), o.getString("name"), o.optBoolean("dm"))
    }
}

data class Msg(val ts: String, val userId: String, val sender: String, val text: String, val mentionsMe: Boolean)

/** A message that mentions you, found by search in a channel you don't follow. */
data class Mention(val channelId: String, val channelName: String, val sender: String, val text: String, val ts: String)

/** Minimal Slack Web API client. Call everything from a background thread. */
object Slack {
    private val names = HashMap<String, String>()
    private var usersLoaded = false

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(
        token: String,
        method: String,
        params: Map<String, String> = emptyMap(),
        post: Boolean = false,
    ): JSONObject {
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val urlStr = "https://slack.com/api/$method" + if (!post && query.isNotEmpty()) "?$query" else ""
        var attempt = 0
        while (true) {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            var retryAfter = 5L
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("Authorization", "Bearer $token")
                if (post) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    conn.outputStream.use { it.write(query.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code != 429) {
                    val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?: throw SlackException("http_$code")
                    val body = stream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (!json.optBoolean("ok")) {
                        val err = json.optString("error", "unknown_error")
                        val needed = json.optString("needed")
                        throw SlackException(if (needed.isNotEmpty()) "$err (needs $needed)" else err)
                    }
                    return json
                }
                retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 5L
            } finally {
                conn.disconnect()
            }
            if (attempt >= 3) throw SlackException("rate_limited (try again in a minute)")
            attempt++
            Thread.sleep(minOf(retryAfter, 30L) * 1000)
        }
    }

    /** Returns (own user id, "name @ workspace"). */
    fun authTest(token: String): Pair<String, String> {
        val r = call(token, "auth.test")
        return r.getString("user_id") to "${r.optString("user")} @ ${r.optString("team")}"
    }

    private fun displayName(u: JSONObject): String {
        val display = u.optJSONObject("profile")?.optString("display_name").orEmpty()
        val real = u.optString("real_name")
        return display.ifEmpty { real.ifEmpty { u.optString("name", u.optString("id")) } }
    }

    /** Fills the name cache in a few calls so lists don't need one lookup per person. */
    fun loadUsers(token: String) {
        var cursor = ""
        var pages = 0
        do {
            val p = mutableMapOf("limit" to "200")
            if (cursor.isNotEmpty()) p["cursor"] = cursor
            val r = call(token, "users.list", p)
            val arr = r.getJSONArray("members")
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                names[u.getString("id")] = displayName(u)
            }
            cursor = r.optJSONObject("response_metadata")?.optString("next_cursor").orEmpty()
            pages++
        } while (cursor.isNotEmpty() && pages < 10)
        usersLoaded = true
    }

    fun ensureUsers(token: String) {
        if (!usersLoaded) loadUsers(token)
    }

    fun userName(token: String, id: String): String {
        if (id.isEmpty()) return "someone"
        names[id]?.let { return it }
        return try {
            val u = call(token, "users.info", mapOf("user" to id)).getJSONObject("user")
            displayName(u).also { names[id] = it }
        } catch (e: Exception) {
            id
        }
    }

    private fun listRaw(token: String, types: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var cursor = ""
        var pages = 0
        do {
            val p = mutableMapOf(
                "types" to types,
                "exclude_archived" to "true",
                "limit" to "200",
            )
            if (cursor.isNotEmpty()) p["cursor"] = cursor
            val r = call(token, "conversations.list", p)
            val arr = r.getJSONArray("channels")
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
            cursor = r.optJSONObject("response_metadata")?.optString("next_cursor").orEmpty()
            pages++
        } while (cursor.isNotEmpty() && pages < 15)
        return out
    }

    /** Public and private channels you're a member of, alphabetical. */
    fun listChannels(token: String): List<Convo> =
        listRaw(token, "public_channel,private_channel")
            .filter { it.optBoolean("is_member") }
            .map { Convo(it.getString("id"), "#" + it.optString("name"), false) }
            .sortedBy { it.name.lowercase() }

    /** All DMs and group DMs, most active first (by Slack's own priority score). */
    fun listDms(token: String): List<Convo> {
        ensureUsers(token)
        return listRaw(token, "im,mpim")
            .filter { !(it.optBoolean("is_im") && it.optBoolean("is_user_deleted")) }
            .sortedByDescending { it.optDouble("priority", 0.0) }
            .map { c ->
                if (c.optBoolean("is_mpim")) {
                    val label = c.optString("name").removePrefix("mpdm-").removeSuffix("-1").replace("--", ", ")
                    Convo(c.getString("id"), label, true)
                } else {
                    Convo(c.getString("id"), userName(token, c.optString("user")), true)
                }
            }
    }

    /** Slack's own "read up to here" marker for you in this conversation. */
    fun lastRead(token: String, channel: String): String {
        val c = call(token, "conversations.info", mapOf("channel" to channel)).getJSONObject("channel")
        return c.optString("last_read", "0")
    }

    /** Top-level messages, oldest first. [oldest] is an exclusive Slack ts, or null for the latest [limit]. */
    fun history(token: String, channel: String, oldest: String?, limit: Int, me: String): List<Msg> {
        val p = mutableMapOf("channel" to channel, "limit" to limit.toString())
        if (oldest != null) p["oldest"] = oldest
        val arr = call(token, "conversations.history", p).getJSONArray("messages")
        val out = ArrayList<Msg>()
        for (i in arr.length() - 1 downTo 0) {
            val m = arr.getJSONObject(i)
            val raw = m.optString("text")
            val uid = m.optString("user")
            val sender = if (uid.isNotEmpty()) userName(token, uid) else m.optString("username").ifEmpty { "app" }
            val text = if (raw.isEmpty()) "[file or attachment]" else cleanText(token, raw)
            val mentionsMe = me.isNotEmpty() && (raw.contains("<@$me>") || raw.contains("<@$me|"))
            out.add(Msg(m.getString("ts"), uid, sender, text, mentionsMe))
        }
        return out
    }

    fun post(token: String, channel: String, text: String) {
        call(token, "chat.postMessage", mapOf("channel" to channel, "text" to text), post = true)
    }

    /** Marks the conversation as read up to [ts] in Slack itself (needs the *:write scopes). */
    fun mark(token: String, channel: String, ts: String) {
        call(token, "conversations.mark", mapOf("channel" to channel, "ts" to ts), post = true)
    }

    /** Recent messages that @mention you in any channel (needs search:read). DMs are skipped. */
    fun searchMentions(token: String, me: String, since: Double): List<Mention> {
        val r = call(
            token,
            "search.messages",
            mapOf("query" to "<@$me>", "count" to "20", "sort" to "timestamp", "sort_dir" to "desc"),
        )
        val arr = r.getJSONObject("messages").getJSONArray("matches")
        val out = ArrayList<Mention>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val ts = m.optString("ts")
            val tsNum = ts.toDoubleOrNull() ?: continue
            if (tsNum <= since) continue
            if (m.optString("user") == me) continue
            val ch = m.optJSONObject("channel") ?: continue
            if (ch.optBoolean("is_im") || ch.optBoolean("is_mpim")) continue
            val sender = m.optString("username").ifEmpty { userName(token, m.optString("user")) }
            out.add(Mention(ch.optString("id"), "#" + ch.optString("name"), sender, cleanText(token, m.optString("text")), ts))
        }
        return out
    }

    private fun cleanText(token: String, raw: String): String {
        var t = raw
        t = Regex("<@([A-Z0-9]+)(\\|[^>]*)?>").replace(t) { "@" + userName(token, it.groupValues[1]) }
        t = Regex("<#[A-Z0-9]+\\|([^>]+)>").replace(t) { "#" + it.groupValues[1] }
        t = Regex("<((?:https?|mailto):[^|>]*)\\|([^>]+)>").replace(t) { it.groupValues[2] }
        t = Regex("<((?:https?|mailto):[^>]+)>").replace(t) { it.groupValues[1] }
        t = Regex("<!([a-z]+)[^>]*>").replace(t) { "@" + it.groupValues[1] }
        return t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
