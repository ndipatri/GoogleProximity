package com.ndipatri.iot.googleproximity.utils;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.base.Optional;
import com.google.common.primitives.Bytes;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

public class BeaconScanHelper {

    private static final String TAG = BeaconScanHelper.class.getSimpleName();

    private boolean isConnectedToBeaconService = false;
    private boolean isScanning = false;
    private boolean isInitialized = false;

    private BeaconManager beaconManager;
    private Region scanRegion;

    private Context context;

    public BeaconScanHelper (final Context context)  {
        this.context = context;
    }

    // Beacon scanning starts upon subscription using 'scanForNearbyBeacon(), the client
    // is responsible for stopping scanning, however, using 'stopBeaconScanning()'
    public Observable<BeaconUpdate> scanForNearbyBeacon(String beaconNamespaceId) {
        return scanForNearbyBeacon(beaconNamespaceId, -1);
    }
    private Subject<BeaconUpdate> scanForRegionSubject;
    public Observable<BeaconUpdate> scanForNearbyBeacon(String beaconNamespaceId, int timeoutSeconds) {
        Log.d(TAG, "Starting AltBeacon discovery...");

        scanForRegionSubject = PublishSubject.create();

        if (!isScanning) {

            if (!isInitialized) {
                initialize(beaconNamespaceId);
                isInitialized = true;
            }

            // if not connected to service, scanning will start once
            // we are connected...
            if (isConnectedToBeaconService) {
                startBeaconScanning();
            }

            isScanning = true;
        }

        Observable<BeaconUpdate> observable = scanForRegionSubject.doOnError(throwable -> {
            Log.e(TAG, "Exception while scanning for beacons. Forcing stop.", throwable);

            stopBeaconScanning();
        });

        if (timeoutSeconds > 0) {
             observable = observable.timeout(timeoutSeconds, TimeUnit.SECONDS,
                     Observable.create(subscriber -> {
                        Log.d(TAG, "Timed out scanning for beacon.");
                        subscriber.onComplete();
                     })
             );
        }

        return observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * A BeaconUpdate without an associated Beacon
     * indicates that the region has been exited.
     */
    public static class BeaconUpdate {
        private Optional<Beacon> beaconOptional;

        public BeaconUpdate() {
            this.beaconOptional = Optional.absent();
        }

        public BeaconUpdate(Beacon beacon) {
            this.beaconOptional = Optional.of(beacon);
        }

        public Optional<Beacon> getBeacon() {
            return beaconOptional;
        }
    }

    public void stopBeaconScanning() {
        Log.d(TAG, "Stopping AltBeacon discovery...");

        if (isScanning) {

            beaconManager.removeAllRangeNotifiers();
            beaconManager.removeAllMonitorNotifiers();
            try {
                if (beaconManager.isBound(beaconConsumer)) {
                    beaconManager.stopMonitoringBeaconsInRegion(scanRegion);
                    beaconManager.stopRangingBeaconsInRegion(scanRegion);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "BLE scan service not yet bound.", e);
            }

            scanForRegionSubject.onComplete();

            isScanning = false;
        }
    }

    private void initialize(String beaconNamespaceId) {

        Identifier nicksBeaconNamespaceId = Identifier.parse(beaconNamespaceId);
        scanRegion = new Region("nicks-beacon-region", nicksBeaconNamespaceId, null, null);

        BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager != null) {
            BluetoothAdapter mBluetoothAdapter = null;
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            mBluetoothAdapter.enable();

            beaconManager = BeaconManager.getInstanceForApplication(context);
            beaconManager.setForegroundScanPeriod(5000);
            beaconManager.setBackgroundScanPeriod(5000);

            // Detect the main identifier (UID) frame:
            beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));

            // Detect the telemetry (TLM) frame:
            beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT));

            beaconManager.bind(beaconConsumer);
        }
    }

    private void startBeaconScanning() {
        Log.d(TAG, "startBeaconScanning()");
        beaconManager.addMonitorNotifier(monitorNotifier);
        try {
            beaconManager.startMonitoringBeaconsInRegion(scanRegion);
        } catch (RemoteException e) {
            Log.e(TAG, "BLE scan service not yet bound.", e);
        }
    }

    private BeaconConsumer beaconConsumer = new BeaconConsumer() {
        @Override
        public void onBeaconServiceConnect() {
            Log.d(TAG, "onBeaconServiceConnected(): Connected!");

            isConnectedToBeaconService = true;

            if (isScanning && beaconManager.getMonitoringNotifiers().isEmpty()) {
                // we're supposed to be monitoring but we had to wake for
                // service connection... so start monitoring now.
                startBeaconScanning();
            }
        }

        @Override
        public Context getApplicationContext() {
            return context;
        }

        @Override
        public void unbindService(ServiceConnection serviceConnection) {
            context.unbindService(serviceConnection);
        }

        @Override
        public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
            return context.bindService(intent, serviceConnection, i);
        }
    };

    private MonitorNotifier monitorNotifier = new MonitorNotifier() {
        @Override
        public void didEnterRegion(Region region) {
            // Start ranging this beacon....
            regionEntered(region);
        }

        @Override
        public void didExitRegion(Region region) {
            regionExited(region);
        }

        @Override
        public void didDetermineStateForRegion(int regionState, Region region) {
            if (regionState == MonitorNotifier.INSIDE) {
                regionEntered(region);
            } else if (regionState == MonitorNotifier.OUTSIDE) {
                regionExited(region);
            }
        }

        protected void regionEntered(Region region) {
            try {
                Log.d(TAG, "Region entered = '" + region + "'.");
                beaconManager.addRangeNotifier(rangeNotifier);
                beaconManager.startRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to start ranging.", e);
            }
        }

        protected void regionExited(Region region) {
            try {
                Log.d(TAG, "Region exited= '" + region + "'.");
                beaconManager.stopRangingBeaconsInRegion(region);

                scanForRegionSubject.onNext(new BeaconUpdate());
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to stop ranging.", e);
            }
        }
    };

    private RangeNotifier rangeNotifier = (nearbyBeacons, region) -> {

        Log.d(TAG, "Ranging update.  Nearby Beacons='" + nearbyBeacons + "', Region='" + region + "'.");

        for (Beacon nearbyBeacon : nearbyBeacons) {
            scanForRegionSubject.onNext(new BeaconUpdate(nearbyBeacon));
        }
    };

    public byte[] getAdvertiseId(Beacon beacon) {

        Identifier namespaceId = beacon.getId1();
        String namespaceIdHex = namespaceId.toHexString().substring(2);

        Identifier instanceId = beacon.getId2();
        String instanceIdHex = instanceId.toHexString().substring(2);

        byte[] namespaceBytes = new byte[0];
        try {
            namespaceBytes = Hex.decodeHex(namespaceIdHex.toCharArray());
        } catch (DecoderException e) {
            e.printStackTrace();
        }
        byte[] instanceBytes = new byte[0];
        try {
            instanceBytes = Hex.decodeHex(instanceIdHex.toCharArray());
        } catch (DecoderException e) {
            e.printStackTrace();
        }

        return Bytes.concat(namespaceBytes, instanceBytes);
    }
}
