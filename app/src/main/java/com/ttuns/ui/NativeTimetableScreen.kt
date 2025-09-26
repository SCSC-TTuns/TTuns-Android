package com.ttuns.ui
import com.ttuns.BuildConfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.floor
import kotlin.math.max

// ===== 네트워크 =====
interface SnuttProxyApi {
    @GET("/api/snutt/search")
    suspend fun search(
        @Query("year") year: Int,
        @Query("semester") semester: String
    ): List<Map<String, Any?>>
}

private fun retrofitForProxy(): SnuttProxyApi {
    val base = BuildConfig.TTUNS_BACKEND_BASE.trim().ifEmpty { "https://ttuns-web.vercel.app/" }
    return Retrofit.Builder()
        .baseUrl(if (base.endsWith("/")) base else "$base/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(SnuttProxyApi::class.java)
}

// ===== 데이터 정규화 =====
data class LectureNorm(
    val title: String,
    val professor: String?,
    val times: List<LTime>
)
data class LTime(
    val day: Int,         // 0=월 … 6=일
    val startMin: Int,    // 분
    val endMin: Int,      // 분
    val room: String?
)

private fun String.toMinGuess(): Int? {
    // "09:00" → 540
    val parts = split(":")
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun normalizeLecture(raw: Map<String, Any?>): LectureNorm? {
    // title 후보 키
    val title = (raw["title"] ?: raw["course_title"] ?: raw["name"] ?: "").toString().trim()
    if (title.isEmpty()) return null

    // 교수명 후보 키
    val prof = when (val p = raw["instructor"] ?: raw["professor"] ?: raw["instructors"]) {
        is String -> p
        is List<*> -> p.joinToString(", ") { it?.toString().orEmpty() }
        else -> null
    }?.trim().takeIf { it?.isNotEmpty() == true }

    // 시간 배열 후보: class_time_json / times / classTimes …
    val timeArr = (raw["class_time_json"] ?: raw["times"] ?: raw["classTimes"]) as? List<*> ?: emptyList<Any?>()

    val parsed = timeArr.mapNotNull { any ->
        val obj = any as? Map<*, *> ?: return@mapNotNull null

        val day = (obj["day"] as? Number)?.toInt()
            ?: (obj["dayOfWeek"] as? Number)?.toInt()
            ?: when (val d = obj["day"]?.toString()) {
                "월","Mon","MON" -> 0
                "화","Tue","TUE" -> 1
                "수","Wed","WED" -> 2
                "목","Thu","THU" -> 3
                "금","Fri","FRI" -> 4
                "토","Sat","SAT" -> 5
                "일","Sun","SUN" -> 6
                else -> null
            }
        val startMin = (obj["start"] as? Number)?.toInt()
            ?: (obj["start_min"] as? Number)?.toInt()
            ?: obj["start_time"]?.toString()?.toMinGuess()
        val endMin = (obj["end"] as? Number)?.toInt()
            ?: (obj["end_min"] as? Number)?.toInt()
            ?: obj["end_time"]?.toString()?.toMinGuess()
        val room = (obj["place"] ?: obj["room"] ?: obj["location"])?.toString()

        if (day == null || startMin == null || endMin == null) null
        else LTime(day, startMin, endMin, room)
    }.filter { it.endMin > it.startMin }

    return LectureNorm(title = title, professor = prof, times = parsed)
}

// ===== 필터(정확일치) =====
private fun professorExact(lec: LectureNorm, q: String): Boolean {
    val a = lec.professor?.replace(" ", "")?.lowercase()?.trim() ?: return false
    val b = q.replace(" ", "").lowercase().trim()
    return a == b
}

private fun roomExact(lec: LectureNorm, q: String): Boolean {
    val needle = q.replace(" ", "").lowercase().trim()
    return lec.times.any { (it.room ?: "").replace(" ", "").lowercase().trim() == needle }
}

// ===== 이벤트 배치 =====
data class Event(
    val title: String,
    val professor: String?,
    val room: String?,
    val day: Int,
    val start: Int,
    val end: Int,
    val col: Int,
    val colCount: Int
)

private fun colorForTitleHsl(title: String): Pair<Color, Color> {
    var h = 0
    for (c in title) h = (h * 31 + c.code) % 360
    val fill = Color.hsl(h.toFloat(), 0.85f, 0.96f, 1f)
    val stroke = Color.hsl(h.toFloat(), 0.70f, 0.42f, 1f)
    return fill to stroke
}

private fun buildEvents(lectures: List<LectureNorm>, showBy: String): List<Event> {
    val out = mutableListOf<Event>()
    lectures.forEach { lec ->
        lec.times.forEach { t ->
            out += Event(
                title = lec.title,
                professor = lec.professor,
                room = t.room,
                day = t.day,
                start = t.startMin,
                end = t.endMin,
                col = 0,
                colCount = 1
            )
        }
    }
    // 요일별 컬럼 배치 (겹침 방지)
    val byDay = out.groupBy { it.day }.toMutableMap()
    val result = mutableListOf<Event>()
    byDay.forEach { (_, list) ->
        val sorted = list.sortedBy { it.start }.toMutableList()
        val active = mutableListOf<Event>()
        val columns = mutableListOf<Int>() // 각 active의 column
        while (sorted.isNotEmpty()) {
            val e = sorted.removeAt(0)
            // active에서 끝난 것 제거
            val ixsToRemove = active.withIndex().filter { it.value.end <= e.start }.map { it.index }.reversed()
            ixsToRemove.forEach { idx ->
                active.removeAt(idx); columns.removeAt(idx)
            }
            // 사용 가능한 가장 작은 컬럼 찾기
            var col = 0
            while (columns.contains(col)) col++
            active += e
            columns += col
            val colCount = max(columns.maxOrNull() ?: 0, col) + 1
            // active에 있는 모든 행사에 colCount 업데이트
            active.forEachIndexed { i, ae ->
                result += if (ae == e) {
                    ae.copy(col = col, colCount = colCount)
                } else {
                    // 이전에 넣었던 ae를 업데이트해야 하므로 교체 구현이 필요
                    // 간단히: result 마지막에서 ae를 빼는 방식을 쓰지 않고,
                    // 정렬 후에 한 번 더 pass로 colCount 보정해도 된다.
                    // 여기선 즉시 추가하고 마지막에 보정 pass.
                    ae
                }
            }
        }
    }
    // colCount 보정: 같은 day, 같은 겹침 그룹에서 최대값 재계산
    return result.groupBy { Triple(it.day, it.start, it.end) }
        .flatMap { (_, list) ->
            val maxCol = (list.maxOfOrNull { it.col } ?: 0) + 1
            list.map { it.copy(colCount = maxCol) }
        }
        .sortedWith(compareBy<Event> { it.day }.thenBy { it.start }.thenBy { it.col })
}

// ===== 시간 경계 =====
private fun timeBounds(evts: List<Event>): Pair<Int, Int> {
    if (evts.isEmpty()) return 9 * 60 to 18 * 60
    val minS = evts.minOf { it.start }
    val maxE = evts.maxOf { it.end }
    val s = floor((minS / 60.0)).toInt() * 60
    val e = ((maxE + 59) / 60) * 60
    return s to e
}

// ===== UI =====
@Composable
fun NativeTimetableScreen() {
    var year by remember { mutableStateOf("2025") }
    var semester by remember { mutableStateOf("3") } // 1=1학기, 3=2학기
    var mode by remember { mutableStateOf("room") }  // "professor" | "room"
    var q by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(listOf<Event>()) }
    var empty by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val api = remember { retrofitForProxy() }

    fun search() {
        val can = year.isNotBlank() && semester.isNotBlank() && q.trim().isNotEmpty()
        if (!can) return
        loading = true
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { api.search(year.toInt(), semester) }
                val all = raw.mapNotNull { normalizeLecture(it) }
                val filtered = when (mode) {
                    "professor" -> all.filter { professorExact(it, q) }
                    else -> all.filter { roomExact(it, q) }
                }
                val evts = buildEvents(filtered, mode)
                events = evts
                empty = evts.isEmpty()
            } catch (e: Exception) {
                events = emptyList()
                empty = true
            } finally {
                loading = false
            }
        }
    }

    val (startMin, endMin) = timeBounds(events)
    val minutesPerDp = 1.1f
    val totalHeight = max(400, ((endMin - startMin) / minutesPerDp).toInt())

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("교수/강의실 시간표", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))

        // 컨트롤
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = year, onValueChange = { year = it },
                    label = { Text("연도") }, modifier = Modifier.width(120.dp)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = semester, onValueChange = { semester = it },
                    label = { Text("학기(1/2/3/4)") }, modifier = Modifier.width(140.dp)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text(if (mode == "professor") "교수명" else "강의실") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedTwo(mode = mode, onChange = { mode = it })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { search() }, enabled = !loading) {
                    Text(if (loading) "불러오는 중…" else "검색")
                }
            }
        }

        if (empty && !loading) {
            Spacer(Modifier.height(8.dp))
            Text("결과가 없습니다. 입력값과 학기를 확인해 주세요.", color = Color(0xFF64748B))
        }

        Spacer(Modifier.height(8.dp))

        // 타임테이블
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .border(1.dp, Color(0xFFE5E7EB))
        ) {
            val days = listOf("월","화","수","목","금","토")
            val scrollH = rememberScrollState()
            val scrollV = rememberScrollState()

            Row(Modifier.horizontalScroll(scrollH)) {
                // 왼쪽 시간축 폭
                val timeColW = 40.dp
                Column {
                    // 헤더
                    Row {
                        Box(Modifier.width(timeColW).height(36.dp).background(Color(0xFFF3F6FF)))
                        days.forEach {
                            Box(
                                Modifier.width(140.dp).height(36.dp).background(Color(0xFFF3F6FF))
                                    .border(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Text(it, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    // 본문
                    Row {
                        // 시간축
                        Column(
                            Modifier.width(timeColW).height(totalHeight.dp).background(Color(0xFFF7F9FF)).verticalScroll(scrollV)
                        ) {
                            val marks = mutableListOf<Int>()
                            var m = (startMin / 60) * 60
                            while (m <= ((endMin + 59) / 60) * 60) {
                                marks += m; m += 60
                            }
                            val unit = 60 / minutesPerDp
                            Box(Modifier.fillMaxWidth().height(totalHeight.dp)) {
                                marks.forEach { t ->
                                    val top = ((t - startMin) / minutesPerDp).dp
                                    Box(Modifier.fillMaxWidth().height(1.dp).offset(y = top).background(Color(0xFFE5E7EB)))
                                    Text(
                                        ((t / 60)).toString(),
                                        modifier = Modifier.offset(y = top - 9.dp).padding(start = 2.dp),
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // 요일 칸들
                        Row(Modifier.verticalScroll(scrollV)) {
                            days.forEachIndexed { dayIdx, _ ->
                                val list = events.filter { it.day == dayIdx }
                                Box(
                                    Modifier.width(140.dp).height(totalHeight.dp).border(1.dp, Color(0xFFE5E7EB))
                                ) {
                                    // 시간 가는 줄
                                    var t = (startMin / 60) * 60
                                    while (t <= ((endMin + 59) / 60) * 60) {
                                        val top = ((t - startMin) / minutesPerDp).dp
                                        Box(Modifier.fillMaxWidth().height(1.dp).offset(y = top).background(Color(0xFFEEF2F7)))
                                        t += 60
                                    }
                                    // 이벤트 블록
                                    list.forEach { e ->
                                        val top = ((e.start - startMin) / minutesPerDp).dp
                                        val height = max(22f, ((e.end - e.start) / minutesPerDp) - 2f).dp
                                        val width = (1f / e.colCount)
                                        val leftPx = width * e.col
                                        val (fill, stroke) = colorForTitleHsl(e.title)
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .width((140.dp.value * width).dp)
                                                .height(height)
                                                .offset(x = (140.dp.value * leftPx).dp, y = top)
                                                .border(2.dp, stroke)
                                                .background(fill)
                                                .padding(6.dp)
                                        ) {
                                            Text(e.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = Color(0xFF0F172A))
                                            val meta = if (mode == "professor") (e.room ?: "") else (e.professor ?: "")
                                            if (meta.isNotBlank()) {
                                                Text(meta, maxLines = 1, color = Color(0xFF334155))
                                            }
                                            Text("${fmt(e.start)}–${fmt(e.end)}", color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTwo(mode: String, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegBtn("교수명", selected = mode == "professor") { onChange("professor") }
        Spacer(Modifier.width(4.dp))
        SegBtn("강의실", selected = mode == "room") { onChange("room") }
    }
}
@Composable
private fun SegBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors(),
        border = if (selected) null else ButtonDefaults.outlinedButtonBorder
    ) { Text(label) }
}

private fun fmt(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return "%02d:%02d".format(h, m)
}
