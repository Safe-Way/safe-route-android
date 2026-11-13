package com.deungbul.anshimgil.ui.guide

import com.deungbul.anshimgil.data.Guidance
import com.deungbul.anshimgil.data.LatLon

/*
 * 화면이 꺼져도 길안내 (11/13, 팀 요청 3번): the guidance service follows the walker with the same reading as the
 * guidance screen (Guidance, instructionText, distanceBucket), so the notification and the voice say the same
 * things while the screen is off.
 */

/** What to show and say after one position. [speak] is null when nothing new is to be said. */
data class Announcement(val shown: String, val speak: String?)

/** Follows positions along [guidance] like the guidance screen does: forward only, speaking when the next turn changes. */
class GuideAnnouncer(private val guidance: Guidance, private val destinationName: String) {
    private var furthest = 0.0
    private var lastKey: String? = null

    fun onFix(position: LatLon): Announcement {
        val raw = guidance.update(position, nearM = furthest)
        // A few meters back is GPS noise (as on the screen): stay at the furthest point.
        val state = if (raw.progressM < furthest && furthest - raw.progressM < BACKTRACK_M && raw.offRouteM <= OFF_ROUTE_WARN_M) {
            guidance.atProgress(furthest).copy(offRouteM = raw.offRouteM)
        } else {
            raw
        }
        furthest = maxOf(furthest, state.progressM)
        if (state.offRouteM > FAR_FROM_ROUTE_M) return Announcement("길에서 ${state.offRouteM}m 떨어져 있어요", null)
        val instruction = instructionText(state, destinationName)
        val key = "${state.nextTurn}-${distanceBucket(state.metersToNextTurn)}-${state.arrived}"
        val speak = if (key != lastKey) instruction.spoken else null
        lastKey = key
        return Announcement("${instruction.big} · ${instruction.action}", speak)
    }
}
