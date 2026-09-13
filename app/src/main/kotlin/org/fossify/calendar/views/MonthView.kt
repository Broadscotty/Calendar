package org.fossify.calendar.views

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import org.fossify.calendar.R
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.calendar.helpers.ROW_COUNT
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime
import kotlin.math.max
import kotlin.math.min

// used in the Monthly view fragment, 1 view per screen
class MonthView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    private class RowEvent(val event: Event) {
        var firstCol = 0
        var lastCol = 0
    }

    companion object {
        private const val EVENT_DOT_COLUMN_COUNT = 3
        private const val EVENT_DOT_ROW_COUNT = 1
        private const val FONT_SCALE_FLOOR = 0.85f
        private const val FONT_SCALE_SAFETY = 0.97f
        private const val MAX_EVENTS_PER_DAY = 7
    }

    private var textPaint: Paint
    private var eventTitlePaint: TextPaint
    private var gridPaint: Paint
    private var circleStrokePaint: Paint
    private var plusTextPaint: Paint
    private var eventDotPaint: Paint
    private var eventStripPaint: Paint
    private var moreTextPaint: Paint
    private var config = context.config
    private var dayWidth = 0f
    private var primaryColor = 0
    private var textColor = 0
    private var weekendsTextColor = 0
    private var weekDaysLetterHeight = 0
    private var normalTextSize = 0
    private var baseNormalTextSize = 0
    private var defaultNormalTextSize = 0
    private var eventTitleHeight = 0
    private var baseEventTitleHeight = 0
    private var defaultEventTitleHeight = 0
    private var currDayOfWeek = 0
    private var smallPadding = 0
    private var horizontalOffset = 0
    private var showWeekNumbers = false
    private var dimPastEvents = true
    private var dimCompletedTasks = true
    private var highlightWeekends = false
    private var isPrintVersion = false
    private var isMonthDayView = false
    private var contentHeight = 0
    private var minimumContentHeight = 0
    private var dayTextRect = Rect()
    private var dayLetters = ArrayList<String>()
    private var days = ArrayList<DayMonthly>()
    private var rowHeights = IntArray(ROW_COUNT)
    private var selectedDayCoords = Point(-1, -1)

    private val dayNumberHeight: Int
        get() = (normalTextSize * 1.4f).toInt()

    private val eventLineStep: Int
        get() = eventTitleHeight + smallPadding * 2

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        primaryColor = context.getProperPrimaryColor()
        textColor = context.getProperTextColor()
        weekendsTextColor = config.highlightWeekendsColor
        showWeekNumbers = config.showWeekNumbers
        dimPastEvents = config.dimPastEvents
        dimCompletedTasks = config.dimCompletedTasks
        highlightWeekends = config.highlightWeekends

        smallPadding = resources.displayMetrics.density.toInt()
        baseNormalTextSize = resources.getDimensionPixelSize(R.dimen.month_view_text_size)
        normalTextSize = (baseNormalTextSize * getMonthViewFontSizeScale()).toInt()
        defaultNormalTextSize = normalTextSize
        weekDaysLetterHeight = normalTextSize

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = FontHelper.getTypeface(context)
        }

        eventDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        plusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 175
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = FontHelper.getTypeface(context)
        }

        gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.adjustAlpha(LOWER_ALPHA)
        }

        circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimension(R.dimen.circle_stroke_width)
            color = primaryColor
        }

        val smallerTextSize = resources.getDimensionPixelSize(R.dimen.month_view_event_text_size)
        baseEventTitleHeight = smallerTextSize
        defaultEventTitleHeight = (baseEventTitleHeight * getMonthViewFontSizeScale()).toInt()
        eventTitleHeight = defaultEventTitleHeight
        eventTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = smallerTextSize.toFloat()
            textAlign = Paint.Align.LEFT
            typeface = FontHelper.getTypeface(context)
        }

        moreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = baseEventTitleHeight.toFloat()
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
            typeface = FontHelper.getTypeface(context)
        }

        eventStripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
    }

    // the wrapper uses this so that even an empty month still fills the whole screen
    fun setMinimumContentHeight(value: Int) {
        if (minimumContentHeight != value) {
            minimumContentHeight = value
            requestLayout()
        }
    }

    // total height of all 6 rows plus the weekday header
    fun getContentHeight() = contentHeight

    fun getRowHeight(row: Int): Int = rowHeights[row]

    fun getRowTop(row: Int): Int {
        var top = weekDaysLetterHeight
        for (i in 0 until row) {
            top += rowHeights[i]
        }
        return top
    }

    fun updateDays(newDays: ArrayList<DayMonthly>, isMonthDayView: Boolean) {
        this.isMonthDayView = isMonthDayView
        days = newDays
        showWeekNumbers = config.showWeekNumbers
        horizontalOffset = context.getWeekNumberWidth()
        normalTextSize = (baseNormalTextSize * getMonthViewFontSizeScale()).toInt()
        defaultNormalTextSize = normalTextSize
        eventTitleHeight = (baseEventTitleHeight * getMonthViewFontSizeScale()).toInt()
        defaultEventTitleHeight = eventTitleHeight
        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
        computeContentHeight()
        invalidate()
    }

    private fun getMonthViewFontSizeScale(): Float = when (config.monthViewFontSize) {
        FONT_SIZE_SMALL -> 0.85f
        FONT_SIZE_LARGE -> 1.15f
        FONT_SIZE_EXTRA_LARGE -> 1.3f
        else -> 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        dayWidth = (width - horizontalOffset) / COLUMN_COUNT.toFloat()

        computeContentHeight()

        val desiredHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            max(contentHeight, height)
        } else {
            contentHeight
        }
        setMeasuredDimension(width, desiredHeight)
    }

    private fun computeContentHeight() {
        if (days.isEmpty()) {
            contentHeight = minimumContentHeight
            return
        }

        if (isMonthDayView) {
            normalTextSize = defaultNormalTextSize
            eventTitleHeight = defaultEventTitleHeight
            syncTextSizes()
            val headerless = max(0, minimumContentHeight - weekDaysLetterHeight)
            val rawRowHeight = headerless / ROW_COUNT
            for (y in 0 until ROW_COUNT) {
                rowHeights[y] = rawRowHeight
            }
            contentHeight = minimumContentHeight
            return
        }

        var rowSlots = IntArray(ROW_COUNT)
        for (y in 0 until ROW_COUNT) {
            rowSlots[y] = getRowSlots(y)
        }

        applyFontScale(rowSlots)
        layoutRows()
    }

    // shrink the effective text sizes so that the busiest week fits into one uniform row height
    private fun applyFontScale(rowSlots: IntArray) {
        normalTextSize = defaultNormalTextSize
        eventTitleHeight = defaultEventTitleHeight
        weekDaysLetterHeight = defaultNormalTextSize
        textPaint.textSize = normalTextSize.toFloat()
        plusTextPaint.textSize = normalTextSize.toFloat()
        eventTitlePaint.textSize = eventTitleHeight.toFloat()

        val available = max(0, minimumContentHeight - weekDaysLetterHeight)
        val uniformRowHeight = available / ROW_COUNT
        val fullDayNumberHeight = (defaultNormalTextSize * 1.4f).toInt()
        val fullLineStep = defaultEventTitleHeight + smallPadding * 2
        var maxRowSlots = 0
        for (y in 0 until ROW_COUNT) {
            maxRowSlots = max(maxRowSlots, rowSlots[y])
        }

        val fullRowHeight = fullDayNumberHeight + maxRowSlots * fullLineStep + smallPadding * 2
        if (fullRowHeight <= uniformRowHeight) {
            return
        }

        val fixedCost = maxRowSlots * smallPadding * 2 + smallPadding * 2
        val variableCost = defaultNormalTextSize * 1.4f + defaultEventTitleHeight * maxRowSlots
        val rawScale = (uniformRowHeight - fixedCost) / variableCost
        val scale = (rawScale * FONT_SCALE_SAFETY).coerceIn(FONT_SCALE_FLOOR, 1f)

        normalTextSize = (defaultNormalTextSize * scale).toInt()
        eventTitleHeight = (defaultEventTitleHeight * scale).toInt()
        syncTextSizes()
    }

    // every week row gets the same height, filling the whole screen evenly
    private fun layoutRows() {
        val available = max(0, minimumContentHeight - weekDaysLetterHeight)
        var remaining = available
        val base = remaining / ROW_COUNT
        for (y in 0 until ROW_COUNT) {
            rowHeights[y] = base
            remaining -= base
        }
        if (remaining > 0) {
            rowHeights[ROW_COUNT - 1] += remaining
        }

        contentHeight = minimumContentHeight
    }

    private fun syncTextSizes() {
        weekDaysLetterHeight = normalTextSize
        textPaint.textSize = normalTextSize.toFloat()
        plusTextPaint.textSize = normalTextSize.toFloat()
        eventTitlePaint.textSize = eventTitleHeight.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (config.showGrid && !isMonthDayView) {
            drawGrid(canvas)
        }

        addWeekDayLetters(canvas)
        if (showWeekNumbers && days.isNotEmpty()) {
            addWeekNumbers(canvas)
        }

        var curId = 0
        for (y in 0 until ROW_COUNT) {
            val rowTop = getRowTop(y)
            for (x in 0 until COLUMN_COUNT) {
                val day = days.getOrNull(curId)
                if (day != null) {
                    val dayNumber = day.value.toString()
                    val numberPaint = getTextPaint(day)
                    numberPaint.textAlign = Paint.Align.LEFT
                    numberPaint.getTextBounds(dayNumber, 0, dayNumber.length, dayTextRect)
                    val xPos = x * dayWidth + horizontalOffset
                    val numberX = xPos + smallPadding * 2
                    val numberBaseline = rowTop + smallPadding * 2 + numberPaint.textSize
                    val circleCenterX = numberX + dayTextRect.width() / 2f
                    val circleCenterY = numberBaseline - dayTextRect.height() / 2f

                    val isDaySelected = selectedDayCoords.x != -1 && x == selectedDayCoords.x && y == selectedDayCoords.y
                    when {
                        isDaySelected -> {
                            canvas.drawCircle(circleCenterX, circleCenterY, numberPaint.textSize * 0.8f, circleStrokePaint)
                            if (day.isToday) {
                                numberPaint.color = textColor
                            }
                        }

                        day.isToday && !isPrintVersion -> {
                            canvas.drawCircle(circleCenterX, circleCenterY, numberPaint.textSize * 0.8f, getCirclePaint(day))
                        }
                    }

                    canvas.drawText(dayNumber, numberX, numberBaseline, numberPaint)

                    if (isMonthDayView) {
                        if (!isDaySelected && !day.isToday && day.dayEvents.isNotEmpty()) {
                            drawEventDots(canvas, day, xPos, rowTop)
                        }
                    } else if (x == 0) {
                        drawMonthEvents(canvas, y, rowTop)
                    }
                }
                curId++
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        // vertical lines
        for (i in 0 until COLUMN_COUNT) {
            var lineX = i * dayWidth
            if (showWeekNumbers) {
                lineX += horizontalOffset
            }
            canvas.drawLine(lineX, 0f, lineX, canvas.height.toFloat(), gridPaint)
        }

        // horizontal lines
        canvas.drawLine(0f, 0f, canvas.width.toFloat(), 0f, gridPaint)
        for (i in 0 until ROW_COUNT) {
            canvas.drawLine(0f, getRowTop(i).toFloat(), canvas.width.toFloat(), getRowTop(i).toFloat(), gridPaint)
        }
        canvas.drawLine(0f, canvas.height.toFloat(), canvas.width.toFloat(), canvas.height.toFloat(), gridPaint)
    }

    private fun addWeekDayLetters(canvas: Canvas) {
        for (i in 0 until COLUMN_COUNT) {
            val xPos = horizontalOffset + (i + 1) * dayWidth - dayWidth / 2
            var weekDayLetterPaint = textPaint
            if (i == currDayOfWeek && !isPrintVersion) {
                weekDayLetterPaint = getColoredPaint(primaryColor)
            } else if (highlightWeekends && context.isWeekendIndex(i)) {
                weekDayLetterPaint = getColoredPaint(weekendsTextColor)
            }
            canvas.drawText(dayLetters[i], xPos, weekDaysLetterHeight * 0.8f, weekDayLetterPaint)
        }
    }

    private fun addWeekNumbers(canvas: Canvas) {
        val weekNumberPaint = Paint(textPaint)

        for (i in 0 until ROW_COUNT) {
            val weekDays = days.subList(i * 7, i * 7 + 7)
            weekNumberPaint.color = if (weekDays.any { it.isToday && !isPrintVersion }) primaryColor else textColor

            // fourth day of the week determines the week of the year number
            val weekOfYear = days.getOrNull(i * 7 + 3)?.weekOfYear ?: 1
            val id = "$weekOfYear:"
            val horizontalMarginFactor = 0.5f
            val xPos = horizontalOffset * horizontalMarginFactor
            canvas.drawText(id, xPos, getRowTop(i) + textPaint.textSize, weekNumberPaint)
        }
    }

    private fun drawEventDots(canvas: Canvas, day: DayMonthly, xPos: Float, rowTop: Int) {
        val xPosCenter = xPos + dayWidth / 2
        val height = dayTextRect.height() * 1.25f
        val eventCount = day.dayEvents.size
        val dotRadius = textPaint.textSize * 0.2f
        val stepSize = dotRadius * 2.5f
        val columnCount = EVENT_DOT_COLUMN_COUNT

        val dayEventsSorted = day.dayEvents
            .asSequence()
            .sortedWith(
                comparator = compareBy({ it.startTS }, { it.endTS }, { it.title })
            )
            .distinctBy { it.color }

        var xDot: Float
        var yDot = rowTop + dayNumberHeight + height + textPaint.textSize / 2
        var indexInRow: Int

        val dotCount = dayEventsSorted.count()
        for ((index, event) in dayEventsSorted.withIndex()) {
            indexInRow = index % columnCount
            xDot = xPosCenter + stepSize * (indexInRow - (min(dotCount, columnCount)) / 2)
            if (dotCount % 2 == 0) { // center even number of dots
                xDot += stepSize / 2
            }

            if (index > 0 && indexInRow == 0) { // next row of dots
                yDot += stepSize
            }

            // Always show a + sign if the event count exceeds columnCount.
            if (eventCount - 1 != index && index >= columnCount * EVENT_DOT_ROW_COUNT - 1) {
                plusTextPaint.textSize = stepSize * 1.5f
                canvas.drawText("+", xDot, yDot + dotRadius * 1.2f, plusTextPaint)
                break
            } else {
                val paint = eventDotPaint.apply { color = event.color }
                canvas.drawCircle(xDot, yDot, dotRadius, paint)
            }
        }
    }

    private fun drawMonthEvents(canvas: Canvas, row: Int, rowTop: Int) {
        val (bars, singlesPerDay) = getRowEventsStructure(row)
        val barBands = packBarBands(bars)
        val rowBottom = rowTop + rowHeights[row] - smallPadding
        val bandCount = if (bars.isEmpty()) 0 else (barBands.maxOrNull()!! + 1)

        val drawnBars = ArrayList<RowEvent>()
        for (i in bars.indices) {
            val lineTop = rowTop + dayNumberHeight + barBands[i] * eventLineStep
            if (lineTop + eventLineStep > rowBottom) {
                continue
            }
            drawEventBar(canvas, bars[i].event, bars[i].firstCol, bars[i].lastCol, lineTop)
            drawnBars.add(bars[i])
        }
        val listTop = rowTop + dayNumberHeight + bandCount * eventLineStep

        val visibleBarsPerDay = IntArray(COLUMN_COUNT)
        for (bar in drawnBars) {
            for (col in bar.firstCol..bar.lastCol) {
                visibleBarsPerDay[col]++
            }
        }

        val drawnPerDay = IntArray(COLUMN_COUNT)
        for (col in 0 until COLUMN_COUNT) {
            val daySingles = singlesPerDay[col]
            val maxLines = max(0, MAX_EVENTS_PER_DAY - visibleBarsPerDay[col])
            var dayTop = listTop
            var count = 0
            while (count < daySingles.size && count < maxLines) {
                if (dayTop + eventLineStep > rowBottom) {
                    break
                }
                drawEventLine(canvas, daySingles[count].event, daySingles[count].firstCol, dayTop)
                dayTop += eventLineStep
                drawnPerDay[col]++
                count++
            }
        }

        for (col in 0 until COLUMN_COUNT) {
            val day = days.getOrNull(row * COLUMN_COUNT + col) ?: continue
            val hidden = day.dayEvents.size - drawnPerDay[col] - visibleBarsPerDay[col]
            if (hidden > 0) {
                val x = (col + 1) * dayWidth + horizontalOffset - smallPadding
                val baseline = rowBottom - smallPadding
                canvas.drawText("+$hidden", x, baseline.toFloat(), moreTextPaint)
            }
        }
    }

    // how many stacked lines a row needs: the packed bar bands on top plus the busiest single day
    private fun getRowSlots(row: Int): Int {
        val (bars, singlesPerDay) = getRowEventsStructure(row)
        val bandCount = if (bars.isEmpty()) 0 else (packBarBands(bars).maxOrNull()!! + 1)
        val coveringBars = IntArray(COLUMN_COUNT)
        for (bar in bars) {
            for (col in bar.firstCol..bar.lastCol) {
                coveringBars[col]++
            }
        }
        var maxDayCount = 0
        for (col in 0 until COLUMN_COUNT) {
            val cap = max(0, MAX_EVENTS_PER_DAY - coveringBars[col])
            maxDayCount = max(maxDayCount, min(singlesPerDay[col].size, cap))
        }
        return bandCount + maxDayCount
    }

    private fun getRowEventsStructure(row: Int): Pair<ArrayList<RowEvent>, Array<ArrayList<RowEvent>>> {
        val bars = ArrayList<RowEvent>()
        val singlesPerDay = Array(COLUMN_COUNT) { ArrayList<RowEvent>() }
        for (rowEvent in getRowEvents(row)) {
            if (rowEvent.lastCol > rowEvent.firstCol) {
                bars.add(rowEvent)
            } else {
                singlesPerDay[rowEvent.firstCol].add(rowEvent)
            }
        }
        bars.sortWith(compareBy({ it.firstCol }, { it.lastCol }))
        return bars to singlesPerDay
    }

    private fun packBarBands(bars: List<RowEvent>): IntArray {
        val barBands = IntArray(bars.size)
        val bands = ArrayList<BooleanArray>()
        for (i in bars.indices) {
            val bar = bars[i]
            var placed = -1
            for (b in 0 until bands.size) {
                val band = bands[b]
                var overlaps = false
                for (c in bar.firstCol..bar.lastCol) {
                    if (band[c]) {
                        overlaps = true
                        break
                    }
                }
                if (!overlaps) {
                    placed = b
                    break
                }
            }
            if (placed == -1) {
                placed = bands.size
                bands.add(BooleanArray(COLUMN_COUNT))
            }
            val band = bands[placed]
            for (c in bar.firstCol..bar.lastCol) {
                band[c] = true
            }
            barBands[i] = placed
        }
        return barBands
    }

    private fun getRowEvents(row: Int): ArrayList<RowEvent> {
        val eventMap = LinkedHashMap<Long?, RowEvent>()
        for (col in 0 until COLUMN_COUNT) {
            val day = days.getOrNull(row * COLUMN_COUNT + col) ?: continue
            for (event in day.dayEvents) {
                val rowEvent = eventMap[event.id]
                if (rowEvent == null) {
                    eventMap[event.id] = RowEvent(event).apply {
                        firstCol = col
                        lastCol = col
                    }
                } else {
                    rowEvent.lastCol = col
                }
            }
        }

        val rowEvents = ArrayList(eventMap.values)
        rowEvents.sortWith(compareBy({ !it.event.getIsAllDay() }, { it.event.startTS }, { it.event.title }))
        return rowEvents
    }

    private fun drawEventBar(canvas: Canvas, event: Event, firstCol: Int, lastCol: Int, lineTop: Int) {
        val left = firstCol * dayWidth + horizontalOffset + smallPadding
        val right = (lastCol + 1) * dayWidth + horizontalOffset - smallPadding
        val top = (lineTop + smallPadding / 2).toFloat()
        val bottom = (lineTop + eventLineStep - smallPadding).toFloat()

        val barColor = getEventLineColor(event)
        eventStripPaint.color = barColor
        val cornerRadius = smallPadding.toFloat()
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, eventStripPaint)

        val paint = getEventBarTitlePaint(barColor)
        val baseline = (lineTop + eventTitleHeight + smallPadding / 2).toFloat()
        val textX = firstCol * dayWidth + horizontalOffset + smallPadding * 2
        val availableWidth = (dayWidth - smallPadding * 4).coerceAtLeast(0f)
        if (availableWidth > 0) {
            val title = event.title.trim()
            val ellipsized = TextUtils.ellipsize(title, eventTitlePaint, availableWidth, TextUtils.TruncateAt.END)
            canvas.drawText(ellipsized.toString(), 0, ellipsized.length, textX, baseline, paint)
        }
    }

    private fun drawEventLine(canvas: Canvas, event: Event, col: Int, lineTop: Int) {
        val paint = getEventLineTitlePaint(event)
        val baseline = (lineTop + eventTitleHeight + smallPadding).toFloat()
        val textX = col * dayWidth + horizontalOffset + smallPadding
        val availableWidth = (dayWidth - smallPadding * 2).coerceAtLeast(0f)
        if (availableWidth > 0) {
            val title = event.title.trim()
            val ellipsized = TextUtils.ellipsize(title, eventTitlePaint, availableWidth, TextUtils.TruncateAt.END)
            canvas.drawText(ellipsized.toString(), 0, ellipsized.length, textX, baseline, paint)
        }
    }

    private fun getEventLineColor(event: Event): Int {
        var paintColor = event.color
        val adjustAlpha = when {
            event.isTask() -> dimCompletedTasks && event.isTaskCompleted()
            else -> dimPastEvents && event.isPastEvent && !isPrintVersion
        }

        if (adjustAlpha) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }

        return paintColor
    }

    private fun getEventLineTitlePaint(event: Event): Paint {
        val curPaint = Paint(eventTitlePaint)
        curPaint.color = getEventLineColor(event)
        curPaint.isStrikeThruText = event.shouldStrikeThrough()
        return curPaint
    }

    private fun getEventBarTitlePaint(barColor: Int): Paint {
        val curPaint = Paint(eventTitlePaint)
        curPaint.color = barColor.getContrastColor()
        return curPaint
    }

    private fun getTextPaint(startDay: DayMonthly): Paint {
        var paintColor = when {
            !isPrintVersion && startDay.isToday -> primaryColor.getContrastColor()
            highlightWeekends && startDay.isWeekend -> weekendsTextColor
            else -> textColor
        }

        if (!startDay.isThisMonth) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }

        return getColoredPaint(paintColor)
    }

    private fun getColoredPaint(color: Int): Paint {
        val curPaint = Paint(textPaint)
        curPaint.color = color
        return curPaint
    }

    private fun getCirclePaint(day: DayMonthly): Paint {
        val curPaint = Paint(textPaint)
        var paintColor = primaryColor
        if (!day.isThisMonth) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }
        curPaint.color = paintColor
        return curPaint
    }

    private fun initWeekDayLetters() {
        dayLetters = context.withFirstDayOfWeekToFront(
            context.resources.getStringArray(org.fossify.commons.R.array.week_days_short).toList()
        )
    }

    private fun setupCurrentDayOfWeekIndex() {
        if (days.firstOrNull { it.isToday && it.isThisMonth } == null) {
            currDayOfWeek = -1
            return
        }

        currDayOfWeek = context.getProperDayIndexInWeek(DateTime())
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(org.fossify.commons.R.color.theme_light_text_color, null)
        } else {
            context.getProperTextColor()
        }

        textPaint.color = textColor
        gridPaint.color = textColor.adjustAlpha(LOWER_ALPHA)
        invalidate()
        initWeekDayLetters()
    }

    fun updateCurrentlySelectedDay(x: Int, y: Int) {
        selectedDayCoords = Point(x, y)
        invalidate()
    }
}