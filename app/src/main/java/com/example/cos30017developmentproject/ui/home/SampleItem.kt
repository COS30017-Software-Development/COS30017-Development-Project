package com.example.cos30017developmentproject.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.cos30017developmentproject.R

/** Presentation-only data; replace with repository records when item features are implemented. */
data class SampleItem(
    @param:StringRes @get:StringRes val title: Int,
    @param:StringRes @get:StringRes val description: Int,
    @param:DrawableRes @get:DrawableRes val image: Int,
    val isFound: Boolean
) {
    companion object {
        val examples = listOf(
            SampleItem(R.string.sample_backpack_title, R.string.sample_backpack_description, R.drawable.sample_backpack, true),
            SampleItem(R.string.sample_bottle_title, R.string.sample_bottle_description, R.drawable.sample_bottle, false),
            SampleItem(R.string.sample_headphones_title, R.string.sample_headphones_description, R.drawable.sample_headphones, true),
            SampleItem(R.string.sample_keys_title, R.string.sample_keys_description, R.drawable.sample_keys, false)
        )
    }
}
