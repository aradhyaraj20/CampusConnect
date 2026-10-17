package com.example.campusconnect.activities.admin

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.adapters.AdAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAdminAdsBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Ad
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * "Let admin display ads of his wish if needed to students" - admin authors a
 * title/message (optionally with an image and link), toggles it active/inactive
 * at will, and it appears as a banner on the student Home feed
 * (see HomeFragment + AdDao.getActiveAd).
 */
class AdminAdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAdsBinding
    private var pendingImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* some providers don't support persistable grants */ }
            pendingImageUri = it
            dialogImagePreview?.let { iv ->
                iv.visibility = View.VISIBLE
                iv.load(it)
            }
        }
    }

    private var dialogImagePreview: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivityAdminAdsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.manage_ads)

        if (!AccessPolicy.canAccessAdmin(SessionManager.getInstance(this).getCurrentUserRole())) {
            Toast.makeText(this, R.string.admin_access_required, Toast.LENGTH_SHORT).show(); finish(); return
        }

        val db = AppDatabase.getDatabase(this)
        val adapter = AdAdapter(
            emptyList(),
            onToggleActive = { ad, checked -> lifecycleScope.launch { db.adDao().setActive(ad.id, checked) } },
            onDelete = { ad ->
                lifecycleScope.launch {
                    db.adDao().deleteAd(ad)
                    Toast.makeText(this@AdminAdsActivity, R.string.ad_removed, Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.list_columns))
        binding.recyclerView.adapter = adapter
        val pager = com.example.campusconnect.core.common.Pager(binding.recyclerView) { adapter.submit(it) }

        lifecycleScope.launch {
            db.adDao().getAllAds().collectLatest { ads ->
                pager.submit(ads)
                binding.emptyState.visibility = if (ads.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.createAdFab.setOnClickListener { showCreateAdDialog() }
    }

    private fun showCreateAdDialog() {
        pendingImageUri = null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_ad, null)
        val titleInput = view.findViewById<EditText>(R.id.adTitleInput)
        val messageInput = view.findViewById<EditText>(R.id.adMessageInput)
        val linkInput = view.findViewById<EditText>(R.id.adLinkInput)
        val imagePreview = view.findViewById<ImageView>(R.id.adImagePreview)
        val pickImageButton = view.findViewById<View>(R.id.pickAdImageButton)
        dialogImagePreview = imagePreview

        pickImageButton.setOnClickListener { pickImage.launch("image/*") }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.create_ad))
            .setView(view)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val title = titleInput.text.toString().trim()
                val message = messageInput.text.toString().trim()
                val link = linkInput.text.toString().trim().ifEmpty { null }
                val linkOk = link == null || android.net.Uri.parse(link).let {
                    it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank()
                }
                if (title.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, R.string.ad_err_required, Toast.LENGTH_SHORT).show()
                } else if (!linkOk) {
                    Toast.makeText(this, R.string.ad_err_https, Toast.LENGTH_LONG).show()
                } else {
                    val adminId = SessionManager.getInstance(this).getCurrentUserId()
                    lifecycleScope.launch {
                        AppDatabase.getDatabase(this@AdminAdsActivity).adDao().insertAd(
                            Ad(
                                title = title,
                                message = message,
                                image_uri = pendingImageUri?.toString(),
                                link_url = link,
                                is_active = true,
                                created_by = adminId
                            )
                        )
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
