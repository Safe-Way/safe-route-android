package com.deungbul.anshimgil.data

/*
 * Why this route: what the route engine weighs for each mode, by day and by night (the team's
 * criteria table, 2026-11-04). [RouteCriteria.short] is the one line shown under the routes;
 * [RouteCriteria.details] opens with 자세히. [RouteCriteria.counts] says which of the server's
 * counts mean something for this mode and time (no street-light count in the daytime, say); a
 * "covered." key is the meters walked within reach of that thing.
 */

data class RouteCriteria(val short: String, val details: List<String>, val counts: Set<String>)

private const val HELP_WORDS = "도움 시설은 편의점·파출소·아동안전지킴이집처럼 도움을 청할 수 있는 곳이에요."
private val NIGHT_COUNTS = setOf("light", "help", "cctv")

fun routeCriteria(mode: UserMode, night: Boolean): RouteCriteria = when (mode) {
    UserMode.GENERAL -> if (!night) {
        RouteCriteria("낮에는 가장 짧은 길로 안내해요.", listOf("낮에는 따로 피하는 곳 없이 가장 짧은 길을 골라요."), emptySet())
    } else {
        RouteCriteria(
            "밤이라 밝은 길을 먼저 골랐어요.",
            listOf(
                "1순위 밝기: 가로등이 많은 길을 가장 중요하게 봐요.",
                "2순위 도움 시설: 길에서 160m 안에 도움 시설이 있는지 봐요.",
                "3순위 CCTV",
                HELP_WORDS,
            ),
            NIGHT_COUNTS,
        )
    }
    UserMode.CHILD -> if (!night) {
        RouteCriteria(
            "어린이보호구역과 도움 시설이 가까운 길이에요.",
            listOf(
                "1순위 어린이보호구역: 300m 안에 보호구역이 있는 길을 가장 중요하게 봐요.",
                "2순위 도움 시설: 길에서 150m 안에 도움 시설이 있는지 봐요.",
                HELP_WORDS,
            ),
            setOf("school", "help", "covered.school", "covered.help"),
        )
    } else {
        RouteCriteria(
            "어두워지기 시작해 밝은 길을 먼저 골랐어요.",
            listOf(
                "어린이는 해 지기 60분 전부터 밤 기준으로 골라요.",
                "1순위 밝기: 가로등이 많은 길을 가장 중요하게 봐요.",
                "2순위 도움 시설: 길에서 150m 안에 도움 시설이 있는지 봐요.",
                "3순위 CCTV",
                "밤에는 어린이보호구역을 기준으로 쓰지 않아요.",
                HELP_WORDS,
            ),
            NIGHT_COUNTS,
        )
    }
    UserMode.SENIOR -> if (!night) {
        RouteCriteria(
            "계단을 피하고, 쉴 곳이 가까운 길이에요.",
            listOf(
                "계단: 돌아가는 길이 너무 멀지 않으면 계단을 피해요.",
                "쉴 곳: 길에서 120m 안에 지금 쓸 수 있는 쉼터가 있는 길을 골라요.",
                "가로등·CCTV는 낮에는 기준으로 쓰지 않아요.",
            ),
            setOf("rest", "covered.rest"),
        )
    } else {
        RouteCriteria(
            "계단을 피하고, 밤이라 밝은 길을 먼저 골랐어요.",
            listOf(
                "계단: 돌아가는 길이 너무 멀지 않으면 계단을 피해요.",
                "1순위 밝기: 가로등이 많은 길을 가장 중요하게 봐요.",
                "2순위 도움 시설: 길에서 120m 안에 도움 시설이 있는지 봐요.",
                "3순위 CCTV",
                "공중화장실은 지도에 보여 주기만 하고 길 고르기에는 쓰지 않아요.",
                HELP_WORDS,
            ),
            NIGHT_COUNTS,
        )
    }
    UserMode.DISABLED -> {
        val avoid = listOf(
            "피하는 곳: 경사로가 확인되지 않은 계단, 턱이 있는 곳, 지날 수 없는 육교, 산길, 징검다리",
            "비포장 길: 1.2배 안에 다른 길이 있으면 피하고, 없으면 지나면서 알려 드려요.",
        )
        if (!night) {
            RouteCriteria("휠체어로 못 지나는 곳을 빼고 가장 짧은 길이에요.", avoid, emptySet())
        } else {
            RouteCriteria(
                "못 지나는 곳을 빼고, 밤이라 밝은 길을 먼저 골랐어요.",
                avoid + listOf(
                    "1순위 밝기: 어두우면 턱이나 패인 곳이 안 보여서 밝은 길을 가장 먼저 봐요.",
                    "2순위 도움 시설: 길에서 120m 안에 도움 시설이 있는지 봐요.",
                    "3순위 CCTV",
                    HELP_WORDS,
                ),
                NIGHT_COUNTS,
            )
        }
    }
}

/**
 * The first line under the routes: the mode's line ([RouteCriteria.short]), unless the server's notes say the route
 * could not do what that line promises — stairs left on every way ("계단을 피하는 길은 없습니다") while the line says
 * the stairs are avoided — when the server's own summary ("가장 짧은 길로 갑니다") leads instead.
 */
fun headlineFor(modeLine: String, explanation: Explanation): String {
    val stairsLeft = explanation.notes.any { it.warn && it.kind == "stairs" }
    val promisesNoStairs = modeLine.contains("계단을 피") || modeLine.contains("못 지나는 곳을 빼고")
    return if (stairsLeft && promisesNoStairs && explanation.summary.isNotBlank()) explanation.summary else modeLine
}
