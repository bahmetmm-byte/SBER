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

private val BodyColor = Color(0xFF2B2E33)
private val ScreenBg = Color(0xFF0E1A12)
private val ScreenFg = Color(0xFFB8F5B8)
private val Accent = Color(0xFFFFE066)
private val Mono = FontFamily.Monospace

private fun money(k: Long): String {
    val r = k / 100
    val c = (k % 100).toInt()
    return "%d,%02d руб.".format(r, c)
}

private fun nowStr(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

private fun randomAuth(): String = (100000..999999).random().toString()

@Composable
fun TerminalApp(
    state: TerminalState,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    tappedTag: String?,
    onTapConsumed: () -> Unit,
    vibrate: () -> Unit...
