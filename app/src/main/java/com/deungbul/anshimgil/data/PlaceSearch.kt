package com.deungbul.anshimgil.data

/*
 * 장소 검색 (11/13): the server's search sent with the phone's position gives the 10 nearest places holding the
 * words (Kakao sort=distance), so from 조선대 "상무역" listed no 상무역 at all. The search is now made twice, near here
 * and by name over the whole country (kept to Gwangju), and the two are joined:
 *  1) the place most likely meant ([pickTripPlace]: a nearby branch of it, else the best by name)
 *  2) places whose name is or begins with the words, nearest first
 *  3) places whose name holds them, nearest first
 *  4) the rest (the search found them by other words, e.g. 전남광주통합특별시청 for 광주광역시청), by name, then near
 */

/** The near and the by-name results as one list, with each one's distance from [here]. */
suspend fun mergedSearch(query: String, near: List<Place>, anywhere: List<Place>, here: LatLon?): List<Place> {
    // "현재 위치", "내 위치": the server answers with where the walker is, alone (채림 #248).
    near.firstOrNull(::isHerePlace)?.let { return listOf(it) }
    val inGwangju = anywhere.filter { isInGwangju(LatLon(it.lat, it.lon)) }
    val all = (near + inGwangju)
        .distinctBy { it.id ?: "${it.name}@${"%.4f".format(java.util.Locale.US, it.lat)},${"%.4f".format(java.util.Locale.US, it.lon)}" }
        .map { p -> if (p.distanceM != null || here == null) p else p.copy(distanceM = metersBetween(here, LatLon(p.lat, p.lon)).toInt()) }
    if (all.isEmpty()) return all
    val q = query.replace(" ", "")
    fun name(p: Place) = p.name.replace(" ", "")
    val best = pickTripPlace(query, near) { inGwangju }?.let { b -> all.firstOrNull { it.name == b.name && it.lat == b.lat && it.lon == b.lon } }
    val byDistance = compareBy<Place> { it.distanceM ?: Int.MAX_VALUE }
    val rest = all.filter { it !== best }
    val begins = rest.filter { name(it).startsWith(q) }.sortedWith(byDistance)
    val holds = rest.filter { it !in begins && name(it).contains(q) }.sortedWith(byDistance)
    val others = rest.filter { it !in begins && it !in holds }
    return listOfNotNull(best) + begins + holds + others
}
