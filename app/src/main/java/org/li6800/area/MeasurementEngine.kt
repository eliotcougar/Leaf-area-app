package org.li6800.area

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.*

enum class Backing { WHITE, BLUE }

data class MarkerDefinition(val id: Int, val x: Double, val y: Double, val side: Double) {
    fun corners() = listOf(Point(x - side / 2, y - side / 2), Point(x + side / 2, y - side / 2),
        Point(x + side / 2, y + side / 2), Point(x - side / 2, y + side / 2))
}

class MetricTemplate(context: Context, asset: String = "template-v2.json") {
    val json = context.assets.open(asset).bufferedReader().use { it.readText() }
    private val definition = JSONObject(json)
    val id: String = definition.getString("id")
    val radius = definition.getDouble("apertureRadiusMm")
    val markers: Map<Int, MarkerDefinition> = definition.getJSONArray("markers").let { array ->
        (0 until array.length()).associate { index ->
            val marker = array.getJSONObject(index)
            val center = marker.getJSONArray("centerMm")
            val id = marker.getInt("id")
            id to MarkerDefinition(id, center.getDouble(0), center.getDouble(1), marker.getDouble("sideMm"))
        }
    }
    init { require(abs(PI * radius * radius - 600.0) < 1e-6) }
}

/** A fixed metric raster; four-by-four subpixel coverage limits circle-edge quantization. */
object AreaRaster {
    const val SIZE = 360
    const val PIXELS_PER_MM = 10.0
    const val HALF_MM = 18.0
    val radius = sqrt(600.0 / PI)
    val weights = FloatArray(SIZE * SIZE) { index ->
        val x = index % SIZE
        val y = index / SIZE
        var inside = 0
        for (sy in 0..3) for (sx in 0..3) {
            val mx = (x + (sx + .5) / 4) / PIXELS_PER_MM - HALF_MM
            val my = (y + (sy + .5) / 4) / PIXELS_PER_MM - HALF_MM
            if (mx * mx + my * my <= radius * radius) inside++
        }
        inside / 16f
    }
    fun area(mask: BooleanArray): Double {
        require(mask.size == weights.size)
        var pixels = 0.0
        mask.forEachIndexed { i, selected -> if (selected) pixels += weights[i] }
        return (pixels / (PIXELS_PER_MM * PIXELS_PER_MM)).coerceIn(0.0, 600.0)
    }

}

data class Measurement(
    val source: Bitmap,
    val annotated: Bitmap,
    val rectified: Bitmap? = null,
    val selection: BooleanArray? = null,
    val overlay: Bitmap? = null,
    val markerCount: Int = 0,
    val template: MetricTemplate? = null,
    val backing: Backing = Backing.WHITE,
    val sensitivity: Float = .5f,
    val fitErrorMm: Double = 0.0,
    val homography: DoubleArray? = null,
    val issue: Int? = null,
    val analyzedAtUtc: String = java.time.Instant.now().toString(),
) {
    val valid get() = issue == null && selection != null && homography != null && template != null
    val areaMm2 get() = selection?.let(AreaRaster::area)
}

/** OpenCV Mats are scoped to a call; only owned Android bitmaps cross to the UI. */
class MeasurementEngine(context: Context) {
    val template = MetricTemplate(context)
    private val detector: ArucoDetector
    init {
        check(OpenCVLoader.initLocal()) { "OpenCV native library could not load" }
        val params = DetectorParameters().apply { set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX) }
        detector = ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50), params)
    }

    fun process(source: Bitmap, backing: Backing, sensitivity: Float): Measurement {
        val mats = mutableListOf<Mat>()
        fun <T : Mat> own(mat: T): T { mats += mat; return mat }
        val rgba = own(Mat())
        val gray = own(Mat())
        val ids = own(Mat())
        val detected = mutableListOf<Mat>()
        val rejected = mutableListOf<Mat>()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            detector.detectMarkers(gray, detected, ids, rejected)
            val recognized = mutableListOf<Pair<Int, List<Point>>>()
            for (i in 0 until ids.rows()) {
                val id = ids.get(i, 0)[0].toInt()
                if (id in template.markers) recognized += id to (0..3).map { k ->
                    val point = detected[i].get(0, k)
                    Point(point[0], point[1])
                }
            }
            val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotated)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(50, 228, 166); style = Paint.Style.STROKE; strokeWidth = max(2f, source.width / 450f)
            }
            recognized.forEach { (_, corners) ->
                val path = Path().apply {
                    moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
                    corners.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }; close()
                }
                canvas.drawPath(path, line)
            }
            fun invalid(issue: Int) = Measurement(source, annotated, markerCount = recognized.size, template = template, issue = issue)
            if (recognized.size < 4) return invalid(R.string.need_four)
            if (recognized.map { it.first }.distinct().size != recognized.size) return invalid(R.string.duplicate_markers)
            val definitions = recognized.map { template.markers.getValue(it.first) }
            if (!surroundsOpening(definitions, template.radius)) return invalid(R.string.spread_markers)
            val minimumEdge = recognized.minOf { (_, corners) ->
                (0..3).minOf { k -> hypot(corners[k].x - corners[(k + 1) % 4].x, corners[k].y - corners[(k + 1) % 4].y) }
            }
            if (minimumEdge < 24) return invalid(R.string.too_small)
            val imagePoints = own(MatOfPoint2f(*recognized.flatMap { it.second }.toTypedArray()))
            val worldPoints = own(MatOfPoint2f(*definitions.flatMap { it.corners() }.toTypedArray()))
            val inliers = own(Mat())
            val h = own(Calib3d.findHomography(imagePoints, worldPoints, Calib3d.RANSAC, .45, inliers, 2000, .995))
            if (h.empty() || Core.countNonZero(inliers) < recognized.size * 4 - 1) return invalid(R.string.fit_failed)
            val fitted = own(MatOfPoint2f())
            Core.perspectiveTransform(imagePoints, fitted, h)
            val expected = worldPoints.toArray()
            val squaredError = fitted.toArray().mapIndexed { i, p -> (p.x - expected[i].x).pow(2) + (p.y - expected[i].y).pow(2) }
            val fitError = sqrt(squaredError.average())
            if (!fitError.isFinite() || fitError > .35 || squaredError.max() > .9.pow(2)) return invalid(R.string.fit_failed)
            val inverse = own(h.inv())
            val circleWorld = own(MatOfPoint2f(*(0..63).map { angle ->
                val radians = angle * 2 * PI / 64
                Point(template.radius * cos(radians), template.radius * sin(radians))
            }.toTypedArray()))
            val circleImage = own(MatOfPoint2f())
            Core.perspectiveTransform(circleWorld, circleImage, inverse)
            val circle = circleImage.toArray()
            if (circle.any { !it.x.isFinite() || !it.y.isFinite() || it.x < 2 || it.y < 2 || it.x >= source.width - 2 || it.y >= source.height - 2 })
                return invalid(R.string.outside_frame)
            val projectedWidth = circle.maxOf { it.x } - circle.minOf { it.x }
            val projectedHeight = circle.maxOf { it.y } - circle.minOf { it.y }
            if (min(projectedWidth, projectedHeight) < 110) return invalid(R.string.too_small)
            val outline = Path().apply {
                moveTo(circle[0].x.toFloat(), circle[0].y.toFloat())
                circle.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }; close()
            }
            canvas.drawPath(outline, line)
            val laplacian = own(Mat())
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            val mean = own(MatOfDouble()); val deviation = own(MatOfDouble())
            Core.meanStdDev(laplacian, mean, deviation)
            if (deviation.toArray()[0].pow(2) < 18) return invalid(R.string.blurred)
            val metricToRaster = own(Mat.eye(3, 3, CvType.CV_64F))
            metricToRaster.put(0, 0, AreaRaster.PIXELS_PER_MM)
            metricToRaster.put(1, 1, AreaRaster.PIXELS_PER_MM)
            // OpenCV indexes pixel centers at integers; AreaRaster integrates each
            // pixel cell using (index + 0.5) / pixelsPerMm. Match those conventions.
            metricToRaster.put(0, 2, AreaRaster.SIZE / 2.0 - .5)
            metricToRaster.put(1, 2, AreaRaster.SIZE / 2.0 - .5)
            val transform = own(Mat()); val empty = own(Mat())
            Core.gemm(metricToRaster, h, 1.0, empty, 0.0, transform)
            val rectified = own(Mat())
            Imgproc.warpPerspective(rgba, rectified, transform, Size(AreaRaster.SIZE.toDouble(), AreaRaster.SIZE.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0, 255.0))
            val bitmap = Bitmap.createBitmap(AreaRaster.SIZE, AreaRaster.SIZE, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rectified, bitmap)
            val values = DoubleArray(9); h.get(0, 0, values)
            val base = Measurement(source, annotated, rectified = bitmap, markerCount = recognized.size, template = template, fitErrorMm = fitError, homography = values)
            return segment(base, backing, sensitivity)
        } finally {
            detected.forEach { it.release() }; rejected.forEach { it.release() }; mats.asReversed().forEach { it.release() }
        }
    }

    fun segment(base: Measurement, backing: Backing, sensitivity: Float): Measurement {
        val bitmap = requireNotNull(base.rectified)
        val pixels = IntArray(AreaRaster.SIZE * AreaRaster.SIZE)
        bitmap.getPixels(pixels, 0, AreaRaster.SIZE, 0, 0, AreaRaster.SIZE, AreaRaster.SIZE)
        val hsv = FloatArray(3)
        val colored = BooleanArray(pixels.size)
        val selected = BooleanArray(pixels.size) { i ->
            if (AreaRaster.weights[i] == 0f) false else {
                Color.colorToHSV(pixels[i], hsv)
                colored[i] = hsv[1] > (.30f - .23f * sensitivity)
                when (backing) {
                    Backing.WHITE -> hsv[1] > (.30f - .23f * sensitivity) || hsv[2] < (.55f + .25f * sensitivity)
                    Backing.BLUE -> !(hsv[0] in 190f..255f && hsv[1] > (.22f + .25f * sensitivity))
                }
            }
        }
        return withSelection(base.copy(backing = backing, sensitivity = sensitivity),
            RimShadowFilter.filter(selected, colored))
    }

    private fun withSelection(base: Measurement, selection: BooleanArray): Measurement {
        val rectified = requireNotNull(base.rectified)
        val pixels = IntArray(AreaRaster.SIZE * AreaRaster.SIZE)
        rectified.getPixels(pixels, 0, AreaRaster.SIZE, 0, 0, AreaRaster.SIZE, AreaRaster.SIZE)
        pixels.indices.forEach { i ->
            val color = pixels[i]
            pixels[i] = when {
                AreaRaster.weights[i] == 0f -> Color.rgb(235, 239, 233)
                selection[i] -> Color.rgb((Color.red(color) * .60 + 48 * .40).toInt(),
                    (Color.green(color) * .60 + 220 * .40).toInt(), (Color.blue(color) * .60 + 151 * .40).toInt())
                else -> color
            }
        }
        val overlay = Bitmap.createBitmap(AreaRaster.SIZE, AreaRaster.SIZE, Bitmap.Config.ARGB_8888)
        overlay.setPixels(pixels, 0, AreaRaster.SIZE, 0, 0, AreaRaster.SIZE, AreaRaster.SIZE)
        Canvas(overlay).drawCircle(AreaRaster.SIZE / 2f, AreaRaster.SIZE / 2f,
            (requireNotNull(base.template).radius * AreaRaster.PIXELS_PER_MM).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(23, 107, 82); strokeWidth = 1.5f; style = Paint.Style.STROKE
            })
        return base.copy(selection = selection, overlay = overlay)
    }

    private fun surroundsOpening(markers: List<MarkerDefinition>, radius: Double): Boolean {
        // Rear-center markers can replace obscured side markers, but a one-sided
        // cluster must not silently extrapolate a calibration across the opening.
        if (markers.none { it.y < -radius } || markers.none { it.y > radius } ||
            markers.none { it.x < -radius } || markers.none { it.x > 0 }) return false
        val sorted = markers.map { Point(it.x, it.y) }.sortedWith(compareBy({ it.x }, { it.y }))
        fun cross(a: Point, b: Point, c: Point) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        fun half(points: List<Point>): List<Point> {
            val result = mutableListOf<Point>()
            points.forEach { p ->
                while (result.size >= 2 && cross(result[result.lastIndex - 1], result.last(), p) <= 0) result.removeAt(result.lastIndex)
                result += p
            }
            return result.dropLast(1)
        }
        val hull = half(sorted) + half(sorted.reversed())
        val center = Point(0.0, 0.0)
        return hull.size >= 3 && hull.indices.all { cross(hull[it], hull[(it + 1) % hull.size], center) > 0 }
    }
}
