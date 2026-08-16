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
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentCalendarBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter

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

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(calendar.time)
            loadEventsForDate(selectedDate)
        }

        // Load today's events initially
        val today = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        loadEventsForDate(today)
    }

    private fun loadEventsForDate(date: String) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).eventDao().getAllEvents().collectLatest { events ->
                val filtered = events.filter { it.date.contains(date) }
                adapter.updateEvents(filtered)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
