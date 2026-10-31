package com.fffemotes.skintools.master.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.fffemotes.skintools.master.domain.repository.MediaRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes images to Pictures/SkinEmotes: MediaStore on Android 10+, a plain file plus a media scan before that.
 * Below Android 10 the caller must already hold WRITE_EXTERNAL_STORAGE.
 */
class MediaRepositoryImpl(private val context: Context) : MediaRepository {

    override fun saveToGallery(image: Int, title: String): Boolean = runCatching {
        val bitmap = ContextCompat.getDrawable(context, image)!!.toBitmap()
        val fileName = title.replace(Regex("[^A-Za-z0-9]+"), "_") + ".png"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.write(it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.write(it) }
        }
    }.isSuccess

    private fun Bitmap.write(out: OutputStream) = check(compress(Bitmap.CompressFormat.PNG, 100, out))

    private companion object {
        const val ALBUM = "SkinEmotes"
    }
}
