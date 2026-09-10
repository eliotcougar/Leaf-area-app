package org.li6800.area

import org.junit.Assert.*
import org.junit.Test

class AreaRasterTest {
    @Test fun analyticAreasAndClipping() {
        assertEquals(0.0, AreaRaster.area(BooleanArray(AreaRaster.SIZE * AreaRaster.SIZE)), 0.0)
        val full = BooleanArray(AreaRaster.SIZE * AreaRaster.SIZE) { true }
        assertEquals(600.0, AreaRaster.area(full), .05)
        val half = BooleanArray(full.size) { it % AreaRaster.SIZE < AreaRaster.SIZE / 2 }
        assertEquals(300.0, AreaRaster.area(half), .05)
        val outside = BooleanArray(full.size) { AreaRaster.weights[it] == 0f }
        assertEquals(0.0, AreaRaster.area(outside), 0.0)
    }
    @Test fun CentralHoleIsExcluded() {
        val withHole = BooleanArray(AreaRaster.SIZE * AreaRaster.SIZE) { index ->
            val x = (index % AreaRaster.SIZE + .5) / AreaRaster.PIXELS_PER_MM - AreaRaster.HALF_MM
            val y = (index / AreaRaster.SIZE + .5) / AreaRaster.PIXELS_PER_MM - AreaRaster.HALF_MM
            x * x + y * y >= 25.0
        }
        assertEquals(600.0 - kotlin.math.PI * 25.0, AreaRaster.area(withHole), .3)
    }
}
