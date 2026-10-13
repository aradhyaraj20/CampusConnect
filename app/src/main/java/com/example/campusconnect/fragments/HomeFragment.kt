package com.example.campusconnect.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.activities.AIAssistantActivity
import com.example.campusconnect.activities.BadgesActivity
import com.example.campusconnect.activities.CreateEventActivity
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.activities.MarketplaceActivity
import com.example.campusconnect.activities.MyProfileActivity
import com.example.campusconnect.activities.NotificationsActivity
import com.example.campusconnect.activities.QrScanActivity
import com.example.campusconnect.activities.SearchActivity
import com.example.campusconnect.activities.SettingsActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.adapters.FlashEventAdapter
import com.example.campusconnect.adapters.RecommendationAdapter
import com.example.campusconnect.core.network.AppConnectivity
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.FragmentHomeBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Event
import com.example.campusconnect.presentation.home.HomeFeedState
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EventAdapter
    private lateinit var flashAdapter: FlashEventAdapter
    private lateinit var recommendationAdapter: RecommendationAdapter
    private var allEvents: List<Event> = emptyList()
    private var userInterests: String? = null

    /** A single Quick Access tile, mapped to a feature that already exists. */
    private data class QuickAction(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val iconColorRes: Int,
        val bgColorRes: Int,
        val isNew: Boolean = false,
        val onClick: () -> Unit
    )

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

        // The header sits under the transparent status bar; pad it by the real inset
        // (24-50dp depending on device/cutout) instead of a fixed 40dp.
        val headerBaseTop = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeHeader) { header, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            header.updatePadding(top = headerBaseTop + top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.homeHeader)

        val session = SessionManager.getInstance(requireContext())
        val currentUserId = session.getCurrentUserId()
        val role = session.getCurrentUserRole()

        loadAdBanner()
        setupEventLists()
        setupHeader(currentUserId)
        buildQuickAccess(role)

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
            AppDatabase.getDatabase(requireContext()).eventDao().getAllEvents().collectLatest { events ->
                allEvents = events
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
        val openDetail: (Event) -> Unit = { event ->
            startActivity(Intent(requireContext(), EventDetailActivity::class.java).apply {
                putExtra("EVENT_ID", event.id)
            })
        }
        adapter = EventAdapter(emptyList(), openDetail)
        flashAdapter = FlashEventAdapter(emptyList(), openDetail)
        recommendationAdapter = RecommendationAdapter(emptyList(), openDetail)

        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = adapter

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
        binding.viewAllButton.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.viewCalendarButton.setOnClickListener { safeNavigate(R.id.calendarFragment) }
        binding.notificationBell.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
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

    // PLACEHOLDER_QUICKACCESS

    /**
     * Builds the "Quick Access" grid. Per product decision every tile maps to a
     * feature that ALREADY exists in the app - there are no placeholder sections
     * (no real GradeSheet/Fees/Library backends). Organizer/Admin accounts get
     * two extra tiles for the tools available to them.
     */
    private fun buildQuickAccess(role: String) {
        val actions = mutableListOf(
            QuickAction(
                R.string.qa_profile, R.string.qa_profile_sub,
                R.drawable.ic_nav_profile, R.color.qa_purple, R.color.qa_purple_bg
            ) {
                // Students have their own Profile tab; MyProfileActivity shows organizer/admin stats.
                if (AccessPolicy.canCreateEvents(role)) {
                    startActivity(Intent(requireContext(), MyProfileActivity::class.java))
                } else {
                    safeNavigate(R.id.profileFragment)
                }
            },
            QuickAction(
                R.string.qa_events, R.string.qa_events_sub,
                R.drawable.ic_nav_events_list, R.color.qa_orange, R.color.qa_orange_bg
            ) { startActivity(Intent(requireContext(), SearchActivity::class.java)) },
            QuickAction(
                R.string.qa_my_events, R.string.qa_my_events_sub,
                R.drawable.ic_nav_calendar, R.color.qa_blue, R.color.qa_blue_bg
            ) { safeNavigate(R.id.calendarFragment) },
            QuickAction(
                R.string.qa_groups, R.string.qa_groups_sub,
                R.drawable.ic_nav_groups, R.color.qa_green, R.color.qa_green_bg
            ) { safeNavigate(R.id.studyGroupsFragment) },
            QuickAction(
                R.string.qa_assistant, R.string.qa_assistant_sub,
                R.drawable.ic_ai_sparkle, R.color.qa_pink, R.color.qa_pink_bg
            ) {
                startActivity(Intent(requireContext(), AIAssistantActivity::class.java).apply {
                    putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_STUDENT)
                })
            },
            QuickAction(
                R.string.qa_notices, R.string.qa_notices_sub,
                R.drawable.ic_nav_campaign, R.color.qa_purple, R.color.qa_purple_bg, isNew = true
            ) { startActivity(Intent(requireContext(), NotificationsActivity::class.java)) },
            QuickAction(
                R.string.qa_marketplace, R.string.qa_marketplace_sub,
                R.drawable.ic_qa_marketplace, R.color.qa_teal, R.color.qa_teal_bg
            ) { startActivity(Intent(requireContext(), MarketplaceActivity::class.java)) },
            QuickAction(
                R.string.qa_badges, R.string.qa_badges_sub,
                R.drawable.ic_qa_badge, R.color.qa_yellow, R.color.qa_yellow_bg
            ) { startActivity(Intent(requireContext(), BadgesActivity::class.java)) },
            QuickAction(
                R.string.qa_settings, R.string.qa_settings_sub,
                R.drawable.ic_qa_settings, R.color.qa_blue, R.color.qa_blue_bg
            ) { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        )

        if (AccessPolicy.canCreateEvents(role)) {
            actions.add(
                QuickAction(
                    R.string.qa_create_event, R.string.qa_create_event_sub,
                    R.drawable.ic_nav_add_event, R.color.qa_orange, R.color.qa_orange_bg
                ) { startActivity(Intent(requireContext(), CreateEventActivity::class.java)) }
            )
            actions.add(
                QuickAction(
                    R.string.qa_scan, R.string.qa_scan_sub,
                    R.drawable.ic_qa_qr, R.color.qa_green, R.color.qa_green_bg
                ) { startActivity(Intent(requireContext(), QrScanActivity::class.java)) }
            )
        }

        renderQuickAccessGrid(actions)
    }

    private fun renderQuickAccessGrid(actions: List<QuickAction>) {
        val container = binding.quickAccessContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val columns = 3
        val gap = dp(6)

        var row: LinearLayout? = null
        actions.forEachIndexed { index, action ->
            if (index % columns == 0) {
                row = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }
                container.addView(row)
            }

            val tile = inflater.inflate(R.layout.item_quick_access, row, false) as MaterialCardView
            bindTile(tile, action)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(gap, gap, gap, gap)
            }
            tile.layoutParams = lp
            row?.addView(tile)
        }

        // Pad the final row so a lone/paired tile keeps the same column width.
        val remainder = actions.size % columns
        if (remainder != 0) {
            repeat(columns - remainder) {
                val filler = View(requireContext())
                filler.layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply {
                    setMargins(gap, gap, gap, gap)
                }
                row?.addView(filler)
            }
        }
    }

    private fun bindTile(tile: MaterialCardView, action: QuickAction) {
        val iconColor = ContextCompat.getColor(requireContext(), action.iconColorRes)
        tile.setCardBackgroundColor(ContextCompat.getColor(requireContext(), action.bgColorRes))

        val chip = tile.findViewById<View>(R.id.qaIconChip)
        chip.backgroundTintList = ColorStateList.valueOf(iconColor)

        val icon = tile.findViewById<ImageView>(R.id.qaIcon)
        icon.setImageResource(action.iconRes)

        val title = tile.findViewById<TextView>(R.id.qaTitle)
        title.setText(action.titleRes)

        val subtitle = tile.findViewById<TextView>(R.id.qaSubtitle)
        subtitle.setText(action.subtitleRes)

        tile.findViewById<TextView>(R.id.qaNewBadge).visibility =
            if (action.isNew) View.VISIBLE else View.GONE

        tile.contentDescription = getString(action.titleRes)
        tile.setOnClickListener { action.onClick() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
        val interests = userInterests?.split(",")?.map { it.trim().lowercase() }?.toMutableList() ?: mutableListOf()

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val currentUserId = SessionManager.getInstance(requireContext()).getCurrentUserId()

            db.userDao().getRecentSearch(currentUserId).first().forEach {
                interests.add(it.query.lowercase())
            }

            if (_binding == null) return@launch

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

    private fun showEvents() {
        val currentTime = System.currentTimeMillis()
        val filtered = allEvents.filter { event ->
            !event.is_flash || (event.expiry_time ?: Long.MAX_VALUE) > currentTime
        }
        adapter.updateEvents(filtered)
        val vis = HomeFeedState.visibility(
            hasLoaded = binding.homeSkeletonInclude.homeSkeleton.visibility != View.VISIBLE,
            eventCount = filtered.size
        )
        binding.homeEmptyInclude.homeEmptyState.visibility = if (vis.empty) View.VISIBLE else View.GONE
        binding.eventsRecyclerView.visibility = if (vis.list) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
