package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.ClaimStatus
import com.example.cos30017developmentproject.data.remote.FirestoreClaimRepository
import com.example.cos30017developmentproject.ui.claims.AdminClaimCard
import com.example.cos30017developmentproject.ui.claims.AdminClaimCardAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AdminClaimReviewActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreClaimRepository
	private lateinit var sessionManager: SessionManager
	private lateinit var drawer: DrawerLayout
	private lateinit var sideNavigation: NavigationView
	private lateinit var bottomNavigation: BottomNavigationView
	private lateinit var adapter: AdminClaimCardAdapter
	private lateinit var backCallback: OnBackPressedCallback
	private var allClaims: List<AdminClaimCard> = emptyList()
	private var claimFilter = ClaimFilter.ALL

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = FirestoreClaimRepository()
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_claim_review)

		drawer = findViewById(R.id.admin_drawer_layout)
		sideNavigation = findViewById(R.id.admin_side_navigation)
		bottomNavigation = findViewById(R.id.admin_bottom_navigation)
		bottomNavigation.isItemActiveIndicatorEnabled = false

		ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			findViewById<View>(R.id.admin_claim_review_root).setPadding(
				bars.left, bars.top, bars.right, bars.bottom
			)
			sideNavigation.setPadding(0, bars.top, 0, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(drawer)

		setupNavigation()
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

		bottomNavigation.selectedItemId = R.id.admin_nav_requests
		sideNavigation.setCheckedItem(R.id.admin_drawer_requests)

		bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.admin_nav_posts -> {
					openPosts()
					false
				}
				R.id.admin_nav_requests -> true
				R.id.admin_nav_profile -> {
					startActivity(Intent(this, ProfileActivity::class.java))
					false
				}
				else -> false
			}
		}

		sideNavigation.setNavigationItemSelectedListener { item ->
			drawer.closeDrawer(GravityCompat.START)
			when (item.itemId) {
				R.id.admin_drawer_posts -> {
					openPosts()
					false
				}
				R.id.admin_drawer_requests -> true
				R.id.admin_drawer_logout -> {
					logout()
					false
				}
				else -> false
			}
		}
	}

	private fun setupRequestsList() {
		adapter = AdminClaimCardAdapter(
			onApprove = { card -> reviewClaim(card.claimId, approve = true) },
			onReject = { card -> reviewClaim(card.claimId, approve = false) }
		)
		findViewById<RecyclerView>(R.id.admin_requests_list).apply {
			layoutManager = LinearLayoutManager(this@AdminClaimReviewActivity)
			this.adapter = this@AdminClaimReviewActivity.adapter
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
		val adminUid = repository.currentUserUid
		if (sessionManager.userId == null || adminUid == null) {
			denyAccess()
			return
		}
		lifecycleScope.launch {
			if (!repository.isAdmin(adminUid)) {
				denyAccess()
				return@launch
			}
			repository.observeAllClaims()
				.catch { error ->
					Log.e(TAG, "Unable to load claim requests", error)
					Toast.makeText(this@AdminClaimReviewActivity, R.string.claims_load_failed, Toast.LENGTH_SHORT).show()
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
	}

	private fun applyClaimFilter() {
		val filtered = when (claimFilter) {
			ClaimFilter.ALL -> allClaims
			ClaimFilter.PENDING -> allClaims.filter { it.status == ClaimStatus.PENDING }
			ClaimFilter.APPROVED -> allClaims.filter { it.status == ClaimStatus.APPROVED }
			ClaimFilter.REJECTED -> allClaims.filter { it.status == ClaimStatus.REJECTED }
		}
		adapter.submitList(filtered)
		findViewById<RecyclerView>(R.id.admin_requests_list).isVisible = filtered.isNotEmpty()
		findViewById<TextView>(R.id.admin_requests_empty).isVisible = filtered.isEmpty()
	}

	private fun reviewClaim(claimId: String, approve: Boolean) {
		lifecycleScope.launch {
			val result = if (approve) {
				repository.approveClaim(claimId)
			} else {
				repository.rejectClaim(claimId)
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
			Toast.makeText(this@AdminClaimReviewActivity, message, Toast.LENGTH_SHORT).show()
		}
	}

	private fun openPosts() {
		startActivity(
			Intent(this, AdminDashboardActivity::class.java)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		)
		finish()
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

	private enum class ClaimFilter { ALL, PENDING, APPROVED, REJECTED }

	private companion object {
		const val TAG = "AdminClaimReviewActivity"
	}
}
