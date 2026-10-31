package com.fffemotes.skintools.launcher.contracts

import androidx.room.*
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum

@Dao
interface atriumViewerGridRecordsDao {
    @Query("SELECT * FROM home_screen_grid_items")
    fun pullAllCells(): List<LobbyLookerMatrixDatum>

    @Query("SELECT * FROM home_screen_grid_items WHERE parent_id = :folderId")
    fun pullBundleCells(folderId: Long): List<LobbyLookerMatrixDatum>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: LobbyLookerMatrixDatum): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<LobbyLookerMatrixDatum>)

    @Query("UPDATE home_screen_grid_items SET title = :title WHERE package_name = :packageName")
    fun reloadAppletTitle(title: String, packageName: String)

    @Query("UPDATE home_screen_grid_items SET title = :title WHERE id = :id")
    fun reloadCellTitle(title: String, id: Long): Int

    @Query("UPDATE home_screen_grid_items SET `left` = :left, `top` = :top, `right` = :right, `bottom` = :bottom, `page` = :page, `docked` = :docked , `parent_id` = :parentId WHERE id = :id")
    fun reloadCellPoint(left: Int, top: Int, right: Int, bottom: Int, page: Int, docked: Boolean, parentId: Long?, id: Long)

    @Query("UPDATE home_screen_grid_items SET widget_id = :widgetId WHERE id = :id")
    fun reloadWidgetKey(widgetId: Int, id: Long): Int

    @Query("DELETE FROM home_screen_grid_items WHERE id = :id")
    fun eraseCellByKey(id: Long)

    @Query("DELETE FROM home_screen_grid_items WHERE parent_id = :id")
    fun eraseCellsWithParentKey(id: Long)

    @Transaction
    fun eraseByKey(id: Long) {
        eraseCellByKey(id)
        eraseCellsWithParentKey(id)
    }

    @Query("DELETE FROM home_screen_grid_items WHERE class_name = :className")
    fun eraseCellsByClassName(className: String)

    @Query("DELETE FROM home_screen_grid_items WHERE package_name = :packageName")
    fun eraseCellByPkgName(packageName: String)

    @Query("DELETE FROM home_screen_grid_items WHERE parent_id IN (SELECT id FROM home_screen_grid_items WHERE package_name = :packageName)")
    fun eraseCellsByParentPkgName(packageName: String)

    @Query("UPDATE home_screen_grid_items SET `left` = `left` + :shiftBy WHERE parent_id == :folderId AND `left` > :shiftFrom AND id != :excludingId")
    fun shiftBundleCells(folderId: Long, shiftFrom: Int, shiftBy: Int, excludingId: Long? = null)

    @Query("UPDATE home_screen_grid_items SET `page` = `page` + :shiftBy WHERE `page` > :shiftFrom")
    fun shiftLeaf(shiftFrom: Int, shiftBy: Int)

    @Transaction
    fun eraseByPkgName(packageName: String) {
        eraseCellByPkgName(packageName)
        eraseCellsByParentPkgName(packageName)
    }
}
