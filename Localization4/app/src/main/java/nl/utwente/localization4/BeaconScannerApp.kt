//package nl.utwente.localization4
//
//import android.app.Application
//import android.util.Log
//import org.altbeacon.beacon.BeaconConsumer
//import org.altbeacon.beacon.BeaconManager
//import org.altbeacon.beacon.BeaconParser
//import org.altbeacon.beacon.Region
//import org.altbeacon.beacon.startup.BootstrapNotifier
//
//class BeaconScannerApp  : Application(), BootstrapNotifier, BeaconConsumer {
//
//    private lateinit var beaconManager: BeaconManager
//
//    override fun onCreate() {
//        super.onCreate()
//
//        // Initialize Beacon Manager
//        beaconManager = BeaconManager.getInstanceForApplication(this)
//
//        // Set up to detect iBeacon (standard)
//        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT))
//
//        // Bind to the service
//        beaconManager.bind(this)
//    }
//
//    override fun didEnterRegion(region: Region?) {
//        // Triggered when entering a region
//    }
//
//    override fun didExitRegion(region: Region?) {
//        // Triggered when exiting a region
//    }
//
//    override fun didDetermineStateForRegion(state: Int, region: Region?) {
//        // Triggered when entering/exiting a region
//    }
//
//    override fun onBeaconServiceConnect() {
//        Log.i("bec", "beacon service connect")
//    }
//}