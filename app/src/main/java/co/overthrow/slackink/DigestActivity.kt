package co.overthrow.slackink

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Everything unread in the conversations you follow, mentions first, plus mentions of you in
 * channels you don't follow. Nothing is pushed: you check it when you choose to.
 */
class DigestActivity : BaseActivity() {
    private lateinit var out: LinearLayout
    private lateinit var status: TextView
    private lateinit var markBtn: Button
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Unread digest", extraBarButton = inkButton("Refresh") { refresh() })
        status = bodyText("", 16f)
        root.addView(status)
        out = scrollArea()
        markBtn = inkButton("Mark all read in Slack") { markAll() }
        root.addView(markBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        if (prefs.token.isEmpty()) {
            status.text = "Add your Slack token in Settings first."
            return
        }
        val stale = System.currentTimeMillis() - Unread.loadedAt > 5 * 60 * 1000
        if (Unread.loadedAt == 0L || stale) refresh() else render()
    }

    private fun refresh() {
        if (loading) return
        loading = true
        status.text = "Checking…"
        bg {
            try {
                Unread.load(prefs) { msg -> runOnUiThread { status.text = msg } }
                runOnUiThread {
                    loading = false
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    status.text = errText(e)
                }
            }
        }
    }

    private fun shorten(s: String, n: Int = 140): String = if (s.length > n) s.take(n) + "…" else s

    private fun render() {
        out.removeAllViews()
        val states = Unread.states.filter { it.count > 0 }
        val total = states.sumOf { it.count }
        val mentionMsgs = states.flatMap { s -> s.unread.filter { it.mentionsMe }.map { s.convo to it } }
        val elsewhere = Unread.elsewhere
        val mentionTotal = mentionMsgs.size + elsewhere.size
        status.text = if (total == 0 && elsewhere.isEmpty()) {
            "All caught up."
        } else {
            "$total unread · $mentionTotal mention you"
        }

        Unread.elsewhereNote?.let {
            out.addView(
                bodyText(
                    "Mention search is off ($it). Add the search:read scope to see mentions in channels you don't follow.",
                    14f,
                ),
            )
        }

        if (mentionTotal > 0) {
            out.addView(bodyText("MENTIONS", 13f, bold = true))
            for ((c, m) in mentionMsgs) {
                out.addView(msgBlock("${c.name}  ·  ${m.sender}  ·  ${formatTs(m.ts)}", shorten(m.text), true))
            }
            for (m in elsewhere) {
                out.addView(
                    msgBlock(
                        "${m.channelName} (not followed)  ·  ${m.sender}  ·  ${formatTs(m.ts)}",
                        shorten(m.text),
                        true,
                    ),
                )
            }
            out.addView(divider())
        }

        val others = states.filter { it.count > it.mentionCount }
        if (others.isNotEmpty()) {
            out.addView(bodyText("OTHER UNREAD", 13f, bold = true))
            for (s in others) {
                val msgs = s.unread.filter { !it.mentionsMe }
                out.addView(bodyText("${s.convo.name}  (${msgs.size})", 18f, bold = true))
                for (m in msgs.takeLast(4)) {
                    out.addView(bodyText("${m.sender}: ${shorten(m.text, 120)}", 16f))
                }
                if (msgs.size > 4) out.addView(bodyText("+ ${msgs.size - 4} earlier", 14f))
            }
        }
    }

    private fun markAll() {
        val todo = Unread.states.filter { it.count > 0 && it.latestTs != null }
        markBtn.isEnabled = false
        status.text = "Marking read…"
        bg {
            try {
                for (s in todo) Slack.mark(prefs.token, s.convo.id, s.latestTs!!)
                runOnUiThread {
                    prefs.lastMentionSeen = nowTs()
                    Unread.clearAll()
                    markBtn.isEnabled = true
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = errText(e)
                    markBtn.isEnabled = true
                }
            }
        }
    }
}
