package com.example.cos30017developmentproject.ui.home

import android.content.Context
import androidx.annotation.DrawableRes
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.local.FoundItemEntity
import com.example.cos30017developmentproject.data.local.LostItemEntity

data class ItemCard(
	val id: Long,
	val title: String,
	val description: String,
	@param:DrawableRes @get:DrawableRes val image: Int,
	val isFound: Boolean
)

fun LostItemEntity.toItemCard(context: Context) = ItemCard(
	id = id,
	title = title,
	description = description,
	image = context.drawableId(imageName),
	isFound = false
)

fun FoundItemEntity.toItemCard(context: Context) = ItemCard(
	id = id,
	title = title,
	description = description,
	image = context.drawableId(imageName),
	isFound = true
)

private fun Context.drawableId(imageName: String): Int =
	resources.getIdentifier(imageName, "drawable", packageName).takeIf { it != 0 }
		?: R.drawable.sample_backpack
