package com.deungbul.anshimgil.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.Favorite
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.SavedPlacesRepository
import com.deungbul.anshimgil.map.CHOSUN_UNIV
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF4A564E)
private val Green = Color(0xFF1F6B42)

private sealed interface Results {
    data object Idle : Results
    data object Loading : Results
    data class Done(val places: List<Place>) : Results
    data class Failed(val message: String) : Results
}

/**
 * Search with saved shortcuts (issue #4). Picking a place hands it to [onPlaceSelected].
 * When choosing a start, [onUseCurrentLocation] adds a "start from here" row.
 */
@Composable
fun SearchScreen(
    placeRepository: PlaceRepository,
    savedPlaces: SavedPlacesRepository,
    onBack: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "어디로 갈까요?",
    onUseCurrentLocation: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Results>(Results.Idle) }
    // Name of the shortcut being filled in, e.g. "집"; the next picked place is saved under it.
    var assigning by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val favorites by savedPlaces.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by savedPlaces.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results = Results.Idle
            return@LaunchedEffect
        }
        delay(300)
        results = Results.Loading
        results = try {
            Results.Done(placeRepository.search(text, CHOSUN_UNIV.latitude, CHOSUN_UNIV.longitude))
        } catch (e: ApiException) {
            Results.Failed(e.message)
        }
    }

    fun pick(place: Place) {
        val target = assigning
        scope.launch {
            if (target != null) {
                savedPlaces.setFavorite(target, place)
                assigning = null
                query = ""
            } else {
                savedPlaces.addRecent(place)
                onPlaceSelected(place)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "지도로 돌아가기", tint = Ink)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder, fontSize = 17.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Green) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Ink),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
        }

        assigning?.let { name ->
            Surface(color = Color(0xFFEEF6F0), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "'$name'(으)로 저장할 장소를 검색해서 골라 주세요",
                        fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { assigning = null }) { Text("취소") }
                }
            }
        }

        if (query.trim().length >= 2) {
            SearchResults(results, onPick = ::pick)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onUseCurrentLocation?.let { useHere ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEEF6F0))
                                .clickable(role = Role.Button, onClick = useHere)
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color(0xFF2F6FDB))
                            Text("현재 위치에서 출발", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("자주 가는 곳", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editing = !editing }) { Text(if (editing) "완료" else "편집", color = Muted) }
                    }
                }
                item {
                    FavoriteGrid(
                        favorites = favorites,
                        editing = editing,
                        onTap = { favorite ->
                            when {
                                editing || favorite.place == null -> assigning = favorite.name
                                else -> pick(favorite.place)
                            }
                        },
                        onRemove = { scope.launch { savedPlaces.removeFavorite(it.name) } },
                        onAdd = { showAddDialog = true },
                    )
                }
                item {
                    Text(
                        if (editing) "누르면 장소를 바꿀 수 있어요." else "한 번 누르면 바로 길을 찾아요.",
                        fontSize = 13.sp, color = Muted,
                    )
                }
                if (recents.isNotEmpty()) {
                    item {
                        Text(
                            "최근 검색", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(recents, key = { "${it.name}|${it.address}" }) { place ->
                        PlaceRow(place, Icons.Filled.History, onClick = { pick(place) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFavoriteDialog(
            existing = favorites.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                editing = false
                assigning = name
            },
        )
    }
}

@Composable
private fun SearchResults(results: Results, onPick: (Place) -> Unit) {
    when (results) {
        Results.Idle, Results.Loading -> Text(
            "찾는 중…", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(20.dp),
        )
        is Results.Failed -> Text(results.message, fontSize = 15.sp, color = Color(0xFFB42318), modifier = Modifier.padding(20.dp))
        is Results.Done -> if (results.places.isEmpty()) {
            Text("검색 결과가 없어요. 다른 이름으로 찾아 보세요.", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                items(results.places, key = { "${it.id}|${it.name}|${it.address}" }) { place ->
                    PlaceRow(place, Icons.Filled.Place, onClick = { onPick(place) })
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, icon: ImageVector, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6B776F))
            Column(Modifier.weight(1f)) {
                Text(place.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.address, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            place.distanceM?.let { Text(formatDistance(it), fontSize = 13.sp, color = Muted) }
        }
        HorizontalDivider(color = Color(0xFFEDF1EC))
    }
}

@Composable
private fun FavoriteGrid(
    favorites: List<Favorite>,
    editing: Boolean,
    onTap: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
    onAdd: () -> Unit,
) {
    val tiles: List<Favorite?> = favorites + null // null = the "add" tile
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { favorite ->
                    Box(Modifier.weight(1f)) {
                        if (favorite == null) AddTile(onAdd) else FavoriteTile(favorite, editing, onTap, onRemove)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FavoriteTile(favorite: Favorite, editing: Boolean, onTap: (Favorite) -> Unit, onRemove: (Favorite) -> Unit) {
    val (icon, color) = when (favorite.name) {
        "집" -> Icons.Filled.Home to Green
        "학교" -> Icons.Filled.School to Color(0xFFC2410C)
        "직장" -> Icons.Filled.Work to Color(0xFF1D4ED8)
        else -> Icons.Filled.Place to Color(0xFF6D28D9)
    }
    val subtitle = favorite.place?.name ?: "등록하기"
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF4F7F3))
                .clickable(role = Role.Button, onClickLabel = "${favorite.name} 길찾기") { onTap(favorite) }
                .padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (favorite.place != null) color else Color.White)
                    .border(2.dp, color, CircleShape),
            ) {
                Icon(icon, contentDescription = null, tint = if (favorite.place != null) Color.White else color)
            }
            Text(favorite.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (editing && (favorite.place != null || !favorite.builtIn)) {
            IconButton(
                onClick = { onRemove(favorite) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "${favorite.name} 지우기", tint = Color(0xFFB42318))
            }
        }
    }
}

@Composable
private fun AddTile(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFFA9B4AC), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "자주 가는 곳 추가", onClick = onAdd)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0xFFA9B4AC), CircleShape),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Muted)
        }
        Text("추가", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("이름 붙이기", fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun AddFavoriteDialog(existing: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = trimmed in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("자주 가는 곳 이름") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(10) },
                placeholder = { Text("예: 학원, 병원") },
                singleLine = true,
                isError = duplicate,
                supportingText = { if (duplicate) Text("이미 있는 이름이에요") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty() && !duplicate) { Text("다음") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun formatDistance(meters: Int): String =
    if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)
