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

    @Test
    fun anUndergroundMallByTheNearestElevatorExitAroundIt() {
        // 11/13: 금남지하상가는 문화전당역과 금남로4가역 출구로 드나든다.
        val mall = Place("3", "금남지하상가", "", 35.1475, 126.9180, null)
        val exits = listOf(
            Facility(FacilityKind.SUBWAY_ENTRANCE, 35.1468, 126.9195, name = "문화전당역 3번 출구", elevator = true),
            Facility(FacilityKind.SUBWAY_ENTRANCE, 35.1490, 126.9160, name = "금남로4가역 1번 출구", elevator = true),
            Facility(FacilityKind.SUBWAY_ENTRANCE, 35.1480, 126.9170, name = "금남로4가역 3번 출구", elevator = false),
        )
        val fromSouth = LatLon(35.1420, 126.9230)
        assertEquals("문화전당역 3번 출구", elevatorExitFor(mall, fromSouth, exits)!!.name)
        val fromNorth = LatLon(35.1540, 126.9120)
        assertEquals("금남로4가역 1번 출구", elevatorExitFor(mall, fromNorth, exits)!!.name)
        assertTrue(arrivesByElevatorExit("금남지하상가"))
        assertTrue(arrivesByElevatorExit("충장로 지하상가"))
        assertTrue(!arrivesByElevatorExit("금남지하상가 다이소"))
        assertTrue(!arrivesByElevatorExit("광주극장"))
    }

    /** 11/16: the server picks the exit from the name and says so in an `entrance` note (server #264). */
    @Test
    fun serverExitLabelFromTheEntranceNote() {
        fun note(text: String) = listOf(RouteNote(false, "surface", "바닥 재질을 모르는 길"), RouteNote(false, "entrance", text))
        assertEquals("문화전당역 3번 출구 엘리베이터", serverExitLabel(note("금남지하상가는 문화전당역 3번 출구(엘리베이터)로 들어가요")))
        assertEquals("문화전당역 5번 출구", serverExitLabel(note("금남지하상가는 문화전당역 5번 출구(계단)로 들어가요")))
        assertEquals("남광주역 2번 출구 엘리베이터", serverExitLabel(note("남광주역은 남광주역 2번 출구(엘리베이터)로 들어가요")))
        assertEquals("남광주역 2번 출구 엘리베이터에 도착했어요", arrivalText(serverExitLabel(note("남광주역은 남광주역 2번 출구(엘리베이터)로 들어가요"))!!).first)
        assertNull(serverExitLabel(listOf(RouteNote(false, "surface", "바닥 재질을 모르는 길"))))
    }
}
