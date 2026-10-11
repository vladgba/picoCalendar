package x.vladgba.calendar

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Suppress("DEPRECATION")
fun Context.color(id: Int): Int =
    if (Build.VERSION.SDK_INT >= 23) getColor(id) else resources.getColor(id)

class MainActivity : Activity() {

    private lateinit var store: EventStore
    private lateinit var monthView: MonthView
    private lateinit var title: TextView
    private lateinit var dayLabel: TextView
    private lateinit var notesList: LinearLayout

    private var shownYear = 0
    private var shownMonth = 0
    private var selYear = 0
    private var selMonth = 0
    private var selDay = 0

    private val selKey get() = EventStore.key(selYear, selMonth, selDay)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = EventStore(java.io.File(filesDir, NOTES_FILE))

        val now = Calendar.getInstance()
        if (savedInstanceState != null && savedInstanceState.containsKey(K_SEL)) {
            val k = savedInstanceState.getInt(K_SEL)
            selYear = k / 10000; selMonth = k / 100 % 100 - 1; selDay = k % 100
            shownYear = savedInstanceState.getInt(K_YEAR)
            shownMonth = savedInstanceState.getInt(K_MONTH)
        } else {
            selYear = now.get(Calendar.YEAR)
            selMonth = now.get(Calendar.MONTH)
            selDay = now.get(Calendar.DAY_OF_MONTH)
            shownYear = selYear; shownMonth = selMonth
            applyDateExtra(intent)
        }

        setContentView(buildUi())
        setupEdgeToEdge()
        refresh()
    }

    /** Launched from the widget: jump to the tapped day. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyDateExtra(intent)) refresh()
    }

    private fun applyDateExtra(intent: Intent?): Boolean {
        val k = intent?.getIntExtra(EXTRA_DATE, 0) ?: 0
        if (k <= 0) return false
        selYear = k / 10000; selMonth = k / 100 % 100 - 1; selDay = k % 100
        shownYear = selYear; shownMonth = selMonth
        intent?.removeExtra(EXTRA_DATE) // don't re-apply on recreation
        return true
    }

    override fun onPause() {
        super.onPause()
        CalendarWidget.updateAll(this) // pick up note changes
    }

    override fun onResume() {
        super.onResume()
        // The date may have changed while we were in the background ("today" marker).
        monthView.setMonth(shownYear, shownMonth)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(K_SEL, selKey)
        outState.putInt(K_YEAR, shownYear)
        outState.putInt(K_MONTH, shownMonth)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
        }

        // Header: ‹  Month Year  ›
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        header.addView(arrow("‹", getString(R.string.prev_month)) { shiftMonth(-1) })
        title = TextView(this).apply {
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.text))
            gravity = Gravity.CENTER
            background = ripple(false)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { goToday() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(arrow("›", getString(R.string.next_month)) { shiftMonth(1) })
        root.addView(header)

        monthView = MonthView(this).apply {
            setPadding(dp(8), 0, dp(8), dp(4))
            hasNotes = { store.has(it) }
            onDayTap = { y, m, d -> select(y, m, d) }
            onSwipe = { shiftMonth(it) }
        }
        root.addView(monthView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(View(this).apply { setBackgroundColor(color(R.color.divider)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(16), dp(4), dp(16), 0)
            })

        // Selected day + add button
        val dayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(16), dp(8))
        }
        dayLabel = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.text))
        }
        dayRow.addView(dayLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val add = TextView(this).apply {
            text = "+"
            textSize = 26f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(color(R.color.on_accent))
            contentDescription = getString(R.string.add_note)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(R.color.accent))
            }
            elevation = dp(3).toFloat()
            setOnClickListener { editNote(-1) }
        }
        dayRow.addView(add, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(dayRow)

        notesList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(notesList) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    private fun arrow(label: String, desc: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 30f
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(color(R.color.accent))
        contentDescription = desc
        background = ripple(true)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
    }

    private fun setupEdgeToEdge() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val w = window
        val decor = w.decorView
        val content = findViewById<FrameLayout>(android.R.id.content).getChildAt(0)

        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION") w.setDecorFitsSystemWindows(false)
            val light = if (night) 0 else
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(light,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            w.isNavigationBarContrastEnforced = false
        } else {
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!night) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 23) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = flags
            // Without light-bar support the white icons need a dark scrim to stay visible.
            @Suppress("DEPRECATION")
            if (!night && Build.VERSION.SDK_INT < 23) w.statusBarColor = 0x44000000
            @Suppress("DEPRECATION")
            if (!night && Build.VERSION.SDK_INT < 26) w.navigationBarColor = 0x44000000
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 28) {
            w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        content.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        content.requestApplyInsets()
    }

    // ---------------------------------------------------------------- logic

    private fun shiftMonth(delta: Int) {
        val c = Calendar.getInstance().apply { clear(); set(shownYear, shownMonth, 1); add(Calendar.MONTH, delta) }
        shownYear = c.get(Calendar.YEAR)
        shownMonth = c.get(Calendar.MONTH)
        // Keep the selection inside the shown month (clamp day, e.g. 31 Jan -> 28 Feb).
        val maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        selYear = shownYear; selMonth = shownMonth; selDay = minOf(selDay, maxDay)
        refresh()
    }

    private fun goToday() {
        val now = Calendar.getInstance()
        select(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }

    private fun select(y: Int, m: Int, d: Int) {
        selYear = y; selMonth = m; selDay = d
        shownYear = y; shownMonth = m // tapping a greyed day of a neighbour month jumps there
        refresh()
    }

    private fun refresh() {
        val c = Calendar.getInstance().apply { clear(); set(shownYear, shownMonth, 1) }
        title.text = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(c.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

        monthView.setMonth(shownYear, shownMonth)
        monthView.selectedKey = selKey

        val sel = Calendar.getInstance().apply { clear(); set(selYear, selMonth, selDay) }
        dayLabel.text = DateFormat.getDateInstance(DateFormat.FULL).format(sel.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

        renderNotes()
    }

    private fun renderNotes() {
        notesList.removeAllViews()
        val notes = store.get(selKey)
        if (notes.isEmpty()) {
            notesList.addView(TextView(this).apply {
                setText(R.string.no_notes)
                setTextColor(color(R.color.text_dim))
                textSize = 14f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }
        notes.forEachIndexed { i, text ->
            val item = TextView(this).apply {
                this.text = text
                textSize = 15f
                setTextColor(color(R.color.text))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(color(R.color.surface))
                    setStroke(dp(1), color(R.color.divider))
                }
                setOnClickListener { editNote(i) }
                setOnLongClickListener { confirmDelete(i); true }
            }
            notesList.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }
    }

    /** index = -1 adds a new note, otherwise edits the existing one. */
    private fun editNote(index: Int) {
        val key = selKey
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHint(R.string.hint_note)
            maxLines = 6
            if (index >= 0) {
                setText(store.get(key)[index])
                setSelection(text.length)
            }
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        val b = AlertDialog.Builder(this)
            .setTitle(if (index < 0) R.string.add_note else R.string.edit_note)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val t = input.text.toString().trim()
                when {
                    t.isEmpty() && index >= 0 -> store.remove(key, index)
                    t.isEmpty() -> {}
                    index < 0 -> store.add(key, t)
                    else -> store.update(key, index, t)
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
        if (index >= 0) b.setNeutralButton(R.string.delete) { _, _ -> store.remove(key, index); refresh() }
        val dialog = b.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun confirmDelete(index: Int) {
        val key = selKey
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_note)
            .setPositiveButton(R.string.delete) { _, _ -> store.remove(key, index); refresh() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun ripple(borderless: Boolean): Drawable? {
        val tv = TypedValue()
        val attr = if (borderless) android.R.attr.selectableItemBackgroundBorderless
        else android.R.attr.selectableItemBackground
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) getDrawable(tv.resourceId) else null
    }

    companion object {
        const val NOTES_FILE = "notes.tsv"
        const val EXTRA_DATE = "date"
        private const val K_SEL = "sel"
        private const val K_YEAR = "year"
        private const val K_MONTH = "month"
    }
}
