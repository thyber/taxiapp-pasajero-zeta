package com.taxiapp.passenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestionAdapter(
    private var suggestions: List<Suggestion>,
    private val onSuggestionClick: (Suggestion) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    fun updateData(newSuggestions: List<Suggestion>) {
        this.suggestions = newSuggestions
        notifyDataSetChanged()
    }

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtIcon: TextView = itemView.findViewById(R.id.txtSuggestionIcon)
        val txtName: TextView = itemView.findViewById(R.id.txtSuggestionName)
        val txtAddress: TextView = itemView.findViewById(R.id.txtSuggestionAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        
        val icon = when (suggestion.id) {
            "airport" -> "✈️"
            "plaza" -> "🏛️"
            "mall" -> "🛍️"
            "cine" -> "🎬"
            else -> "📍"
        }
        
        holder.txtIcon.text = icon
        holder.txtName.text = suggestion.name
        holder.txtAddress.text = suggestion.address
        
        holder.itemView.setOnClickListener {
            onSuggestionClick(suggestion)
        }
    }

    override fun getItemCount(): Int = suggestions.size
}
