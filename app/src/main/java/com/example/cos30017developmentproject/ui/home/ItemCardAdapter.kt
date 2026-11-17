package com.example.cos30017developmentproject.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R

class ItemCardAdapter(private val items: List<SampleItem>) : RecyclerView.Adapter<ItemCardAdapter.ItemViewHolder>() {
    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.item_image)
        private val title: TextView = view.findViewById(R.id.item_title)
        private val description: TextView = view.findViewById(R.id.item_description)
        private val type: TextView = view.findViewById(R.id.item_type)

        fun bind(item: SampleItem) {
            val context = itemView.context
            image.setImageResource(item.image)
            image.contentDescription = context.getString(R.string.item_image_description, context.getString(item.title))
            title.setText(item.title)
            description.setText(item.description)
            type.setText(if (item.isFound) R.string.found else R.string.lost)
            type.setTextColor(ContextCompat.getColor(context, if (item.isFound) R.color.black else R.color.brand_red_accessible))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ItemViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.component_item_card, parent, false)
    )

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size
}
