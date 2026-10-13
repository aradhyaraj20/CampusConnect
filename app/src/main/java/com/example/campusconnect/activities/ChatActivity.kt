package com.example.campusconnect.activities

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.adapters.MessageAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityChatBinding
import com.example.campusconnect.models.Message
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private var eventId: Int = -1
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Chat"

        val prefs = getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt("user_id", -1)

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
            AppDatabase.getDatabase(this@ChatActivity).messageDao().getMessagesForEvent(eventId).collectLatest { messages ->
                adapter.updateMessages(messages)
                binding.chatRecyclerView.scrollToPosition(messages.size - 1)
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
