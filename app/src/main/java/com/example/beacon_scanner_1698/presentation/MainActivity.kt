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
            Button(onClick = { scanning = !scanning }) {
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

                val currentTimeMillis = System.currentTimeMillis()
                val elapsedTimeNanos = SystemClock.elapsedRealtimeNanos()
                val currentTimestamp = currentTimeMillis + (timestamp - elapsedTimeNanos) / 1000000L

                val date = Date(currentTimestamp)
                val formattedDate =
                    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(date)

//                val adjustedRssi = rssi

                val txPowerLevel = scanRecord?.txPowerLevel
//                값을 받아서 확인해보면 txPowerLevel은 1로 나타남


//                Log.d("BLE_MYLOG", "Device : $deviceName Time: $formattedDate, RSSI: $adjustedRssi")
//                scannedDevices.add("Time: $formattedDate, RSSI: $adjustedRssi")

                if (deviceName == "Plutocon Pro") {
                    val deviceInfo = "Time: $formattedDate, RSSI: $rssi"
                    Log.d("BLE_MYLOG", deviceInfo)

                    saveToExternalCsv("ble_data.csv", deviceInfo)


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

            Log.d("BLE_MYLOG", "Data saved to $fileName")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}


