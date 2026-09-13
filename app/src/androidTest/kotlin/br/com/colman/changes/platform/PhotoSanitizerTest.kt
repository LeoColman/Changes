// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Critério 7.3.1: foto com tags GPS resulta em arquivo sem nenhuma tag GPS. */
@RunWith(AndroidJUnit4::class)
class PhotoSanitizerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sanitizer = PhotoSanitizer(context, Dispatchers.IO)

    private fun photoWithLocation(): File {
        val file = File.createTempFile("gps", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setLatLong(-23.5505, -46.6333)
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "760/1")
            setAttribute(ExifInterface.TAG_MAKE, "TestCamera")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:12 10:00:00")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertNotNull("fixture must carry a location", ExifInterface(file).latLong)
        return file
    }

    @Test
    fun sanitizedPhotoHasNoGpsTagAtAll() {
        val raw = photoWithLocation()
        val clean = sanitizer.sanitize(raw)
        val exif = ExifInterface(clean)
        assertNull(exif.latLong)
        GPS_TAGS.forEach { tag -> assertNull("GPS tag $tag survived", exif.getAttribute(tag)) }
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        raw.delete()
        clean.delete()
    }

    @Test
    fun orientationIsAppliedToThePixelsBeforeMetadataIsDropped() {
        val raw = photoWithLocation()
        val clean = sanitizer.sanitize(raw)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(clean.path, bounds)
        assertEquals(HEIGHT, bounds.outWidth)
        assertEquals(WIDTH, bounds.outHeight)
        assertTrue(clean.length() > 0)
        assertFalse(clean.path == raw.path)
        raw.delete()
        clean.delete()
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 32
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_VERSION_ID,
        )
    }
}
