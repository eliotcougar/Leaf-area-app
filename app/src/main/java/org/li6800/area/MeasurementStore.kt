package org.li6800.area

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SavedMeasurement(val file: File, val sampleId: String, val areaMm2: Double, val time: String, val synthetic: Boolean)

class MeasurementStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "measurements").apply { mkdirs() }

    fun save(measurement: Measurement, sampleId: String, source: String, synthetic: Boolean,
             backing: Backing, sensitivity: Float, heldWhenSaved: Boolean = false): SavedMeasurement {
        check(measurement.valid)
        val template = requireNotNull(measurement.template)
        val now = Instant.now().toString()
        val area = requireNotNull(measurement.areaMm2)
        val name = "leaf-area-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.zip"
        val output = File(directory, name)
        val pending = File(directory, "$name.partial")
        val details = JSONObject().apply {
            put("schemaVersion", 1); put("appVersion", BuildConfig.VERSION_NAME); put("sampleId", sampleId)
            put("heldWhenSaved", heldWhenSaved)
            put("rimShadowFilter", "detached low-color components confined to outer 1.5 mm excluded")
            put("savedAtUtc", now); put("analyzedAtUtc", measurement.analyzedAtUtc)
            put("source", source); put("synthetic", synthetic)
            put("processedImageWidth", measurement.source.width); put("processedImageHeight", measurement.source.height)
            put("areaMm2", area); put("areaCm2", area / 100); put("coveragePercent", area / 6)
            put("apertureAreaMm2", 600); put("templateId", template.id)
            put("markerCount", measurement.markerCount); put("fitErrorMm", measurement.fitErrorMm)
            put("imageToMmHomography", JSONArray(requireNotNull(measurement.homography).toList()))
            put("backing", backing.name); put("sensitivity", sensitivity.toDouble())
            put("rectifiedPixelsPerMm", AreaRaster.PIXELS_PER_MM)
            put("rectifiedOriginMm", JSONArray(listOf(-AreaRaster.HALF_MM, -AreaRaster.HALF_MM)))
            put("areaMethod", "circle-clipped projected tissue area, 4x4 subpixel circle coverage")
            put("accuracyStatus", "first iteration; physical measurement accuracy not established")
        }
        try {
            ZipOutputStream(pending.outputStream().buffered()).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
                fun bitmap(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat) {
                    val bytes = ByteArrayOutputStream()
                    check(bitmap.compress(format, 95, bytes))
                    entry(name, bytes.toByteArray())
                }
                entry("measurement.json", details.toString(2).toByteArray())
                // User-controlled cells are quoted and neutralized against spreadsheet formulas.
                fun cell(value: String): String {
                    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
                    return "\"${safe.replace("\"", "\"\"")}\""
                }
                val csv = "sample_id,saved_at_utc,area_mm2,area_cm2,coverage_percent,template,synthetic\r\n" +
                    "${cell(sampleId)},${cell(now)},$area,${area / 100},${area / 6},${template.id},$synthetic\r\n"
                entry("measurement.csv", csv.toByteArray())
                entry("template.json", template.json.toByteArray())
                bitmap("source.jpg", measurement.source, Bitmap.CompressFormat.JPEG)
                bitmap("rectified.png", requireNotNull(measurement.rectified), Bitmap.CompressFormat.PNG)
                bitmap("overlay.png", requireNotNull(measurement.overlay), Bitmap.CompressFormat.PNG)
                val selection = requireNotNull(measurement.selection)
                val pixels = IntArray(selection.size) { i ->
                    if (selection[i] && AreaRaster.weights[i] > 0f) Color.WHITE else Color.BLACK
                }
                bitmap("tissue-mask.png", Bitmap.createBitmap(pixels, AreaRaster.SIZE, AreaRaster.SIZE, Bitmap.Config.ARGB_8888), Bitmap.CompressFormat.PNG)
            }
            check(pending.renameTo(output)) { "Could not finalize measurement" }
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
        return SavedMeasurement(output, sampleId, area, now, synthetic)
    }

    fun list(): List<SavedMeasurement> = directory.listFiles().orEmpty().filter { it.extension == "zip" }
        .sortedByDescending { it.name }.mapNotNull { file ->
            runCatching {
                ZipFile(file).use { zip ->
                    val details = JSONObject(zip.getInputStream(zip.getEntry("measurement.json")).bufferedReader().use { it.readText() })
                    SavedMeasurement(file, details.getString("sampleId"), details.getDouble("areaMm2"),
                        details.optString("savedAtUtc", details.optString("capturedAtUtc")), details.optBoolean("synthetic"))
                }
            }.getOrNull()
        }
}
