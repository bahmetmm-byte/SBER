package com.example.sbermock

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun Key(
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
internal fun Line(
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
internal fun Dots() {
    val a by rememberInfiniteTransition(label = "dots").animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a"
    )
    Text("●  ●  ●", color = Accent, fontSize = 22.sp, modifier = Modifier.alpha(a))
}

@Composable
internal fun Items(items: List<String>, sel: Int) {
    val start = (sel - 5).coerceAtLeast(0)
    items.drop(start).take(6).forEachIndexed { i, t ->
        val on = i + start == sel
        Line((if (on) "> " else "  ") + t, if (on) Accent else ScreenFg, 20)
    }
}

@Composable
internal fun ColumnScope.IdleScreen() {
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
internal fun ColumnScope.TapScreen(
    s: TerminalState,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    onSim: () -> Unit
) {
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
internal fun ColumnScope.ResultScreen(s: TerminalState) {
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
internal fun ColumnScope.Receipt(s: TerminalState) {
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
internal fun ServiceScreen(s: TerminalState) {
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
internal fun ColumnScope.SettlementScreen(s: TerminalState) {
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
