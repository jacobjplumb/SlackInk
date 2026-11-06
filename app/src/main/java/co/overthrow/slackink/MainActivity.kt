package co.overthrow.slackink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen(
            "Slack Ink",
            showBack = false,
            extraBarButton = inkButton("Settings") {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
        )

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            inkButton("Refresh") { refresh() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) },
        )
        row.addView(
            inkButton("Digest") { startActivity(Intent(this, DigestActivity::class.java)) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        root.addView(row)
        status = bodyText("", 14f)
        root.addView(status)
        list = scrollArea()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.notifyOn) Notifier.schedule(this)
        if (prefs.token.isEmpty()) {
            list.removeAllViews()
            list.addView(bodyText("Welcome. Open Settings and paste your Slack token to get started."))
            return
        }
        val stale = System.currentTimeMillis() - Unread.loadedAt > 5 * 60 * 1000
        if (Unread.loadedAt == 0L || stale) refresh() else render()
    }

    private fun refresh() {
        if (loading || prefs.token.isEmpty()) return
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

    private fun render() {
        list.removeAllViews()
        status.text = "Updated " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(Unread.loadedAt))
        val states = Unread.visible(prefs)
        val unread = states.filter { it.count > 0 }
        val quiet = states.filter { it.count == 0 }
        val other = Unread.elsewhere

        if (other.isNotEmpty()) {
            list.addView(sectionLabel("MENTIONS IN CHANNELS YOU DON'T FOLLOW"))
            addRow(
                inkRow("${other.size} mention${if (other.size == 1) "" else "s"}", 0, true, false) {
                    startActivity(Intent(this, DigestActivity::class.java))
                },
            )
        }

        list.addView(sectionLabel("UNREAD (${unread.size})"))
        if (unread.isEmpty()) list.addView(bodyText("All caught up.", 16f))
        for (s in unread) addRow(inkRow(s.convo.name, s.count, s.mentionCount > 0, false) { open(s) })

        if (quiet.isNotEmpty()) {
            list.addView(sectionLabel("NOTHING NEW"))
            for (s in quiet) addRow(inkRow(s.convo.name, 0, false, true) { open(s) })
        }
    }

    private fun addRow(v: View) {
        list.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun sectionLabel(t: String): TextView =
        bodyText(t, 13f, bold = true).apply { setPadding(0, dp(12), 0, 0) }

    private fun open(s: ConvoState) {
        startActivity(
            Intent(this, ConversationActivity::class.java)
                .putExtra("id", s.convo.id)
                .putExtra("name", s.convo.name),
        )
    }
}
