package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.ItemStatus
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ui.home.ItemImageLoader
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AdminItemDetailsActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var firestoreRepository: FirestoreItemRepository
	private lateinit var sessionManager: SessionManager
	private var itemId = ""
	private var itemType = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		firestoreRepository = FirestoreItemRepository()
		itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
		itemType = intent.getStringExtra(EXTRA_ITEM_TYPE).orEmpty()
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_item_details)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_item_details_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		findViewById<MaterialToolbar>(R.id.admin_item_details_toolbar).setNavigationOnClickListener { finish() }
		findViewById<View>(R.id.admin_close_item).setOnClickListener { confirmClose() }
		findViewById<View>(R.id.admin_delete_item).setOnClickListener { confirmDelete() }
		verifyAdminAndLoad()
	}

	private fun verifyAdminAndLoad() {
		val adminId = sessionManager.userId
		if (itemId.isBlank() || itemType !in setOf(ItemDetailsActivity.TYPE_LOST, ItemDetailsActivity.TYPE_FOUND) ||
			adminId == null
		) {
			denyAccess()
			return
		}
		lifecycleScope.launch {
			if (!repository.isAdmin(adminId)) {
				denyAccess()
				return@launch
			}
			loadItem()
		}
	}

	private fun loadItem() {
		lifecycleScope.launch {
			try {
				val cloudItem = firestoreRepository.getItem(itemId)
					?.takeIf {
						it.type == if (itemType == ItemDetailsActivity.TYPE_FOUND) {
							FirestoreItemRepository.TYPE_FOUND
						} else {
							FirestoreItemRepository.TYPE_LOST
						}
					}
				if (cloudItem == null) {
					Toast.makeText(this@AdminItemDetailsActivity, R.string.item_not_found, Toast.LENGTH_SHORT).show()
					finish()
					return@launch
				}
				val poster = repository.getUserByFirebaseUid(cloudItem.postedBy)
				val details = AdminItemDetails(
					cloudItem.itemName,
					cloudItem.description,
					cloudItem.location,
					cloudItem.category,
					cloudItem.status,
					cloudItem.imageName,
					cloudItem.imageUrl,
					poster
				)
				val imageId = resources.getIdentifier(details.imageName, "drawable", packageName)
				val fallback = if (imageId != 0) imageId else R.drawable.sample_backpack
				findViewById<ImageView>(R.id.admin_item_image).apply {
					ItemImageLoader.load(this, details.imageUrl, fallback, ImageView.ScaleType.CENTER_CROP)
					setOnClickListener { ItemImageLoader.showPreview(this@AdminItemDetailsActivity, details.imageUrl, fallback) }
				}
				findViewById<TextView>(R.id.admin_item_name).text = details.title
				findViewById<TextView>(R.id.admin_item_type).apply {
					val isFound = itemType == ItemDetailsActivity.TYPE_FOUND
					setText(if (isFound) R.string.found else R.string.lost)
					setTextColor(
						ContextCompat.getColor(
							this@AdminItemDetailsActivity,
							if (isFound) R.color.black else R.color.admin_red
						)
					)
				}
				bindStatus(details.status)
				findViewById<TextView>(R.id.admin_item_category).text = details.category
				findViewById<TextView>(R.id.admin_item_location).text = details.location
				findViewById<TextView>(R.id.admin_item_description).text = details.description
				findViewById<TextView>(R.id.admin_item_poster).text = details.poster?.username ?: cloudItem.postedBy
				findViewById<TextView>(R.id.admin_item_poster_email).apply {
					val email = details.poster?.email.orEmpty()
					text = email
					isVisible = email.isNotBlank()
				}
				findViewById<TextView>(R.id.admin_item_poster_student_id).apply {
					val studentId = details.poster?.studentId.orEmpty()
					text = studentId
					isVisible = studentId.isNotBlank()
				}
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: Exception) {
				Log.e(TAG, "Unable to load administrator item", exception)
				Toast.makeText(this@AdminItemDetailsActivity, R.string.items_load_failed, Toast.LENGTH_SHORT).show()
				finish()
			}
		}
	}

	private fun bindStatus(status: String) {
		val statusTextRes = when (status) {
			ItemStatus.CLOSED -> R.string.item_closed
			ItemStatus.PENDING_RETURN -> R.string.item_pending_return
			ItemStatus.CLAIMED -> R.string.item_claimed
			else -> R.string.item_open
		}
		findViewById<TextView>(R.id.admin_item_status).setText(statusTextRes)
		val isClosed = status == ItemStatus.CLOSED
		findViewById<MaterialButton>(R.id.admin_close_item).apply {
			isEnabled = !isClosed
			alpha = if (isClosed) 0.5f else 1f
			setText(if (isClosed) R.string.item_closed else R.string.admin_mark_closed)
		}
	}

	private fun confirmClose() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.admin_close_item_title)
			.setMessage(R.string.admin_close_item_message)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.admin_mark_closed) { _, _ -> closeItem() }
			.show()
	}

	private fun closeItem() {
		val adminId = sessionManager.userId ?: return denyAccess()
		lifecycleScope.launch {
			try {
				if (!repository.isAdmin(adminId)) {
					denyAccess()
					return@launch
				}
				firestoreRepository.closeItem(itemId)
				bindStatus(ItemStatus.CLOSED)
				Toast.makeText(this@AdminItemDetailsActivity, R.string.admin_item_closed_success, Toast.LENGTH_SHORT).show()
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: Exception) {
				Log.e(TAG, "Unable to close administrator item", exception)
				Toast.makeText(this@AdminItemDetailsActivity, R.string.admin_close_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun confirmDelete() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.delete_item_title)
			.setMessage(R.string.delete_item_message)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.delete) { _, _ -> deleteItem() }
			.show()
	}

	private fun deleteItem() {
		val adminId = sessionManager.userId ?: return denyAccess()
		lifecycleScope.launch {
			try {
				if (!repository.isAdmin(adminId)) {
					denyAccess()
					return@launch
				}
				val cleanupError = firestoreRepository.deleteItem(itemId)
				if (cleanupError != null) {
					Toast.makeText(this@AdminItemDetailsActivity, R.string.item_delete_cleanup_failed, Toast.LENGTH_SHORT).show()
				} else {
					Toast.makeText(this@AdminItemDetailsActivity, R.string.item_deleted, Toast.LENGTH_SHORT).show()
				}
				finish()
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: Exception) {
				Log.e(TAG, "Unable to delete administrator item", exception)
				Toast.makeText(this@AdminItemDetailsActivity, R.string.admin_delete_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun denyAccess() {
		sessionManager.logout()
		Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show()
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private data class AdminItemDetails(
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val status: String,
		val imageName: String,
		val imageUrl: String?,
		val poster: com.example.cos30017developmentproject.data.local.UserEntity?
	)

	companion object {
		const val EXTRA_ITEM_ID = "admin_item_id"
		const val EXTRA_ITEM_TYPE = "admin_item_type"
		private const val TAG = "AdminItemDetailsActivity"
	}
}
