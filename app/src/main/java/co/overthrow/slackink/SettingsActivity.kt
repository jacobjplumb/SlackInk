package co.overthrow.slackink

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : BaseActivity() {
    private lateinit var tokenInput: EditText
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var notifyBox: CheckBox
    private lateinit var fromInput: EditText
    private lateinit var toInput: EditText
    private val checks = ArrayList<Pair<CheckBox, Convo>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Settings")
        val inner = scrollArea()

        inner.addView(bodyText("Slack user token (starts with xoxp-)", 16f))
        tokenInput = EditText(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.token)
        }
        inner.addView(tokenInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        inner.addView(
            inkButton("Save token & load channels") { connect() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        status = bodyText("", 16f)
        inner.addView(status)
        inner.addView(divider())

        inner.addView(bodyText("Direct messages", 20f, bold = true))
        inner.addView(bodyText("All DMs and group DMs are included automatically (your 40 most active).", 15f))
        inner.addView(
            styledCheck("Hide DMs with nothing unread", prefs.hideQuietDms).apply {
                setOnCheckedChangeListener { _, v -> prefs.hideQuietDms = v }
            },
        )
        inner.addView(divider())

        inner.addView(bodyText("Notifications", 20f, bold = true))
        inner.addView(
            bodyText(
                "Checks Slack about every 15 minutes and alerts you to new DMs and @mentions. " +
                    "Android may delay checks while the phone is idle.",
                15f,
            ),
        )
        notifyBox = styledCheck("Notify me about DMs and mentions", prefs.notifyOn)
        notifyBox.setOnCheckedChangeListener { _, v -> onNotifyToggle(v) }
        inner.addView(notifyBox)
        inner.addView(
            styledCheck("Quiet hours (no alerts)", prefs.quietOn).apply {
                setOnCheckedChangeListener { _, v -> prefs.quietOn = v }
            },
        )
        val hours = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fromInput = hourInput(prefs.quietFrom)
        toInput = hourInput(prefs.quietTo)
        hours.addView(bodyText("From ", 16f))
        hours.addView(fromInput, LinearLayout.LayoutParams(dp(64), WRAP_CONTENT))
        hours.addView(bodyText("  to ", 16f))
        hours.addView(toInput, LinearLayout.LayoutParams(dp(64), WRAP_CONTENT))
        hours.addView(bodyText("  (24h clock)", 14f))
        inner.addView(hours)
        inner.addView(
            inkButton("Save quiet hours") { saveQuiet() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        inner.addView(divider())

        inner.addView(bodyText("Channels to follow", 20f, bold = true))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(listBox)
        inner.addView(
            inkButton("Save channels") { saveSelection() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) },
        )

        // Start with the saved channels (all ticked); loading from Slack shows the full list.
        showList(prefs.channels)
    }

    private fun styledCheck(label: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = label
        textSize = 18f
        setTextColor(Color.BLACK)
        buttonTintList = ColorStateList.valueOf(Color.BLACK)
        isChecked = checked
        setPadding(dp(8), dp(10), dp(8), dp(10))
    }

    private fun hourInput(value: Int): EditText = EditText(this).apply {
        textSize = 18f
        setTextColor(Color.BLACK)
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        gravity = Gravity.CENTER
    }

    private fun onNotifyToggle(on: Boolean) {
        if (!on) {
            prefs.notifyOn = false
            Notifier.cancel(this)
            status.text = "Notifications off."
            return
        }
        if (prefs.token.isEmpty()) {
            notifyBox.isChecked = false
            status.text = "Save your token first."
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        enableNotify()
    }

    private fun enableNotify() {
        prefs.notifyOn = true
        prefs.lastNotified = nowTs()
        Notifier.schedule(this, force = true)
        status.text = "Notifications on. Only messages from now on will alert. First check within about 15 minutes."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableNotify()
        } else {
            notifyBox.isChecked = false
            status.text = "Notifications are blocked. Allow them for Slack Ink in the phone's app settings."
        }
    }

    private fun saveQuiet() {
        val from = (fromInput.text.toString().toIntOrNull() ?: 22).coerceIn(0, 23)
        val to = (toInput.text.toString().toIntOrNull() ?: 7).coerceIn(0, 23)
        prefs.quietFrom = from
        prefs.quietTo = to
        fromInput.setText(from.toString())
        toInput.setText(to.toString())
        status.text = "Quiet hours saved: %02d:00 to %02d:00.".format(from, to)
    }

    private fun connect() {
        val token = tokenInput.text.toString().trim()
        if (token.isEmpty()) {
            status.text = "Paste your token first."
            return
        }
        prefs.token = token
        Unread.loadedAt = 0L
        status.text = "Connecting…"
        bg {
            try {
                val (uid, who) = Slack.authTest(token)
                prefs.me = uid
                Slack.loadUsers(token)
                val all = Slack.listChannels(token)
                runOnUiThread {
                    status.text = "Connected as $who. ${all.size} channels found."
                    showList(all)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = errText(e) }
            }
        }
    }

    private fun showList(all: List<Convo>) {
        val chosen = prefs.channels.map { it.id }.toSet()
        listBox.removeAllViews()
        checks.clear()
        if (all.isEmpty()) listBox.addView(bodyText("Nothing loaded yet.", 16f))
        for (c in all) {
            val box = styledCheck(c.name, c.id in chosen)
            checks.add(box to c)
            listBox.addView(box)
        }
    }

    private fun saveSelection() {
        val picked = checks.filter { it.first.isChecked }.map { it.second }
        prefs.channels = picked
        Unread.loadedAt = 0L
        status.text = "Saved ${picked.size} channels."
    }
}
