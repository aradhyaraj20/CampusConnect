package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.databinding.ItemUserRowBinding
import com.example.campusconnect.models.User

class AdminUserAdapter(
    private var users: List<User>,
    private val onWarn: (User) -> Unit,
    private val onSuspend: (User) -> Unit,
    private val onChangeRole: (User) -> Unit = {}
) : RecyclerView.Adapter<AdminUserAdapter.Holder>() {
    class Holder(val binding: ItemUserRowBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemUserRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val user = users[position]
        holder.binding.userName.text = user.name
        val courseSuffix = user.course?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        holder.binding.userMeta.text = "${user.role} · ${user.email} · ${user.account_status}$courseSuffix"
        holder.binding.warnButton.setOnClickListener { onWarn(user) }
        holder.binding.root.setOnLongClickListener { if (user.role != "ADMIN") onChangeRole(user); true }
        holder.binding.suspendButton.setOnClickListener { onSuspend(user) }
        holder.binding.suspendButton.isEnabled = user.account_status != "SUSPENDED" && user.role != "ADMIN"
        holder.binding.warnButton.isEnabled = user.role != "ADMIN"
    }
    override fun getItemCount() = users.size
    fun submit(newUsers: List<User>) { users = newUsers; notifyDataSetChanged() }
}
