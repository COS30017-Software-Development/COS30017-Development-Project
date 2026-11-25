package com.example.cos30017developmentproject.ui.home

import android.content.Context
import androidx.annotation.DrawableRes
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.local.FoundItemEntity
import com.example.cos30017developmentproject.data.local.LostItemEntity
import com.example.cos30017developmentproject.data.remote.FirestoreItem
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository

data class ItemCard(
	val id: String,
	val title: String,
	val description: String,
	@param:DrawableRes @get:DrawableRes val image: Int,
	val isFound: Boolean
)

fun LostItemEntity.toItemCard(context: Context) = ItemCard(
	id = id.toString(),
	title = title,
	description = description,
	image = context.drawableId(imageName),
	isFound = false
)

fun FoundItemEntity.toItemCard(context: Context) = ItemCard(
	id = id.toString(),
	title = title,
	description = description,
	image = context.drawableId(imageName),
	isFound = true
)

fun FirestoreItem.toItemCard(context: Context) = ItemCard(
	id = itemId,
	title = itemName,
	description = description,
	image = context.drawableId(imageName),
	isFound = type == FirestoreItemRepository.TYPE_FOUND
)

private fun Context.drawableId(imageName: String): Int =
	resources.getIdentifier(imageName, "drawable", packageName).takeIf { it != 0 }
		?: R.drawable.sample_backpack
