package co.overthrow.slackink

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class ConversationActivity : BaseActivity() {
    private lateinit var messages: LinearLayout
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var markBtn: Button
    private var channelId = ""
    private var lastRead = "0"
    private var latestTs: String? = null
    private var shown: List<Msg> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id")
        if (id == null) {
            finish()
            return
        }
        channelId = id
        val name = intent.getStringExtra("name") ?: "Conversation"

        setupScreen(name, extraBarButton = inkButton("Refresh") { load() })
        status = bodyText("", 16f)
        root.addView(status)
        messages = scrollArea()

        root.addView(divider())
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Reply"
            textSize = 18f
            setTextColor(Color.BLACK)
            setHintTextColor(0xFF666666.toInt())
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        send = inkButton("Send") { sendReply() }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) })
        root.addView(inputRow)

        markBtn = inkButton("Mark read in Slack") { markRead() }
        root.addView(markBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        load()
    }

    private fun load() {
        status.text = "Loading…"
        bg {
            try {
                val lr = Slack.lastRead(prefs.token, channelId)
                val msgs = Slack.history(prefs.token, channelId, null, 30, prefs.me)
                runOnUiThread {
                    lastRead = lr
                    latestTs = msgs.lastOrNull()?.ts
                    shown = msgs
                    render()
                    status.text = ""
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = errText(e) }
            }
        }
    }

    private fun isNew(m: Msg, lr: Double): Boolean = m.userId != prefs.me && m.ts.toDouble() > lr

    private fun render() {
        messages.removeAllViews()
        if (shown.isEmpty()) messages.addView(bodyText("No messages yet."))
        val lr = lastRead.toDoubleOrNull() ?: 0.0
        val newCount = shown.count { isNew(it, lr) }
        var dividerShown = false
        for (m in shown) {
            val fresh = isNew(m, lr)
            if (fresh && !dividerShown) {
                messages.addView(newDivider(newCount))
                dividerShown = true
            }
            messages.addView(msgBlock("${m.sender}  ·  ${formatTs(m.ts)}", m.text, fresh && m.mentionsMe))
            messages.addView(
                View(this).apply { setBackgroundColor(0xFF999999.toInt()) },
                LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                },
            )
        }
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun newDivider(n: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            View(context).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(0, dp(2), 1f),
        )
        addView(bodyText("  $n NEW  ", 14f, bold = true))
        addView(
            View(context).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(0, dp(2), 1f),
        )
    }

    private fun markRead() {
        val ts = latestTs
        if (ts == null) {
            status.text = "Nothing to mark."
            return
        }
        markBtn.isEnabled = false
        status.text = "Marking read…"
        bg {
            try {
                Slack.mark(prefs.token, channelId, ts)
                runOnUiThread {
                    Unread.clear(channelId)
                    lastRead = ts
                    render()
                    status.text = "Marked read in Slack."
                    markBtn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = errText(e)
                    markBtn.isEnabled = true
                }
            }
        }
    }

    private fun sendReply() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        send.isEnabled = false
        status.text = "Sending…"
        bg {
            try {
                Slack.post(prefs.token, channelId, text)
                runOnUiThread {
                    input.setText("")
                    send.isEnabled = true
                    load()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = errText(e)
                    send.isEnabled = true
                }
            }
        }
    }
}
