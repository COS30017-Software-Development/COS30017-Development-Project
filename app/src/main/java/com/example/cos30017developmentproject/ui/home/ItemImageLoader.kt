package com.example.cos30017developmentproject.ui.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.cos30017developmentproject.R

object ItemImageLoader {
	fun load(imageView: ImageView, source: Any?, fallback: Int, scaleType: ImageView.ScaleType) {
		imageView.scaleType = scaleType
		Glide.with(imageView)
			.load(source ?: fallback)
			.placeholder(fallback)
			.error(fallback)
			.into(imageView)
	}

	fun showPreview(context: Context, source: Any?, fallback: Int) {
		val dialog = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
		val container = FrameLayout(context).apply {
			setBackgroundColor(ContextCompat.getColor(context, R.color.avatar_viewer_scrim))
			isClickable = true
			setOnClickListener { dialog.dismiss() }
		}
		val imageView = ImageView(context).apply {
			contentDescription = context.getString(R.string.item_image_preview_description)
			scaleType = ImageView.ScaleType.FIT_CENTER
			isClickable = true
		}
		val screen = context.resources.displayMetrics
		container.addView(
			imageView,
			FrameLayout.LayoutParams(
				(screen.widthPixels * PREVIEW_WIDTH_RATIO).toInt(),
				(screen.heightPixels * PREVIEW_HEIGHT_RATIO).toInt(),
				Gravity.CENTER
			)
		)
		val close = ImageButton(context).apply {
			contentDescription = context.getString(R.string.close_image_preview)
			setImageResource(R.drawable.ic_crop_cancel)
			imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
			background = ColorDrawable(Color.TRANSPARENT)
			setPadding(0, 0, 0, 0)
			setOnClickListener { dialog.dismiss() }
		}
		container.addView(
			close,
			FrameLayout.LayoutParams(
				(56 * screen.density).toInt(),
				(56 * screen.density).toInt(),
				Gravity.TOP or Gravity.END
			)
		)
		Glide.with(context)
			.load(source ?: fallback)
			.placeholder(fallback)
			.error(fallback)
			.into(imageView)
		dialog.setContentView(container)
		dialog.setCanceledOnTouchOutside(true)
		dialog.window?.apply {
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		}
		dialog.show()
		dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
	}

	private const val PREVIEW_WIDTH_RATIO = 0.9f
	private const val PREVIEW_HEIGHT_RATIO = 0.9f
}
