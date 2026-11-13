package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevatorExitTest {
    private val station = Place("1", "남광주역 광주1호선", "", 35.1385, 126.9200, null)
    private fun exit(n: Int, lat: Double, elevator: Boolean, name: String = "남광주역 ${n}번 출구") =
        Facility(FacilityKind.SUBWAY_ENTRANCE, lat, 126.9200, name = name, elevator = elevator)

    @Test
    fun stationNames() {
        assertEquals("남광주역", stationName("남광주역 광주1호선"))
        assertEquals("문화전당역", stationName("문화전당역"))
        assertNull(stationName("남광주역 4번 출구"))
        assertNull(stationName("스타벅스 광주문화전당점"))
    }

    @Test
    fun theNearestExitWithAnElevator() {
        val from = LatLon(35.1460, 126.9200) // north of the station
        val exits = listOf(
            exit(1, 35.1380, elevator = true),  // south
            exit(4, 35.1390, elevator = true),  // north: nearer to the walker
            exit(3, 35.1395, elevator = false), // nearest, but no elevator
            exit(2, 35.1391, elevator = true, name = "문화전당역 2번 출구"), // another station
        )
        val e = elevatorExitFor(station, from, exits)!!
        assertEquals("남광주역 4번 출구", e.name)
        assertEquals("남광주역 4번 출구 엘리베이터", e.label)
    }

    @Test
    fun noneWithoutAnElevatorOrForAnotherPlace() {
        val from = LatLon(35.1460, 126.9200)
        assertNull(elevatorExitFor(station, from, listOf(exit(3, 35.1395, elevator = false))))
        assertNull(elevatorExitFor(Place("2", "스타벅스", "", 35.1385, 126.92, null), from, listOf(exit(4, 35.139, elevator = true))))
        // Too far from the station to be its exit.
        assertNull(elevatorExitFor(station, from, listOf(exit(4, 35.1440, elevator = true))))
    }

    @Test
    fun arrivingAtAnElevatorExitSaysSo() {
        // 11/13 폰 시연: 돌고개역 2번 출구에 도착했는데 "목적지에 도착했어요"뿐이었다.
        val label = ElevatorExit("돌고개역 2번 출구", LatLon(35.1516, 126.8955)).label
        assertEquals("돌고개역 2번 출구", elevatorExitName(label))
        assertEquals("돌고개역 2번 출구 엘리베이터에 도착했어요", arrivalText(label).first)
        assertTrue(arrivalText(label).second.contains("엘리베이터를 타고"))
        assertEquals("목적지에 도착했어요", arrivalText("광주사직공원").first)
        assertEquals(null, elevatorExitName("광주사직공원"))
    }
}
