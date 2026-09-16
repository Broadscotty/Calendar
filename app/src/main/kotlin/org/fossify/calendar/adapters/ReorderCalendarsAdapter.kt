package org.fossify.calendar.adapters

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.ItemReorderCalendarBinding
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setFillWithStroke

class ReorderCalendarsAdapter(
    val activity: SimpleActivity,
    val calendars: ArrayList<CalendarEntity>,
    val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ReorderCalendarsAdapter.CalendarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        return CalendarViewHolder(
            binding = ItemReorderCalendarBinding.inflate(activity.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) =
        holder.bindView(calendar = calendars[position])

    override fun getItemCount() = calendars.size

    inner class CalendarViewHolder(val binding: ItemReorderCalendarBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(calendar: CalendarEntity) {
            binding.apply {
                reorderCalendarTitle.text = calendar.getDisplayTitle()
                reorderCalendarTitle.setTextColor(activity.getProperTextColor())
                reorderCalendarColor.setFillWithStroke(
                    calendar.color,
                    activity.getProperBackgroundColor()
                )
                reorderCalendarDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this@CalendarViewHolder)
                    }
                    false
                }
            }
        }
    }
}