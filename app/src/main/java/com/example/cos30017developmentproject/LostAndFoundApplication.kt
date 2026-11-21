package com.example.cos30017developmentproject

import android.app.Application
import com.example.cos30017developmentproject.data.local.AppDatabase

class LostAndFoundApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		AppDatabase.getInstance(this).openHelper.writableDatabase
	}
}
