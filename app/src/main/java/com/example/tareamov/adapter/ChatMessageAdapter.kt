package com.example.tareamov.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tareamov.R
import com.example.tareamov.data.entity.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageAdapter : ListAdapter<ChatMessage, ChatMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userMessageContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val botMessageContainer: LinearLayout = itemView.findViewById(R.id.botMessageContainer)
        private val userMessageTextView: TextView = itemView.findViewById(R.id.userMessageTextView)
        private val botMessageTextView: TextView = itemView.findViewById(R.id.botMessageTextView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.messageTimestampTextView)

        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                // Show user message
                userMessageContainer.visibility = View.VISIBLE
                botMessageContainer.visibility = View.GONE
                userMessageTextView.text = message.message
            } else {
                // Show bot message
                userMessageContainer.visibility = View.GONE
                botMessageContainer.visibility = View.VISIBLE
                botMessageTextView.text = message.message
            }
            
            // Set timestamp
            timestampTextView.text = timeFormat.format(Date(message.timestamp))
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
