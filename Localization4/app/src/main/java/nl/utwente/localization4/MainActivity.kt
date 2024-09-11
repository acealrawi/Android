

package nl.utwente.localization4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp



data class BLEDevice(
    val name: String?,
    val uid: String,
    val rssi: Int,
    val distance: Double
)

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val scanPeriod: Long = 10000 // Scan for 10 seconds
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "BLE_SCAN"
    }

    // Registering the permission request using the new API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBleScan()
        } else {
            Log.d(TAG, "Permission denied.")
        }
    }

    // MutableState to hold scanned devices for UI
    private val scannedDevices = mutableStateListOf<BLEDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BluetoothAdapter
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Set content using Jetpack Compose
        setContent {
            BLEScannerUI(scannedDevices, ::startBleScan, ::stopBleScan)
        }
    }

    // Start scanning for BLE devices
    private fun startBleScan() {
        if (isScanning) return

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        // Stop scanning after a pre-defined scan period
        handler.postDelayed({
            stopBleScan()
        }, scanPeriod)

        isScanning = true
        scannedDevices.clear()  // Clear previous results
        bluetoothLeScanner?.startScan(leScanCallback)
        Log.d(TAG, "Started BLE scan")
    }

    // Stop scanning for BLE devices
    private fun stopBleScan() {
        if (!isScanning) return

        isScanning = false
        bluetoothLeScanner?.stopScan(leScanCallback)
        Log.d(TAG, "Stopped BLE scan")
    }

    // Function to calculate distance from RSSI using the path loss model. Note that this function uses some assumptions like th txPower
//    This method calculates the approximate distance to a BLE device using the RSSI and the logarithmic path loss formula.
//    The txPower is the reference RSSI value (usually around -59 dBm) at 1 meter.
//    The n value (path loss exponent) is set to 2.0, assuming free space, but you can adjust it based on the environment (e.g., indoor environments will have higher values).

    private fun calculateDistance(rssi: Int, txPower: Int = -59): Double {
        val n = 2.0 // Path loss exponent (free space)
        return Math.pow(10.0, (txPower - rssi) / (10 * n))
    }

    // ScanCallback to receive scan results
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device: BluetoothDevice = it.device
                val rssi = it.rssi
                val uid = device.address
                val distance = calculateDistance(rssi)

                // Add device information to the list
                scannedDevices.add(
                    BLEDevice(
                        name = device.name ?: "Unknown",
                        uid = uid,
                        rssi = rssi,
                        distance = distance
                    )
                )

                Log.d(TAG, "Device found: Name: ${device.name ?: "Unknown"}, UID: $uid, RSSI: $rssi, Approx. Distance: $distance meters")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult?>) {
            for (result in results) {
                result?.let {
                    val device: BluetoothDevice = it.device
                    val rssi = it.rssi
                    val uid = device.address
                    val distance = calculateDistance(rssi)

                    // Add device information to the list
                    scannedDevices.add(
                        BLEDevice(
                            name = device.name ?: "Unknown",
                            uid = uid,
                            rssi = rssi,
                            distance = distance
                        )
                    )

                    Log.d(TAG, "Device found in batch: Name: ${device.name ?: "Unknown"}, UID: $uid, RSSI: $rssi, Approx. Distance: $distance meters")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

    @Composable
    fun BLEScannerUI(
        scannedDevices: List<BLEDevice>,
        startScan: () -> Unit,
        stopScan: () -> Unit
    ) {
        var scanningState by remember { mutableStateOf(isScanning) }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Button(
                onClick = {
                    if (scanningState) {
                        stopScan()
                    } else {
                        startScan()
                    }
                    scanningState = !scanningState
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(text = if (scanningState) "Stop Scan" else "Start Scan")
            }

            // Display the scanned devices
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedDevices) { device ->
                    DeviceRow(device)
                }
            }
        }
    }

    @Composable
    fun DeviceRow(device: BLEDevice) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(text = "Name: ${device.name}")
            Text(text = "UID: ${device.uid}")
            Text(text = "RSSI: ${device.rssi}")
            Text(text = "Distance: %.2f meters".format(device.distance))
        }
    }

    @Preview
    @Composable
    fun PreviewBLEScannerUI() {
        val sampleDevices = listOf(
            BLEDevice("Device 1", "00:11:22:33:44:55", -65, 1.5),
            BLEDevice("Device 2", "66:77:88:99:AA:BB", -75, 2.3)
        )
        BLEScannerUI(sampleDevices, {}, {})
    }
}

//import android.Manifest
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.bluetooth.BluetoothManager
//import android.bluetooth.le.BluetoothLeScanner
//import android.bluetooth.le.ScanCallback
//import android.bluetooth.le.ScanResult
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.Column
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//
//
//class MainActivity : ComponentActivity() {
//
//    private lateinit var bluetoothAdapter: BluetoothAdapter
//    private var bluetoothLeScanner: BluetoothLeScanner? = null
//    private var isScanning = false
//    private val scanPeriod: Long = 10000 // Scan for 10 seconds
//    private val handler = Handler(Looper.getMainLooper())
//
//    companion object {
//        private const val TAG = "BLE_SCAN"
//    }
//
//    // Registering the permission request using the new API
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            startBleScan()
//        } else {
//            Log.d(TAG, "Permission denied.")
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Initialize BluetoothAdapter
//        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
//        bluetoothAdapter = bluetoothManager.adapter
//        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
//
//        // Set content using Jetpack Compose
//        setContent {
//            BLEScannerUI()
//        }
//    }
//
//    // Start scanning for BLE devices
//    private fun startBleScan() {
//        if (isScanning) return
//
//        // Check for location permission
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//            != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//            return
//        }
//
//        // Stop scanning after a pre-defined scan period
//        handler.postDelayed({
//            stopBleScan()
//        }, scanPeriod)
//
//        isScanning = true
//        bluetoothLeScanner?.startScan(leScanCallback)
//        Log.d(TAG, "Started BLE scan")
//    }
//
//    // Stop scanning for BLE devices
//    private fun stopBleScan() {
//        if (!isScanning) return
//
//        isScanning = false
//        bluetoothLeScanner?.stopScan(leScanCallback)
//        Log.d(TAG, "Stopped BLE scan")
//    }
//
//    // ScanCallback to receive scan results
//    private val leScanCallback = object : ScanCallback() {
//        override fun onScanResult(callbackType: Int, result: ScanResult?) {
//            result?.let {
//                val device: BluetoothDevice = it.device
//                Log.d(TAG, "Device found: ${device.name} - ${device.address}")
//            }
//        }
//
//        override fun onBatchScanResults(results: List<ScanResult?>) {
//            for (result in results) {
//                result?.device?.let { device ->
//                    Log.d(TAG, "Device found in batch: ${device.name} - ${device.address}")
//                }
//            }
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            Log.e(TAG, "Scan failed with error code: $errorCode")
//        }
//    }
//
//    @Composable
//    fun BLEScannerUI() {
//        val scanningState = remember { mutableStateOf(isScanning) }
//
//        Column {
//            Text(text = if (scanningState.value) "Scanning..." else "Not Scanning")
//            Button(onClick = {
//                if (scanningState.value) {
//                    stopBleScan()
//                } else {
//                    startBleScan()
//                }
//                scanningState.value = !scanningState.value
//            }) {
//                Text(text = if (scanningState.value) "Stop Scan" else "Start Scan")
//            }
//        }
//    }
//
//    @Preview
//    @Composable
//    fun PreviewBLEScannerUI() {
//        BLEScannerUI()
//    }
//}
