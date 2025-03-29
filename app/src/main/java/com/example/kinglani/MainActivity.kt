package com.example.kinglani

import android.Manifest
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.nfc.tech.MifareClassic

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val nfcIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(nfcIntentFilter)
        techListsArray = arrayOf(arrayOf(MifareClassic::class.java.name))

        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.setOnClickListener {
            isScanning = true
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techListsArray)
            Toast.makeText(this, "NFC 태그를 스캔해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            handleNfcIntent(intent)
            isScanning = false
        }
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            val mifareClassic = MifareClassic.get(it)
            val uid: ByteArray = it.id
            val uidHex = uid.joinToString(":") { byte -> "%02X".format(byte) }
            val uidDec = uid.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }

            mifareClassic?.let { mfc ->
                try {
                    mfc.connect()
                    Toast.makeText(this, "UID : ${uidDec}", Toast.LENGTH_SHORT).show()
                    mfc.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "태그 읽기 실패", Toast.LENGTH_SHORT).show()
                }
                updateScanButtonState()
            }
        }
    }

    private fun updateScanButtonState() {
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.text = "대여 중"
        // 필요하다면 버튼 색상 등 다른 UI 요소도 업데이트
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}