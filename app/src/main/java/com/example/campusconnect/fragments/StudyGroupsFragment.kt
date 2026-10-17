package com.example.campusconnect.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.campusconnect.adapters.trackRsvps
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.adapters.LegendAdapter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentStudyGroupsBinding
import com.example.campusconnect.models.User
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StudyGroupsFragment : Fragment() {
    private var _binding: FragmentStudyGroupsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private lateinit var pager: com.example.campusconnect.core.common.Pager<com.example.campusconnect.models.Event>
    private lateinit var legendAdapter: LegendAdapter

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

        adapter.trackRsvps(requireContext(), viewLifecycleOwner)

        legendAdapter = LegendAdapter(emptyList())
        binding.legendsRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.legendsRecyclerView.adapter = legendAdapter

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.groupsRecyclerView.adapter = adapter
        pager = com.example.campusconnect.core.common.Pager(binding.groupsRecyclerView) { adapter.updateEvents(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val currentUserId = SessionManager.getInstance(requireContext()).getCurrentUserId()
            val currentUser = if (currentUserId != -1) db.userDao().getUserById(currentUserId) else null

            // Karma competition ("Legends") is scoped to classmates/batchmates on
            // the same course, not the whole app - a student only sees and competes
            // with peers registered on their own course.
            val course = currentUser?.course
            if (!course.isNullOrBlank()) {
                db.userDao().getTopUsersByCourse(course, 5).collectLatest { users ->
                    showLegends(users)
                }
            } else {
                // Organizers/admins previewing this screen, or students who never
                // set a course, fall back to the global board.
                db.userDao().getTopUsers(5).collectLatest { users ->
                    showLegends(users)
                }
            }
        }

        // Groups the student already has a seat in live under Home / My Events, so they are left
        // out here; the list updates as soon as they join or leave one.
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val userId = SessionManager.getInstance(requireContext()).getCurrentUserId()
            combine(db.eventDao().getAllEvents(), db.rsvpDao().getRsvpsForUser(userId)) { events, rsvps ->
                val joined = rsvps.filter { it.status == "going" }.map { it.event_id }.toSet()
                val allGroups = events.filter { it.type == "study_group" }
                allGroups.filter { it.id !in joined } to allGroups.isNotEmpty()
            }.collectLatest { (openGroups, anyGroups) ->
                pager.submit(openGroups)
                binding.groupsEmptyText.setText(if (anyGroups) R.string.study_groups_all_joined else R.string.study_groups_empty)
                binding.groupsEmptyText.visibility = if (openGroups.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** An empty leaderboard hides its header instead of leaving an orphan title. */
    private fun showLegends(users: List<User>) {
        legendAdapter.updateLegends(users)
        val visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
        binding.legendHeader.visibility = visibility
        binding.legendsRecyclerView.visibility = visibility
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
