package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.databinding.ItemReportRowBinding
import com.example.campusconnect.models.Report

class ReportAdapter(
    private var reports: List<Report>,
    private val onDismiss: (Report) -> Unit,
    private val onRemove: (Report) -> Unit,
    private val onResolve: (Report) -> Unit
) : RecyclerView.Adapter<ReportAdapter.Holder>() {
    class Holder(val binding: ItemReportRowBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val report = reports[position]
        holder.binding.reportTitle.text = "${report.targetType} #${report.targetId} · ${report.status}"
        holder.binding.reportMeta.text = report.reason
        val pending = report.status.equals("PENDING", true) || report.status.equals("UNDER_REVIEW", true)
        holder.binding.dismissButton.isEnabled = pending
        holder.binding.removeButton.isEnabled = pending
        holder.binding.resolveButton.isEnabled = pending
        holder.binding.dismissButton.setOnClickListener { onDismiss(report) }
        holder.binding.removeButton.setOnClickListener { onRemove(report) }
        holder.binding.resolveButton.setOnClickListener { onResolve(report) }
    }
    override fun getItemCount() = reports.size
    fun submit(newReports: List<Report>) { reports = newReports; notifyDataSetChanged() }
}
