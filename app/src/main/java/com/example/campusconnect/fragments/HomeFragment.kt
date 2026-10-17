package com.example.campusconnect.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.campusconnect.adapters.trackRsvps
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.appbar.AppBarLayout
import com.example.campusconnect.R
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.activities.NotificationsActivity
import com.example.campusconnect.activities.SearchActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.adapters.FlashEventAdapter
import com.example.campusconnect.adapters.RecommendationAdapter
import com.example.campusconnect.core.network.AppConnectivity
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentHomeBinding
import com.example.campusconnect.models.Event
import com.example.campusconnect.presentation.home.HomeFeedState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private lateinit var myEventsAdapter: EventAdapter
    private lateinit var pager: com.example.campusconnect.core.common.Pager<com.example.campusconnect.models.Event>
    private lateinit var flashAdapter: FlashEventAdapter
    private lateinit var recommendationAdapter: RecommendationAdapter
    private var allEvents: List<Event> = emptyList()
    private var userInterests: String? = null
    private var joinedEventIds: Set<Int> = emptySet()

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

        // The collapsing header sits under the transparent status bar: pad it by the real inset
        // (24-50dp depending on device/cutout) and keep the pinned bar + inset visible when collapsed.
        val barExtraTop = (4 * resources.displayMetrics.density).toInt()
        val collapsedBottom = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeHeader) { header, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            header.updatePadding(top = top + barExtraTop)
            header.minimumHeight = top + barExtraTop + binding.homePinnedBar.layoutParams.height + collapsedBottom
            insets
        }
        ViewCompat.requestApplyInsets(binding.homeHeader)

        // Fade the greeting + search out as the header collapses so it never shows through the pinned bar.
        binding.homeAppBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
            val range = appBar.totalScrollRange
            val fraction = if (range > 0) -verticalOffset.toFloat() / range else 0f
            val alpha = (1f - fraction * 1.8f).coerceIn(0f, 1f)
            binding.homeHeaderContent.alpha = alpha
            binding.homeHeaderContent.visibility = if (alpha <= 0.01f) View.INVISIBLE else View.VISIBLE
        })

        val session = SessionManager.getInstance(requireContext())
        val currentUserId = session.getCurrentUserId()

        loadAdBanner()
        setupEventLists()
        setupHeader(currentUserId)
        setupMyEvents(currentUserId)

        if (AppConnectivity.isOffline(requireContext())) {
            binding.offlineBannerInclude.offlineBanner.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.userDao().getUserByIdFlow(currentUserId).collectLatest { user ->
                userInterests = user?.interests
                binding.greetingText.text = user?.name
                    ?.substringBefore(' ')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.home_greeting, it) }
                    ?: getString(R.string.home_greeting_generic)
                updateRecommendations()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            // Re-emits on event or RSVP changes so joining an event moves it from
            // "Upcoming Events" up into "My Events" immediately.
            combine(
                db.eventDao().getAllEvents(),
                db.rsvpDao().getRsvpsForUser(currentUserId)
            ) { events, rsvps ->
                events to rsvps.filter { it.status == "going" }.mapTo(HashSet()) { it.event_id }
            }.collectLatest { (events, goingIds) ->
                if (_binding == null) return@collectLatest
                allEvents = events
                joinedEventIds = goingIds
                binding.homeSkeletonInclude.homeSkeleton.visibility = View.GONE

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

                updateRecommendations()
                showEvents()
            }
        }

        refreshNotificationBadge(currentUserId)
    }

    private fun setupEventLists() {
        val openDetail: (Event) -> Unit = ::openEventDetail
        adapter = EventAdapter(emptyList(), openDetail)
        adapter.trackRsvps(requireContext(), viewLifecycleOwner)
        flashAdapter = FlashEventAdapter(emptyList(), openDetail)
        recommendationAdapter = RecommendationAdapter(emptyList(), openDetail)

        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = adapter
        pager = com.example.campusconnect.core.common.Pager(binding.eventsRecyclerView) { adapter.updateEvents(it) }

        binding.flashRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.flashRecyclerView.adapter = flashAdapter

        binding.recommendationsRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recommendationsRecyclerView.adapter = recommendationAdapter
    }

    private fun setupHeader(currentUserId: Int) {
        binding.searchBar.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.viewCalendarButton.setOnClickListener { safeNavigate(R.id.calendarFragment) }
        binding.notificationBell.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }
        binding.homeOverflowButton.setOnClickListener { anchor ->
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.toolbar_account_menu, popup.menu)
            popup.setOnMenuItemClickListener {
                com.example.campusconnect.core.common.AccountMenuHelper.handleMenuItem(
                    requireActivity() as androidx.appcompat.app.AppCompatActivity, it.itemId
                )
            }
            popup.show()
        }
    }

    private fun refreshNotificationBadge(currentUserId: Int) {
        if (currentUserId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            val count = runCatching {
                AppDatabase.getDatabase(requireContext()).notificationDao().getNotificationCount(currentUserId)
            }.getOrDefault(0)
            if (_binding == null) return@launch
            if (count > 0) {
                binding.notifBadge.text = if (count > 9) "9+" else count.toString()
                binding.notifBadge.visibility = View.VISIBLE
            } else {
                binding.notifBadge.visibility = View.GONE
            }
        }
    }

    private fun safeNavigate(destinationId: Int) {
        runCatching { findNavController().navigate(destinationId) }
    }

    /**
     * "My Events": upcoming events this student has a confirmed seat for. Waitlisted and past
     * events are left to the My Events tab ("See all").
     */
    private fun setupMyEvents(currentUserId: Int) {
        myEventsAdapter = EventAdapter(emptyList(), ::openEventDetail)
        myEventsAdapter.trackRsvps(requireContext(), viewLifecycleOwner)
        binding.myEventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myEventsRecyclerView.adapter = myEventsAdapter

        binding.myEventsSeeAll.setOnClickListener { safeNavigate(R.id.calendarFragment) }
        binding.myEventsBrowseButton.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        val startOfToday = startOfTodayMillis()

        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).eventDao()
                .getUpcomingGoingEvents(currentUserId, startOfToday)
                .collectLatest { joined ->
                    if (_binding == null) return@collectLatest
                    val shown = joined.take(MY_EVENTS_LIMIT)
                    myEventsAdapter.updateEvents(shown)
                    val empty = joined.isEmpty()
                    binding.myEventsRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
                    binding.myEventsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.myEventsSeeAll.visibility = if (joined.size > MY_EVENTS_LIMIT) View.VISIBLE else View.GONE
                }
        }
    }

    private fun openEventDetail(event: Event) {
        startActivity(Intent(requireContext(), EventDetailActivity::class.java).apply {
            putExtra("EVENT_ID", event.id)
        })
    }

    private var dismissedAdId: Int? = null

    /**
     * Shows whatever ad the admin currently has toggled active (AdminAdsActivity)
     * as a dismissible banner. Purely admin-authored content - no ad network.
     */
    private fun loadAdBanner() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).adDao().getActiveAd().collectLatest { ad ->
                if (_binding == null) return@collectLatest
                if (ad == null || ad.id == dismissedAdId) {
                    binding.adBannerCard.visibility = View.GONE
                    return@collectLatest
                }
                binding.adBannerCard.visibility = View.VISIBLE
                binding.adBannerTitle.text = ad.title
                binding.adBannerMessage.text = ad.message
                if (!ad.image_uri.isNullOrBlank()) {
                    binding.adBannerImage.visibility = View.VISIBLE
                    binding.adBannerImage.load(ad.image_uri)
                } else {
                    binding.adBannerImage.visibility = View.GONE
                }
                val safeUri = ad.link_url?.let { android.net.Uri.parse(it.trim()) }
                    ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
                val clickTarget: View.OnClickListener? = safeUri?.let { uri ->
                    View.OnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                        } catch (_: Exception) { /* no browser available */ }
                    }
                }
                binding.adBannerCard.setOnClickListener(clickTarget)
                binding.adBannerDismiss.setOnClickListener {
                    dismissedAdId = ad.id
                    binding.adBannerCard.visibility = View.GONE
                }
            }
        }
    }

    private fun updateRecommendations() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val currentUserId = SessionManager.getInstance(requireContext()).getCurrentUserId()
            val searches = db.userDao().getRecentSearch(currentUserId).first().map { it.query.lowercase() }

            if (_binding == null) return@launch
            // Read the interests only now, after the suspension: this runs from both the user and the
            // events flows, and whichever finishes last must see the latest of both.
            val interests = (userInterests?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
                ?: emptyList()) + searches

            if (interests.isEmpty() || allEvents.isEmpty()) {
                binding.recommendationsHeader.visibility = View.GONE
                binding.recommendationsRecyclerView.visibility = View.GONE
                return@launch
            }

            val recommended = allEvents.filter { event ->
                !event.is_flash && (
                    interests.any { it in event.category.lowercase() } ||
                        interests.any { it in event.title.lowercase() } ||
                        interests.any { it in (event.tags ?: "").lowercase() }
                    )
            }.distinctBy { it.id }.take(5)

            if (recommended.isNotEmpty()) {
                recommendationAdapter.updateEvents(recommended)
                binding.recommendationsHeader.visibility = View.VISIBLE
                binding.recommendationsRecyclerView.visibility = View.VISIBLE
            } else {
                binding.recommendationsHeader.visibility = View.GONE
                binding.recommendationsRecyclerView.visibility = View.GONE
            }
        }
    }

    /**
     * "Upcoming Events": everything from today onward, soonest first, minus events the student
     * already has a confirmed seat for (those are listed under "My Events" above). Flash sessions
     * stay until they expire, as before. Events with no date (timestamp 0) go last.
     */
    private fun showEvents() {
        val currentTime = System.currentTimeMillis()
        val startOfToday = startOfTodayMillis()
        val filtered = allEvents
            .asSequence()
            .filter { it.id !in joinedEventIds }
            .filter { event ->
                if (event.is_flash) {
                    (event.expiry_time ?: Long.MAX_VALUE) > currentTime
                } else {
                    event.date_timestamp == 0L || event.date_timestamp >= startOfToday
                }
            }
            .sortedBy { if (it.date_timestamp == 0L) Long.MAX_VALUE else it.date_timestamp }
            .toList()
        pager.submit(filtered)
        val vis = HomeFeedState.visibility(
            hasLoaded = binding.homeSkeletonInclude.homeSkeleton.visibility != View.VISIBLE,
            eventCount = filtered.size
        )
        binding.homeEmptyInclude.homeEmptyState.visibility = if (vis.empty) View.VISIBLE else View.GONE
        binding.eventsRecyclerView.visibility = if (vis.list) View.VISIBLE else View.GONE
    }

    private fun startOfTodayMillis(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val MY_EVENTS_LIMIT = 3
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
