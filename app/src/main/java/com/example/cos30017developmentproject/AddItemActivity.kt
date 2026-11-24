package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.FoundItemEntity
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.local.LostItemEntity
import com.example.cos30017developmentproject.ui.items.ItemValidation
import com.example.cos30017developmentproject.ui.items.ItemValidationError
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AddItemActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager
	private var isSaving = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		if (sessionManager.userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_add_item)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.add_item_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		findViewById<View>(R.id.add_item_cancel).setOnClickListener { finish() }
		findViewById<View>(R.id.add_item_save).setOnClickListener { saveItem() }
	}

	private fun saveItem() {
		if (isSaving) return
		val title = input(R.id.add_item_name)
		val category = input(R.id.add_item_category)
		val location = input(R.id.add_item_location)
		val description = input(R.id.add_item_description)
		val imageName = input(R.id.add_item_image_name)
		val validationError = ItemValidation.validate(title, category, location, description, imageName)
		if (validationError != null) {
			showValidationError(validationError)
			return
		}
		val userId = sessionManager.userId
		if (userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		val isLost = findViewById<RadioGroup>(R.id.add_item_type).checkedRadioButtonId == R.id.add_item_type_lost
		isSaving = true
		findViewById<MaterialButton>(R.id.add_item_save).isEnabled = false
		lifecycleScope.launch {
			try {
				if (isLost) {
					repository.addLostItem(
						LostItemEntity(
							title = title,
							description = description,
							location = location,
							category = category,
							imageName = imageName,
							postedByUserId = userId
						)
					)
				} else {
					repository.addFoundItem(
						FoundItemEntity(
							title = title,
							description = description,
							location = location,
							category = category,
							imageName = imageName,
							postedByUserId = userId
						)
					)
				}
				Toast.makeText(this@AddItemActivity, R.string.item_posted, Toast.LENGTH_SHORT).show()
				finish()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Unable to create item", e)
				Toast.makeText(this@AddItemActivity, R.string.item_save_failed, Toast.LENGTH_SHORT).show()
			} finally {
				isSaving = false
				if (!isFinishing) {
					findViewById<MaterialButton>(R.id.add_item_save).isEnabled = true
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

	private companion object {
		const val TAG = "AddItemActivity"
	}
}
