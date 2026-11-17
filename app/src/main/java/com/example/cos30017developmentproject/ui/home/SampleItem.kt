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
            SampleItem(R.string.sample_keys_title, R.string.sample_keys_description, R.drawable.sample_keys, false),
            SampleItem(R.string.sample_laptop_backpack_title, R.string.sample_laptop_backpack_description, R.drawable.sample_backpack, true),
            SampleItem(R.string.sample_sports_bottle_title, R.string.sample_sports_bottle_description, R.drawable.sample_bottle, true),
            SampleItem(R.string.sample_wireless_headphones_title, R.string.sample_wireless_headphones_description, R.drawable.sample_headphones, false),
            SampleItem(R.string.sample_house_keys_title, R.string.sample_house_keys_description, R.drawable.sample_keys, true),
            SampleItem(R.string.sample_study_backpack_title, R.string.sample_study_backpack_description, R.drawable.sample_backpack, false),
            SampleItem(R.string.sample_reusable_bottle_title, R.string.sample_reusable_bottle_description, R.drawable.sample_bottle, true),
            SampleItem(R.string.sample_study_headphones_title, R.string.sample_study_headphones_description, R.drawable.sample_headphones, true),
            SampleItem(R.string.sample_locker_keys_title, R.string.sample_locker_keys_description, R.drawable.sample_keys, false)
        )
    }
}
