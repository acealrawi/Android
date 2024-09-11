package nl.utwente.localization

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.pow

data class BluetoothDeviceInfo(
    val id: String,
    val name: String,
    val rssi: Int,
    val estimatedDistance: Double?,
    val txPower: Int?,
    val type: DeviceType,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class DeviceType {
    BLE, BEACON, CLASSIC
}

class BluetoothScannerViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _isBluetoothOn = MutableStateFlow(false)
    val isBluetoothOn = _isBluetoothOn.asStateFlow()

    private val _scanningStatus = MutableStateFlow("Not scanning")
    val scanningStatus = _scanningStatus.asStateFlow()

    private var isScanning = false

    fun updateBluetoothStatus(isOn: Boolean) {
        _isBluetoothOn.value = isOn
    }

    fun startScanning(context: Context) {
        if (isScanning) return
        isScanning = true
        _scanningStatus.value = "Scanning..."

        // Start BLE scanning
        val bleScanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB"))) // Example: Battery Service UUID
                .build()
        )

        bleScanner.startScan(scanFilters, scanSettings, bleScanCallback)

        // Start Beacon scanning
        val messageClient = Nearby.getMessagesClient(context)
        val messageRequest = MessageFilter.Builder()
            .includeAllMyTypes()
            .build()

        messageClient.subscribe(messageListener)

        // Start Classic Bluetooth scanning
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bluetoothAdapter.startDiscovery()
    }

    fun stopScanning(context: Context) {
        if (!isScanning) return
        isScanning = false
        _scanningStatus.value = "Scan stopped"

        val bleScanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter.bluetoothLeScanner
        bleScanner.stopScan(bleScanCallback)

        val messageClient = Nearby.getMessagesClient(context)
        messageClient.unsubscribe(messageListener)

        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bluetoothAdapter.cancelDiscovery()
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = BluetoothDeviceInfo(
                id = result.device.address,
                name = result.device.name ?: "Unknown BLE Device",
                rssi = result.rssi,
                estimatedDistance = calculateDistance(result.rssi, result.txPower ?: -59),
                txPower = result.txPower,
                type = DeviceType.BLE
            )
            updateDevice(device)
        }
    }

    private val messageListener = object : MessageListener() {
        override fun onFound(message: Message) {
            val device = BluetoothDeviceInfo(
                id = String(message.content),
                name = "Beacon",
                rssi = 1,
                estimatedDistance = 1.0,
                txPower = -59,
                type = DeviceType.BEACON
            )
            updateDevice(device)
        }

        override fun onLost(message: Message) {
            removeDevice(String(message.content))
        }
    }

    fun onClassicDeviceFound(device: BluetoothDevice, rssi: Int) {
        val bluetoothDeviceInfo = BluetoothDeviceInfo(
            id = device.address,
            name = device.name ?: "Unknown Classic Device",
            rssi = rssi,
            estimatedDistance = null,  // We don't typically estimate distance for classic Bluetooth
            txPower = null,
            type = DeviceType.CLASSIC
        )
        updateDevice(bluetoothDeviceInfo)
    }

    private fun updateDevice(device: BluetoothDeviceInfo) {
        viewModelScope.launch {
            _devices.value = _devices.value.filter { it.id != device.id } + device
        }
    }

    private fun removeDevice(id: String) {
        viewModelScope.launch {
            _devices.value = _devices.value.filter { it.id != id }
        }
    }

    fun cleanupOldDevices() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            _devices.value = _devices.value.filter { device ->
                currentTime - device.lastSeen < 60000 // Remove devices not seen in the last 60 seconds
            }
        }
    }
}

@Composable
fun ScannerApp() {
    val viewModel: BluetoothScannerViewModel = viewModel()
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isBluetoothOn by viewModel.isBluetoothOn.collectAsState()
    val scanningStatus by viewModel.scanningStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.cleanupOldDevices()
        // Check initial Bluetooth state
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        viewModel.updateBluetoothStatus(bluetoothManager.adapter?.isEnabled == true)
    }

    // Register for Bluetooth state changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        viewModel.updateBluetoothStatus(state == BluetoothAdapter.STATE_ON)
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        device?.let { viewModel.onClassicDeviceFound(it, rssi) }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Bluetooth Status: ${if (isBluetoothOn) "ON" else "OFF"}")
        Text("Scanning Status: $scanningStatus")

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { viewModel.startScanning(context) }) {
                Text("Start Scanning")
            }
            Button(onClick = { viewModel.stopScanning(context) }) {
                Text("Stop Scanning")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(devices) { device ->
                DeviceItem(device)
            }
        }
    }
}


@Composable
fun DeviceItem(device: BluetoothDeviceInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Name: ${device.name}", style = MaterialTheme.typography.titleMedium)
            Text("Type: ${device.type}")
            Text("RSSI: ${device.rssi} dBm")
            device.estimatedDistance?.let {
                Text("Estimated Distance: %.2f m".format(it))
            }
            device.txPower?.let {
                Text("TX Power: $it dBm")
            }
        }
    }
}

fun calculateDistance(rssi: Int, txPower: Int): Double {
    if (rssi == 0) return -1.0
    val ratio = rssi * 1.0 / txPower
    return if (ratio < 1.0) {
        ratio.pow(10.0)
    } else {
        (0.89976) * ratio.pow(7.7095) + 0.111
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            MaterialTheme {
                ScannerApp()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}