package com.example.cos30017developmentproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ui.items.ItemValidation
import com.example.cos30017developmentproject.ui.items.ItemValidationError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EditItemActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreItemRepository
	private lateinit var localRepository: LostAndFoundRepository
	private var itemId = ""
	private var itemType = ""
	private var isSaving = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		itemId = intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_ID).orEmpty()
		itemType = intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_TYPE).orEmpty()
		if (itemId.isBlank() || itemType !in setOf(ItemDetailsActivity.TYPE_LOST, ItemDetailsActivity.TYPE_FOUND)) {
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_edit_item)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_item_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = FirestoreItemRepository()
		localRepository = LostAndFoundRepository(AppDatabase.getInstance(this))
		findViewById<View>(R.id.edit_item_cancel).setOnClickListener { finish() }
		findViewById<View>(R.id.edit_item_save).setOnClickListener { saveItem() }
		loadItem()
	}

	private fun loadItem() {
		lifecycleScope.launch {
			try {
				val currentUid = repository.currentUserUid
				val cloudItem = repository.getItem(itemId)
				val item = cloudItem?.takeIf { it.postedBy == currentUid }?.let {
					EditableItem(it.itemName, it.description, it.location, it.category, it.imageName)
				} ?: if (cloudItem == null) {
					val localId = itemId.toLongOrNull()
					val userId = SessionManager(this@EditItemActivity).userId
					when (itemType) {
						ItemDetailsActivity.TYPE_LOST -> localId?.let { id ->
							localRepository.getLostItem(id)?.takeIf { it.postedByUserId == userId }?.let {
								EditableItem(it.title, it.description, it.location, it.category, it.imageName)
							}
						}
						ItemDetailsActivity.TYPE_FOUND -> localId?.let { id ->
							localRepository.getFoundItem(id)?.takeIf { it.postedByUserId == userId }?.let {
								EditableItem(it.title, it.description, it.location, it.category, it.imageName)
							}
						}
						else -> null
					}
				} else null
				val editableItem = item ?: run {
					showPermissionDeniedAndFinish()
					return@launch
				}
				findViewById<EditText>(R.id.edit_item_name).setText(editableItem.title)
				findViewById<EditText>(R.id.edit_item_category).setText(editableItem.category)
				findViewById<EditText>(R.id.edit_item_location).setText(editableItem.location)
				findViewById<EditText>(R.id.edit_item_description).setText(editableItem.description)
				findViewById<EditText>(R.id.edit_item_image_name).setText(editableItem.imageName)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to load item", e)
				Toast.makeText(this@EditItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
				finish()
			}
		}
	}

	private fun saveItem() {
		if (isSaving) return
		val title = input(R.id.edit_item_name)
		val category = input(R.id.edit_item_category)
		val location = input(R.id.edit_item_location)
		val description = input(R.id.edit_item_description)
		val imageName = input(R.id.edit_item_image_name)
		val validationError = ItemValidation.validate(title, category, location, description, imageName)
		if (validationError != null) {
			showValidationError(validationError)
			return
		}
		isSaving = true
		findViewById<MaterialButton>(R.id.edit_item_save).isEnabled = false
		lifecycleScope.launch {
			try {
				val currentUid = repository.currentUserUid
					?: run {
						showPermissionDeniedAndFinish()
						return@launch
					}
				val item = repository.getItem(itemId)
				val expectedType = if (itemType == ItemDetailsActivity.TYPE_FOUND) {
					FirestoreItemRepository.TYPE_FOUND
				} else {
					FirestoreItemRepository.TYPE_LOST
				}
				if (item != null && (item.postedBy != currentUid || item.type != expectedType)) {
					showPermissionDeniedAndFinish()
					return@launch
				}
				if (item != null) {
					repository.updateItem(itemId, title, description, category, location, imageName)
				} else {
					val localId = itemId.toLongOrNull()
						?: run {
							showPermissionDeniedAndFinish()
							return@launch
						}
					val userId = SessionManager(this@EditItemActivity).userId
						?: run {
							showPermissionDeniedAndFinish()
							return@launch
						}
					val updated = when (itemType) {
						ItemDetailsActivity.TYPE_LOST -> localRepository.updateLostItemOwned(
							localId, userId, title, description, location, category, imageName
						)
						ItemDetailsActivity.TYPE_FOUND -> localRepository.updateFoundItemOwned(
							localId, userId, title, description, location, category, imageName
						)
						else -> false
					}
					if (!updated) {
						showPermissionDeniedAndFinish()
						return@launch
					}
				}
				Toast.makeText(this@EditItemActivity, R.string.item_updated, Toast.LENGTH_SHORT).show()
				setResult(RESULT_OK)
				finish()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to update item", e)
				Toast.makeText(this@EditItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			} finally {
				isSaving = false
				if (!isFinishing) {
					findViewById<MaterialButton>(R.id.edit_item_save).isEnabled = true
				}
			}
		}
	}

	private fun input(id: Int) = findViewById<EditText>(id).text.toString().trim()

	private fun showValidationError(error: ItemValidationError) {
		val message = when (error) {
			ItemValidationError.REQUIRED -> R.string.item_fields_required
			ItemValidationError.TITLE_TOO_LONG -> R.string.item_title_too_long
			ItemValidationError.CATEGORY_TOO_LONG -> R.string.item_category_too_long
			ItemValidationError.LOCATION_TOO_LONG -> R.string.item_location_too_long
			ItemValidationError.DESCRIPTION_TOO_LONG -> R.string.item_description_too_long
			ItemValidationError.INVALID_IMAGE_NAME -> R.string.item_image_name_invalid
		}
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
	}

	private fun showPermissionDeniedAndFinish() {
		Toast.makeText(this, R.string.item_action_not_allowed, Toast.LENGTH_SHORT).show()
		finish()
	}

	private data class EditableItem(
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val imageName: String
	)

	private companion object {
		const val TAG = "EditItemActivity"
	}
}
