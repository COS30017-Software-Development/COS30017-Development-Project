package com.example.cos30017developmentproject.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cos30017developmentproject.auth.PasswordHasher

@Database(
	entities = [UserEntity::class, LostItemEntity::class, FoundItemEntity::class, ClaimEntity::class],
	version = 6,
	exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun userDao(): UserDao
	abstract fun lostItemDao(): LostItemDao
	abstract fun foundItemDao(): FoundItemDao
	abstract fun claimDao(): ClaimDao

	companion object {
		private const val DATABASE_NAME = "campus_lost_and_found.db"
		val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE users ADD COLUMN password_hash BLOB")
				db.execSQL("ALTER TABLE users ADD COLUMN password_salt BLOB")
			}
		}
		val MIGRATION_2_3 = object : Migration(2, 3) {
			override fun migrate(db: SupportSQLiteDatabase) {
				setSeedUserPassword(db, "user1@swinburne.edu.my", "User1@Pass123")
				setSeedUserPassword(db, "user2@swinburne.edu.my", "User2@Pass123")
			}
		}
		val MIGRATION_3_4 = object : Migration(3, 4) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE users ADD COLUMN avatar_path TEXT")
			}
		}
		val MIGRATION_4_5 = object : Migration(4, 5) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'STUDENT'")
				insertAdminUser(db)
			}
		}
		val MIGRATION_5_6 = object : Migration(5, 6) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE found_items ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
				db.execSQL("""
					CREATE TABLE IF NOT EXISTS claims (
						id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						found_item_id INTEGER NOT NULL,
						claimant_user_id INTEGER NOT NULL,
						description TEXT NOT NULL,
						status TEXT NOT NULL,
						submitted_at INTEGER NOT NULL,
						FOREIGN KEY(found_item_id) REFERENCES found_items(id) ON DELETE CASCADE,
						FOREIGN KEY(claimant_user_id) REFERENCES users(id) ON DELETE CASCADE
					)
				""")
				db.execSQL("CREATE INDEX IF NOT EXISTS index_claims_found_item_id ON claims(found_item_id)")
				db.execSQL("CREATE INDEX IF NOT EXISTS index_claims_claimant_user_id ON claims(claimant_user_id)")
				db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_claims_claimant_user_id_found_item_id ON claims(claimant_user_id, found_item_id)")
			}
		}

		@Volatile
		private var instance: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
			instance ?: Room.databaseBuilder(
				context.applicationContext,
				AppDatabase::class.java,
				DATABASE_NAME
			).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).addCallback(SeedDataCallback()).build().also { instance = it }
		}
	}
}

private class SeedDataCallback : RoomDatabase.Callback() {
	override fun onCreate(db: SupportSQLiteDatabase) {
		super.onCreate(db)
		insertSeedUser(db, 1, "user1", "user1@swinburne.edu.my", "100000001", "User1@Pass123")
		insertSeedUser(db, 2, "user2", "user2@swinburne.edu.my", "100000002", "User2@Pass123")
		insertAdminUser(db)

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

private fun insertSeedUser(
	db: SupportSQLiteDatabase,
	id: Long,
	username: String,
	email: String,
	studentId: String,
	password: String
) {
	val storedPassword = PasswordHasher().hash(password.toCharArray())
	db.compileStatement(
		"INSERT INTO users (id, username, email, student_id, password_hash, password_salt, role) VALUES (?, ?, ?, ?, ?, ?, ?)"
	).apply {
		bindLong(1, id)
		bindString(2, username)
		bindString(3, email)
		bindString(4, studentId)
		bindBlob(5, storedPassword.hash)
		bindBlob(6, storedPassword.salt)
		bindString(7, UserRole.STUDENT)
		executeInsert()
	}
}

private fun setSeedUserPassword(db: SupportSQLiteDatabase, email: String, password: String) {
	val storedPassword = PasswordHasher().hash(password.toCharArray())
	db.compileStatement(
		"UPDATE users SET password_hash = ?, password_salt = ? WHERE email = ? AND password_hash IS NULL AND password_salt IS NULL"
	).apply {
		bindBlob(1, storedPassword.hash)
		bindBlob(2, storedPassword.salt)
		bindString(3, email)
		executeUpdateDelete()
	}
}

private fun insertAdminUser(db: SupportSQLiteDatabase) {
	val email = "admin@campus.local"
	val existing = db.compileStatement(
		"SELECT COUNT(*) FROM users WHERE email = ?"
	).apply { bindString(1, email) }.simpleQueryForLong()
	if (existing > 0) {
		db.execSQL("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@campus.local'")
		return
	}
	val storedPassword = PasswordHasher().hash("Admin1@Pass".toCharArray())
	db.compileStatement(
		"INSERT INTO users (id, username, email, student_id, password_hash, password_salt, role) VALUES (?, ?, ?, ?, ?, ?, ?)"
	).apply {
		bindLong(1, 3)
		bindString(2, "Administrator")
		bindString(3, email)
		bindString(4, "000000000")
		bindBlob(5, storedPassword.hash)
		bindBlob(6, storedPassword.salt)
		bindString(7, UserRole.ADMIN)
		executeInsert()
	}
}
