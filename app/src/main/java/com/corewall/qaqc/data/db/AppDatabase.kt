package com.corewall.qaqc.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ElementNameEntity::class,
        InspectionEntity::class,
        CommentEntity::class,
        RangeEditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun elementNameDao(): ElementNameDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun commentDao(): CommentDao
    abstract fun rangeEditDao(): RangeEditDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "corewall.db"
                ).build().also { instance = it }
            }
    }
}
