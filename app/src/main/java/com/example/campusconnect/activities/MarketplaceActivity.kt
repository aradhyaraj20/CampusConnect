package com.example.campusconnect.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.MarketplaceAdapter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMarketplaceBinding
import com.example.campusconnect.databinding.DialogAddMarketplaceItemBinding
import com.example.campusconnect.models.MarketplaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarketplaceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMarketplaceBinding
    private lateinit var adapter: MarketplaceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarketplaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.marketplace)

        adapter = MarketplaceAdapter(emptyList()) { item ->
            showItemDetail(item)
        }

        binding.marketplaceRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.marketplaceRecyclerView.adapter = adapter

        // This FAB previously had no click handler at all - the only way to add an
        // item was to insert it directly into the database.
        binding.addItemFab.setOnClickListener { showAddItemDialog() }

        binding.scrollUpFab.setOnClickListener {
            binding.marketplaceRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.marketplaceRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MarketplaceActivity).marketplaceDao().getAllItems()
                .collectLatest { items ->
                    adapter.updateItems(items)
                    val empty = items.isEmpty()
                    binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.marketplaceRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
                    binding.scrollButtons.visibility = if (items.size > 4) View.VISIBLE else View.GONE
                }
        }
    }

    private fun showItemDetail(item: MarketplaceItem) {
        val price = if (item.is_swap) {
            getString(R.string.mk_swap_for, item.exchange_with ?: getString(R.string.mk_open_to_offers))
        } else {
            getString(R.string.mk_price_format, item.price)
        }
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage("${item.description}\n\n${item.category}\n$price\n\n${item.posted_at}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAddItemDialog() {
        val userId = SessionManager.getInstance(this).getCurrentUserId()
        if (userId <= 0) {
            Toast.makeText(this, R.string.mk_login_required, Toast.LENGTH_SHORT).show()
            return
        }

        val d = DialogAddMarketplaceItemBinding.inflate(layoutInflater)

        // Swap listings don't have a price - show the right field for the mode.
        d.swapSwitch.setOnCheckedChangeListener { _, isSwap ->
            d.priceLayout.visibility = if (isSwap) View.GONE else View.VISIBLE
            d.exchangeLayout.visibility = if (isSwap) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mk_add)
            .setView(d.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.mk_post, null)
            .create()

        dialog.show()
        // Attach after show() so validation errors don't dismiss the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = d.titleInput.text?.toString()?.trim().orEmpty()
            val desc = d.descriptionInput.text?.toString()?.trim().orEmpty()
            val category = d.categoryInput.text?.toString()?.trim().orEmpty()
            val isSwap = d.swapSwitch.isChecked
            val priceText = d.priceInput.text?.toString()?.trim().orEmpty()

            d.titleLayout.error = null
            d.priceLayout.error = null

            if (title.isBlank()) {
                d.titleLayout.error = getString(R.string.mk_err_title); return@setOnClickListener
            }
            val price = if (isSwap) 0.0 else priceText.toDoubleOrNull() ?: -1.0
            if (!isSwap && price < 0) {
                d.priceLayout.error = getString(R.string.mk_err_price); return@setOnClickListener
            }

            val item = MarketplaceItem(
                title = title,
                description = desc.ifBlank { getString(R.string.mk_no_description) },
                category = category.ifBlank { getString(R.string.mk_general) },
                price = price,
                image_uri = null,
                poster_id = userId,
                posted_at = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                is_swap = isSwap,
                exchange_with = if (isSwap) d.exchangeInput.text?.toString()?.trim().orEmpty()
                    .ifBlank { null } else null
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MarketplaceActivity).marketplaceDao().insertItem(item)
                }
                dialog.dismiss()
                Toast.makeText(this@MarketplaceActivity, R.string.mk_posted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
