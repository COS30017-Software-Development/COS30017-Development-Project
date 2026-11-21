package com.example.cos30017developmentproject.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
	entities = [UserEntity::class, LostItemEntity::class, FoundItemEntity::class],
	version = 2,
	exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun userDao(): UserDao
	abstract fun lostItemDao(): LostItemDao
	abstract fun foundItemDao(): FoundItemDao

	companion object {
		private const val DATABASE_NAME = "campus_lost_and_found.db"
		val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE users ADD COLUMN password_hash BLOB")
				db.execSQL("ALTER TABLE users ADD COLUMN password_salt BLOB")
			}
		}

		@Volatile
		private var instance: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
			instance ?: Room.databaseBuilder(
				context.applicationContext,
				AppDatabase::class.java,
				DATABASE_NAME
			).addMigrations(MIGRATION_1_2).addCallback(SeedDataCallback()).build().also { instance = it }
		}
	}
}

private class SeedDataCallback : RoomDatabase.Callback() {
	override fun onCreate(db: SupportSQLiteDatabase) {
		super.onCreate(db)
		db.execSQL("INSERT INTO users (id, username, email, student_id) VALUES (1, 'user1', 'user1@swinburne.edu.my', '100000001')")
		db.execSQL("INSERT INTO users (id, username, email, student_id) VALUES (2, 'user2', 'user2@swinburne.edu.my', '100000002')")

		// These records preserve the existing SampleItem titles, descriptions, types, and drawable names.
		db.execSQL("INSERT INTO lost_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Water bottle', 'Lost in a lecture room. A reusable bottle with a black lid.', 'Lecture room', 'Bottle', 'sample_bottle', 1)")
		db.execSQL("INSERT INTO lost_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Set of keys', 'Lost on campus. Two keys attached to a red key ring.', 'Campus', 'Keys', 'sample_keys', 2)")
		db.execSQL("INSERT INTO lost_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Wireless headphones', 'Lost near the library. Black wireless headphones with padded ear cups.', 'Library', 'Headphones', 'sample_headphones', 1)")
		db.execSQL("INSERT INTO lost_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Study backpack', 'Lost after an afternoon class. A black bag containing study notes.', 'Classroom', 'Backpack', 'sample_backpack', 2)")
		db.execSQL("INSERT INTO lost_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Locker keys', 'Lost near the study area. A small set of keys with a red ring.', 'Study area', 'Keys', 'sample_keys', 1)")

		db.execSQL("INSERT INTO found_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Black backpack', 'Found near the library entrance. Black backpack with a front zip pocket.', 'Library entrance', 'Backpack', 'sample_backpack', 2)")
		db.execSQL("INSERT INTO found_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Headphones', 'Found in a campus study area. Black over-ear headphones.', 'Study area', 'Headphones', 'sample_headphones', 1)")
		db.execSQL("INSERT INTO found_items (title, description, location, category, image_name, posted_by_user_id) VALUES ('Sports bottle', 'Found near the sports area. A reusable bottle with a screw-top lid.', 'Sports area', 'Bottle', 'sample_bottle', 2)")
	}
}
