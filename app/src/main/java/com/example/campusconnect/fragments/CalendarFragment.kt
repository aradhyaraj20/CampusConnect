package com.example.campusconnect.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.R
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentCalendarBinding
import com.example.campusconnect.models.Event
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private var allEvents: List<Event> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter(emptyList()) { event ->
            val intent = Intent(requireContext(), EventDetailActivity::class.java)
            intent.putExtra("EVENT_ID", event.id)
            startActivity(intent)
        }

        binding.calendarEventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.calendarEventsRecyclerView.adapter = adapter

        binding.scrollUpFab.setOnClickListener {
            binding.calendarEventsRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.calendarEventsRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        binding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val selectedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(calendarDay.calendar.time)
                binding.calendarFeedTitle.text = getString(R.string.events_on_date, selectedDate)
                filterEventsForDate(calendarDay.calendar)
            }
        })

        loadAllEvents()
    }

    private fun loadAllEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).eventDao().getAllEvents().collectLatest { events ->
                allEvents = events
                setupCalendarMarkers(events)
                showUpcomingEvents()
            }
        }
    }

    private fun setupCalendarMarkers(events: List<Event>) {
        val calendarDays = events.map { event ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = event.date_timestamp
            val day = CalendarDay(calendar)
            day.imageResource = R.drawable.ic_campus_logo
            day
        }
        binding.calendarView.setCalendarDays(calendarDays)
    }

    private fun showUpcomingEvents() {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val upcoming = allEvents.filter { it.date_timestamp >= today.timeInMillis }
            .sortedBy { it.date_timestamp }
        
        binding.calendarFeedTitle.setText(R.string.upcoming_events)
        showEvents(upcoming)
    }

    private fun filterEventsForDate(calendar: Calendar) {
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        val filtered = allEvents.filter {
            val eventCal = Calendar.getInstance()
            eventCal.timeInMillis = it.date_timestamp
            eventCal.get(Calendar.DAY_OF_MONTH) == day &&
            eventCal.get(Calendar.MONTH) == month &&
            eventCal.get(Calendar.YEAR) == year
        }
        showEvents(filtered)
    }

    private fun showEvents(events: List<Event>) {
        adapter.updateEvents(events)
        binding.calendarEmptyText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
