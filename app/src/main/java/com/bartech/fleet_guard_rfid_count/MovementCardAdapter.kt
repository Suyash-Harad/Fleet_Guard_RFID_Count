package com.bartech.fleet_guard_rfid_count

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class MovementCardAdapter(private val context: Context, private val items: List<Pair<String, String>>) : BaseAdapter() {

    private val cardBackgroundColors = listOf(
        R.color.info_light,
        R.color.success_light,
        R.color.warning_light,
        R.color.error_light
    )

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_card, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val item = items[position]
        viewHolder.title.text = item.first
        viewHolder.description.text = item.second

        // Set dynamic colors based on the position
        val colorIndex = position % cardBackgroundColors.size
        val cardBackground = ContextCompat.getColor(context, cardBackgroundColors[colorIndex])
        viewHolder.card.setCardBackgroundColor(cardBackground)

        return view
    }

    private class ViewHolder(view: View) {
        val card: MaterialCardView = view as MaterialCardView
        val title: TextView = view.findViewById(R.id.tv_card_title)
        val description: TextView = view.findViewById(R.id.tv_card_description)
    }
}
