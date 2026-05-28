package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AssignmentListAdapter(
    private val items: List<AssignmentEntity>,
    private val onItemClick: (AssignmentEntity) -> Unit
) : RecyclerView.Adapter<AssignmentListAdapter.AssignmentViewHolder>() {

    class AssignmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutAssignmentIconBg: LinearLayout =
            itemView.findViewById(R.id.layoutAssignmentIconBg)
        val ivAssignmentIcon: ImageView = itemView.findViewById(R.id.ivAssignmentIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvAssignmentItemTitle)
        val tvInfo: TextView = itemView.findViewById(R.id.tvAssignmentItemInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssignmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assignment, parent, false)
        return AssignmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssignmentViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvInfo.text = "${item.type} · ${item.dueDate}"

        if (item.type == "팀 프로젝트") {
            holder.layoutAssignmentIconBg.setBackgroundResource(R.drawable.bg_assignment_item_icon_team)
            holder.ivAssignmentIcon.setImageResource(R.drawable.ic_group)
        } else {
            holder.layoutAssignmentIconBg.setBackgroundResource(R.drawable.bg_assignment_item_icon_personal)
            holder.ivAssignmentIcon.setImageResource(R.drawable.ic_user)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}