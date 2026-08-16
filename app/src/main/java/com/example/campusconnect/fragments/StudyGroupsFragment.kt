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
import com.example.campusconnect.databinding.FragmentStudyGroupsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StudyGroupsFragment : Fragment() {
    private var _binding: FragmentStudyGroupsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudyGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter(emptyList()) { group ->
            val intent = Intent(requireContext(), EventDetailActivity::class.java)
            intent.putExtra("EVENT_ID", group.id)
            startActivity(intent)
        }

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.groupsRecyclerView.adapter = adapter

        binding.scrollUpFab.setOnClickListener {
            binding.groupsRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.groupsRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).eventDao().getAllEvents().collectLatest { events ->
                val studyGroups = events.filter { it.type == "study_group" }
                adapter.updateEvents(studyGroups)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
