package org.fossify.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.ReorderCalendarsAdapter
import org.fossify.calendar.databinding.DialogReorderCalendarsBinding
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class ReorderCalendarsDialog(
    val activity: SimpleActivity,
    calendars: List<CalendarEntity>,
    val callback: (List<Long>) -> Unit
) {
    private var dialog: AlertDialog? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val binding by activity.viewBinding(DialogReorderCalendarsBinding::inflate)
    private val orderedCalendars = ArrayList(calendars)

    init {
        val adapter = ReorderCalendarsAdapter(activity, orderedCalendars) { holder ->
            itemTouchHelper.startDrag(holder)
        }
        binding.reorderCalendarsList.adapter = adapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int = makeMovementFlags(ItemTouchHelper.ACTION_STATE_DRAG, 0)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition < 0 || toPosition < 0) {
                    return false
                }

                orderedCalendars.add(toPosition, orderedCalendars.removeAt(fromPosition))
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = false

            override fun isItemViewSwipeEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(binding.reorderCalendarsList)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmReorder() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun confirmReorder() {
        callback(orderedCalendars.mapNotNull { it.id })
        dialog?.dismiss()
    }
}