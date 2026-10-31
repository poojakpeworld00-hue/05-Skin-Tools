package com.fffemotes.skintools.launcher.contracts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fffemotes.skintools.launcher.entities.GadgetHub

@Dao
interface appletHomebasesDao {
    @Query("SELECT * FROM apps")
    fun pullAppletHomebases(): List<GadgetHub>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(appLaunchers: List<GadgetHub>)

    @Query("DELETE FROM apps WHERE package_name = :packageName")
    fun eraseApplet(packageName: String)

    @Query("DELETE FROM apps WHERE id = :id")
    fun eraseByKey(id: Long)
}
