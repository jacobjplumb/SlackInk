package co.overthrow.slackink

data class ConvoState(val convo: Convo, val unread: List<Msg>, val latestTs: String?) {
    val count: Int get() = unread.size
    val mentionCount: Int get() = unread.count { it.mentionsMe }
}

/** In-memory snapshot of what's unread, shared by the home and digest screens. */
object Unread {
    /** How many of your most active DMs to check. Each one costs two Slack calls. */
    private const val DM_LIMIT = 40

    @Volatile var states: List<ConvoState> = emptyList()
    @Volatile var elsewhere: List<Mention> = emptyList()
    @Volatile var elsewhereNote: String? = null
    @Volatile var loadedAt: Long = 0L

    fun load(prefs: Prefs, onProgress: (String) -> Unit) {
        val token = prefs.token
        val me = prefs.me
        val all = Slack.listDms(token).take(DM_LIMIT) + prefs.channels
        val out = ArrayList<ConvoState>()
        for ((i, c) in all.withIndex()) {
            onProgress("Checking ${i + 1} of ${all.size}…")
            try {
                out.add(check(token, me, c))
            } catch (e: SlackException) {
                val m = e.message.orEmpty()
                val fatal = m.startsWith("missing_scope") || m.startsWith("invalid_auth") ||
                    m.startsWith("token") || m.startsWith("rate_limited") || m.startsWith("account")
                if (fatal) throw e
                // Otherwise skip a conversation that can't be read (left, archived, etc.).
            }
        }
        states = out

        val followed = all.map { it.id }.toSet()
        val since = prefs.lastMentionSeen?.toDoubleOrNull() ?: (System.currentTimeMillis() / 1000.0 - 86400)
        try {
            elsewhere = Slack.searchMentions(token, me, since).filter { it.channelId !in followed }
            elsewhereNote = null
        } catch (e: SlackException) {
            elsewhere = emptyList()
            elsewhereNote = e.message
        }
        loadedAt = System.currentTimeMillis()
    }

    private fun check(token: String, me: String, c: Convo): ConvoState {
        val lr = Slack.lastRead(token, c.id)
        // A never-read conversation reports 0: only look back a week rather than flag everything.
        val oldest = if ((lr.toDoubleOrNull() ?: 0.0) < 1.0) nowTs(-7L * 86400) else lr
        val msgs = Slack.history(token, c.id, oldest, 100, me)
        return ConvoState(c, msgs.filter { it.userId != me }, msgs.lastOrNull()?.ts)
    }

    /** Unread first, mentions first among those, then alphabetical. */
    fun visible(prefs: Prefs): List<ConvoState> =
        states
            .filter { !(prefs.hideQuietDms && it.convo.isDm && it.count == 0) }
            .sortedWith(compareBy({ it.count == 0 }, { it.mentionCount == 0 }, { it.convo.name.lowercase() }))

    fun clear(id: String) {
        states = states.map { if (it.convo.id == id) it.copy(unread = emptyList()) else it }
    }

    fun clearAll() {
        states = states.map { it.copy(unread = emptyList()) }
        elsewhere = emptyList()
    }
}
