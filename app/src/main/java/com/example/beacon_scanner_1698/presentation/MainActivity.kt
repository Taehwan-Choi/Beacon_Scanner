package com.example.beacon_scanner_1698.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback

    private var wakeLock: PowerManager.WakeLock? = null

    private val scannedDevices = mutableStateListOf<String>()
    private var scanRunnable: Runnable? = null
    private lateinit var handler: Handler
    private var isScanning = false



    private var scanStartTime: Long = 0

    private var max0FTime: Long = 0
    private var max1FTime: Long = 0
    private var max2FTime: Long = 0
    private var max3FTime: Long = 0

    private var max0Frssi: Int = -100
    private var max1Frssi: Int = -100
    private var max2Frssi: Int = -100
    private var max3Frssi: Int = -100









    companion object {
        private lateinit var appContext: Context

        fun getAppContext(): Context {
            return appContext
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appContext = applicationContext

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp::KeepScreenOn"
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestBluetoothPermission()

        if (permissionArray.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

            scanCallback = createScanCallback()

            handler = Handler(Looper.getMainLooper())

            setContent {
                MainScreen(bluetoothLeScanner)
            }
        }
    }

    private val permissionArray = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private fun requestBluetoothPermission() {
        if (permissionArray.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            Log.d("BLE_MYLOG", "permission granted")
        } else {
            ActivityCompat.requestPermissions(this, permissionArray, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        wakeLock?.acquire(1000 * 60 * 60)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        wakeLock?.release()
    }

    override fun onDestroy() {
        if (permissionArray.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            bluetoothLeScanner.flushPendingScanResults(scanCallback)
            bluetoothLeScanner.stopScan(scanCallback)
        }
        super.onDestroy()
    }

    @Composable
    fun MainScreen(bluetoothLeScanner: BluetoothLeScanner) {
        val context = LocalContext.current
        var scanning by remember { mutableStateOf(false) }

        LaunchedEffect(scanning) {
            if (scanning) {
                scanRunnable = startScanning(context, bluetoothLeScanner, handler)
            } else {
                stopScanning(context, bluetoothLeScanner, handler, scanRunnable)
                scanRunnable = null
            }
        }

        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (scanning) "Scanning..." else "Bluetooth Scanner", color = Color.White)
            Button(onClick = {
                scanning = !scanning

                if (scanning) {
                    scanStartTime = System.currentTimeMillis()
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scanStartTime))
                    Log.d("BLE_MYLOG", "Scan started at: $formattedDate")
                }


            }) {
                Text(text = if (scanning) "Stop Scan" else "Start Scan", color = Color.White)


            }
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                val lastDeviceInfo = scannedDevices.lastOrNull()
                if (lastDeviceInfo != null) {
                    Text(text = lastDeviceInfo, color = Color.White)
                } else {
                    val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    Text(text = "Time: $currentTime, Not detected", color = Color.White)
                }
            }
        }
    }

    private fun startScanning(
        context: Context,
        bluetoothLeScanner: BluetoothLeScanner,
        handler: Handler
    ): Runnable {
        if (isScanning) {
            Log.d("BLE_MYLOG", "Already scanning")
            return Runnable {}
        }

        Log.d("BLE_MYLOG", "Start Scanning")

        val scanRunnable = object : Runnable {
            override fun run() {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val scanSettings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()

                    val scanFilters = listOf(ScanFilter.Builder().build())

                    handler.post {
                        bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
                        isScanning = true
                    }

                    Log.d("BLE_MYLOG", "scanRunnable created")
                }
            }
        }

        handler.post(scanRunnable)

        return scanRunnable
    }

    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val rssi = result.rssi
                val scanRecord = result.scanRecord
                val deviceName = scanRecord?.deviceName
                val timestamp = result.timestampNanos

                val deviceAddress = result.device.address

                val deviceNum = when (deviceAddress) {
//테스트용 2개
                    "C7:9B:B1:1F:96:2B" -> "0"
                    "CE:ED:15:41:12:4A" -> "0"
//시점부
                    "FC:FC:F3:80:FC:6C" -> "1"
                    "E2:52:D8:6f:0D:E0" -> "1"
//0F
                    "D4:9E:2C:82:CA:C2" -> "2"
                    "FD:59:4E:E4:90:5A" -> "2"
//1F
                    "E1:7C:49:A9:44:47" -> "3"
                    "C1:57:CB:B2:4F:2C" -> "3"
//2F
                    "FE:0D:C5:67:D2:1A" -> "4"
                    "C6:0A:94:3C:AD:B6" -> "4"
//3F
                    "CA:F3:16:9D:86:61" -> "5"
                    "F9:03:56:63:15:85" -> "5"
//종점부
                    "DC:4A:14:06:36:26" -> "6"
                    "E1:F5:79:EE:09:9D" -> "6"

                    else -> "9"
                }



                val currentTimeMillis = System.currentTimeMillis()
                val elapsedTimeNanos = SystemClock.elapsedRealtimeNanos()
                val currentTimestamp = currentTimeMillis + (timestamp - elapsedTimeNanos) / 1000000L

                val date = Date(currentTimestamp)
                val formattedDate =
                    SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(date)


                val txPowerLevel = scanRecord?.txPowerLevel
//                값을 받아서 확인해보면 txPowerLevel은 1로 나타남


//                Log.d("BLE_MYLOG", "Device : $deviceName Time: $formattedDate, RSSI: $adjustedRssi")
//                scannedDevices.add("Time: $formattedDate, RSSI: $adjustedRssi")


//-85 이하의 신호는 선별적으로 제한
                if ((deviceName == "Plutocon Pro") and (rssi >= -85)) {


                    

                    //각 위치에 도달햇을떄, 해당 구간의 가장 센 rssi 이상일 경우 통과시간을 갱신하는 방식

                    if ((deviceNum == "2") and (rssi > max0Frssi)){
                        max0Frssi = rssi
                        max0FTime = System.currentTimeMillis()
                    }

                    if ((deviceNum == "3") and (rssi > max1Frssi)){
                        max1Frssi = rssi
                        max1FTime = System.currentTimeMillis()
                    }

                    if ((deviceNum == "4") and (rssi > max2Frssi)){
                        max2Frssi = rssi
                        max2FTime = System.currentTimeMillis()
                    }

                    if ((deviceNum == "5") and (rssi > max3Frssi)){
                        max3Frssi = rssi
                        max3FTime = System.currentTimeMillis()
                    }




//종점에 도달했을 경우, 0f,1f,2f,3f시간이 모두 존재한다면 최종적인 구간기록을 저장하는 로직
                    if ((deviceNum == "6") and (max0FTime > 0) and (max1FTime > 0) and (max2FTime > 0) and (max3FTime > 0)){

                        val f1Time = max1FTime - max0FTime
                        val f2Time = max2FTime - max1FTime
                        val f3Time = max3FTime - max2FTime

                        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scanStartTime))


                        val result = "$formattedDate, $f1Time, $f2Time, $f3Time, $max0Frssi, $max0FTime, $max1Frssi, $max1FTime, $max2Frssi, $max2FTime, $max3Frssi, $max3FTime"


//                    최종적인 결과값 저장
                        saveToExternalCsv("final_result.csv", result)

//                    저장한 후 값을 다시 초기화하기
                        max0FTime = 0
                        max1FTime = 0
                        max2FTime = 0
                        max3FTime = 0

                        max0Frssi = -100
                        max1Frssi = -100
                        max2Frssi = -100
                        max3Frssi = -100
                    }




//                시점부에 왔을 경우, 모든 통과기록 기준을 초기화하는 작업
//                내려오는 동안에 찍힌 시간이 초기화 되지 않으면 반복 조교시에 에러가 생기므로
//                또한, 시점부에 있는 내내 계속 초기화할 필요는 없으므로 3f기록이 존재하는 조건 추가로 걸어줌
                    if ((deviceNum == "1") and (max3FTime > 0) ){
                        max0FTime = 0
                        max1FTime = 0
                        max2FTime = 0
                        max3FTime = 0

                        max0Frssi = -100
                        max1Frssi = -100
                        max2Frssi = -100
                        max3Frssi = -100
                    }



                    val deviceInfo = "$currentTimeMillis, $formattedDate, $deviceNum, $rssi, $deviceAddress"


                    Log.d("BLE_MYLOG", deviceInfo)
//                    디바이스 주소는 C7:9B:B1:1F:96:2B  // CE:ED:15:41:12:4A

                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scanStartTime))

                    saveToExternalCsv("$formattedDate.csv", deviceInfo)

//                    데이터 위치 : /storage/emulated/0/Android/data/com.example.beacon_scanner_1698/files

                    scannedDevices.add(deviceInfo)
                }





            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                super.onBatchScanResults(results)
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                if (errorCode != SCAN_FAILED_ALREADY_STARTED) {
                    Log.e("BLE_MYLOG", "Scan failed with error code: $errorCode")
                } else {
                    Log.d("BLE_MYLOG", "Scan failed with error code: $errorCode (Already started)")
                }
            }
        }
    }

    private fun stopScanning(
        context: Context,
        bluetoothLeScanner: BluetoothLeScanner,
        handler: Handler,
        scanRunnable: Runnable?
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.d("BLE_MYLOG", "Stop Scanning")
            scanRunnable?.let {
                handler.removeCallbacks(it)
            }
            bluetoothLeScanner.stopScan(scanCallback)
            isScanning = false

        }
    }


    fun saveToExternalCsv(fileName: String, data: String) {
        try {
            val file = File(getAppContext().getExternalFilesDir(null), fileName)
            val fileOutputStream = FileOutputStream(file, true)
            val bufferedWriter = BufferedWriter(OutputStreamWriter(fileOutputStream))

            bufferedWriter.append(data)
            bufferedWriter.newLine()
            bufferedWriter.close()
            fileOutputStream.close()

//            Log.d("BLE_MYLOG", "Data saved to $fileName")



        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}


