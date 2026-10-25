package com.example.sbermock

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

enum class Screen {
    IDLE, MENU, AMOUNT, TAP_CARD, PIN, PROCESSING, RESULT, RECEIPT,
    REFUND_CARD_DIGITS, SERVICE_MENU, SETTLEMENT
}

enum class Op(val title: String) {
    PAYMENT("ОПЛАТА"), REFUND("ВОЗВРАТ"), CANCEL("ОТМЕНА")
}

data class Txn(
    val op: Op, val kopecks: Long, val time: String,
    val cardTail: String, val authCode: String, val approved: Boolean
)

class TerminalState {
    var screen by mutableStateOf(Screen.IDLE)
    var op by mutableStateOf(Op.PAYMENT)
    var amountDigits by mutableStateOf("")
    var pin by mutableStateOf("")
    var cardTail by mutableStateOf("0000")
    var approved by mutableStateOf(true)
    var declineReason by mutableStateOf("")
    var authCode by mutableStateOf("000000")
    var statusLine by mutableStateOf("")
    var menuIndex by mutableStateOf(0)
    var serviceIndex by mutableStateOf(0)
    val shift = mutableStateListOf<Txn>()
    var lastTxn by mutableStateOf<Txn?>(null)
    var shiftClosed by mutableStateOf(false)

    val kopecks: Long get() = amountDigits.toLongOrNull() ?: 0L

    fun reset() {
        screen = Screen.IDLE; amountDigits = ""; pin = ""
        op = Op.PAYMENT; statusLine = ""
    }
}

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val state = TerminalState()
    private var nfc: NfcAdapter? = null
    private var nfcTapped by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        setContent {
            TerminalApp(
                state = state,
                nfcAvailable = nfc != null,
                nfcEnabled = nfc?.isEnabled == true,
                tappedTag = nfcTapped,
                onTapConsumed = { nfcTapped = null },
                vibrate = { vibrateShort() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        nfc?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }

    // Вызывается при касании карты. Берём только последние 4 цифры UID для чека.
    override fun onTagDiscovered(tag: Tag) {
        val hex = tag.id.joinToString("") { "%02X".format(it) }
        val tail = hex.filter { it.isDigit() }.padStart(4, '0').takeLast(4)
        runOnUiThread { nfcTapped = tail }
    }

    private fun vibrateShort() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
