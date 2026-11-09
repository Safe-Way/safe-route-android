package com.deungbul.anshimgil.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkerPlaceTest {
    /** The map's center sits halfway down what the padding leaves. */
    private fun centerY(padding: Int, height: Int) = padding + (height - padding) / 2.0

    @Test
    fun walkerSitsLowBelowTheCard() {
        val padding = walkerTopPaddingPx(coveredPx = 400, mapHeightPx = 1600)
        // 70% of the way down the 1200px below the card: 400 + 840.
        assertEquals(1240.0, centerY(padding, 1600), 1.0)
    }

    @Test
    fun notMeasuredYetKeepsTheCardPadding() {
        assertEquals(300, walkerTopPaddingPx(coveredPx = 300, mapHeightPx = 0))
    }
}
