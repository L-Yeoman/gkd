package li.songe.gkd.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore.Images.ImageColumns
import android.provider.MediaStore.Images.Media
import android.text.TextUtils
import android.widget.Toast
import com.blankj.utilcode.util.ToastUtils
import li.songe.gkd.App
import li.songe.gkd.BuildConfig
import li.songe.gkd.app
import java.io.File

private val filesDir by lazy {
    app.getExternalFilesDir(null) ?: app.filesDir
}
val dbFolder by lazy { filesDir.resolve("db") }
val subsFolder by lazy { filesDir.resolve("subscription") }
val snapshotFolder by lazy { filesDir.resolve("snapshot") }

private val cacheDir by lazy {
    app.externalCacheDir ?: app.cacheDir
}
val snapshotZipDir by lazy { cacheDir.resolve("snapshotZip") }
val newVersionApkDir by lazy { cacheDir.resolve("newVersionApk") }
val logZipDir by lazy { cacheDir.resolve("logZip") }
val imageCacheDir by lazy { cacheDir.resolve("imageCache") }
val shareDir by lazy { cacheDir.resolve("share") }


fun initFolder() {
    listOf(
        dbFolder,
        subsFolder,
        snapshotFolder,
        snapshotZipDir,
        newVersionApkDir,
        logZipDir,
        imageCacheDir,
        shareDir
    ).forEach { f ->
        if (!f.exists()) {
            f.mkdirs()
        }
    }
}

fun insertMediaPic(context: Context, file: File?): Boolean {
    if (file==null) return false
    // val file = File(filePath)
    //判断android Q  (10 ) 版本
    return if ( Build.VERSION.SDK_INT >= 29) {
        if (file == null || !file.exists()) {
            false
        } else {
            try {
                Media.insertImage(
                    context.getContentResolver(),
                    file.getAbsolutePath(),
                    file.getName(),
                    null
                )
                ToastUtils.showShort("已保存相册")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    } else {
        val values = ContentValues()
        values.put(Media.DATA, file.getAbsolutePath())
        values.put(Media.MIME_TYPE, "image/jpeg")
        values.put(ImageColumns.DATE_TAKEN, System.currentTimeMillis().toString() + "")
        context.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values)
        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://" + file.getAbsolutePath())
            )
        )
        ToastUtils.showShort("已保存相册")
        true
    }
}