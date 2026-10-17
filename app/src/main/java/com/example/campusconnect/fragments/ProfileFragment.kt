package com.example.campusconnect.fragments

import com.example.campusconnect.R
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.campusconnect.adapters.trackRsvps
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.decode.SvgDecoder
import coil.load
import com.example.campusconnect.activities.AvatarPickerActivity
import com.example.campusconnect.activities.EditProfileActivity
import com.example.campusconnect.activities.QrTicketActivity
import com.example.campusconnect.activities.SettingsActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentProfileBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private lateinit var pager: com.example.campusconnect.core.common.Pager<com.example.campusconnect.models.Event>
    private var currentUserId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager.getInstance(requireContext())
        currentUserId = session.getCurrentUserId()
        val userName = session.getCurrentUserName()
        val userEmail = session.getCurrentUserEmail()

        binding.userName.text = userName
        binding.userEmail.text = userEmail

        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).userDao().getUserByIdFlow(currentUserId).collectLatest { user ->
                user?.let {
                    binding.userKarma.text = getString(R.string.karma_format, it.karma_points)
                    binding.userBio.text = it.bio ?: "No bio yet. Tap edit to add one!"
                    
                    binding.interestsChipGroup.removeAllViews()
                    it.interests?.split(",")?.forEach { interest ->
                        val cleanInterest = interest.trim()
                        if (cleanInterest.isNotEmpty()) {
                            val chip = Chip(requireContext())
                            chip.text = cleanInterest
                            binding.interestsChipGroup.addView(chip)
                        }
                    }

                    if (it.avatar_uri != null) {
                        binding.profileImage.load(it.avatar_uri) {
                            crossfade(true)
                            placeholder(com.example.campusconnect.R.drawable.ic_user_placeholder)
                            error(com.example.campusconnect.R.drawable.ic_user_placeholder)
                        }
                    } else {
                        binding.profileImage.setImageResource(com.example.campusconnect.R.drawable.ic_user_placeholder)
                    }
                }
            }
        }

        adapter = EventAdapter(emptyList()) { event ->
            // Open QR ticket for this event
            viewLifecycleOwner.lifecycleScope.launch {
                val rsvp = AppDatabase.getDatabase(requireContext()).rsvpDao().getUserRSVPForEvent(currentUserId, event.id)
                rsvp?.let {
                    val intent = Intent(requireContext(), QrTicketActivity::class.java)
                    intent.putExtra("RSVP_ID", it.id)
                    startActivity(intent)
                }
            }
        }
        adapter.trackRsvps(requireContext(), viewLifecycleOwner)

        binding.myEventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myEventsRecyclerView.adapter = adapter
        pager = com.example.campusconnect.core.common.Pager(binding.myEventsRecyclerView) { adapter.updateEvents(it) }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), AvatarPickerActivity::class.java))
        }

        binding.editAvatarIcon.setOnClickListener {
            startActivity(Intent(requireContext(), AvatarPickerActivity::class.java))
        }

        loadJoinedEvents()
    }

    private fun loadJoinedEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.eventDao().getJoinedEvents(currentUserId).collectLatest { joinedEvents ->
                pager.submit(joinedEvents)
                binding.joinedEmptyText.visibility = if (joinedEvents.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
