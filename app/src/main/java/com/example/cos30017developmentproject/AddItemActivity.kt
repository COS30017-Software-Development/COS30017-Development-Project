package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.launch

class AddItemActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager

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
		val title = input(R.id.add_item_name)
		val category = input(R.id.add_item_category)
		val location = input(R.id.add_item_location)
		val description = input(R.id.add_item_description)
		val imageName = input(R.id.add_item_image_name)
		if (listOf(title, category, location, description, imageName).any(String::isBlank)) {
			Toast.makeText(this, R.string.item_fields_required, Toast.LENGTH_SHORT).show()
			return
		}
		val userId = sessionManager.userId
		if (userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		val isLost = findViewById<RadioGroup>(R.id.add_item_type).checkedRadioButtonId == R.id.add_item_type_lost
		lifecycleScope.launch {
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
		}
	}

	private fun input(id: Int) = findViewById<EditText>(id).text.toString().trim()
}
