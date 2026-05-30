package com.example.ffmpegterm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ffmpegterm.R

class LogAdapter : ListAdapter<String, LogAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        view.setTextColor(0xFFE0E0E0.toInt())
        view.textSize = 13f
        view.setTypeface(android.graphics.Typeface.MONOSPACE)
        view.setPadding(16, 4, 16, 4)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getItem(position)
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    object DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(old: String, new: String) = old == new
        override fun areContentsTheSame(old: String, new: String) = old == new
    }
}
