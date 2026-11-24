package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.local.UserRole
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AdminItemDetailsActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager
	private var itemId = INVALID_ITEM_ID
	private var itemType = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		itemId = intent.getLongExtra(EXTRA_ITEM_ID, INVALID_ITEM_ID)
		itemType = intent.getStringExtra(EXTRA_ITEM_TYPE).orEmpty()
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_item_details)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_item_details_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		findViewById<MaterialToolbar>(R.id.admin_item_details_toolbar).setNavigationOnClickListener { finish() }
		findViewById<View>(R.id.admin_delete_item).setOnClickListener { confirmDelete() }
		verifyAdminAndLoad()
	}

	private fun verifyAdminAndLoad() {
		val adminId = sessionManager.userId
		if (itemId == INVALID_ITEM_ID || itemType !in setOf(ItemDetailsActivity.TYPE_LOST, ItemDetailsActivity.TYPE_FOUND) ||
			adminId == null || sessionManager.role != UserRole.ADMIN
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
			val details = when (itemType) {
				ItemDetailsActivity.TYPE_LOST -> repository.getLostItem(itemId)?.let { item ->
					val poster = repository.getUser(item.postedByUserId)
					AdminItemDetails(item.id, item.title, item.description, item.location, item.category, item.imageName, poster)
				}
				ItemDetailsActivity.TYPE_FOUND -> repository.getFoundItem(itemId)?.let { item ->
					val poster = repository.getUser(item.postedByUserId)
					AdminItemDetails(item.id, item.title, item.description, item.location, item.category, item.imageName, poster)
				}
				else -> null
			}
			if (details == null) {
				Toast.makeText(this@AdminItemDetailsActivity, R.string.item_not_found, Toast.LENGTH_SHORT).show()
				finish()
				return@launch
			}
			val imageId = resources.getIdentifier(details.imageName, "drawable", packageName)
			findViewById<ImageView>(R.id.admin_item_image).setImageResource(
				if (imageId != 0) imageId else R.drawable.sample_backpack
			)
			findViewById<TextView>(R.id.admin_item_name).text = details.title
			findViewById<TextView>(R.id.admin_item_type).text = itemType
			findViewById<TextView>(R.id.admin_item_category).text = details.category
			findViewById<TextView>(R.id.admin_item_location).text = details.location
			findViewById<TextView>(R.id.admin_item_description).text = details.description
			findViewById<TextView>(R.id.admin_item_poster).text = details.poster?.username.orEmpty()
			findViewById<TextView>(R.id.admin_item_poster_email).text = details.poster?.email.orEmpty()
			findViewById<TextView>(R.id.admin_item_poster_student_id).text = details.poster?.studentId.orEmpty()
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
			val deleted = when (itemType) {
				ItemDetailsActivity.TYPE_LOST -> repository.deleteLostItemAsAdmin(itemId, adminId)
				ItemDetailsActivity.TYPE_FOUND -> repository.deleteFoundItemAsAdmin(itemId, adminId)
				else -> false
			}
			if (deleted) {
				Toast.makeText(this@AdminItemDetailsActivity, R.string.item_deleted, Toast.LENGTH_SHORT).show()
				finish()
			} else {
				Toast.makeText(
					this@AdminItemDetailsActivity,
					R.string.admin_delete_failed,
					Toast.LENGTH_SHORT
				).show()
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
		val id: Long,
		val title: String,
		val description: String,
		val location: String,
		val category: String,
		val imageName: String,
		val poster: com.example.cos30017developmentproject.data.local.UserEntity?
	)

	companion object {
		const val EXTRA_ITEM_ID = "admin_item_id"
		const val EXTRA_ITEM_TYPE = "admin_item_type"
		private const val INVALID_ITEM_ID = -1L
	}
}
