package org.li6800.area

/** Reject detached, low-color components confined to the outer 1.5 mm of the aperture.
 * Colored tissue and components extending inward remain intact; no circle erosion
 * or largest-component shortcut is used, so leaf tips and separate fragments survive.
 */
object RimShadowFilter {
    const val BAND_MM = 1.5
    private val interior = BooleanArray(AreaRaster.SIZE * AreaRaster.SIZE) { i ->
        val x = (i % AreaRaster.SIZE + .5) / AreaRaster.PIXELS_PER_MM - AreaRaster.HALF_MM
        val y = (i / AreaRaster.SIZE + .5) / AreaRaster.PIXELS_PER_MM - AreaRaster.HALF_MM
        x * x + y * y <= (AreaRaster.radius - BAND_MM) * (AreaRaster.radius - BAND_MM)
    }

    fun filter(candidate: BooleanArray, colored: BooleanArray): BooleanArray {
        require(candidate.size == interior.size && colored.size == candidate.size)
        val accepted = BooleanArray(candidate.size)
        val visited = BooleanArray(candidate.size)
        val queue = IntArray(candidate.size)
        val width = AreaRaster.SIZE
        for (start in candidate.indices) {
            if (!candidate[start] || visited[start]) continue
            var count = 1
            var head = 0
            var hasTissue = false
            queue[0] = start
            visited[start] = true
            while (head < count) {
                val i = queue[head++]
                if (interior[i] || colored[i]) hasTissue = true
                val x = i % width
                val y = i / width
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until width) continue
                    val next = ny * width + nx
                    if (candidate[next] && !visited[next]) {
                        visited[next] = true
                        queue[count++] = next
                    }
                }
            }
            if (hasTissue) for (j in 0 until count) accepted[queue[j]] = true
        }
        return accepted
    }
}
