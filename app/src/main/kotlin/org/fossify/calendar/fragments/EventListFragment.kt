package org.fossify.calendar.fragments

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.EventListAdapter
import org.fossify.calendar.databinding.FragmentEventListBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.editEvent
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getEventListItems
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.EVENTS_LIST_VIEW
import org.fossify.calendar.helpers.FETCH_INTERVAL
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.INITIAL_EVENTS
import org.fossify.calendar.helpers.MIN_EVENTS_TRESHOLD
import org.fossify.calendar.helpers.UPDATE_BOTTOM
import org.fossify.calendar.helpers.UPDATE_TOP
import org.fossify.calendar.helpers.getNowSeconds
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.ListEvent
import org.fossify.calendar.models.ListItem
import org.fossify.calendar.models.ListSectionDay
import org.fossify.calendar.models.ListSectionMonth
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.joda.time.DateTime
import kotlin.math.max
import kotlin.math.min


class EventListFragment : MyFragmentHolder(), RefreshRecyclerViewListener {
    private var mEvents = ArrayList<Event>()
    private var minFetchedTS = 0L
    private var maxFetchedTS = 0L
    private var wereInitialEventsAdded = false
    private var hasBeenScrolled = false
    private var bottomItemAtRefresh: ListItem? = null

    private var use24HourFormat = false

    private var mDayCode = ""

    private var mToolbarTitle = ""

    private lateinit var binding: FragmentEventListBinding

    override val viewType = EVENTS_LIST_VIEW

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEventListBinding.inflate(inflater, container, false)
        binding.root.background = ColorDrawable(requireContext().getProperBackgroundColor())
        binding.calendarEventsListHolder.id = (System.currentTimeMillis() % 100000).toInt()
        binding.calendarEmptyListPlaceholder2.apply {
            setTextColor(context.getProperPrimaryColor())
            underlineText()
            setOnClickListener {
                activity?.hideKeyboard()
                context.launchNewEventIntent(getNewEventDayCode())
            }
        }

        use24HourFormat = requireContext().config.use24HourFormat
        mDayCode = arguments?.getString(DAY_CODE) ?: ""
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        checkEvents()
        val defaultTitle = Formatter.getLongMonthYear(requireContext(), Formatter.getTodayCode())
        mToolbarTitle = defaultTitle
        (activity as? MainActivity)?.setToolbarTitle(defaultTitle)
        val use24Hour = requireContext().config.use24HourFormat
        if (use24Hour != use24HourFormat) {
            use24HourFormat = use24Hour
            (binding.calendarEventsList.adapter as? EventListAdapter)?.toggle24HourFormat(
                use24HourFormat
            )
        }
    }

    override fun onPause() {
        super.onPause()
        use24HourFormat = requireContext().config.use24HourFormat
    }

    private fun checkEvents() {
        if (!wereInitialEventsAdded) {
            val now = DateTime()
            val defaultMin = now.minusMinutes(requireContext().config.displayPastEvents).seconds()
            val defaultMax = now.plusMonths(6).seconds()
            minFetchedTS = if (mDayCode.isEmpty()) defaultMin else min(defaultMin, Formatter.getDayStartTS(mDayCode))
            maxFetchedTS = if (mDayCode.isEmpty()) defaultMax else max(defaultMax, Formatter.getDayEndTS(mDayCode))
        }

        requireContext().eventsHelper.getEvents(minFetchedTS, maxFetchedTS) { events ->
            if (events.size >= MIN_EVENTS_TRESHOLD) {
                receivedEvents(events, INITIAL_EVENTS)
            } else {
                if (!wereInitialEventsAdded) {
                    maxFetchedTS += FETCH_INTERVAL
                }

                requireContext().eventsHelper.getEvents(minFetchedTS, maxFetchedTS) {
                    mEvents = it
                    receivedEvents(mEvents, INITIAL_EVENTS, !wereInitialEventsAdded)
                }
            }
            wereInitialEventsAdded = true
        }
    }

    private fun receivedEvents(
        events: ArrayList<Event>,
        updateStatus: Int,
        forceRecreation: Boolean = false
    ) {
        if (context == null || activity == null) {
            return
        }

        mEvents = events
        val listItems = requireContext().getEventListItems(mEvents)

        activity?.runOnUiThread {
            if (activity == null) {
                return@runOnUiThread
            }

            val currAdapter = binding.calendarEventsList.adapter
            if (currAdapter == null || forceRecreation) {
                EventListAdapter(
                    activity as SimpleActivity,
                    listItems,
                    true,
                    this,
                    binding.calendarEventsList
                ) {
                    if (it is ListEvent) {
                        context?.editEvent(it)
                    }
                }.apply {
                    binding.calendarEventsList.adapter = this
                    if (mDayCode.isNotEmpty()) {
                        scrollToAnchorDay()
                    } else {
                        scrollToNextAppointment()
                    }
                }

                if (requireContext().areSystemAnimationsEnabled) {
                    binding.calendarEventsList.scheduleLayoutAnimation()
                }

                binding.calendarEventsList.endlessScrollListener =
                    object : MyRecyclerView.EndlessScrollListener {
                        override fun updateTop() {
                            fetchPreviousPeriod()
                        }

                        override fun updateBottom() {
                            fetchNextPeriod()
                        }
                    }

                binding.calendarEventsList.addOnScrollListener(object :
                    RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        updateToolbarMonth()
                    }

                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (!hasBeenScrolled) {
                            hasBeenScrolled = true
                            (activity as? MainActivity)?.refreshItems()
                            (activity as? MainActivity)?.refreshMenuItems()
                        }
                    }
                })
            } else {
                (currAdapter as EventListAdapter).updateListItems(listItems)
                if (updateStatus == UPDATE_TOP) {
                    val item = listItems.indexOfFirst { it == bottomItemAtRefresh }
                    if (item != -1) {
                        binding.calendarEventsList.scrollToPosition(item)
                    }
                } else if (updateStatus == UPDATE_BOTTOM) {
                    binding.calendarEventsList.smoothScrollBy(
                        0,
                        requireContext().resources.getDimension(R.dimen.endless_scroll_move_height)
                            .toInt()
                    )
                }
            }
            checkPlaceholderVisibility()
            updateToolbarMonth()
        }
    }

    private fun updateToolbarMonth() {
        val adapter = binding.calendarEventsList.adapter as? EventListAdapter ?: return
        val layoutManager = binding.calendarEventsList.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition == RecyclerView.NO_POSITION || firstVisiblePosition >= adapter.listItems.size) {
            return
        }

        var monthTitle = ""
        val firstItem = adapter.listItems.getOrNull(firstVisiblePosition)
        if (firstItem is ListSectionMonth) {
            monthTitle = firstItem.title
        } else {
            for (i in firstVisiblePosition downTo 0) {
                val item = adapter.listItems.getOrNull(i)
                if (item is ListSectionDay) {
                    monthTitle = Formatter.getLongMonthYear(requireContext(), item.code)
                    break
                }
            }
        }

        if (monthTitle.isNotEmpty() && monthTitle != mToolbarTitle) {
            mToolbarTitle = monthTitle
            (activity as? MainActivity)?.setToolbarTitle(monthTitle)
        }
    }

    private fun checkPlaceholderVisibility() {
        binding.calendarEmptyListPlaceholder.beVisibleIf(mEvents.isEmpty())
        binding.calendarEmptyListPlaceholder2.beVisibleIf(mEvents.isEmpty())
        binding.calendarEventsList.beGoneIf(mEvents.isEmpty())
        if (activity != null) {
            binding.calendarEmptyListPlaceholder.setTextColor(requireActivity().getProperTextColor())
            if (mEvents.isEmpty()) {
                val placeholderTextId = if (requireActivity().config.displayCalendars.isEmpty()) {
                    R.string.everything_filtered_out
                } else {
                    R.string.no_upcoming_events
                }

                binding.calendarEmptyListPlaceholder.setText(placeholderTextId)
            }
        }
    }

    private fun scrollToAnchorDay() {
        val adapter = binding.calendarEventsList.adapter as? EventListAdapter ?: return
        val targetIndex = adapter.listItems.indexOfFirst { it is ListSectionDay && it.code >= mDayCode }
        if (targetIndex != -1) {
            binding.calendarEventsList.post {
                (binding.calendarEventsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    targetIndex,
                    0
                )
                binding.calendarEventsList.onGlobalLayout {
                    hasBeenScrolled = false
                    (activity as? MainActivity)?.refreshItems()
                    (activity as? MainActivity)?.refreshMenuItems()
                }
            }
        }
    }

    private fun scrollToNextAppointment() {
        val adapter = binding.calendarEventsList.adapter as? EventListAdapter ?: return
        val now = getNowSeconds()
        // Only consider events that STARTED TODAY or later. The fetch window includes
        // multi-day events from previous days (e.g. a parking booking that started
        // days ago but ends tonight): those sort ABOVE today's sections, so picking
        // them scrolled the list into yesterday instead of to the next meeting.
        val todayStartTS = Formatter.getDayStartTS(Formatter.getTodayCode())
        val upcoming = adapter.listItems.filterIsInstance<ListEvent>().filter { it.endTS > now }
        val pick = upcoming.firstOrNull { it.startTS >= todayStartTS && !it.isAllDay }
            ?: upcoming.firstOrNull { it.startTS >= todayStartTS }
            ?: upcoming.firstOrNull { !it.isAllDay }
            ?: upcoming.firstOrNull()
        val targetIndex = if (pick != null) {
            adapter.listItems.indexOfFirst { it === pick }
        } else {
            -1
        }

        // Fallback: if nothing has endTS > now (e.g. shifted timestamps), at least
        // jump to today's section instead of leaving the list at the top of yesterday.
        val scrollIndex = if (targetIndex != -1) {
            targetIndex
        } else {
            adapter.listItems.indexOfFirst { it is ListSectionDay && !it.isPastSection }
        }

        if (scrollIndex != -1) {
            binding.calendarEventsList.post {
                (binding.calendarEventsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    scrollIndex,
                    0
                )
                binding.calendarEventsList.onGlobalLayout {
                    // Re-assert after layout: state restoration or the layout animation
                    // can win over the post() scroll and dump the list back to the top.
                    val firstVisible = (binding.calendarEventsList.layoutManager as? LinearLayoutManager)
                        ?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    if (firstVisible != scrollIndex && !hasBeenScrolled) {
                        (binding.calendarEventsList.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(scrollIndex, 0)
                    }
                    hasBeenScrolled = false
                    (activity as? MainActivity)?.refreshItems()
                    (activity as? MainActivity)?.refreshMenuItems()
                }
            }
        }
    }

    private fun fetchPreviousPeriod() {
        val lastPosition =
            (binding.calendarEventsList.layoutManager as MyLinearLayoutManager).findLastVisibleItemPosition()
        bottomItemAtRefresh =
            (binding.calendarEventsList.adapter as EventListAdapter).listItems[lastPosition]

        val oldMinFetchedTS = minFetchedTS - 1
        minFetchedTS -= FETCH_INTERVAL
        requireContext().eventsHelper.getEvents(minFetchedTS, oldMinFetchedTS) {
            it.forEach { event ->
                if (mEvents.firstOrNull { it.id == event.id && it.startTS == event.startTS } == null) {
                    mEvents.add(0, event)
                }
            }

            receivedEvents(mEvents, UPDATE_TOP)
        }
    }

    private fun fetchNextPeriod() {
        val oldMaxFetchedTS = maxFetchedTS + 1
        maxFetchedTS += FETCH_INTERVAL
        requireContext().eventsHelper.getEvents(oldMaxFetchedTS, maxFetchedTS) {
            it.forEach { event ->
                if (mEvents.firstOrNull { it.id == event.id && it.startTS == event.startTS } == null) {
                    mEvents.add(0, event)
                }
            }

            receivedEvents(mEvents, UPDATE_BOTTOM)
        }
    }

    override fun refreshItems() {
        checkEvents()
    }

    override fun goToToday() {
        // In the simple all-events list, "Today" means current time: reuse the snap
        // so it lands on what is running now or the next appointment. The snap's own
        // fallback still jumps to the top of today's section when nothing is upcoming.
        if (mDayCode.isEmpty()) {
            scrollToNextAppointment()
            return
        }
        val listItems = requireContext().getEventListItems(mEvents)
        val firstNonPastSectionIndex =
            listItems.indexOfFirst { it is ListSectionDay && !it.isPastSection }
        if (firstNonPastSectionIndex != -1) {
            (binding.calendarEventsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                firstNonPastSectionIndex,
                0
            )
            binding.calendarEventsList.onGlobalLayout {
                hasBeenScrolled = false
                (activity as? MainActivity)?.refreshItems()
                (activity as? MainActivity)?.refreshMenuItems()
            }
        }
    }

    override fun showGoToDateDialog() {}

    override fun refreshEvents() {
        checkEvents()
    }

    override fun shouldGoToTodayBeVisible() = hasBeenScrolled

    override fun getNewEventDayCode() = mDayCode.ifEmpty { Formatter.getTodayCode() }

    override fun printView() {
        binding.apply {
            if (calendarEventsList.isGone()) {
                context?.toast(org.fossify.commons.R.string.no_items_found)
                return@apply
            }

            (calendarEventsList.adapter as? EventListAdapter)?.togglePrintMode()
            Handler().postDelayed({
                requireContext().printBitmap(calendarEventsList.getViewBitmap())

                Handler().postDelayed({
                    (calendarEventsList.adapter as? EventListAdapter)?.togglePrintMode()
                }, 1000)
            }, 1000)
        }
    }

    override fun getCurrentDate() = null
}
