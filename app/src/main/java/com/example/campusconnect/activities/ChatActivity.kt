package com.example.campusconnect.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.MessageAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityChatBinding
import com.example.campusconnect.models.Message
import com.example.campusconnect.utils.AuthorizationManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private var eventId: Int = -1
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.chat_title)

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1) {
            finish()
            return
        }

        adapter = MessageAdapter(emptyList())
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = adapter

        binding.scrollUpFab.setOnClickListener {
            binding.chatRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ChatActivity)
            if (!AuthorizationManager.canOpenEventSpace(db, currentUserId, eventId)) {
                Toast.makeText(this@ChatActivity, R.string.event_space_locked, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            db.messageDao().getMessagesForEvent(eventId).collectLatest { messages ->
                adapter.updateMessages(messages)
                binding.chatEmptyText.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                if (messages.isNotEmpty()) binding.chatRecyclerView.scrollToPosition(messages.size - 1)
            }
        }

        binding.sendButton.setOnClickListener {
            val content = binding.messageEditText.text.toString()
            if (content.isNotEmpty()) {
                sendMessage(content)
                binding.messageEditText.text.clear()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun sendMessage(content: String) {
        lifecycleScope.launch {
            val message = Message(
                event_id = eventId,
                user_id = currentUserId,
                content = content,
                sent_at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            )
            AppDatabase.getDatabase(this@ChatActivity).messageDao().insertMessage(message)
        }
    }
}
