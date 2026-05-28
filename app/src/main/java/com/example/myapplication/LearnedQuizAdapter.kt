package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LearnedQuizAdapter(
    private var items: List<SavedQuizItem>,
    private val onItemClick: (SavedQuizItem) -> Unit,
    private val onRenameClick: (SavedQuizItem) -> Unit,
    private val onDeleteClick: (SavedQuizItem) -> Unit
) : RecyclerView.Adapter<LearnedQuizAdapter.ViewHolder>() {

    fun submitList(newItems: List<SavedQuizItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconWrap: LinearLayout = view.findViewById(R.id.iconWrap)
        val tvQuizTitle: TextView = view.findViewById(R.id.tvQuizTitle)
        val tvQuizMeta: TextView = view.findViewById(R.id.tvQuizMeta)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val btnRename: ImageButton = view.findViewById(R.id.btnRename)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_learned_quiz_modern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvQuizTitle.text = item.fileName.removeSuffix(".txt")

        val dateText = SimpleDateFormat(
            "yyyy.MM.dd HH:mm",
            Locale.getDefault()
        ).format(Date(item.savedAt))

        holder.tvQuizMeta.text = dateText
        holder.tvStatusBadge.text = "오답 복습"

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnRename.setOnClickListener { onRenameClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}