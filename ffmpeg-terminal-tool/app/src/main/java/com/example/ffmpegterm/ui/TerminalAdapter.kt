package com.example.ffmpegterm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ffmpegterm.R

class TerminalAdapter : RecyclerView.Adapter<TerminalAdapter.TerminalViewHolder>() {

    private val logList = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TerminalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_terminal_log, parent, false)
        return TerminalViewHolder(view)
    }

    override fun onBindViewHolder(holder: TerminalViewHolder, position: Int) {
        holder.bind(logList[position])
    }

    override fun getItemCount(): Int {
        return logList.size
    }

    fun addLog(log: String) {
        logList.add(log)
        notifyItemInserted(logList.size - 1)
    }

    fun clearLogs() {
        logList.clear()
        notifyDataSetChanged()
    }

    class TerminalViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val logTextView: TextView = itemView.findViewById(R.id.log_text_view)

        fun bind(log: String) {
            logTextView.text = log
        }
    }
}