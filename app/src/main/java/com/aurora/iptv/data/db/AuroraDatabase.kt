package com.aurora.iptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromPlaylistType(value: PlaylistType): String = value.name

    @TypeConverter
    fun toPlaylistType(value: String): PlaylistType =
        runCatching { PlaylistType.valueOf(value) }.getOrDefault(PlaylistType.M3U)

    @TypeConverter
    fun fromStreamKind(value: StreamKind): String = value.name

    @TypeConverter
    fun toStreamKind(value: String): StreamKind =
        runCatching { StreamKind.valueOf(value) }.getOrDefault(StreamKind.LIVE)
}

@Database(
    entities = [
        PlaylistEntity::class,
        StreamEntity::class,
        EpgEntity::class,
        ProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun streamDao(): StreamDao
    abstract fun epgDao(): EpgDao
    abstract fun progressDao(): ProgressDao

    companion object {
        fun build(context: Context): AuroraDatabase =
            Room.databaseBuilder(context, AuroraDatabase::class.java, "aurora.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
