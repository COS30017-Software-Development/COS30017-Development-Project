package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.example.cos30017developmentproject.data.remote.FirestoreClaimRepository
import com.example.cos30017developmentproject.ui.claims.ClaimCard
import com.example.cos30017developmentproject.ui.claims.ClaimCardAdapter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MyClaimsActivity : AppCompatActivity() {
	private lateinit var repository: FirestoreClaimRepository
	private lateinit var sessionManager: SessionManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		val userId = sessionManager.userId
		if (userId == null) {
			startActivity(Intent(this, AuthActivity::class.java))
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_my_claims)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.my_claims_root)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		repository = FirestoreClaimRepository()
		findViewById<MaterialToolbar>(R.id.my_claims_toolbar).setNavigationOnClickListener { finish() }
		val adapter = ClaimCardAdapter { card ->
			startActivity(Intent(this, ItemDetailsActivity::class.java).apply {
				putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, card.foundItemId.toString())
				putExtra(ItemDetailsActivity.EXTRA_ITEM_TYPE, ItemDetailsActivity.TYPE_FOUND)
			})
		}
		findViewById<RecyclerView>(R.id.my_claims_list).apply {
			layoutManager = LinearLayoutManager(this@MyClaimsActivity)
			this.adapter = adapter
		}
		lifecycleScope.launch {
			repository.observeMyClaims()
				.catch { error ->
					Log.e(TAG, "Unable to load current user's claims", error)
					Toast.makeText(this@MyClaimsActivity, R.string.claims_load_failed, Toast.LENGTH_SHORT).show()
					emit(emptyList())
				}
				.collect { claims ->
					val cards = claims.map { claim ->
						ClaimCard(
							foundItemId = claim.itemId,
							title = claim.itemTitle,
							description = claim.description,
							status = claim.status,
							submittedAt = claim.submittedAt
						)
					}
					adapter.submitList(cards)
					findViewById<android.widget.TextView>(R.id.my_claims_empty).isVisible = cards.isEmpty()
				}
		}
	}

	private companion object {
		const val TAG = "MyClaimsActivity"
	}
}
