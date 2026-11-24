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
import com.example.cos30017developmentproject.ui.items.ItemValidation
import com.example.cos30017developmentproject.ui.items.ItemValidationError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EditItemActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private var itemId = INVALID_ITEM_ID
	private var itemType = ""
	private var isSaving = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		itemId = intent.getLongExtra(ItemDetailsActivity.EXTRA_ITEM_ID, INVALID_ITEM_ID)
		itemType = intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_TYPE).orEmpty()
		if (itemId == INVALID_ITEM_ID || itemType !in setOf(ItemDetailsActivity.TYPE_LOST, ItemDetailsActivity.TYPE_FOUND)) {
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
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		findViewById<View>(R.id.edit_item_cancel).setOnClickListener { finish() }
		findViewById<View>(R.id.edit_item_save).setOnClickListener { saveItem() }
		loadItem()
	}

	private fun loadItem() {
		lifecycleScope.launch {
			try {
				val userId = SessionManager(this@EditItemActivity).userId
				val item = when (itemType) {
					ItemDetailsActivity.TYPE_LOST -> repository.getLostItem(itemId)?.takeIf { it.postedByUserId == userId }?.let {
						EditableItem(it.title, it.description, it.location, it.category, it.imageName)
					}
					ItemDetailsActivity.TYPE_FOUND -> repository.getFoundItem(itemId)?.takeIf { it.postedByUserId == userId }?.let {
						EditableItem(it.title, it.description, it.location, it.category, it.imageName)
					}
					else -> null
				} ?: run {
					showPermissionDeniedAndFinish()
					return@launch
				}
				findViewById<EditText>(R.id.edit_item_name).setText(item.title)
				findViewById<EditText>(R.id.edit_item_category).setText(item.category)
				findViewById<EditText>(R.id.edit_item_location).setText(item.location)
				findViewById<EditText>(R.id.edit_item_description).setText(item.description)
				findViewById<EditText>(R.id.edit_item_image_name).setText(item.imageName)
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
				val userId = SessionManager(this@EditItemActivity).userId
					?: run {
						showPermissionDeniedAndFinish()
						return@launch
					}
				val updated = when (itemType) {
					ItemDetailsActivity.TYPE_LOST -> repository.updateLostItemOwned(
						itemId, userId, title, description, location, category, imageName
					)
					ItemDetailsActivity.TYPE_FOUND -> repository.updateFoundItemOwned(
						itemId, userId, title, description, location, category, imageName
					)
					else -> false
				}
				if (updated) {
					Toast.makeText(this@EditItemActivity, R.string.item_updated, Toast.LENGTH_SHORT).show()
					setResult(RESULT_OK)
					finish()
				} else showPermissionDeniedAndFinish()
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
		const val INVALID_ITEM_ID = -1L
		const val TAG = "EditItemActivity"
	}
}
