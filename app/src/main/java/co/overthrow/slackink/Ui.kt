package co.overthrow.slackink

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

private fun Context.box(fill: Int) = GradientDrawable().apply {
    setColor(fill)
    setStroke(dp(2), Color.BLACK)
}

fun Context.inkButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 18f
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(0xFF888888.toInt(), Color.BLACK),
        ),
    )
    stateListAnimator = null
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), box(0xFFCCCCCC.toInt()))
        addState(intArrayOf(), box(Color.WHITE))
    }
    minHeight = dp(52)
    minimumHeight = dp(52)
    setPadding(dp(16), dp(8), dp(16), dp(8))
    setOnClickListener { onClick() }
}

fun Context.bodyText(text: String, size: Float = 18f, bold: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(Color.BLACK)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(4), 0, dp(4))
}

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(Color.BLACK)
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(2)).apply {
        topMargin = dp(8)
        bottomMargin = dp(8)
    }
}

fun Context.badge(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.BLACK)
    textSize = 15f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setPadding(dp(8), dp(2), dp(8), dp(2))
}

fun Context.mentionTag(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextColor(Color.BLACK)
    textSize = 12f
    typeface = Typeface.DEFAULT_BOLD
    background = GradientDrawable().apply {
        setColor(Color.WHITE)
        setStroke(dp(2), Color.BLACK)
    }
    setPadding(dp(6), 0, dp(6), 0)
}

/** One tappable conversation row: name, optional "@ you" tag, optional unread count badge. */
fun Context.inkRow(name: String, count: Int, mention: Boolean, quiet: Boolean, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(2), if (quiet) 0xFF888888.toInt() else Color.BLACK)
        }
        val label = TextView(context).apply {
            text = name
            textSize = 18f
            setTextColor(if (quiet) 0xFF555555.toInt() else Color.BLACK)
        }
        addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (mention) {
            addView(mentionTag("@ you"), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(8) })
        }
        if (count > 0) addView(badge(if (count >= 100) "100+" else count.toString()))
        isClickable = true
        setOnClickListener { onClick() }
    }

/** A message: bold header line, body below, and a black bar on the left when highlighted. */
fun Context.msgBlock(header: String, body: String, highlight: Boolean): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        if (highlight) {
            addView(
                View(context).apply { setBackgroundColor(Color.BLACK) },
                LinearLayout.LayoutParams(dp(4), MATCH_PARENT).apply { rightMargin = dp(8) },
            )
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(bodyText(header, 14f, bold = true))
        col.addView(bodyText(body, 18f))
        addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

fun formatTs(ts: String): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date((ts.toDouble() * 1000).toLong()))

fun nowTs(offsetSeconds: Long = 0): String =
    String.format(Locale.US, "%.6f", System.currentTimeMillis() / 1000.0 + offsetSeconds)

/** Shared screen scaffold: title bar with optional Back / extra button, then content below. */
abstract class BaseActivity : Activity() {
    protected lateinit var root: LinearLayout
    protected lateinit var scrollView: ScrollView
    protected val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    protected fun setupScreen(title: String, showBack: Boolean = true, extraBarButton: Button? = null) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (showBack) bar.addView(inkButton("Back") { finish() })
        val titleView = bodyText(title, 22f, bold = true).apply { setPadding(dp(12), 0, dp(12), 0) }
        bar.addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        extraBarButton?.let { bar.addView(it) }
        root.addView(bar)
        root.addView(divider())
        setContentView(root)
    }

    /** Adds a scrolling area that takes the remaining height; returns the layout to add rows to. */
    protected fun scrollArea(): LinearLayout {
        val sv = ScrollView(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(inner, MATCH_PARENT, WRAP_CONTENT)
        root.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        scrollView = sv
        return inner
    }

    protected fun bg(block: () -> Unit) {
        Thread(block).start()
    }

    protected fun errText(e: Exception): String = when (e) {
        is SlackException -> "Slack error: ${e.message}"
        else -> "Network problem: ${e.message ?: e.javaClass.simpleName}"
    }
}
