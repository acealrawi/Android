package nl.utwente.localization2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.altbeacon.beacon.*

class BeaconScannerActivity : ComponentActivity(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private val PERMISSION_REQUEST_COARSE_LOCATION = 1

    private val _beacons = MutableStateFlow<List<Beacon>>(emptyList())
    private val beacons = _beacons.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))

        setContent {
            MaterialTheme {
                BeaconScannerScreen(beacons = beacons.collectAsState().value)
            }
        }

        checkLocationPermission()
    }

    @Composable
    fun BeaconScannerScreen(beacons: List<Beacon>) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Detected Beacons",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (beacons.isEmpty()) {
                Text("No beacons detected yet.")
            } else {
                LazyColumn {
                    items(beacons) { beacon ->
                        BeaconItem(beacon)
                    }
                }
            }
        }
    }

    @Composable
    fun BeaconItem(beacon: Beacon) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("UUID: ${beacon.id1}")
                Text("Major: ${beacon.id2}, Minor: ${beacon.id3}")
                Text("Distance: ${"%.2f".format(beacon.distance)} meters")
                Text("RSSI: ${beacon.rssi} dBm")
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST_COARSE_LOCATION)
        } else {
            startBeaconScanning()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_COARSE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startBeaconScanning()
                } else {
                    Log.e("BeaconScanner", "Coarse location permission not granted")
                }
            }
        }
    }

    private fun startBeaconScanning() {
        beaconManager.bind(this)
    }

    override fun onBeaconServiceConnect() {
        beaconManager.removeAllMonitorNotifiers()
        beaconManager.addRangeNotifier { beacons, _ ->
            _beacons.value = beacons.toList()
        }

        try {
            beaconManager.startRangingBeaconsInRegion(Region("myRangingUniqueId", null, null, null))
        } catch (e: RemoteException) {
            Log.e("BeaconScanner", "Error starting ranging", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beaconManager.unbind(this)
    }
}