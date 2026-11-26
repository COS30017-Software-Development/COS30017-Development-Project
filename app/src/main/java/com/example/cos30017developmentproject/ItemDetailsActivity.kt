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
import com.example.cos30017developmentproject.data.local.ClaimSubmissionResult
import com.example.cos30017developmentproject.data.local.ItemStatus
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.remote.FirestoreClaimRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ui.home.ItemImageLoader
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ItemDetailsActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var firestoreRepository: FirestoreItemRepository
	private lateinit var firestoreClaimRepository: FirestoreClaimRepository
	private val editItem = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			itemIdFromIntent(intent)?.let { loadItem(it, intent.getStringExtra(EXTRA_ITEM_TYPE).orEmpty()) }
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
		firestoreRepository = FirestoreItemRepository()
		firestoreClaimRepository = FirestoreClaimRepository()

		val id = itemIdFromIntent(intent)
		val type = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: ""
		if (id.isNullOrBlank() || type !in setOf(TYPE_LOST, TYPE_FOUND)) {
			showItemNotFound()
			return
		}
		loadItem(id, type)
	}

	private fun loadItem(id: String, type: String) {
		lifecycleScope.launch {
			try {
				val cloudItem = firestoreRepository.getItem(id)
				val expectedType = if (type == TYPE_FOUND) {
					FirestoreItemRepository.TYPE_FOUND
				} else {
					FirestoreItemRepository.TYPE_LOST
				}
				val item = if (cloudItem != null) {
					cloudItem.takeIf { it.type == expectedType }?.let {
						ItemDetails(
							id = it.itemId,
							title = it.itemName,
							description = it.description,
							location = it.location,
							category = it.category,
							imageName = it.imageName,
							imageUrl = it.imageUrl,
							postedByUid = it.postedBy,
							status = it.status,
							isFirestoreItem = true
						)
					}
				} else {
					val localId = id.toLongOrNull()
					when (type) {
						TYPE_LOST -> localId?.let { roomId ->
							repository.getLostItem(roomId)?.let { lost ->
								val poster = repository.getUser(lost.postedByUserId)
								ItemDetails(
									id = id,
									title = lost.title,
									description = lost.description,
									location = lost.location,
									category = lost.category,
									imageName = lost.imageName,
									postedByUid = poster?.firebaseUid,
									postedByUserId = lost.postedByUserId,
									roomItemId = roomId
								)
							}
						}
						TYPE_FOUND -> localId?.let { roomId ->
							repository.getFoundItem(roomId)?.let { found ->
								val poster = repository.getUser(found.postedByUserId)
								ItemDetails(
									id = id,
									title = found.title,
									description = found.description,
									location = found.location,
									category = found.category,
									imageName = found.imageName,
									postedByUid = poster?.firebaseUid,
									postedByUserId = found.postedByUserId,
									status = found.status,
									roomItemId = roomId
								)
							}
						}
						else -> null
					}
				} ?: run {
					showItemNotFound()
					return@launch
				}
				val username = item.postedByUserId?.let { repository.getUser(it)?.username }
					?: item.postedByUid?.let { repository.getUserByFirebaseUid(it)?.username }
				bindItem(item, type, username ?: item.postedByUid)
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
		val fallback = if (imageId != 0) imageId else R.drawable.sample_backpack
		findViewById<ImageView>(R.id.item_details_image).apply {
			ItemImageLoader.load(this, item.imageUrl, fallback, ImageView.ScaleType.CENTER_CROP)
			contentDescription = getString(R.string.item_image_description, item.title)
			setOnClickListener { ItemImageLoader.showPreview(this@ItemDetailsActivity, item.imageUrl, fallback) }
		}
		findViewById<TextView>(R.id.item_details_name).text = item.title
		findViewById<TextView>(R.id.item_details_type).text = getString(
			R.string.item_type_format,
			getString(if (type == TYPE_FOUND) R.string.found else R.string.lost)
		)
		findViewById<TextView>(R.id.item_details_status).setText(
			when (item.status) {
				ItemStatus.CLOSED -> R.string.item_closed
				ItemStatus.CLAIMED -> R.string.item_claimed
				ItemStatus.PENDING_RETURN -> R.string.item_pending_return
				else -> R.string.item_open
			}
		)
		findViewById<TextView>(R.id.item_details_category).text = item.category
		findViewById<TextView>(R.id.item_details_location).text = item.location
		findViewById<TextView>(R.id.item_details_description).text = item.description
		findViewById<View>(R.id.item_details_poster_section).isVisible = username != null
		findViewById<TextView>(R.id.item_details_poster).text = username.orEmpty()
		findViewById<View>(R.id.item_owner_actions).isVisible = isCurrentUserOwner(item)
		findViewById<View>(R.id.edit_item).setOnClickListener { openEdit(item.id, type) }
		findViewById<View>(R.id.delete_item).setOnClickListener { confirmDelete(item.id, type) }
		findViewById<TextView>(R.id.claim_item).apply {
			setOnClickListener { openClaimDialog(item, type) }
			setText(if (type == TYPE_FOUND) R.string.claim_request else R.string.found_request)
		}
		updateClaimButton(item, type)
	}

	private fun updateClaimButton(item: ItemDetails, type: String) {
		val claimButton = findViewById<View>(R.id.claim_item)
		val session = SessionManager(this)
		val userId = session.userId
		if (!item.isFirestoreItem || type !in setOf(TYPE_FOUND, TYPE_LOST) ||
			item.status == ItemStatus.CLOSED ||
			(item.postedByUserId != null && item.postedByUserId == userId) ||
			(item.postedByUid != null && item.postedByUid == firestoreRepository.currentUserUid)
		) {
			claimButton.isVisible = false
			return
		}
		if (userId == null) {
			claimButton.isVisible = true
			claimButton.setOnClickListener {
				startActivity(Intent(this, AuthActivity::class.java))
			}
			return
		}
		lifecycleScope.launch {
			try {
				val alreadyRequested = firestoreClaimRepository.hasPendingClaim(item.id)
				claimButton.isVisible = !alreadyRequested
				claimButton.isEnabled = !alreadyRequested
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to check for an existing request", e)
				claimButton.isVisible = false
				Toast.makeText(this@ItemDetailsActivity, R.string.claim_submit_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun openClaimDialog(item: ItemDetails, type: String) {
		val userId = SessionManager(this).userId
		if (userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			return
		}
		val dialogView = layoutInflater.inflate(R.layout.dialog_submit_claim, null)
		val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.claim_description_layout)
		val input = dialogView.findViewById<TextInputEditText>(R.id.claim_description)
		val dialog = MaterialAlertDialogBuilder(this)
			.setTitle(if (type == TYPE_FOUND) R.string.claim_request_title else R.string.found_request_title)
			.setView(dialogView)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.submit_claim, null)
			.create()
		dialog.setOnShowListener {
			dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
				val description = input.text?.toString().orEmpty().trim()
				when {
					description.length < 10 -> inputLayout.error = getString(R.string.claim_description_too_short)
					description.length > 500 -> inputLayout.error = getString(R.string.claim_description_too_long)
					else -> {
						inputLayout.error = null
						submitClaim(dialog, item, userId, description)
					}
				}
			}
		}
		dialog.show()
	}

	private fun submitClaim(
		dialog: androidx.appcompat.app.AlertDialog,
		item: ItemDetails,
		userId: Long,
		description: String
	) {
		lifecycleScope.launch {
			try {
				val result = if (item.isFirestoreItem) {
					val applicant = repository.getUser(userId)
					firestoreClaimRepository.submitClaim(
						itemId = item.id,
						description = description,
						studentName = applicant?.username.orEmpty(),
						studentEmail = applicant?.email.orEmpty(),
						studentNumber = applicant?.studentId.orEmpty()
					)
				} else {
					val roomItemId = item.roomItemId ?: return@launch
					repository.submitClaim(roomItemId, userId, description)
				}
				val message = when (result) {
					ClaimSubmissionResult.SUCCESS -> R.string.claim_submitted
					ClaimSubmissionResult.ITEM_NOT_FOUND -> R.string.item_not_found
					ClaimSubmissionResult.ITEM_CLOSED -> R.string.claim_item_closed
					ClaimSubmissionResult.OWNER_CANNOT_CLAIM -> R.string.claim_owner_not_allowed
					ClaimSubmissionResult.ALREADY_CLAIMED -> R.string.claim_already_submitted
					ClaimSubmissionResult.DESCRIPTION_TOO_SHORT -> R.string.claim_description_too_short
					ClaimSubmissionResult.DESCRIPTION_TOO_LONG -> R.string.claim_description_too_long
					ClaimSubmissionResult.FAILED -> R.string.claim_submit_failed
				}
				Toast.makeText(this@ItemDetailsActivity, message, Toast.LENGTH_SHORT).show()
				if (result == ClaimSubmissionResult.SUCCESS) {
					dialog.dismiss()
					findViewById<View>(R.id.claim_item).isEnabled = false
					findViewById<View>(R.id.claim_item).visibility = View.GONE
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to submit request", e)
				Toast.makeText(this@ItemDetailsActivity, R.string.claim_submit_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun openEdit(id: String, type: String) {
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

	private fun confirmDelete(id: String, type: String) {
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

	private fun deleteItem(id: String, type: String) {
		lifecycleScope.launch {
			try {
				val userId = SessionManager(this@ItemDetailsActivity).userId
					?: run {
						showPermissionDenied()
						return@launch
					}
				var cleanupFailed = false
				val cloudItem = firestoreRepository.getItem(id)
				if (cloudItem != null) {
					if (cloudItem.postedBy != firestoreRepository.currentUserUid) {
						showPermissionDenied()
						return@launch
					}
					cleanupFailed = firestoreRepository.deleteItem(id) != null
				} else {
					val roomId = id.toLongOrNull()
						?: run {
							showPermissionDenied()
							return@launch
						}
					val deleted = when (type) {
						TYPE_LOST -> repository.deleteLostItemOwned(roomId, userId)
						TYPE_FOUND -> repository.deleteFoundItemOwned(roomId, userId)
						else -> false
					}
					if (!deleted) {
						showPermissionDenied()
						return@launch
					}
				}
				Toast.makeText(
					this@ItemDetailsActivity,
					if (cleanupFailed) R.string.item_delete_cleanup_failed else R.string.item_deleted,
					Toast.LENGTH_SHORT
				).show()
				finish()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to delete item", e)
				Toast.makeText(this@ItemDetailsActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private suspend fun isCurrentUserOwner(id: String, type: String): Boolean {
		val cloudItem = firestoreRepository.getItem(id)
		if (cloudItem != null) return cloudItem.postedBy == firestoreRepository.currentUserUid
		val userId = SessionManager(this).userId ?: return false
		val roomId = id.toLongOrNull() ?: return false
		return when (type) {
			TYPE_LOST -> repository.getLostItem(roomId)?.postedByUserId == userId
			TYPE_FOUND -> repository.getFoundItem(roomId)?.postedByUserId == userId
			else -> false
		}
	}

	private fun isCurrentUserOwner(item: ItemDetails): Boolean =
		(item.postedByUid != null && item.postedByUid == firestoreRepository.currentUserUid) ||
			(item.postedByUserId != null && item.postedByUserId == SessionManager(this).userId)

	private fun showPermissionDenied() {
		Toast.makeText(this, R.string.item_action_not_allowed, Toast.LENGTH_SHORT).show()
	}

	private fun showItemNotFound() {
		Toast.makeText(this, R.string.item_not_found, Toast.LENGTH_SHORT).show()
		finish()
	}

	private data class ItemDetails(
		val id: String,
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val imageName: String,
		val imageUrl: String? = null,
		val postedByUid: String?,
		val postedByUserId: Long? = null,
		val status: String = ItemStatus.OPEN,
		val roomItemId: Long? = null,
		val isFirestoreItem: Boolean = false
	)

	private fun itemIdFromIntent(intent: Intent): String? = intent.getStringExtra(EXTRA_ITEM_ID)

	companion object {
		const val EXTRA_ITEM_ID = "item_id"
		const val EXTRA_ITEM_TYPE = "item_type"
		const val TYPE_LOST = "Lost"
		const val TYPE_FOUND = "Found"
		private const val TAG = "ItemDetailsActivity"
	}
}
