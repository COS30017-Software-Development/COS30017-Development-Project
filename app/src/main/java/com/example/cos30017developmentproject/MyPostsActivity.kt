package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.ui.home.ItemCard
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.toItemCard
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MyPostsActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager
	private lateinit var adapter: ItemCardAdapter
	private var filter = PostFilter.ALL
	private var allCards: List<ItemCard> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		if (sessionManager.userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_my_posts)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.my_posts_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		findViewById<MaterialToolbar>(R.id.my_posts_toolbar).setNavigationOnClickListener { finish() }
		adapter = ItemCardAdapter(onItemClick = ::openItemDetails)
		adapter.setListMode(true)
		findViewById<RecyclerView>(R.id.my_posts_list).apply {
			layoutManager = LinearLayoutManager(this@MyPostsActivity)
			adapter = this@MyPostsActivity.adapter
		}
		findViewById<View>(R.id.my_posts_filter_all).setOnClickListener { setFilter(PostFilter.ALL) }
		findViewById<View>(R.id.my_posts_filter_lost).setOnClickListener { setFilter(PostFilter.LOST) }
		findViewById<View>(R.id.my_posts_filter_found).setOnClickListener { setFilter(PostFilter.FOUND) }
		setFilter(PostFilter.ALL)
		observePosts()
	}

	private fun observePosts() {
		val userId = sessionManager.userId ?: return
		lifecycleScope.launch {
			combine(repository.observeLostItems(), repository.observeFoundItems()) { lost, found ->
				(lost.filter { it.postedByUserId == userId }.map { it.toItemCard(this@MyPostsActivity) } +
					found.filter { it.postedByUserId == userId }.map { it.toItemCard(this@MyPostsActivity) })
			}.collect { cards ->
				allCards = cards
				applyFilter()
			}
		}
	}

	private fun setFilter(nextFilter: PostFilter) {
		filter = nextFilter
		updateFilterButtons()
		applyFilter()
	}

	private fun applyFilter() {
		val filtered = when (filter) {
			PostFilter.ALL -> allCards
			PostFilter.LOST -> allCards.filter { !it.isFound }
			PostFilter.FOUND -> allCards.filter { it.isFound }
		}
		adapter.submitList(filtered)
		findViewById<TextView>(R.id.my_posts_empty).isVisible = filtered.isEmpty()
	}

	private fun updateFilterButtons() {
		bindFilterButton(R.id.my_posts_filter_all, filter == PostFilter.ALL)
		bindFilterButton(R.id.my_posts_filter_lost, filter == PostFilter.LOST)
		bindFilterButton(R.id.my_posts_filter_found, filter == PostFilter.FOUND)
	}

	private fun bindFilterButton(id: Int, selected: Boolean) {
		val button = findViewById<MaterialButton>(id)
		button.backgroundTintList = ContextCompat.getColorStateList(
			this,
			if (selected) R.color.brand_red_accessible else R.color.white
		)
		button.setTextColor(
			ContextCompat.getColor(this, if (selected) R.color.white else R.color.brand_red_accessible)
		)
		button.strokeColor = ContextCompat.getColorStateList(
			this,
			if (selected) R.color.brand_red_accessible else R.color.brand_red_accessible
		)
		button.strokeWidth = (1 * resources.displayMetrics.density).toInt()
	}

	private fun openItemDetails(item: ItemCard) {
		startActivity(
			Intent(this, ItemDetailsActivity::class.java).apply {
				putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id)
				putExtra(
					ItemDetailsActivity.EXTRA_ITEM_TYPE,
					if (item.isFound) ItemDetailsActivity.TYPE_FOUND else ItemDetailsActivity.TYPE_LOST
				)
			}
		)
	}

	private enum class PostFilter {
		ALL,
		LOST,
		FOUND
	}
}
