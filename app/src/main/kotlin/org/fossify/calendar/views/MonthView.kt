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
        get() = (normalTextSize * 1.8f).toInt()

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
        baseNormalTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_text_size)
        normalTextSize = (baseNormalTextSize * getMonthViewFontSizeScale()).toInt()
        defaultNormalTextSize = normalTextSize
        weekDaysLetterHeight = normalTextSize * 2

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

        var maxEventCounts = IntArray(ROW_COUNT)
        for (y in 0 until ROW_COUNT) {
            maxEventCounts[y] = getRowEvents(y).size
        }

        applyFontScale(maxEventCounts)
        layoutRows(maxEventCounts)
    }

    // shrink the effective text sizes so that every row fits on screen without scrolling
    private fun applyFontScale(maxEventCounts: IntArray) {
        normalTextSize = defaultNormalTextSize
        eventTitleHeight = defaultEventTitleHeight
        weekDaysLetterHeight = defaultNormalTextSize * 2
        textPaint.textSize = normalTextSize.toFloat()
        plusTextPaint.textSize = normalTextSize.toFloat()
        eventTitlePaint.textSize = eventTitleHeight.toFloat()

        val available = max(0, minimumContentHeight - weekDaysLetterHeight)
        val fullDayNumberHeight = (defaultNormalTextSize * 1.8f).toInt()
        val fullLineStep = defaultEventTitleHeight + smallPadding * 2
        var fullRows = 0
        for (y in 0 until ROW_COUNT) {
            fullRows += fullDayNumberHeight + maxEventCounts[y] * fullLineStep + smallPadding * 2
        }

        if (fullRows <= available) {
            return
        }

        var fixedCost = 0
        var variableCost = 0f
        for (y in 0 until ROW_COUNT) {
            fixedCost += smallPadding * 2 * (maxEventCounts[y] + 1)
            variableCost += defaultNormalTextSize * 1.8f + defaultEventTitleHeight * maxEventCounts[y]
        }

        val rawScale = (available - fixedCost) / variableCost
        val scale = (rawScale * FONT_SCALE_SAFETY).coerceIn(FONT_SCALE_FLOOR, 1f)

        normalTextSize = (defaultNormalTextSize * scale).toInt()
        eventTitleHeight = (defaultEventTitleHeight * scale).toInt()
        syncTextSizes()
    }

    // compute the per-row heights so that the whole month fills exactly the available screen height
    private fun layoutRows(maxEventCounts: IntArray) {
        var neededHeight = weekDaysLetterHeight
        for (y in 0 until ROW_COUNT) {
            rowHeights[y] = dayNumberHeight + maxEventCounts[y] * eventLineStep + smallPadding * 2
            neededHeight += rowHeights[y]
        }

        if (neededHeight > minimumContentHeight) {
            var overflow = neededHeight - minimumContentHeight
            for (y in ROW_COUNT - 1 downTo 0) {
                if (overflow <= 0) break
                val minRow = dayNumberHeight + eventLineStep + smallPadding * 2
                val removable = min(overflow, rowHeights[y] - minRow)
                rowHeights[y] -= removable
                overflow -= removable
            }
        } else if (neededHeight < minimumContentHeight) {
            val extra = (minimumContentHeight - neededHeight) / ROW_COUNT
            if (extra > 0) {
                for (y in 0 until ROW_COUNT) {
                    rowHeights[y] += extra
                    neededHeight += extra
                }
            }
            if (neededHeight < minimumContentHeight) {
                rowHeights[ROW_COUNT - 1] += minimumContentHeight - neededHeight
            }
        }

        contentHeight = minimumContentHeight
    }

    private fun syncTextSizes() {
        weekDaysLetterHeight = normalTextSize * 2
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
            canvas.drawText(dayLetters[i], xPos, weekDaysLetterHeight * 0.7f, weekDayLetterPaint)
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
        val rowEvents = getRowEvents(row)
        val rowBottom = rowTop + rowHeights[row] - smallPadding
        val capacity = ((rowBottom - (rowTop + dayNumberHeight)) / eventLineStep).coerceAtLeast(0)

        var lineTop = rowTop + dayNumberHeight
        var index = 0
        val drawnPerDay = IntArray(COLUMN_COUNT)
        for (rowEvent in rowEvents) {
            if (index >= capacity) {
                break
            }
            if (rowEvent.lastCol > rowEvent.firstCol) {
                drawEventBar(canvas, rowEvent.event, rowEvent.firstCol, rowEvent.lastCol, lineTop)
            } else {
                drawEventLine(canvas, rowEvent.event, rowEvent.firstCol, lineTop)
            }
            for (col in rowEvent.firstCol..rowEvent.lastCol) {
                drawnPerDay[col]++
            }
            lineTop += eventLineStep
            index++
        }

        for (col in 0 until COLUMN_COUNT) {
            val day = days.getOrNull(row * COLUMN_COUNT + col) ?: continue
            val hidden = day.dayEvents.size - drawnPerDay[col]
            if (hidden > 0) {
                val x = (col + 1) * dayWidth + horizontalOffset - smallPadding
                val baseline = rowBottom - smallPadding
                canvas.drawText("+$hidden", x, baseline.toFloat(), moreTextPaint)
            }
        }
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
        val top = (lineTop + smallPadding).toFloat()
        val bottom = (lineTop + eventLineStep - smallPadding).toFloat()

        val barColor = getEventLineColor(event)
        eventStripPaint.color = barColor
        val cornerRadius = smallPadding.toFloat()
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, eventStripPaint)

        val paint = getEventBarTitlePaint(barColor)
        val baseline = (lineTop + eventTitleHeight + smallPadding).toFloat()
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
        curPaint.color = textColor
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