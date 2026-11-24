package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ItemDetailsActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private val editItem = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			loadItem(intent.getLongExtra(EXTRA_ITEM_ID, INVALID_ITEM_ID), intent.getStringExtra(EXTRA_ITEM_TYPE).orEmpty())
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_item_details)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.item_details_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			insets
		}

		findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.item_details_toolbar)
			.setNavigationOnClickListener { finish() }
		findViewById<View>(R.id.back_to_items).setOnClickListener { finish() }
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))

		val id = intent.getLongExtra(EXTRA_ITEM_ID, INVALID_ITEM_ID)
		val type = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: ""
		if (id == INVALID_ITEM_ID || type !in setOf(TYPE_LOST, TYPE_FOUND)) {
			showItemNotFound()
			return
		}
		loadItem(id, type)
	}

	private fun loadItem(id: Long, type: String) {
		lifecycleScope.launch {
			try {
				val item = when (type) {
					TYPE_LOST -> repository.getLostItem(id)?.let {
						ItemDetails(it.id, it.title, it.description, it.location, it.category, it.imageName, it.postedByUserId)
					}
					TYPE_FOUND -> repository.getFoundItem(id)?.let {
						ItemDetails(it.id, it.title, it.description, it.location, it.category, it.imageName, it.postedByUserId)
					}
					else -> null
				} ?: run {
					showItemNotFound()
					return@launch
				}
				bindItem(item, type, repository.getUser(item.postedByUserId)?.username)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to load item", e)
				Toast.makeText(this@ItemDetailsActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
				finish()
			}
		}
	}

	private fun bindItem(item: ItemDetails, type: String, username: String?) {
		val imageId = resources.getIdentifier(item.imageName, "drawable", packageName)
		findViewById<ImageView>(R.id.item_details_image).setImageResource(
			if (imageId != 0) imageId else R.drawable.sample_backpack
		)
		findViewById<ImageView>(R.id.item_details_image).contentDescription = getString(R.string.item_image_description, item.title)
		findViewById<TextView>(R.id.item_details_name).text = item.title
		findViewById<TextView>(R.id.item_details_type).text = getString(
			R.string.item_type_format,
			getString(if (type == TYPE_FOUND) R.string.found else R.string.lost)
		)
		findViewById<TextView>(R.id.item_details_status).setText(R.string.item_active)
		findViewById<TextView>(R.id.item_details_category).text = item.category
		findViewById<TextView>(R.id.item_details_location).text = item.location
		findViewById<TextView>(R.id.item_details_description).text = item.description
		findViewById<View>(R.id.item_details_poster_section).isVisible = username != null
		findViewById<TextView>(R.id.item_details_poster).text = username.orEmpty()
		findViewById<View>(R.id.item_owner_actions).isVisible =
			SessionManager(this).userId == item.postedByUserId
		findViewById<View>(R.id.edit_item).setOnClickListener { openEdit(item.id, type) }
		findViewById<View>(R.id.delete_item).setOnClickListener { confirmDelete(item.id, type) }
	}

	private fun openEdit(id: Long, type: String) {
		lifecycleScope.launch {
			if (!isCurrentUserOwner(id, type)) {
				showPermissionDenied()
				return@launch
			}
			editItem.launch(Intent(this@ItemDetailsActivity, EditItemActivity::class.java).apply {
				putExtra(EXTRA_ITEM_ID, id)
				putExtra(EXTRA_ITEM_TYPE, type)
			})
		}
	}

	private fun confirmDelete(id: Long, type: String) {
		lifecycleScope.launch {
			try {
				if (!isCurrentUserOwner(id, type)) {
					showPermissionDenied()
					return@launch
				}
				MaterialAlertDialogBuilder(this@ItemDetailsActivity)
					.setTitle(R.string.delete_item_title)
					.setMessage(R.string.delete_item_message)
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.delete) { _, _ -> deleteItem(id, type) }
					.show()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to verify item ownership", e)
				Toast.makeText(this@ItemDetailsActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun deleteItem(id: Long, type: String) {
		lifecycleScope.launch {
			try {
				val userId = SessionManager(this@ItemDetailsActivity).userId
					?: run {
						showPermissionDenied()
						return@launch
					}
				val deleted = when (type) {
					TYPE_LOST -> repository.deleteLostItemOwned(id, userId)
					TYPE_FOUND -> repository.deleteFoundItemOwned(id, userId)
					else -> false
				}
				if (deleted) {
					Toast.makeText(this@ItemDetailsActivity, R.string.item_deleted, Toast.LENGTH_SHORT).show()
					finish()
				} else showPermissionDenied()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to delete item", e)
				Toast.makeText(this@ItemDetailsActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private suspend fun isCurrentUserOwner(id: Long, type: String): Boolean {
		val userId = SessionManager(this).userId ?: return false
		return when (type) {
			TYPE_LOST -> repository.getLostItem(id)?.postedByUserId == userId
			TYPE_FOUND -> repository.getFoundItem(id)?.postedByUserId == userId
			else -> false
		}
	}

	private fun showPermissionDenied() {
		Toast.makeText(this, R.string.item_action_not_allowed, Toast.LENGTH_SHORT).show()
	}

	private fun showItemNotFound() {
		Toast.makeText(this, R.string.item_not_found, Toast.LENGTH_SHORT).show()
		finish()
	}

	private data class ItemDetails(
		val id: Long,
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val imageName: String,
		val postedByUserId: Long
	)

	companion object {
		const val EXTRA_ITEM_ID = "item_id"
		const val EXTRA_ITEM_TYPE = "item_type"
		const val TYPE_LOST = "Lost"
		const val TYPE_FOUND = "Found"
		private const val INVALID_ITEM_ID = -1L
		private const val TAG = "ItemDetailsActivity"
	}
}
