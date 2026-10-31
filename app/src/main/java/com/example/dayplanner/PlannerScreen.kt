@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dayplanner

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------- общие константы и хелперы ----------

private val RU: Locale = Locale.forLanguageTag("ru-RU")

private fun String.cap(): String = replaceFirstChar { it.titlecase(RU) }

private fun eventsWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "событий"
        m10 == 1 -> "событие"
        m10 in 2..4 -> "события"
        else -> "событий"
    }
}

// Пейджер месяцев: с января 2000 по декабрь 2100
private val MONTH0: YearMonth = YearMonth.of(2000, 1)
private const val PAGE_COUNT = 101 * 12

private fun pageOf(ym: YearMonth): Int =
    ((ym.year - 2000) * 12 + ym.monthValue - 1).coerceIn(0, PAGE_COUNT - 1)

private fun monthOf(page: Int): YearMonth = MONTH0.plusMonths(page.toLong())

private val TodayRed = Color(0xFFFF3B30)
private val NoteYellow = Color(0xFFF5B800)
private val EventColors = listOf(
    Color(0xFF3B82F6), // синий
    Color(0xFF22A06B), // зелёный
    Color(0xFFEF5B5B), // красный
    Color(0xFF9B6BE0), // фиолетовый
    Color(0xFFF08C2E), // оранжевый
    Color(0xFF14B8C6)  // бирюзовый
)

private fun eventColor(e: Event): Color = EventColors[(e.id % EventColors.size).toInt()]

private data class ChipData(val text: String, val color: Color)

// ---------- корневой экран ----------

@Composable
fun PlannerScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { Db.get(ctx) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    var selectedStr by rememberSaveable { mutableStateOf(today.toString()) }
    var dayOpen by rememberSaveable { mutableStateOf(false) }

    // растёт при любом изменении данных, чтобы перечитать их из базы
    var version by remember { mutableIntStateOf(0) }
    var dialogOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Event?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = pageOf(YearMonth.from(today)),
        pageCount = { PAGE_COUNT }
    )
    val month = monthOf(pagerState.currentPage)
    val selected = LocalDate.parse(selectedStr)

    fun goToMonth(ym: YearMonth) {
        scope.launch { pagerState.animateScrollToPage(pageOf(ym)) }
    }

    fun closeDay() {
        version++
        TodayWidget.updateAll(ctx)
        dayOpen = false
    }

    BackHandler(enabled = dayOpen) { closeDay() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
        contentColor = cs.onBackground
    ) {
        Crossfade(targetState = dayOpen, label = "screen") { open ->
            if (open) {
                DayScreen(
                    db = db,
                    selected = selected,
                    version = version,
                    onBack = { closeDay() },
                    onAdd = {
                        editTarget = null
                        dialogOpen = true
                    },
                    onEdit = {
                        editTarget = it
                        dialogOpen = true
                    }
                )
            } else {
                MonthScreen(
                    db = db,
                    month = month,
                    today = today,
                    selected = selected,
                    version = version,
                    pagerState = pagerState,
                    onYearClick = { showMonthPicker = true },
                    onThemeClick = { showThemeDialog = true },
                    onAdd = {
                        editTarget = null
                        dialogOpen = true
                    },
                    onDayClick = {
                        selectedStr = it.toString()
                        dayOpen = true
                    },
                    onToday = {
                        val t = LocalDate.now()
                        selectedStr = t.toString()
                        goToMonth(YearMonth.from(t))
                    }
                )
            }
        }
    }

    if (dialogOpen) {
        val target = editTarget
        val onDelete: (() -> Unit)? = target?.let { t ->
            {
                db.deleteEvent(t.id)
                version++
                TodayWidget.updateAll(ctx)
                dialogOpen = false
            }
        }
        EventDialog(
            initial = target,
            dateLabel = selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).cap(),
            onDismiss = { dialogOpen = false },
            onSave = { title, time, desc ->
                db.saveEvent(Event(target?.id ?: 0L, selectedStr, time, title, desc))
                version++
                TodayWidget.updateAll(ctx)
                dialogOpen = false
            },
            onDelete = onDelete
        )
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = month,
            onPick = {
                showMonthPicker = false
                goToMonth(it)
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = remember { Prefs.nightMode(ctx) },
            onPick = {
                showThemeDialog = false
                Prefs.setNightMode(ctx, it)
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

// ---------- экран месяца (как в Календаре на iPhone) ----------

@Composable
private fun MonthScreen(
    db: Db,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    version: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onYearClick: () -> Unit,
    onThemeClick: () -> Unit,
    onAdd: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onToday: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(onClick = onYearClick) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Выбрать месяц")
                    Text(month.year.toString(), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Pill(onClick = onThemeClick) { ThemeIcon() }
                Spacer(Modifier.width(8.dp))
                Pill(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Новое событие")
                }
            }

            Text(
                text = month.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).cap(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            )

            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)) {
                for (i in 1..7) {
                    Text(
                        text = DayOfWeek.of(i).getDisplayName(TextStyle.NARROW, RU).uppercase(RU),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i >= 6) cs.onSurface.copy(alpha = 0.4f) else cs.onSurfaceVariant
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                MonthPage(db, monthOf(page), today, selected, version, onDayClick)
            }

            // место под кнопку «Сегодня»
            Spacer(modifier = Modifier
                .navigationBarsPadding()
                .height(76.dp))
        }

        Pill(
            onClick = onToday,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 16.dp)
        ) {
            Text("Сегодня", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MonthPage(
    db: Db,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    version: Int,
    onDayClick: (LocalDate) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // неделя начинается с понедельника
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    val start = first.minusDays(offset.toLong())

    val eventsByDay = remember(month, version) {
        db.eventsInRange(first.toString(), month.atEndOfMonth().toString())
    }
    val notesByDay = remember(month, version) {
        db.notesInRange(first.toString(), month.atEndOfMonth().toString())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        for (w in 0 until weeks) {
            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    if (YearMonth.from(date) == month) {
                        val key = date.toString()
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            note = notesByDay[key],
                            events = eventsByDay[key] ?: emptyList(),
                            onClick = { onDayClick(date) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    note: String?,
    events: List<Event>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    BoxWithConstraints(modifier = modifier.clickable(onClick = onClick)) {
        // сколько строк-плашек помещается в ячейку по высоте
        val slots = ((maxHeight - 32.dp) / 16.dp).toInt().coerceAtLeast(0)

        val chips = buildList<ChipData> {
            val firstLine = note?.lineSequence()?.firstOrNull { it.isNotBlank() }
            if (firstLine != null) add(ChipData(firstLine.trim(), NoteYellow))
            events.forEach { add(ChipData(it.title, eventColor(it))) }
        }
        val shown = if (chips.size <= slots) chips.size else (slots - 1).coerceAtLeast(0)
        val more = chips.size - shown

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isToday -> TodayRed
                            isSelected -> cs.onSurface.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                    )
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 15.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isToday -> Color.White
                        weekend -> cs.onSurface.copy(alpha = 0.5f)
                        else -> cs.onSurface
                    }
                )
            }
            Spacer(Modifier.height(2.dp))
            chips.take(shown).forEach { ChipView(it) }
            if (more > 0) {
                Text(
                    text = "+$more",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChipView(chip: ChipData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(chip.color.copy(alpha = 0.22f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .background(chip.color)
        )
        Text(
            text = chip.text,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 3.dp, end = 2.dp)
        )
    }
}

// ---------- кнопки-«таблетки» и иконка темы ----------

@Composable
private fun Pill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = cs.surfaceContainerHigh,
        contentColor = cs.onSurface,
        shadowElevation = 2.dp,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** Значок «контраст»: круг, у которого закрашена левая половина. */
@Composable
private fun ThemeIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val diameter = size.minDimension - stroke
        drawCircle(color = color, radius = diameter / 2, style = Stroke(width = stroke))
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter)
        )
    }
}

// ---------- экран дня: заметка + события ----------

@Composable
private fun DayScreen(
    db: Db,
    selected: LocalDate,
    version: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Event) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val events = remember(selected, version) { db.eventsFor(selected.toString()) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Событие") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
                    Text(
                        selected.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).cap(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "dayheader") { DayHeader(selected, events.size) }
                item(key = "note") { NoteCard(db = db, dateKey = selected.toString()) }
                if (events.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Нажмите «Событие», чтобы запланировать что-нибудь на этот день",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(events, key = { it.id }) { e ->
                        EventCard(e, onClick = { onEdit(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(selected: LocalDate, count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).cap(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (count == 0) "Событий нет" else "$count ${eventsWord(count)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoteCard(db: Db, dateKey: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val text = remember(dateKey) { mutableStateOf(db.noteFor(dateKey)) }

    // в базу заметка пишется сразу, виджет обновляется с задержкой — не на каждую букву
    LaunchedEffect(dateKey, text.value) {
        delay(600)
        TodayWidget.updateAll(ctx)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Text(
                text = "ЗАМЕТКА ДНЯ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onTertiaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 16.dp)
            )
            TextField(
                value = text.value,
                onValueChange = {
                    text.value = it
                    db.saveNote(dateKey, it)
                },
                placeholder = { Text("Что важно в этот день?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = cs.onTertiaryContainer,
                    unfocusedTextColor = cs.onTertiaryContainer,
                    focusedPlaceholderColor = cs.onTertiaryContainer.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = cs.onTertiaryContainer.copy(alpha = 0.6f),
                    cursorColor = cs.onTertiaryContainer
                )
            )
        }
    }
}

@Composable
private fun EventCard(e: Event, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = eventColor(e)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = if (e.time.isEmpty()) "Без времени" else e.time,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (e.description.isNotBlank()) {
                    Text(
                        text = e.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ---------- диалоги ----------

@Composable
private fun EventDialog(
    initial: Event?,
    dateLabel: String,
    onDismiss: () -> Unit,
    onSave: (title: String, time: String, desc: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var titleError by remember { mutableStateOf(false) }

    fun pickTime() {
        val hour = if (time.length == 5) time.substring(0, 2).toInt() else 9
        val minute = if (time.length == 5) time.substring(3).toInt() else 0
        TimePickerDialog(
            ctx,
            { _, h, m -> time = String.format(Locale.ROOT, "%02d:%02d", h, m) },
            hour,
            minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое событие" else "Событие") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Название") },
                    singleLine = true,
                    isError = titleError,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { pickTime() }) {
                        Text(if (time.isEmpty()) "Указать время" else time)
                    }
                    if (time.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { time = "" }) { Text("Сбросить") }
                    }
                }
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Удалить событие") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    titleError = true
                } else {
                    onSave(title.trim(), time, desc.trim())
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun MonthPickerDialog(
    initial: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var year by remember { mutableIntStateOf(initial.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (year > 2000) year-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий год")
                }
                Text(
                    text = year.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { if (year < 2100) year++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий год")
                }
            }
        },
        text = {
            Column {
                for (r in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 3) {
                            val m = r * 3 + c + 1
                            val ym = YearMonth.of(year, m)
                            TextButton(
                                onClick = { onPick(ym) },
                                modifier = Modifier.weight(1f),
                                colors = if (ym == initial) {
                                    ButtonDefaults.textButtonColors(containerColor = cs.primaryContainer)
                                } else {
                                    ButtonDefaults.textButtonColors()
                                }
                            ) {
                                Text(
                                    text = Month.of(m).getDisplayName(TextStyle.SHORT_STANDALONE, RU).cap(),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ThemeDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to "Как в системе",
        AppCompatDelegate.MODE_NIGHT_NO to "Светлая",
        AppCompatDelegate.MODE_NIGHT_YES to "Тёмная"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тема") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
