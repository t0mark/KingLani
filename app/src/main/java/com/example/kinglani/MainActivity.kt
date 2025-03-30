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
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
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
import com.kakao.vectormap.label.LabelTextBuilder


class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private var isScanning = false
    private var isRented = false  // 스쿠터 대여 상태 추적
    private var isReturning = false  // 반납 모드인지 추적
    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap
    private lateinit var locationManager: LocationManager
    private var labelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null
    private var loadingDialog: AlertDialog? = null
    private var isParkingAvailable = false  // 주차 가능 여부 추적

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
            if (!isRented) {
                // 대여 모드
                initiateRental()
            } else {
                // 반납 모드
                initiateReturn()
            }
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

    // 대여 시작 함수
    private fun initiateRental() {
        isScanning = true
        isReturning = false

        // 로딩 상태 표시
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.isEnabled = false  // 버튼 비활성화
        btnScan.text = "스캔 중..."  // 텍스트 변경

        // 내장 프로그레스 다이얼로그 표시
        showLoadingDialog("대여를 위해 NFC 태그를 스캔해주세요.")

        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techListsArray)
        Toast.makeText(this, "NFC 태그를 스캔해주세요.", Toast.LENGTH_SHORT).show()
    }

    // 반납 시작 함수
    private fun initiateReturn() {
        if (!isParkingAvailable) {
            // 주차 불가능 구역인 경우
            Toast.makeText(this, "주차 가능 구역으로 이동해주세요.", Toast.LENGTH_LONG).show()

            val ivScooterStatus = findViewById<ImageView>(R.id.iv_scooter_status)

            // 빨간색으로 변경
            ivScooterStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light),
                PorterDuff.Mode.SRC_IN)

            // 2초 후에 다시 초록색으로 변경
            Handler(Looper.getMainLooper()).postDelayed({
                ivScooterStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light),
                    PorterDuff.Mode.SRC_IN)
            }, 2000) // 2000 밀리초 = 2초

            return
        }

        isScanning = true
        isReturning = true

        // 로딩 상태 표시
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.isEnabled = false  // 버튼 비활성화
        btnScan.text = "스캔 중..."  // 텍스트 변경

        // 내장 프로그레스 다이얼로그 표시
        showLoadingDialog("반납을 위해 NFC 태그를 스캔해주세요.")

        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techListsArray)
        Toast.makeText(this, "NFC 태그를 스캔해주세요.", Toast.LENGTH_SHORT).show()
    }

    // 로딩 다이얼로그 표시 함수
    private fun showLoadingDialog(message: String = "NFC 태그를 스캔해주세요...") {
        // 기존 다이얼로그가 있으면 닫기
        dismissLoadingDialog()

        // ProgressBar 생성
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }

        // AlertDialog 생성
        val builder = AlertDialog.Builder(this)
        builder.setTitle("처리 중")
            .setMessage(message)
            .setView(progressBar)
            .setCancelable(false)  // 뒤로가기 버튼으로 취소 불가능

        loadingDialog = builder.create()
        loadingDialog?.show()
    }

    // 로딩 다이얼로그 닫기 함수
    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
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
                1000,  // 최소 시간 간격 (밀리초) - 1초
                5f,   // 최소 거리 간격 (미터) - 5미터
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

    // updateMapWithLocation 메서드를 수정합니다
    private fun updateMapWithLocation(location: Location) {
        val currentPosition = LatLng.from(location.latitude, location.longitude)

        // 카카오맵이 초기화되지 않았으면 리턴
        if (!::kakaoMap.isInitialized) return

        // 주차 가능 구역 좌표 (지정된 위치)
        // 평양
        // val parkingZoneLatitude = 39.032
        // val parkingZoneLongitude = 125.75

        // 연구실 위도: 35.846428, 경도: 127.133398
         val parkingZoneLatitude = 35.846428
         val parkingZoneLongitude = 127.133398

        // 현재 위치와 주차 가능 구역 사이의 거리 계산 (미터 단위)
        val distance = calculateDistance(
            location.latitude, location.longitude,
            parkingZoneLatitude, parkingZoneLongitude
        )

        // 주차 가능 반경 (미터)
        val parkingRadius = 20.0  // 50미터 반경 내에 있으면 주차 가능

        // 주차 가능 여부 업데이트
        isParkingAvailable = distance <= parkingRadius

        // GPS 정보 텍스트뷰 업데이트 - 위치 정보와 주차 가능 여부 표시
        val tvGpsInfo = findViewById<TextView>(R.id.tv_gps_info)
        val parkingStatus = if (isParkingAvailable) "주차 가능" else "주차 불가능"
        tvGpsInfo.text = "위치: 위도 ${location.latitude.format(6)}, 경도 ${location.longitude.format(6)}\n$parkingStatus"

        // 대여 중이고 버튼이 활성화된 상태라면 버튼 텍스트 업데이트
        if (isRented) {
            val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
            if (btnScan.isEnabled) {
                btnScan.text = "반납하기"
            }
        }

        try {
            // labelLayer가 null인 경우 다시 가져오기
            if (labelLayer == null) {
                labelLayer = kakaoMap.labelManager?.layer
            }

            // labelLayer가 null이 아닌 경우에만 작업 수행
            labelLayer?.let { layer ->
                // 기존 현재 위치 라벨이 있으면 삭제
                currentLocationLabel?.let { label ->
                    layer.remove(label)
                }

                // 마커 생성 - 기본 아이콘 사용 (SDK 내장)
                val markerStyle = LabelStyle.from(android.R.drawable.ic_menu_myplaces)
                val labelStyles = LabelStyles.from(markerStyle)
                val labelOptions = LabelOptions.from(currentPosition)
                    .setStyles(labelStyles)

                // 로그 추가
                Log.d("MainActivity", "마커 추가 시도: $currentPosition")

                // 라벨 추가하고 객체 저장
                currentLocationLabel = layer.addLabel(labelOptions)

                // 로그 추가
                Log.d("MainActivity", "마커 추가 완료: ${currentLocationLabel != null}")
            }
        } catch (e: Exception) {
            // 예외가 발생할 경우 로그에 기록
            Log.e("MainActivity", "마커 추가 실패", e)
        }

        // 카메라 현재 위치로 이동
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(currentPosition, 15))
    }

    // 두 위치 간의 거리를 계산하는 함수 (Haversine 공식 사용)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // 지구 반지름 (미터)

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    // Double 포맷팅 확장 함수 추가
    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    // 위치 마커 스타일 생성 함수 추가
    private fun getLocationMarkerStyle(): LabelStyles {
        // 앱에 있는 킥보드 아이콘 사용
        val defaultStyle = LabelStyle.from(R.drawable.ic_scooter)
            .setAnchorPoint(0.5f, 1.0f)  // 마커의 하단 중앙이 좌표에 위치하도록 설정
            .setZoomLevel(5)

        return LabelStyles.from(defaultStyle)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 위치 업데이트 중지
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
        }
        // 로딩 다이얼로그 닫기
        dismissLoadingDialog()
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
            val uidDec = uid.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }

            mifareClassic?.let { mfc ->
                try {
                    mfc.connect()
                    // Toast.makeText(this, "UID : ${uidDec}", Toast.LENGTH_SHORT).show()
                    mfc.close()

                    if (isReturning) {
                        // 반납 처리
                        completeReturn()
                    } else {
                        // 대여 처리
                        completeRental()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "태그 읽기 실패", Toast.LENGTH_SHORT).show()

                    // 스캔 실패 - 원래 상태로 복귀
                    resetScanState()
                }
            }
        } ?: run {
            // 태그가 null인 경우 - 원래 상태로 복귀
            Toast.makeText(this, "태그를 인식할 수 없습니다.", Toast.LENGTH_SHORT).show()
            resetScanState()
        }
    }

    // 대여 완료 처리
    private fun completeRental() {
        // 로딩 다이얼로그 닫기
        dismissLoadingDialog()

        // 대여 상태로 변경
        isRented = true

        // 버튼 상태 업데이트
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.isEnabled = true
        btnScan.text = "반납하기"

        // 스쿠터 상태 이미지뷰 색상 변경 (초록색)
        val ivScooterStatus = findViewById<ImageView>(R.id.iv_scooter_status)
        ivScooterStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light),
            PorterDuff.Mode.SRC_IN)

        Toast.makeText(this, "대여가 완료되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 반납 완료 처리
    private fun completeReturn() {
        // 로딩 다이얼로그 닫기
        dismissLoadingDialog()

        // 대여 상태 해제
        isRented = false
        isReturning = false

        // 버튼 상태 업데이트
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.isEnabled = true
        btnScan.text = "대여하기"

        // 스쿠터 상태 이미지뷰 색상 변경 (회색 또는 원래 색상)
        val ivScooterStatus = findViewById<ImageView>(R.id.iv_scooter_status)
        ivScooterStatus.clearColorFilter()  // 색상 필터 제거

        Toast.makeText(this, "반납이 완료되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 스캔 실패 또는 취소 시 상태 초기화 함수
    private fun resetScanState() {
        isScanning = false
        isReturning = false

        // 로딩 다이얼로그 닫기
        dismissLoadingDialog()

        // 버튼 상태 복원
        val btnScan = findViewById<Button>(R.id.btn_nfc_scan)
        btnScan.isEnabled = true

        // 대여 상태에 따라 버튼 텍스트 설정
        if (isRented) {
            btnScan.text = "반납하기"
        } else {
            btnScan.text = "대여하기"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}