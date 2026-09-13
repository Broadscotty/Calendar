package org.fossify.calendar.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import org.fossify.calendar.R
import org.fossify.calendar.databinding.MonthViewBackgroundBinding
import org.fossify.calendar.databinding.MonthViewBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getWeekNumberWidth
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.launchNewTaskIntent
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.TYPE_EVENT
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.models.DayMonthly
import org.fossify.commons.compose.extensions.getActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.models.RadioItem
import kotlin.math.abs

// used in the Monthly view fragment, 1 view per screen
class MonthViewWrapper(
    context: Context,
    attrs: AttributeSet,
    defStyle: Int
) : FrameLayout(context, attrs, defStyle) {
    private var dayWidth = 0f
    private var weekDaysLetterHeight = 0
    private var horizontalOffset = 0
    private var parentHeight = 0
    private var contentHeight = 0
    private var wereViewsAdded = false
    private var isMonthDayView = true
    private var days = ArrayList<DayMonthly>()
    private var inflater: LayoutInflater
    private var binding: MonthViewBinding
    private var dayClickCallback: ((day: DayMonthly) -> Unit)? = null

    private val touchSlop: Int
    private val scroller: OverScroller
    private var velocityTracker: VelocityTracker? = null
    private var downRawY = 0f
    private var lastRawY = 0f
    private var isDragging = false

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        val normalTextSize =
            resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_text_size).toFloat()
        weekDaysLetterHeight = 2 * normalTextSize.toInt()

        inflater = LayoutInflater.from(context)
        binding = MonthViewBinding.inflate(inflater, this, true)
        setupHorizontalOffset()

        clipChildren = true
        clipToPadding = true
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        scroller = OverScroller(context)

        onGlobalLayout {
            if (!wereViewsAdded && days.isNotEmpty()) {
                measureSizes()
                addClickableBackgrounds()
                binding.monthView.updateDays(days, isMonthDayView)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        parentHeight = height
        dayWidth = (width - horizontalOffset) / COLUMN_COUNT.toFloat()

        binding.monthView.setMinimumContentHeight(height)
        binding.monthView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        contentHeight = binding.monthView.measuredHeight

        setMeasuredDimension(width, height)
        scrollTo(scrollX, scrollY.coerceIn(0, maxScrollY()))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        binding.monthView.layout(0, 0, right - left, contentHeight)

        var index = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === binding.monthView) {
                continue
            }

            val viewX = index % COLUMN_COUNT
            val viewY = index / COLUMN_COUNT
            val cellWidth = dayWidth.toInt()
            val cellHeight = binding.monthView.getRowHeight(viewY)
            val childLeft = (viewX * dayWidth + horizontalOffset).toInt()
            val childTop = binding.monthView.getRowTop(viewY)

            child.measure(
                MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)
            )

            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
            index++
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (maxScrollY() == 0) {
            return super.onInterceptTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawY = event.rawY
                lastRawY = event.rawY
                isDragging = false
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!isDragging && abs(event.rawY - downRawY) > touchSlop) {
                    isDragging = true
                }
                return isDragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (maxScrollY() == 0) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain()
                }
                velocityTracker?.addMovement(event)
                lastRawY = event.rawY
                isDragging = false
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!isDragging && abs(event.rawY - downRawY) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    val dy = (lastRawY - event.rawY).toInt()
                    lastRawY = event.rawY
                    if (dy != 0) {
                        scrollBy(0, dy)
                        scrollTo(scrollX, scrollY.coerceIn(0, maxScrollY()))
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                if (isDragging) {
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    scroller.fling(scrollX, scrollY, 0, -velocityY.toInt(), 0, 0, 0, maxScrollY())
                    invalidate()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scrollX, scroller.currY.coerceIn(0, maxScrollY()))
            invalidate()
        }
    }

    private fun maxScrollY(): Int = (contentHeight - parentHeight).coerceAtLeast(0)

    fun updateDays(
        newDays: ArrayList<DayMonthly>,
        addEvents: Boolean,
        callback: ((DayMonthly) -> Unit)? = null
    ) {
        setupHorizontalOffset()
        measureSizes()
        dayClickCallback = callback
        days = newDays
        if (dayWidth != 0f) {
            addClickableBackgrounds()
        }

        isMonthDayView = !addEvents
        binding.monthView.updateDays(days, isMonthDayView)
    }

    private fun setupHorizontalOffset() {
        horizontalOffset = context.getWeekNumberWidth()
    }

    private fun measureSizes() {
        dayWidth = (width - horizontalOffset) / COLUMN_COUNT.toFloat()
    }

    private fun addClickableBackgrounds() {
        removeAllViews()
        binding = MonthViewBinding.inflate(inflater, this, true)
        wereViewsAdded = true
        days.forEachIndexed { index, day ->
            addViewBackground(index % COLUMN_COUNT, index / COLUMN_COUNT, day)
        }
    }

    private fun addViewBackground(viewX: Int, viewY: Int, day: DayMonthly) {
        MonthViewBackgroundBinding.inflate(inflater, this, false).root.apply {
            if (isMonthDayView) {
                background = null
            }
            //Accessible label composed by day and month
            contentDescription = "${day.value} ${
                Formatter.getMonthName(
                    context,
                    Formatter.getDateTimeFromCode(day.code).monthOfYear
                )
            }"

            setOnClickListener {
                dayClickCallback?.invoke(day)

                if (isMonthDayView) {
                    binding.monthView.updateCurrentlySelectedDay(viewX, viewY)
                }
            }

            setOnLongClickListener {
                if (context.config.allowCreatingTasks) {
                    val items = arrayListOf(
                        RadioItem(TYPE_EVENT, context.getString(R.string.event)),
                        RadioItem(TYPE_TASK, context.getString(R.string.task))
                    )

                    RadioGroupDialog(context.getActivity(), items) {
                        if (it == TYPE_EVENT) {
                            context.launchNewEventIntent(day.code)
                        } else {
                            context.launchNewTaskIntent(day.code)
                        }
                    }
                } else {
                    context.launchNewEventIntent(day.code)
                }
                true
            }

            addView(this)
        }
    }

    fun togglePrintMode() {
        binding.monthView.togglePrintMode()
    }
}