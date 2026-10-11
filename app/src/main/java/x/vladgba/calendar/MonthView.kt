package x.vladgba.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

/**
 * Month grid drawn directly on a Canvas: 7 columns x 6 rows plus a weekday header.
 * Tap selects a day, horizontal fling switches month.
 */
class MonthView(context: Context) : View(context) {

    var year = 0; private set
    var month = 0; private set // 0-based

    /** Selected day as yyyymmdd key. */
    var selectedKey = 0
        set(value) { field = value; invalidate() }

    var hasNotes: (Int) -> Boolean = { false }
    var onDayTap: (year: Int, month0: Int, day: Int) -> Unit = { _, _, _ -> }
    var onSwipe: (direction: Int) -> Unit = {}

    private val firstDow = Calendar.getInstance().firstDayOfWeek
    private val weekdayNames: Array<String> = DateFormatSymbols.getInstance().shortWeekdays

    private val density = resources.displayMetrics.density
    private val headerH = 32 * density

    private val cText = context.color(R.color.text)
    private val cDim = context.color(R.color.text_dim)
    private val cAccent = context.color(R.color.accent)
    private val cOnAccent = context.color(R.color.on_accent)
    private val cToday = context.color(R.color.today_bg)
    private val cWeekend = context.color(R.color.weekend)

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Precomputed cells for the current month: key per cell + whether it belongs to the month.
    private val cellKeys = IntArray(42)
    private val cellInMonth = BooleanArray(42)
    private val cellDow = IntArray(42)
    private var todayKey = 0

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val idx = cellAt(e.x, e.y)
            if (idx < 0) return false
            val k = cellKeys[idx]
            performClick()
            onDayTap(k / 10000, k / 100 % 100 - 1, k % 100)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > abs(vy) && abs(vx) > 600) {
                onSwipe(if (vx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    fun setMonth(year: Int, month0: Int) {
        this.year = year
        this.month = month0
        val now = Calendar.getInstance()
        todayKey = EventStore.key(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))

        val c = Calendar.getInstance().apply {
            clear()
            set(year, month0, 1)
        }
        val offset = (c.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
        c.add(Calendar.DAY_OF_MONTH, -offset)
        for (i in 0 until 42) {
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            cellKeys[i] = EventStore.key(y, m, c.get(Calendar.DAY_OF_MONTH))
            cellInMonth[i] = m == month0
            cellDow[i] = c.get(Calendar.DAY_OF_WEEK)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        contentDescription = DateFormatSymbols.getInstance().months[month0] + " " + year
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cell = w / 7f
        // Square-ish cells, but never more than ~60% of the screen (landscape, tablets).
        val maxH = resources.displayMetrics.heightPixels * 0.6f
        val desired = min(headerH + cell * 6 * 0.9f, maxH).toInt()
        setMeasuredDimension(w, resolveSize(desired, heightMeasureSpec))
    }

    private fun cellW() = (width - paddingLeft - paddingRight) / 7f
    private fun cellH() = (height - paddingTop - paddingBottom - headerH) / 6f

    private fun cellAt(x: Float, y: Float): Int {
        val col = ((x - paddingLeft) / cellW()).toInt()
        val row = ((y - paddingTop - headerH) / cellH()).toInt()
        if (y - paddingTop < headerH || col !in 0..6 || row !in 0..5) return -1
        return row * 7 + col
    }

    @Suppress("ClickableViewAccessibility") // performClick() is called from onSingleTapUp
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        playSoundEffect(android.view.SoundEffectConstants.CLICK)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cw = cellW()
        val ch = cellH()
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()

        // Weekday header
        val headBase = top + headerH / 2 - (headPaint.descent() + headPaint.ascent()) / 2
        for (col in 0 until 7) {
            val dow = (firstDow - 1 + col) % 7 + 1
            headPaint.color = if (isWeekend(dow)) cWeekend else cDim
            canvas.drawText(weekdayNames[dow].uppercase(), left + cw * (col + 0.5f), headBase, headPaint)
        }

        val radius = min(cw, ch) * 0.42f
        dayPaint.textSize = min(cw, ch) * 0.36f
        val dotR = 2.5f * density

        for (i in 0 until 42) {
            val cx = left + cw * (i % 7 + 0.5f)
            val cy = top + headerH + ch * (i / 7 + 0.5f)
            val k = cellKeys[i]
            val selected = k == selectedKey
            val today = k == todayKey

            if (selected) {
                fillPaint.color = cAccent
                canvas.drawCircle(cx, cy, radius, fillPaint)
            } else if (today) {
                fillPaint.color = cToday
                canvas.drawCircle(cx, cy, radius, fillPaint)
            }

            dayPaint.color = when {
                selected -> cOnAccent
                !cellInMonth[i] -> cDim
                today -> cAccent
                isWeekend(cellDow[i]) -> cWeekend
                else -> cText
            }
            dayPaint.typeface = if (today || selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val base = cy - (dayPaint.descent() + dayPaint.ascent()) / 2
            canvas.drawText((k % 100).toString(), cx, base, dayPaint)

            if (hasNotes(k)) {
                fillPaint.color = if (selected) cOnAccent else cAccent
                canvas.drawCircle(cx, cy + radius * 0.62f, dotR, fillPaint)
            }
        }
    }

    private fun isWeekend(dow: Int) = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
}
