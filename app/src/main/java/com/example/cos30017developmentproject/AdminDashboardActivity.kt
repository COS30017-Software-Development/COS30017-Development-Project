package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager
	private lateinit var adapter: ItemCardAdapter
	private var allCards: List<ItemCard> = emptyList()
	private var filter = AdminFilter.ALL

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_dashboard)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_dashboard_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}

		findViewById<MaterialToolbar>(R.id.admin_dashboard_toolbar).setNavigationOnClickListener {
			sessionManager.logout()
			startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
			finish()
		}
		findViewById<View>(R.id.admin_logout).setOnClickListener {
			sessionManager.logout()
			startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
			finish()
		}
		findViewById<View>(R.id.admin_claim_requests).setOnClickListener {
			startActivity(Intent(this, AdminClaimReviewActivity::class.java))
		}
		adapter = ItemCardAdapter(onItemClick = ::openDetails)
		adapter.setListMode(true)
		findViewById<RecyclerView>(R.id.admin_items_list).apply {
			layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
			adapter = this@AdminDashboardActivity.adapter
		}
		findViewById<View>(R.id.admin_filter_all).setOnClickListener { setFilter(AdminFilter.ALL) }
		findViewById<View>(R.id.admin_filter_lost).setOnClickListener { setFilter(AdminFilter.LOST) }
		findViewById<View>(R.id.admin_filter_found).setOnClickListener { setFilter(AdminFilter.FOUND) }
		verifyAdminAndObserve()
	}

	private fun verifyAdminAndObserve() {
		val adminId = sessionManager.userId
		if (adminId == null) {
			denyAccess()
			return
		}
		lifecycleScope.launch {
			if (!repository.isAdmin(adminId)) {
				denyAccess()
				return@launch
			}
			combine(repository.observeLostItems(), repository.observeFoundItems()) { lost, found ->
				lost.map { it.toItemCard(this@AdminDashboardActivity) } +
					found.map { it.toItemCard(this@AdminDashboardActivity) }
			}.collect { cards ->
				allCards = cards
				applyFilter()
			}
		}
	}

	private fun setFilter(filter: AdminFilter) {
		this.filter = filter
		applyFilter()
	}

	private fun applyFilter() {
		val cards = when (filter) {
			AdminFilter.ALL -> allCards
			AdminFilter.LOST -> allCards.filter { !it.isFound }
			AdminFilter.FOUND -> allCards.filter { it.isFound }
		}
		adapter.submitList(cards)
		findViewById<RecyclerView>(R.id.admin_items_list).isVisible = cards.isNotEmpty()
		findViewById<TextView>(R.id.admin_items_empty).isVisible = cards.isEmpty()
	}

	private fun openDetails(card: ItemCard) {
		startActivity(Intent(this, AdminItemDetailsActivity::class.java).apply {
			putExtra(AdminItemDetailsActivity.EXTRA_ITEM_ID, card.id)
			putExtra(
				AdminItemDetailsActivity.EXTRA_ITEM_TYPE,
				if (card.isFound) ItemDetailsActivity.TYPE_FOUND else ItemDetailsActivity.TYPE_LOST
			)
		})
	}

	private fun denyAccess() {
		sessionManager.logout()
		Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show()
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private enum class AdminFilter { ALL, LOST, FOUND }
}
