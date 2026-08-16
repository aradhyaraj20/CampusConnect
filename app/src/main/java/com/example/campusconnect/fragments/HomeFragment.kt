package com.example.campusconnect.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.activities.CreateEventActivity
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.adapters.FlashEventAdapter
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentHomeBinding
import com.example.campusconnect.models.Event
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private lateinit var flashAdapter: FlashEventAdapter
    private var allEvents: List<Event> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter(emptyList()) { event ->
            val intent = Intent(requireContext(), EventDetailActivity::class.java)
            intent.putExtra("EVENT_ID", event.id)
            startActivity(intent)
        }

        flashAdapter = FlashEventAdapter(emptyList()) { event ->
            val intent = Intent(requireContext(), EventDetailActivity::class.java)
            intent.putExtra("EVENT_ID", event.id)
            startActivity(intent)
        }

        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = adapter

        binding.flashRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.flashRecyclerView.adapter = flashAdapter

        binding.addEventFab.setOnClickListener {
            startActivity(Intent(requireContext(), CreateEventActivity::class.java))
        }

        binding.scrollUpFab.setOnClickListener {
            binding.eventsRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.eventsRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterEvents(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).eventDao().getAllEvents().collectLatest { events ->
                allEvents = events
                
                // Handle Flash Sessions
                val currentTime = System.currentTimeMillis()
                val flashSessions = events.filter { it.is_flash && (it.expiry_time ?: 0) > currentTime }
                
                if (flashSessions.isNotEmpty()) {
                    flashAdapter.updateEvents(flashSessions)
                    binding.liveNowHeader.visibility = View.VISIBLE
                    binding.flashRecyclerView.visibility = View.VISIBLE
                } else {
                    binding.liveNowHeader.visibility = View.GONE
                    binding.flashRecyclerView.visibility = View.GONE
                }

                filterEvents(binding.searchEditText.text.toString())
            }
        }
    }

    private fun filterEvents(query: String) {
        val currentTime = System.currentTimeMillis()
        val filtered = allEvents.filter { event ->
            // Don't show expired flash sessions in main feed
            val isNotExpiredFlash = !event.is_flash || (event.expiry_time ?: Long.MAX_VALUE) > currentTime
            val matchesQuery = query.isEmpty() || 
                    event.title.contains(query, ignoreCase = true) || 
                    event.description.contains(query, ignoreCase = true)
            
            isNotExpiredFlash && matchesQuery
        }
        adapter.updateEvents(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
