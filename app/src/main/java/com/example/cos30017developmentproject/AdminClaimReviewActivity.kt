package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
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
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.ui.claims.AdminClaimCard
import com.example.cos30017developmentproject.ui.claims.AdminClaimCardAdapter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AdminClaimReviewActivity : AppCompatActivity() {
	private lateinit var repository: LostAndFoundRepository
	private lateinit var sessionManager: SessionManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		repository = LostAndFoundRepository(AppDatabase.getInstance(this))
		enableEdgeToEdge()
		setContentView(R.layout.activity_admin_claim_review)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_claim_review_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		findViewById<MaterialToolbar>(R.id.admin_claim_review_toolbar).setNavigationOnClickListener { finish() }
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
			val adapter = AdminClaimCardAdapter(
				onApprove = { card -> reviewClaim(card.claimId, adminId, approve = true) },
				onReject = { card -> reviewClaim(card.claimId, adminId, approve = false) }
			)
			findViewById<RecyclerView>(R.id.admin_claim_review_list).apply {
				layoutManager = LinearLayoutManager(this@AdminClaimReviewActivity)
				this.adapter = adapter
			}
			combine(
				repository.observeAllClaims(),
				repository.observeUsers(),
				repository.observeFoundItems()
			) { claims, users, foundItems ->
				claims.mapNotNull { claim ->
					val applicant = users.find { it.id == claim.claimantUserId } ?: return@mapNotNull null
					val item = foundItems.find { it.id == claim.foundItemId } ?: return@mapNotNull null
					AdminClaimCard(
						claimId = claim.id,
						itemTitle = item.title,
						itemLocation = item.location,
						applicantName = applicant.username,
						applicantEmail = applicant.email,
						applicantStudentId = applicant.studentId,
						description = claim.description,
						status = claim.status,
						submittedAt = claim.submittedAt
					)
				}
			}.collect { cards ->
				adapter.submitList(cards)
				findViewById<android.widget.TextView>(R.id.admin_claim_review_empty).isVisible = cards.isEmpty()
			}
		}
	}

	private fun reviewClaim(claimId: Long, adminId: Long, approve: Boolean) {
		lifecycleScope.launch {
			val result = if (approve) {
				repository.approveClaim(claimId, adminId)
			} else {
				repository.rejectClaim(claimId, adminId)
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

	private fun denyAccess() {
		sessionManager.logout()
		Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show()
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}
}
