package com.deungbul.anshimgil.data

/*
 * 현재 위치 주소 (11/13, 채림 safe-route #248): the "현재 위치" button and the start choice show the address of where
 * the walker is, e.g. "광주 동구 필문대로 309 (조선대학교 본관)".
 */

/** "address (building)" or the address alone when the name is just the address. */
fun hereLine(p: Place): String =
    if (p.address.isBlank()) p.name else if (p.name.isBlank() || p.name == p.address) p.address else "${p.address} (${p.name})"

/** The server's answer to a search for "현재 위치", "내 위치": one place with id "here" (its address line). */
fun isHerePlace(p: Place): Boolean = p.id == "here"
