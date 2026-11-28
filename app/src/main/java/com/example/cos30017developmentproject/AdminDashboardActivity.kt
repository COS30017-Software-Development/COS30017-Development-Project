package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.ClaimStatus
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.remote.FirestoreClaimRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ui.claims.AdminClaimCard
import com.example.cos30017developmentproject.ui.claims.AdminClaimCardAdapter
import com.example.cos30017developmentproject.ui.home.ItemCard
import com.example.cos30017developmentproject.ui.home.ItemCardAdapter
import com.example.cos30017developmentproject.ui.home.toItemCard
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var firestoreRepository: FirestoreItemRepository
	private lateinit var claimRepository: FirestoreClaimRepository
	private lateinit var sessionManager: SessionManager
	private lateinit var drawer: DrawerLayout
	private lateinit var sideNavigation: NavigationView
	private lateinit var bottomNavigation: BottomNavigationView
	private lateinit var postsPage: View
	private lateinit var requestsPage: View
	private lateinit var adapter: ItemCardAdapter
	private lateinit var requestsAdapter: AdminClaimCardAdapter
	private lateinit var backCallback: OnBackPressedCallback
	private var allPosts: List<AdminPostEntry> = emptyList()
	private var allClaims: List<AdminClaimCard> = emptyList()
	private var filter = AdminFilter.ALL
	private var claimFilter = ClaimFilter.ALL
	private var searchQuery = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		firestoreRepository = FirestoreItemRepository()
		claimRepository = FirestoreClaimRepository()
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_dashboard)

		drawer = findViewById(R.id.admin_drawer_layout)
		sideNavigation = findViewById(R.id.admin_side_navigation)
		bottomNavigation = findViewById(R.id.admin_bottom_navigation)
		bottomNavigation.isItemActiveIndicatorEnabled = false
		postsPage = findViewById(R.id.admin_posts_page)
		requestsPage = findViewById(R.id.admin_requests_page)

		ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			findViewById<View>(R.id.admin_dashboard_root).setPadding(
				bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom)
			)
			sideNavigation.setPadding(0, bars.top, 0, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(drawer)

		setupNavigation()
		setupPostsList()
		setupRequestsList()
		verifyAdminAndObserve()
	}

	private fun setupNavigation() {
		backCallback = object : OnBackPressedCallback(false) {
			override fun handleOnBackPressed() {
				drawer.closeDrawer(GravityCompat.START)
			}
		}
		onBackPressedDispatcher.addCallback(this, backCallback)
		drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
			override fun onDrawerOpened(drawerView: View) {
				backCallback.isEnabled = true
			}

			override fun onDrawerClosed(drawerView: View) {
				backCallback.isEnabled = false
			}
		})

		findViewById<ImageButton>(R.id.admin_menu_button).setOnClickListener {
			drawer.openDrawer(GravityCompat.START)
		}
		findViewById<ImageButton>(R.id.admin_logout_button).setOnClickListener {
			logout()
		}

		bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.admin_nav_posts -> {
					showTab(showPosts = true)
					true
				}
				R.id.admin_nav_requests -> {
					showTab(showPosts = false)
					true
				}
				else -> false
			}
		}

		sideNavigation.setNavigationItemSelectedListener { item ->
			drawer.closeDrawer(GravityCompat.START)
			when (item.itemId) {
				R.id.admin_drawer_posts -> {
					bottomNavigation.selectedItemId = R.id.admin_nav_posts
					true
				}
				R.id.admin_drawer_requests -> {
					bottomNavigation.selectedItemId = R.id.admin_nav_requests
					true
				}
				R.id.admin_drawer_logout -> {
					logout()
					false
				}
				else -> false
			}
		}
		bottomNavigation.selectedItemId = R.id.admin_nav_posts
	}

	private fun showTab(showPosts: Boolean) {
		postsPage.isVisible = showPosts
		requestsPage.isVisible = !showPosts
		sideNavigation.setCheckedItem(
			if (showPosts) R.id.admin_drawer_posts else R.id.admin_drawer_requests
		)
	}

	private fun setupPostsList() {
		adapter = ItemCardAdapter(onItemClick = ::openDetails)
		adapter.setListMode(true)
		findViewById<RecyclerView>(R.id.admin_items_list).apply {
			layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
			adapter = this@AdminDashboardActivity.adapter
		}

		val searchEdit = findViewById<EditText>(R.id.admin_search)
		val insetsController = WindowCompat.getInsetsController(window, searchEdit)
		searchEdit.doAfterTextChanged {
			searchQuery = it?.toString().orEmpty()
			applyFilter()
		}
		searchEdit.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				insetsController.hide(WindowInsetsCompat.Type.ime())
				searchEdit.clearFocus()
				true
			} else false
		}

		findViewById<ChipGroup>(R.id.admin_filter_group).setOnCheckedStateChangeListener { _, checkedIds ->
			val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
			val nextFilter = when (checkedId) {
				R.id.admin_filter_lost -> AdminFilter.LOST
				R.id.admin_filter_found -> AdminFilter.FOUND
				else -> AdminFilter.ALL
			}
			setFilter(nextFilter)
		}
	}

	private fun setupRequestsList() {
		requestsAdapter = AdminClaimCardAdapter(
			onApprove = { card -> reviewClaim(card.claimId, approve = true) },
			onReject = { card -> reviewClaim(card.claimId, approve = false) }
		)
		findViewById<RecyclerView>(R.id.admin_requests_list).apply {
			layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
			adapter = requestsAdapter
		}
		findViewById<ChipGroup>(R.id.admin_claim_filter_group).setOnCheckedStateChangeListener { _, checkedIds ->
			val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
			claimFilter = when (checkedId) {
				R.id.admin_claim_filter_pending -> ClaimFilter.PENDING
				R.id.admin_claim_filter_approved -> ClaimFilter.APPROVED
				R.id.admin_claim_filter_rejected -> ClaimFilter.REJECTED
				else -> ClaimFilter.ALL
			}
			applyClaimFilter()
		}
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
			launch { observeItems() }
			launch { observeClaims() }
		}
	}

	private suspend fun observeItems() {
		val posterCache = mutableMapOf<String, String>()
		firestoreRepository.observeItems()
			.catch { error ->
				Log.e(TAG, "Unable to load administrator items", error)
				Toast.makeText(this@AdminDashboardActivity, R.string.items_load_failed, Toast.LENGTH_SHORT).show()
				emit(emptyList())
			}
			.collect { items ->
				allPosts = items.map { item ->
					val posterText = posterCache.getOrPut(item.postedBy) {
						val poster = repository.getUserByFirebaseUid(item.postedBy)
						listOfNotNull(poster?.username, poster?.email, poster?.studentId, item.postedBy)
							.joinToString(" ")
					}
					AdminPostEntry(
						card = item.toItemCard(this@AdminDashboardActivity),
						posterSearchText = posterText
					)
				}
				applyFilter()
			}
	}

	private suspend fun observeClaims() {
		claimRepository.observeAllClaims()
			.catch { error ->
				Log.e(TAG, "Unable to load claim requests", error)
				Toast.makeText(this@AdminDashboardActivity, R.string.claims_load_failed, Toast.LENGTH_SHORT).show()
				emit(emptyList())
			}
			.collect { claims ->
				val approvedItemIds = claims.asSequence()
					.filter { it.status == ClaimStatus.APPROVED }
					.map { it.itemId }
					.toSet()
				allClaims = claims.map { claim ->
					AdminClaimCard(
						claimId = claim.claimId,
						itemTitle = claim.itemTitle,
						itemLocation = claim.itemLocation,
						applicantName = claim.studentName,
						applicantEmail = claim.studentEmail,
						applicantStudentId = claim.studentNumber,
						description = claim.description,
						status = claim.status,
						submittedAt = claim.submittedAt,
						canApprove = claim.itemId !in approvedItemIds
					)
				}
				applyClaimFilter()
			}
	}

	private fun applyClaimFilter() {
		val filtered = when (claimFilter) {
			ClaimFilter.ALL -> allClaims
			ClaimFilter.PENDING -> allClaims.filter { it.status == ClaimStatus.PENDING }
			ClaimFilter.APPROVED -> allClaims.filter { it.status == ClaimStatus.APPROVED }
			ClaimFilter.REJECTED -> allClaims.filter { it.status == ClaimStatus.REJECTED }
		}
		requestsAdapter.submitList(filtered)
		findViewById<RecyclerView>(R.id.admin_requests_list).isVisible = filtered.isNotEmpty()
		findViewById<TextView>(R.id.admin_requests_empty).isVisible = filtered.isEmpty()
	}

	private fun reviewClaim(claimId: String, approve: Boolean) {
		lifecycleScope.launch {
			val result = if (approve) {
				claimRepository.approveClaim(claimId)
			} else {
				claimRepository.rejectClaim(claimId)
			}
			val message = when (result) {
				ClaimReviewResult.SUCCESS ->
					if (approve) R.string.claim_approved_success else R.string.claim_rejected_success
				ClaimReviewResult.ACCESS_DENIED -> R.string.admin_access_denied
				ClaimReviewResult.ALREADY_PROCESSED -> R.string.claim_review_already_processed
				ClaimReviewResult.ITEM_CLOSED -> R.string.claim_review_item_closed
				ClaimReviewResult.NOT_FOUND,
				ClaimReviewResult.ITEM_NOT_FOUND,
				ClaimReviewResult.FAILED -> R.string.claim_review_failed
			}
			Toast.makeText(this@AdminDashboardActivity, message, Toast.LENGTH_SHORT).show()
		}
	}

	private fun setFilter(filter: AdminFilter) {
		this.filter = filter
		applyFilter()
	}

	private fun applyFilter() {
		val normalizedQuery = searchQuery.trim().lowercase()
		val cards = allPosts.asSequence()
			.filter { entry ->
				when (filter) {
					AdminFilter.ALL -> true
					AdminFilter.LOST -> !entry.card.isFound
					AdminFilter.FOUND -> entry.card.isFound
				}
			}
			.filter { entry ->
				normalizedQuery.isEmpty() ||
					entry.card.title.lowercase().contains(normalizedQuery) ||
					entry.posterSearchText.lowercase().contains(normalizedQuery)
			}
			.map { it.card }
			.toList()
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

	private fun logout() {
		sessionManager.logout()
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private fun denyAccess() {
		sessionManager.logout()
		Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show()
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private data class AdminPostEntry(
		val card: ItemCard,
		val posterSearchText: String
	)

	private enum class AdminFilter { ALL, LOST, FOUND }

	private enum class ClaimFilter { ALL, PENDING, APPROVED, REJECTED }

	private companion object {
		const val TAG = "AdminDashboardActivity"
	}
}
