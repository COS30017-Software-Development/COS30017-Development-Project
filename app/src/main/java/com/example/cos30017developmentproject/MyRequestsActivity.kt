package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
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
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.ClaimStatus
import com.example.cos30017developmentproject.data.remote.FirestoreClaim
import com.example.cos30017developmentproject.data.remote.FirestoreClaimRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.data.remote.FirestoreNotificationRepository
import com.example.cos30017developmentproject.ui.claims.ClaimCard
import com.example.cos30017developmentproject.ui.claims.ClaimCardAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MyRequestsActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreClaimRepository
	private lateinit var adapter: ClaimCardAdapter
	private var direction = RequestDirection.SENT
	private var filter = RequestFilter.ALL
	private var sentRequests: List<FirestoreClaim> = emptyList()
	private var receivedRequests: List<FirestoreClaim> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (SessionManager(this).userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_my_requests)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.my_requests_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = FirestoreClaimRepository()
		intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.let { notificationId ->
			lifecycleScope.launch {
				try {
					FirestoreNotificationRepository().markAsRead(notificationId)
				} catch (error: Exception) {
					Log.e(TAG, "Unable to mark opened notification as read", error)
				}
			}
		}
		direction = if (intent.getStringExtra(EXTRA_DIRECTION) == DIRECTION_RECEIVED) {
			RequestDirection.RECEIVED
		} else {
			RequestDirection.SENT
		}
		findViewById<MaterialToolbar>(R.id.my_requests_toolbar).setNavigationOnClickListener {
			backToItems()
		}
		findViewById<RecyclerView>(R.id.my_requests_list).apply {
			layoutManager = LinearLayoutManager(this@MyRequestsActivity)
			val requestAdapter = ClaimCardAdapter(::openItemDetails, ::acceptRequest)
			adapter = requestAdapter
			this@MyRequestsActivity.adapter = requestAdapter
		}
		findViewById<MaterialButton>(R.id.my_requests_back_to_items).setOnClickListener { backToItems() }
		findViewById<MaterialButton>(R.id.my_requests_sent).setOnClickListener {
			setDirection(RequestDirection.SENT)
		}
		findViewById<MaterialButton>(R.id.my_requests_received).setOnClickListener {
			setDirection(RequestDirection.RECEIVED)
		}
		findViewById<MaterialButton>(R.id.my_requests_filter_all).setOnClickListener {
			setFilter(RequestFilter.ALL)
		}
		findViewById<MaterialButton>(R.id.my_requests_filter_claim).setOnClickListener {
			setFilter(RequestFilter.CLAIM)
		}
		findViewById<MaterialButton>(R.id.my_requests_filter_found).setOnClickListener {
			setFilter(RequestFilter.FOUND)
		}
		updateFilters()
		observeRequests()
	}

	private fun observeRequests() {
		lifecycleScope.launch {
			combine(repository.observeMyClaims(), repository.observeIncomingRequests()) { sent, received ->
				sent to received
			}.catch { error ->
				Log.e(TAG, "Unable to load requests", error)
				Toast.makeText(this@MyRequestsActivity, R.string.claims_load_failed, Toast.LENGTH_SHORT).show()
				emit(emptyList<FirestoreClaim>() to emptyList())
			}.collect { (sent, received) ->
				sentRequests = sent
				receivedRequests = received
				applyFilters()
			}
		}
	}

	private fun setDirection(next: RequestDirection) {
		direction = next
		updateFilters()
		applyFilters()
	}

	private fun setFilter(next: RequestFilter) {
		filter = next
		updateFilters()
		applyFilters()
	}

	private fun updateFilters() {
		bindFilterButton(R.id.my_requests_sent, direction == RequestDirection.SENT)
		bindFilterButton(R.id.my_requests_received, direction == RequestDirection.RECEIVED)
		bindFilterButton(R.id.my_requests_filter_all, filter == RequestFilter.ALL)
		bindFilterButton(R.id.my_requests_filter_claim, filter == RequestFilter.CLAIM)
		bindFilterButton(R.id.my_requests_filter_found, filter == RequestFilter.FOUND)
	}

	private fun bindFilterButton(id: Int, selected: Boolean) {
		val button = findViewById<MaterialButton>(id)
		button.backgroundTintList = ContextCompat.getColorStateList(
			this,
			if (selected) R.color.brand_red_accessible else R.color.white
		)
		button.setTextColor(ContextCompat.getColor(this, if (selected) R.color.white else R.color.brand_red_accessible))
		button.strokeColor = ContextCompat.getColorStateList(this, R.color.brand_red_accessible)
		button.strokeWidth = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
	}

	private fun applyFilters() {
		val incoming = direction == RequestDirection.RECEIVED
		val requests = if (incoming) receivedRequests else sentRequests
		val cards = requests
			.filter { request ->
				when (filter) {
					RequestFilter.ALL -> true
					RequestFilter.CLAIM -> request.itemType == FirestoreItemRepository.TYPE_FOUND
					RequestFilter.FOUND -> request.itemType == FirestoreItemRepository.TYPE_LOST
				}
			}
			.map { request ->
				ClaimCard(
					claimId = request.claimId,
					itemId = request.itemId,
					itemType = request.itemType,
					title = request.itemTitle,
					itemLocation = request.itemLocation,
					description = request.description,
					status = request.status,
					submittedAt = request.submittedAt,
					senderName = if (incoming) request.studentName else "",
					isIncoming = incoming
				)
			}
		adapter.submitList(cards)
		findViewById<TextView>(R.id.my_requests_empty).isVisible = cards.isEmpty()
	}

	private fun acceptRequest(card: ClaimCard) {
		lifecycleScope.launch {
			val result = repository.acceptRequest(card.claimId)
			val message = when (result) {
				ClaimReviewResult.SUCCESS -> R.string.request_accepted
				ClaimReviewResult.ACCESS_DENIED -> R.string.request_owner_only
				ClaimReviewResult.ALREADY_PROCESSED -> R.string.claim_review_already_processed
				ClaimReviewResult.ITEM_CLOSED -> R.string.claim_item_closed
				ClaimReviewResult.NOT_FOUND,
				ClaimReviewResult.ITEM_NOT_FOUND,
				ClaimReviewResult.FAILED -> R.string.claim_review_failed
			}
			Toast.makeText(this@MyRequestsActivity, message, Toast.LENGTH_SHORT).show()
		}
	}

	private fun openItemDetails(card: ClaimCard) {
		startActivity(Intent(this, ItemDetailsActivity::class.java).apply {
			putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, card.itemId)
			putExtra(
				ItemDetailsActivity.EXTRA_ITEM_TYPE,
				if (card.itemType == FirestoreItemRepository.TYPE_FOUND) {
					ItemDetailsActivity.TYPE_FOUND
				} else {
					ItemDetailsActivity.TYPE_LOST
				}
			)
		})
	}

	private fun backToItems() {
		startActivity(
			Intent(this, MainActivity::class.java)
				.putExtra(MainActivity.EXTRA_SELECTED_NAVIGATION, R.id.nav_items)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		)
		finish()
	}

	private enum class RequestDirection { SENT, RECEIVED }
	private enum class RequestFilter { ALL, CLAIM, FOUND }

	companion object {
		const val EXTRA_DIRECTION = "request_direction"
		const val EXTRA_NOTIFICATION_ID = "notification_id"
		const val DIRECTION_SENT = "sent"
		const val DIRECTION_RECEIVED = "received"
		private const val TAG = "MyRequestsActivity"
	}
}
