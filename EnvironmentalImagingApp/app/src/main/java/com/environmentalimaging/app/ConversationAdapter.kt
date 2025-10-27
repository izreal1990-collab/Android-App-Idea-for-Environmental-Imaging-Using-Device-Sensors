package com.environmentalimaging.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Conversation Adapter for AI Chat Interface
 */
class ConversationAdapter(
    private val conversations: List<ConversationalAIActivity.ConversationEntry>,
    private val onSuggestionClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (conversations[position].type) {
            ConversationalAIActivity.ConversationEntry.Type.USER -> VIEW_TYPE_USER
            ConversationalAIActivity.ConversationEntry.Type.AI -> VIEW_TYPE_AI
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conversation_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_AI -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conversation_ai, parent, false)
                AIMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val conversation = conversations[position]
        
        when (holder) {
            is UserMessageViewHolder -> holder.bind(conversation)
            is AIMessageViewHolder -> holder.bind(conversation, onSuggestionClick)
        }
    }
    
    override fun getItemCount(): Int = conversations.size
    
    /**
     * ViewHolder for User Messages
     */
    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageCard: MaterialCardView = itemView.findViewById(R.id.messageCard)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        
        fun bind(conversation: ConversationalAIActivity.ConversationEntry) {
            messageText.text = conversation.message
            timeText.text = conversation.getFormattedTime()
        }
    }
    
    /**
     * ViewHolder for AI Messages
     */
    class AIMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageCard: MaterialCardView = itemView.findViewById(R.id.messageCard)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val suggestionsGroup: ChipGroup = itemView.findViewById(R.id.suggestionsGroup)
        
        fun bind(
            conversation: ConversationalAIActivity.ConversationEntry,
            onSuggestionClick: (String) -> Unit
        ) {
            messageText.text = conversation.message
            timeText.text = conversation.getFormattedTime()
            
            // Handle suggestions
            suggestionsGroup.removeAllViews()
            if (conversation.suggestions.isNotEmpty()) {
                suggestionsGroup.visibility = View.VISIBLE
                conversation.suggestions.forEach { suggestion ->
                    val chip = Chip(itemView.context).apply {
                        text = suggestion
                        isClickable = true
                        setOnClickListener {
                            onSuggestionClick(suggestion)
                        }
                    }
                    suggestionsGroup.addView(chip)
                }
            } else {
                suggestionsGroup.visibility = View.GONE
            }
        }
    }
}