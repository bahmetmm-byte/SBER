package com.example.sbermock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val BodyColor = Color(0xFF2B2E33)
private val ScreenBg = Color(0xFF0E1A12)
private val ScreenFg = Color(0xFFB8F5B8)
private val Accent = Color(0xFFFFE066)
private val Mono = FontFamily.Monospace

private fun money(k: Long): String {
    val a = abs(k)
    val sign = if (k < 0) "-" else ""
    return sign + "%d,%02d руб.".format(a / 100, (a % 100).toInt())
}

private fun nowStr(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

private fun randomAuth(): String = (100000..999999).random().toString()

private fun cardTapped(s: TerminalState, tail: String) {
    s.cardTail = tail
    s.pin = ""
    s.screen = when {
        s.op == Op.REFUND -> Screen.REFUND_CARD_DIGITS
        s.kopecks > 300000L -> Screen.PIN
        else -> Screen.PROCESSING
    }
}

private suspend fun runProcessing(s: TerminalState) {
    val steps = listOf(
        "Жду приглашения…", "Устанавливаю связь", "Связь есть",
        "Передаю запрос…", "Жду ответа…", "Ответ получен"
    )
    for (m in steps) {
        s.statusLine = m
        delay(700)
    }
    val reason = when {
        s.op != Op.CANCEL && s.pin == "0000" -> "НЕВЕРНЫЙ ПИН"
        s.op == Op.PAYMENT && (1..100).random() <= 10 -> "НЕДОСТАТОЧНО СРЕДСТВ"
        else -> ""
    }
    s.approved = reason.isEmpty()
    s.declineReason = reason
    s.authCode = randomAuth()
    var k = s.kopecks
    var tail = s.cardTail
    if (s.op == Op.CANCEL) {
        val t = s.shift.getOrNull(s.serviceIndex)
        if (t != null) {
            k = t.kopecks
            tail = t.cardTail
            s.shift.remove(t)
            s.amountDigits = k.toString()
            s.cardTail = tail
        }
    }
    val txn = Txn(s.op, k, nowStr(), tail, s.authCode, s.approved)
    if (s.approved && s.op != Op.CANCEL) {
        if (s.shiftClosed) {
            s.shift.clear()
            s.shiftClosed = false
        }
        s.shift.add(txn)
    }
    s.lastTxn = txn
    s.screen = Screen.RESULT
}

@Composable
fun TerminalApp(
    state: TerminalState,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    tappedTag: String?,
    onTapConsumed: () -> Unit,
    vibrate: () -> Unit
) {
    LaunchedEffect(tappedTag) {
        if (tappedTag != null) {
            if (state.screen == Screen.TAP_CARD) {
                vibrate()
                cardTapped(state, tappedTag)
            }
            onTapConsumed()
        }
    }
    LaunchedEffect(state.screen) {
        when (state.screen) {
            Screen.PROCESSING -> runProcessing(state)
            Screen.RESULT -> {
                delay(1800)
                if (state.approved) state.screen = Screen.RECEIPT
            }
            else -> {}
        }
    }
    LaunchedEffect(state.screen, state.statusLine) {
        if (state.screen == Screen.SETTLEMENT && state.statusLine == "WORK") {
            delay(2500)
            state.shiftClosed = true
            state.statusLine = "DONE"
        }
    }

    fun onDigit(d: String) {
        when (state.screen) {
            Screen.AMOUNT ->
                if (state.amountDigits.length < 8)
                    state.amountDigits = (state.amountDigits + d).trimStart('0')
            Screen.PIN, Screen.REFUND_CARD_DIGITS ->
                if (state.pin.length < 4) state.pin += d
            else -> {}
        }
    }

    fun onFix() {
        when (state.screen) {
            Screen.AMOUNT -> state.amountDigits = state.amountDigits.dropLast(1)
            Screen.PIN, Screen.REFUND_CARD_DIGITS -> state.pin = state.pin.dropLast(1)
            else -> {}
        }
    }

    fun onCancel() {
        when {
            state.screen == Screen.PROCESSING -> {}
            state.screen == Screen.SETTLEMENT && state.statusLine == "WORK" -> {}
            state.screen == Screen.SERVICE_MENU && state.statusLine == "CONFIRM" ->
                state.statusLine = ""
            else -> state.reset()
        }
    }

    fun onMove(delta: Int) {
        when (state.screen) {
            Screen.MENU -> state.menuIndex = (state.menuIndex + delta).coerceIn(0, 3)
            Screen.SERVICE_MENU -> {
                val size = if (state.op == Op.CANCEL) state.shift.size else 3
                state.serviceIndex = (state.serviceIndex + delta).coerceIn(0, maxOf(size - 1, 0))
            }
            else -> {}
        }
    }

    fun onEnter() {
        when (state.screen) {
            Screen.IDLE -> state.screen = Screen.MENU
            Screen.MENU -> {
                state.serviceIndex = 0
                state.statusLine = ""
                when (state.menuIndex) {
                    0 -> { state.op = Op.PAYMENT; state.amountDigits = ""; state.screen = Screen.AMOUNT }
                    1 -> { state.op = Op.REFUND; state.amountDigits = ""; state.screen = Screen.AMOUNT }
                    2 -> { state.op = Op.CANCEL; state.screen = Screen.SERVICE_MENU }
                    else -> { state.op = Op.PAYMENT; state.screen = Screen.SERVICE_MENU }
                }
            }
            Screen.AMOUNT -> if (state.kopecks > 0) state.screen = Screen.TAP_CARD
            Screen.TAP_CARD -> {}
            Screen.REFUND_CARD_DIGITS -> if (state.pin.length == 4) {
                state.pin = ""
                state.screen = Screen.PIN
            }
            Screen.PIN -> if (state.pin.length == 4) state.screen = Screen.PROCESSING
            Screen.PROCESSING -> {}
            Screen.RESULT -> if (state.approved) state.screen = Screen.RECEIPT else state.reset()
            Screen.RECEIPT -> state.reset()
            Screen.SERVICE_MENU -> {
                if (state.op == Op.CANCEL) {
                    if (state.statusLine == "CONFIRM") {
                        state.pin = ""
                        state.screen = Screen.PROCESSING
                    } else if (!state.shiftClosed && state.shift.isNotEmpty()) {
                        state.statusLine = "CONFIRM"
                    }
                } else when (state.serviceIndex) {
                    0 -> { state.statusLine = ""; state.screen = Screen.SETTLEMENT }
                    1 -> { state.statusLine = "REPORT"; state.screen = Screen.SETTLEMENT }
                    else -> {
                        val t = state.lastTxn
                        if (t == null) {
                            state.statusLine = "НЕТ ЧЕКА"
                        } else {
                            state.op = t.op
                            state.amountDigits = t.kopecks.toString()
                            state.cardTail = t.cardTail
                            state.authCode = t.authCode
                            state.approved = t.approved
                            state.screen = Screen.RECEIPT
                        }
                    }
                }
            }
            Screen.SETTLEMENT -> when (state.statusLine) {
                "" -> state.statusLine = "WORK"
                "WORK" -> {}
                else -> state.reset()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(BodyColor).systemBarsPadding().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(shape).background(ScreenBg)
                .border(3.dp, Color(0xFF555A60), shape).padding(14.dp)
        ) {
            AnimatedContent(
                targetState = state.screen,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "screen"
            ) { screen ->
                Column(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.IDLE -> IdleScreen()
                        Screen.MENU -> {
                            Line("ГЛАВНОЕ МЕНЮ", Accent, 20, true)
                            Items(listOf("Оплата", "Возврат", "Отмена", "Служебные операции"), state.menuIndex)
                        }
                        Screen.AMOUNT -> {
                            Line(state.op.title, Accent, 20, true)
                            Line("Введите сумму:")
                            Spacer(Modifier.height(16.dp))
                            Line(money(state.kopecks), Accent, 28, true)
                        }
                        Screen.TAP_CARD -> TapScreen(state, nfcAvailable, nfcEnabled) {
                            vibrate()
                            cardTapped(state, (1000..9999).random().toString())
                        }
                        Screen.REFUND_CARD_DIGITS -> {
                            Line("ВОЗВРАТ", Accent, 20, true)
                            Line("Введите 4 последние цифры карты:")
                            Spacer(Modifier.height(16.dp))
                            Line(state.pin.padEnd(4, '_'), Accent, 28, true)
                        }
                        Screen.PIN -> {
                            Line(money(state.kopecks), Accent, 22, true)
                            Line("Введите ПИН:")
                            Spacer(Modifier.height(16.dp))
                            Line("*".repeat(state.pin.length).padEnd(4, '_'), Accent, 28, true)
                            Line("ВВОД - подтвердить", ScreenFg, 14)
                        }
                        Screen.PROCESSING -> {
                            Line(state.op.title, Accent, 20, true)
                            Spacer(Modifier.height(16.dp))
                            Line(state.statusLine, ScreenFg, 18)
                            Spacer(Modifier.height(16.dp))
                            Dots()
                        }
                        Screen.RESULT -> ResultScreen(state)
                        Screen.RECEIPT -> Receipt(state)
                        Screen.SERVICE_MENU -> ServiceScreen(state)
                        Screen.SETTLEMENT -> SettlementScreen(state)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("ОТМЕНА", Color(0xFFC62828), Modifier.weight(1f)) { onCancel() }
            Key("ИСПР.", Color(0xFFF9A825), Modifier.weight(1f), Color.Black) { onFix() }
            Key("ВВОД", Color(0xFF2E7D32), Modifier.weight(1f)) { onEnter() }
        }
        val gray = Color(0xFF4A4F57)
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d -> Key(d, gray, Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("▲", gray, Modifier.weight(1f)) { onMove(-1) }
            Key("0", gray, Modifier.weight(1f)) { onDigit("0") }
            Key("▼", gray, Modifier.weight(1f)) { onMove(1) }
        }
    }
}

@Composable
private fun Key(
    label: String,
    bg: Color,
    modifier: Modifier = Modifier,
    fg: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Line(
    t: String,
    c: Color = ScreenFg,
    size: Int = 18,
    bold: Boolean = false,
    center: Boolean = false
) {
    Text(
        t, color = c, fontFamily = Mono, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Dots() {
    val a by rememberInfiniteTransition(label = "dots").animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a"
    )
    Text("●  ●  ●", color = Accent, fontSize = 22.sp, modifier = Modifier.alpha(a))
}

@Composable
private fun IdleScreen() {
    var now by remember { mutableStateOf(nowStr()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowStr()
            delay(1000)
        }
    }
    Spacer(Modifier.height(30.dp))
    Line("Вставьте,", ScreenFg, 24, true, true)
    Line("приложите карту", ScreenFg, 24, true, true)
    Spacer(Modifier.height(24.dp))
    Line(now, Accent, 18, false, true)
    Spacer(Modifier.weight(1f))
    Line("ВВОД - меню", ScreenFg, 14, false, true)
}

@Composable
private fun TapScreen(s: TerminalState, nfcAvailable: Boolean, nfcEnabled: Boolean, onSim: () -> Unit) {
    val a by rememberInfiniteTransition(label = "tap").animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )
    Line(s.op.title + "  " + money(s.kopecks), Accent, 20, true)
    Spacer(Modifier.height(12.dp))
    Line("Приложите, вставьте или проведите карту", ScreenFg, 18)
    Spacer(Modifier.height(16.dp))
    Text(
        "))) КАРТА (((", color = Accent, fontFamily = Mono, fontSize = 24.sp,
        modifier = Modifier.fillMaxWidth().alpha(a), textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    when {
        !nfcAvailable -> Line("NFC в телефоне нет", Color(0xFFFF8A80), 14)
        !nfcEnabled -> Line("Включите NFC в настройках", Color(0xFFFF8A80), 14)
        else -> Line("NFC готов: приложите карту к задней крышке", ScreenFg, 14)
    }
    Spacer(Modifier.weight(1f))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1F3B27))
            .clickable { onSim() }.padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Имитация: приложить карту", color = ScreenFg, fontSize = 14.sp)
    }
}

@Composable
private fun ResultScreen(s: TerminalState) {
    val sc = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { sc.animateTo(1f, spring(dampingRatio = 0.35f)) }
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth().scale(sc.value), contentAlignment = Alignment.Center) {
        Text(
            if (s.approved) "ОДОБРЕНО" else "ОТКАЗАНО",
            color = if (s.approved) Color(0xFF69F0AE) else Color(0xFFFF8A80),
            fontFamily = Mono, fontSize = 32.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(16.dp))
    if (s.approved) {
        Line("Код авторизации: " + s.authCode, ScreenFg, 16, false, true)
    } else {
        Line(s.declineReason, Accent, 18, true, true)
        Spacer(Modifier.weight(1f))
        Line("ВВОД - выход", ScreenFg, 14, false, true)
    }
}

@Composable
private fun Receipt(s: TerminalState) {
    val lines = listOf(
        "ТЕРМИНАЛ 00012345",
        "(учебный макет)",
        s.lastTxn?.time ?: nowStr(),
        s.op.title,
        "СУММА: " + money(s.kopecks),
        "КАРТА: **** " + s.cardTail,
        "КОД АВТ.: " + s.authCode,
        if (s.approved) "ОДОБРЕНО" else "ОТКАЗАНО"
    )
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (shown < lines.size) {
            delay(250)
            shown++
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5F5F0)).padding(10.dp)
    ) {
        lines.take(shown).forEach { l ->
            Text(l, color = Color.Black, fontFamily = Mono, fontSize = 15.sp)
        }
    }
    Spacer(Modifier.weight(1f))
    if (shown >= lines.size) Line("ВВОД - выход", ScreenFg, 14, false, true)
}

@Composable
private fun ServiceScreen(s: TerminalState) {
    if (s.op == Op.CANCEL) {
        Line("ОТМЕНА ОПЕРАЦИИ", Accent, 20, true)
        when {
            s.shiftClosed -> Line("Смена закрыта, отмена недоступна", Color(0xFFFF8A80), 16)
            s.shift.isEmpty() -> Line("Нет операций в смене", Color(0xFFFF8A80), 16)
            s.statusLine == "CONFIRM" -> {
                Spacer(Modifier.height(12.dp))
                Line("Отменить операцию?", ScreenFg, 20, true)
                Line("ВВОД - ДА", Color(0xFF69F0AE), 18)
                Line("ОТМЕНА - НЕТ", Color(0xFFFF8A80), 18)
            }
            else -> Items(s.shift.map { it.time.takeLast(5) + " " + money(it.kopecks) }, s.serviceIndex)
        }
    } else {
        Line("СЛУЖЕБНЫЕ ОПЕРАЦИИ", Accent, 20, true)
        Items(listOf("Сверка итогов", "Отчёт по смене", "Повтор чека"), s.serviceIndex)
        if (s.statusLine == "НЕТ ЧЕКА") Line("Нет чека для повтора", Color(0xFFFF8A80), 14)
    }
}

@Composable
private fun SettlementScreen(s: TerminalState) {
    when (s.statusLine) {
        "" -> {
            Line("СВЕРКА ИТОГОВ", Accent, 20, true)
            Spacer(Modifier.height(12.dp))
            Line("Закрыть смену?", ScreenFg, 20)
            Line("ВВОД - да, ОТМЕНА - нет", ScreenFg, 14)
        }
        "WORK" -> {
            Line("СВЕРКА ИТОГОВ", Accent, 20, true)
            Spacer(Modifier.height(16.dp))
            Line("Печать отчёта…", ScreenFg, 18)
            Spacer(Modifier.height(16.dp))
            Dots()
        }
        else -> {
            val total = s.shift.sumOf { if (it.op == Op.REFUND) -it.kopecks else it.kopecks }
            Line(if (s.statusLine == "DONE") "СВЕРКА ЗАВЕРШЕНА" else "ОТЧЁТ ПО СМЕНЕ", Accent, 20, true)
            Spacer(Modifier.height(12.dp))
            Line("Операций: " + s.shift.size, ScreenFg, 18)
            Line("Итого: " + money(total), ScreenFg, 18)
            if (s.statusLine == "DONE") Line("Смена закрыта", ScreenFg, 16)
            Spacer(Modifier.weight(1f))
            Line("ВВОД - выход", ScreenFg, 14, false, true)
        }
    }
}

@Composable
private fun Items(items: List<String>, sel: Int) {
    val start = (sel - 5).coerceAtLeast(0)
    items.drop(start).take(6).forEachIndexed { i, t ->
        val on = i + start == sel
        Line((if (on) "> " else "  ") + t, if (on) Accent else ScreenFg, 20)
    }
}
