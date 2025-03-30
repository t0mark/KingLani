package com.example.kinglani

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.nfc.tech.MifareClassic
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private var isScanning = false
    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap
    private lateinit var locationManager: LocationManager
    private var labelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null

    // 위치 권한 요청 코드 상수 선언
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

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

        KakaoMapSdk.init(this, "0e65354445b1cdd3b3617cdc58435c05")

        mapView = findViewById<MapView>(R.id.map_view)
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                // 맵이 소멸될 때 호출
            }

            override fun onMapError(error: Exception) {
                // 맵 로딩 중 에러 발생 시 호출
                error.printStackTrace()
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                // 맵이 준비되었을 때 호출
                kakaoMap = map

                // 줌 및 회전 제스처 활성화
                kakaoMap.setGestureEnable(GestureType.Zoom, true)
                kakaoMap.setGestureEnable(GestureType.Rotate, true)

                // 라벨 레이어 가져오기
                labelLayer = kakaoMap.labelManager?.layer

                // 위치 권한 확인 및 요청
                checkLocationPermission()
            }
        })
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없는 경우 권한 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // 이미 권한이 있는 경우 위치 업데이트 시작
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 허용된 경우 위치 업데이트 시작
                startLocationUpdates()
            }
        }
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            // 위치 업데이트 리스너 설정
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,  // 최소 시간 간격 (밀리초) - 5초
                10f,   // 최소 거리 간격 (미터) - 10미터
                locationListener
            )

            // 마지막으로 알려진 위치 가져오기
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            lastKnownLocation?.let {
                updateMapWithLocation(it)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // 위치가 변경되면 맵 업데이트
            updateMapWithLocation(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    // updateMapWithLocation 메서드 내부 수정
    private fun updateMapWithLocation(location: Location) {
        val currentPosition = LatLng.from(location.latitude, location.longitude)

        // 라벨 스타일 생성
        if (!::kakaoMap.isInitialized) return

        // labelLayer가 null이 아닌 경우에만 작업 수행
        labelLayer?.let { layer ->
            // 기존 현재 위치 라벨이 있으면 삭제
            currentLocationLabel?.let { label ->
                layer.remove(label)  // Label 객체를 직접 전달
            }

            // 현재 위치에 마커 추가
            val markerStyle = LabelStyles.from(LabelStyle.from(R.drawable.ic_current_location))
            val labelOptions = LabelOptions.from(currentPosition)
                .setStyles(markerStyle)

            // 라벨 추가하고 객체 저장
            currentLocationLabel = layer.addLabel(labelOptions)
        }

        // 카메라 현재 위치로 이동
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(currentPosition, 15))
    }

    override fun onDestroy() {
        super.onDestroy()
        // 위치 업데이트 중지
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
        }
        // 맵 종료
        mapView.finish()
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