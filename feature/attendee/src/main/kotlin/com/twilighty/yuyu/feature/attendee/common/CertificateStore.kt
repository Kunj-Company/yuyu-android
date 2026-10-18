package com.twilighty.yuyu.feature.attendee.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.twilighty.yuyu.core.common.dispatchers.Dispatcher
import com.twilighty.yuyu.core.common.dispatchers.YuyuDispatcher
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.di.BareClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads certificate JPEGs and saves them to Pictures/Yuyu or prepares them for sharing. */
interface CertificateStore {
    /** Saves the JPEG at [url] to the gallery (Pictures/Yuyu). */
    suspend fun saveToGallery(url: String, title: String): AppResult<Uri>

    /** Downloads to the app cache and returns a shareable content URI. */
    suspend fun prepareShare(url: String, title: String): AppResult<Uri>
}

@Singleton
class AndroidCertificateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:BareClient private val client: OkHttpClient,
    @param:Dispatcher(YuyuDispatcher.IO) private val io: CoroutineDispatcher,
) : CertificateStore {

    override suspend fun saveToGallery(url: String, title: String): AppResult<Uri> = withContext(io) {
        val bytes = when (val r = download(url)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return@withContext r
        }
        try {
            AppResult.Success(writeToMediaStore(bytes, fileName(title)))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Unknown("Couldn't save the certificate. Check your storage and try again."))
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.Forbidden("Allow Yuyu to save photos, then try again."))
        }
    }

    override suspend fun prepareShare(url: String, title: String): AppResult<Uri> = withContext(io) {
        val bytes = when (val r = download(url)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return@withContext r
        }
        try {
            val dir = File(context.cacheDir, "certificates").apply { mkdirs() }
            val file = File(dir, fileName(title))
            file.writeBytes(bytes)
            AppResult.Success(FileProvider.getUriForFile(context, "${context.packageName}.attendee.files", file))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Unknown("Couldn't prepare the certificate to share."))
        }
    }

    private fun download(url: String): AppResult<ByteArray> {
        if (!isSafeHttpUrl(url)) return AppResult.Failure(AppError.Unknown("That certificate link isn't valid."))
        return try {
            client.newCall(Request.Builder().url(url).header("Accept", "image/jpeg").build()).execute().use { response ->
                when {
                    response.isSuccessful -> AppResult.Success(response.body.bytes())
                    response.code == 404 -> AppResult.Failure(AppError.NotFound("This certificate is no longer available."))
                    response.code == 429 -> AppResult.Failure(AppError.RateLimited())
                    else -> AppResult.Failure(AppError.Server())
                }
            }
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network())
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Unknown("That certificate link isn't valid."))
        }
    }

    private fun writeToMediaStore(bytes: ByteArray, name: String): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Yuyu")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("open failed")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Yuyu").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: Uri.fromFile(file)
    }

    private fun fileName(title: String): String {
        val base = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "certificate" }
        return "yuyu-certificate-$base.jpg"
    }
}
