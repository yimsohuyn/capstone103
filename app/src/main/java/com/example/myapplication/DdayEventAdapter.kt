package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DdayEventAdapter(
    private val items: List<DdayEvent>,
    private val onItemClick: (DdayEvent) -> Unit
) : RecyclerView.Adapter<DdayEventAdapter.DdayViewHolder>() {

    class DdayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDday: TextView = itemView.findViewById(R.id.tvDday)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DdayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dday_item, parent, false)
        return DdayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DdayViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvDday.text = item.ddayLabel

        val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        holder.tvDate.text = fmt.format(Date(item.startMillis))

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}

