package com.deungbul.anshimgil.data

/*
 * 아이콘 설명 (11/9): what each picture on the map means, shown when its icon is tapped (home and guidance map)
 * and said by AI 비서 when asked ("집에 하트 그려진 아이콘 뭐야?"). Pictures are described as drawn in
 * map/MapOverlays.kt drawPicture.
 */

/** One or two short sentences: what the place is and why the map shows it. */
val FacilityKind.meaning: String
    get() = when (this) {
        FacilityKind.CCTV -> "CCTV가 있는 곳이에요. 둥근 카메라 그림이에요. CCTV가 많은 길을 더 안전한 길로 골라요."
        FacilityKind.POLICE -> "경찰서나 파출소예요. 별이 있는 방패 그림이에요. 위급할 때 도움을 청할 수 있어요."
        FacilityKind.GUARD_HOUSE -> "아동안전지킴이집이에요. 집 안에 하트 그림이에요. 아이가 위험할 때 들어가 도움을 청할 수 있는 가게나 집이에요."
        FacilityKind.EMERGENCY_BELL -> "안전 비상벨이에요. 빨간 종 그림이에요. 위급할 때 누르면 경찰이나 관제센터로 연결돼요."
        FacilityKind.CONVENIENCE_STORE -> "편의점이에요. 늦게까지 문을 열어 도움을 청하기 좋은 곳이에요."
        FacilityKind.HEAT_SHELTER -> "무더위쉼터예요. 집 안에 해 그림이에요. 더울 때 들어가 쉴 수 있는 곳이에요."
        FacilityKind.COLD_SHELTER -> "한파쉼터예요. 집 안에 눈송이 그림이에요. 추울 때 들어가 몸을 녹일 수 있는 곳이에요."
        FacilityKind.TOILET -> "누구나 쓸 수 있는 공중화장실이나 개방화장실이에요. 남녀 사람 그림이에요."
        FacilityKind.ACCESSIBLE_TOILET -> "장애인 화장실이 있는 곳이에요. 휠체어 탄 사람 그림이에요."
        FacilityKind.CURB_RAMP -> "보도 턱을 낮춘 횡단보도예요. 휠체어나 유모차로 건너기 편해요. 회색 점은 턱 높이를 아직 모르는 곳이에요."
        FacilityKind.ELEVATOR -> "엘리베이터가 있는 육교예요. 계단 없이 길을 건널 수 있어요."
        FacilityKind.OVERPASS -> "엘리베이터 없는 육교예요. 계단을 올라야 해서 길을 고를 때 피해요."
        FacilityKind.STAIRS -> "계단이에요. 빨간 계단 그림이에요. 계단이 힘든 분께는 계단을 피한 길을 골라 드려요."
        FacilityKind.SUBWAY -> "지하철역이에요. 누르면 다음 열차와 엘리베이터 있는 출구를 볼 수 있어요."
        FacilityKind.SUBWAY_ENTRANCE -> "지하철역 출구예요. 엘리베이터 표시가 있으면 엘리베이터로 오르내릴 수 있는 출구예요."
        FacilityKind.SCHOOL_ZONE -> "어린이 보호구역이에요. 걷는 아이 그림이에요. 둘레의 노란 동그라미 안은 차가 천천히 다녀야 하는 곳이에요."
    }

/** Words in a question that point at one picture. Checked in order, so the more telling words come first. */
private val PICTURE_WORDS: List<Pair<List<String>, FacilityKind>> = listOf(
    listOf("하트", "지킴이") to FacilityKind.GUARD_HOUSE,
    listOf("눈송이", "눈꽃", "눈 모양", "눈모양", "한파") to FacilityKind.COLD_SHELTER,
    listOf("해 ", "해가", "해모양", "해 모양", "햇빛", "햇볕", "태양", "무더위") to FacilityKind.HEAT_SHELTER,
    listOf("휠체어") to FacilityKind.ACCESSIBLE_TOILET,
    listOf("종 ", "종이", "종모양", "종 모양", "벨") to FacilityKind.EMERGENCY_BELL,
    listOf("카메라", "cctv", "씨씨티비") to FacilityKind.CCTV,
    listOf("방패", "별", "파출소", "경찰") to FacilityKind.POLICE,
    listOf("계단") to FacilityKind.STAIRS,
    listOf("육교") to FacilityKind.OVERPASS,
    listOf("엘리베이터") to FacilityKind.ELEVATOR,
    listOf("횡단보도", "줄무늬", "턱") to FacilityKind.CURB_RAMP,
    listOf("출구") to FacilityKind.SUBWAY_ENTRANCE,
    listOf("지하철", "기차", "전철") to FacilityKind.SUBWAY,
    listOf("남녀", "화장실", "사람 두", "두 사람") to FacilityKind.TOILET,
    listOf("편의점", "가게", "상점", "차양") to FacilityKind.CONVENIENCE_STORE,
    listOf("아이", "어린이", "보호구역", "걷는") to FacilityKind.SCHOOL_ZONE,
)

/** Words that say the question is about a picture on the map, not about going somewhere. */
private val ICON_WORDS = listOf("아이콘", "그림", "표시", "마크", "모양", "기호", "그려")

/** Words that ask what something is. */
private val WHAT_WORDS = listOf("뭐", "머야", "머예", "머에", "머냐", "머지", "머임", "무슨", "무엇", "뭔", "뜻", "의미", "알려")

/**
 * AI 비서's answer when [said] asks what a picture on the map is ("집에 하트 그려진 아이콘 뭐야?"); null when it
 * does not. A house alone could be three pictures, so all three are told; a question about the icons in
 * general gets the list.
 */
fun iconAnswer(said: String): String? {
    val s = said.lowercase().replace("?", " ") + " "
    val aboutPicture = ICON_WORDS.any { it in s } || (WHAT_WORDS.any { it in s } && listOf("하트", "방패", "눈송이", "햇빛", "태양").any { it in s })
    if (!aboutPicture || WHAT_WORDS.none { it in s }) return null
    // "아이콘" would read as "아이" (a child), so it is taken out before looking for the picture.
    val picture = s.replace("아이콘", " ")
    PICTURE_WORDS.firstOrNull { (words, _) -> words.any { it in picture } }?.let { (_, kind) -> return kind.meaning }
    if ("집" in picture) {
        return "집 모양은 세 가지예요. 하트가 있으면 아동안전지킴이집, 해가 있으면 무더위쉼터, 눈송이가 있으면 한파쉼터예요."
    }
    return "지도 그림은 이래요. 빨간 종은 비상벨, 둥근 카메라는 CCTV, 방패는 파출소, 집 안 하트는 아동안전지킴이집, " +
        "집 안 해와 눈송이는 무더위·한파쉼터, 빨간 계단은 피할 계단이에요. 그림을 누르면 무엇인지 알려 드려요."
}
