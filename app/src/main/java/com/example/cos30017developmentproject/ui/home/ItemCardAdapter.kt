package com.example.cos30017developmentproject.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R

/** One card layout and one binding path for home, grid and list presentations. */
class ItemCardAdapter(
	items: List<ItemCard> = emptyList(),
	private val onItemClick: ((ItemCard) -> Unit)? = null
) :
	ListAdapter<ItemCard, ItemCardAdapter.ItemViewHolder>(ItemDiff) {
	private var listMode = false

	init {
		submitList(items)
	}

	fun setListMode(enabled: Boolean) {
		if (listMode == enabled) return
		listMode = enabled
		notifyItemRangeChanged(0, itemCount)
	}

	override fun getItemViewType(position: Int) = if (listMode) 1 else 0

	class ItemViewHolder(
		view: View,
		listMode: Boolean,
		private val onItemClick: ((ItemCard) -> Unit)?
	) : RecyclerView.ViewHolder(view) {
		private val image: ImageView = view.findViewById(R.id.item_image)
		private val title: TextView = view.findViewById(R.id.item_title)
		private val description: TextView = view.findViewById(R.id.item_description)
		private val type: TextView = view.findViewById(R.id.item_type)

		init {
			if (listMode) {
				val density = view.resources.displayMetrics.density
				view.findViewById<LinearLayout>(R.id.item_card_content).apply {
					orientation = LinearLayout.HORIZONTAL
					minimumHeight = (132 * density).toInt()
				}
				view.findViewById<View>(R.id.item_image_container).layoutParams = LinearLayout.LayoutParams(
					(112 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT
				)
				view.findViewById<View>(R.id.item_text_container).layoutParams = LinearLayout.LayoutParams(
					0,
					ViewGroup.LayoutParams.WRAP_CONTENT,
					1f
				)
			}
		}

		fun bind(item: ItemCard) {
			val context = itemView.context
			image.setImageResource(item.image)
			image.contentDescription = context.getString(R.string.item_image_description, item.title)
			title.text = item.title
			description.text = item.description
			type.setText(if (item.isFound) R.string.found else R.string.lost)
			type.setTextColor(
				ContextCompat.getColor(
					context, if (item.isFound) R.color.black else R.color.brand_red_accessible
				)
			)
			itemView.setOnClickListener(onItemClick?.let { listener -> { listener(item) } })
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ItemViewHolder(
		LayoutInflater.from(parent.context).inflate(R.layout.component_item_card, parent, false),
		viewType == 1,
		onItemClick
	)

	override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bind(
		getItem(
			position
		)
	)

	private object ItemDiff : DiffUtil.ItemCallback<ItemCard>() {
		override fun areItemsTheSame(old: ItemCard, new: ItemCard) =
			old.id == new.id && old.isFound == new.isFound
		override fun areContentsTheSame(old: ItemCard, new: ItemCard) = old == new
	}
}
