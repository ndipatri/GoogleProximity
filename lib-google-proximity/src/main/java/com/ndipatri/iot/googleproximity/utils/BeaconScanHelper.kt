package com.ndipatri.iot.googleproximity.utils


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.RemoteException
import android.util.Log

import com.google.common.base.Optional
import com.google.common.primitives.Bytes

import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex

import java.util.concurrent.TimeUnit

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

class BeaconScanHelper(private val context: Context) {

    private var isConnectedToBeaconService = false
    private var isScanning = false
    private var isInitialized = false

    private var beaconManager: BeaconManager? = null
    private var scanRegion: Region? = null
    private var scanForRegionSubject: Subject<BeaconUpdate>? = null

    private val beaconConsumer = object : BeaconConsumer {
        override fun onBeaconServiceConnect() {
            Log.d(TAG, "onBeaconServiceConnected(): Connected!")

            isConnectedToBeaconService = true

            if (isScanning && beaconManager!!.monitoringNotifiers.isEmpty()) {
                // we're supposed to be monitoring but we had to wake for
                // service connection... so start monitoring now.
                startBeaconScanning()
            }
        }

        override fun getApplicationContext(): Context {
            return context
        }

        override fun unbindService(serviceConnection: ServiceConnection) {
            context.unbindService(serviceConnection)
        }

        override fun bindService(intent: Intent, serviceConnection: ServiceConnection, i: Int): Boolean {
            return context.bindService(intent, serviceConnection, i)
        }
    }

    private val monitorNotifier = object : MonitorNotifier {
        override fun didEnterRegion(region: Region) {
            // Start ranging this beacon....
            regionEntered(region)
        }

        override fun didExitRegion(region: Region) {
            regionExited(region)
        }

        override fun didDetermineStateForRegion(regionState: Int, region: Region) {
            if (regionState == MonitorNotifier.INSIDE) {
                regionEntered(region)
            } else if (regionState == MonitorNotifier.OUTSIDE) {
                regionExited(region)
            }
        }

        protected fun regionEntered(region: Region) {
            try {
                Log.d(TAG, "Region entered = '$region'.")
                beaconManager!!.addRangeNotifier(rangeNotifier)
                beaconManager!!.startRangingBeaconsInRegion(region)
            } catch (e: RemoteException) {
                Log.e(TAG, "Unable to start ranging.", e)
            }

        }

        protected fun regionExited(region: Region) {
            try {
                if (!beaconManager!!.rangingNotifiers.isEmpty()) {
                    Log.d(TAG, "Region exited= '$region'.")
                    beaconManager!!.stopRangingBeaconsInRegion(region)

                    scanForRegionSubject!!.onNext(BeaconUpdate())
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Unable to stop ranging.", e)
            }

        }
    }

    private val rangeNotifier = { nearbyBeacons: Collection<Beacon>, region: Region ->

        Log.d(TAG, "Ranging update.  Nearby Beacons='$nearbyBeacons', Region='$region'.")

        for (nearbyBeacon in nearbyBeacons) {
            scanForRegionSubject!!.onNext(BeaconUpdate(nearbyBeacon))
        }
    }

    @JvmOverloads
    fun scanForNearbyBeacon(beaconNamespaceId: String, timeoutSeconds: Int = -1): Observable<BeaconUpdate> {
        Log.d(TAG, "Starting AltBeacon discovery...")

        scanForRegionSubject = PublishSubject.create()

        if (!isScanning) {

            if (!isInitialized) {
                initialize(beaconNamespaceId)
                isInitialized = true
            }

            // if not connected to service, scanning will start once
            // we are connected...
            if (isConnectedToBeaconService) {
                startBeaconScanning()
            }

            isScanning = true
        }

        var observable = scanForRegionSubject!!.doOnError { throwable ->
            Log.e(TAG, "Exception while scanning for beacons. Forcing stop.", throwable)

            stopBeaconScanning()
        }

        if (timeoutSeconds > 0) {
            observable = observable.timeout(timeoutSeconds.toLong(), TimeUnit.SECONDS,
                    Observable.create { subscriber ->
                        Log.d(TAG, "Timed out scanning for beacon.")
                        subscriber.onComplete()
                    }
            )
        }

        return observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * A BeaconUpdate without an associated Beacon
     * indicates that the region has been exited.
     */
    class BeaconUpdate {
        var beacon: Optional<Beacon>? = null
            private set

        constructor() {
            this.beacon = Optional.absent()
        }

        constructor(beacon: Beacon) {
            this.beacon = Optional.of(beacon)
        }
    }

    fun stopBeaconScanning() {
        Log.d(TAG, "Stopping AltBeacon discovery...")

        if (isScanning) {

            beaconManager!!.removeAllRangeNotifiers()
            beaconManager!!.removeAllMonitorNotifiers()
            try {
                if (beaconManager!!.isBound(beaconConsumer)) {
                    beaconManager!!.stopMonitoringBeaconsInRegion(scanRegion)
                    beaconManager!!.stopRangingBeaconsInRegion(scanRegion)
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "BLE scan service not yet bound.", e)
            }

            scanForRegionSubject!!.onComplete()

            isScanning = false
        }
    }

    private fun initialize(beaconNamespaceId: String) {

        val nicksBeaconNamespaceId = Identifier.parse(beaconNamespaceId)
        scanRegion = Region("nicks-beacon-region", nicksBeaconNamespaceId, null, null)

        val mBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (mBluetoothManager != null) {
            var mBluetoothAdapter: BluetoothAdapter? = null
            mBluetoothAdapter = mBluetoothManager.adapter
            mBluetoothAdapter!!.enable()

            beaconManager = BeaconManager.getInstanceForApplication(context)
            beaconManager!!.setForegroundScanPeriod(5000)
            beaconManager!!.setBackgroundScanPeriod(5000)

            // Detect the main identifier (UID) frame:
            beaconManager!!.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT))

            // Detect the telemetry (TLM) frame:
            beaconManager!!.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT))

            beaconManager!!.bind(beaconConsumer)
        }
    }

    private fun startBeaconScanning() {
        Log.d(TAG, "startBeaconScanning()")
        beaconManager!!.addMonitorNotifier(monitorNotifier)
        try {
            beaconManager!!.startMonitoringBeaconsInRegion(scanRegion)
        } catch (e: RemoteException) {
            Log.e(TAG, "BLE scan service not yet bound.", e)
        }

    }

    fun getAdvertiseId(beacon: Beacon): ByteArray {

        val namespaceId = beacon.id1
        val namespaceIdHex = namespaceId.toHexString().substring(2)

        val instanceId = beacon.id2
        val instanceIdHex = instanceId.toHexString().substring(2)

        var namespaceBytes = ByteArray(0)
        try {
            namespaceBytes = Hex.decodeHex(namespaceIdHex.toCharArray())
        } catch (e: DecoderException) {
            e.printStackTrace()
        }

        var instanceBytes = ByteArray(0)
        try {
            instanceBytes = Hex.decodeHex(instanceIdHex.toCharArray())
        } catch (e: DecoderException) {
            e.printStackTrace()
        }

        return Bytes.concat(namespaceBytes, instanceBytes)
    }

    companion object {

        private val TAG = BeaconScanHelper::class.java!!.getSimpleName()
    }
}// Beacon scanning starts upon subscription using 'scanForNearbyBeacon(), the client
// is responsible for stopping scanning, however, using 'stopBeaconScanning()'
