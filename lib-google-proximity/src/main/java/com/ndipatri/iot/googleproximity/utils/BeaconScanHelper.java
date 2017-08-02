package com.ndipatri.iot.googleproximity.utils;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.test.espresso.IdlingResource;
import android.util.Log;

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

    public static final int NEARBY_PANEL_SCAN_TIMEOUT_SECONDS = 10;

    private boolean isConnectedToBeaconService = false;
    private boolean isScanning = false;
    private boolean isInitialized = false;

    private BeaconManager beaconManager;
    private Region scanRegion;
    private BeaconScanIdlingResource idlingResource;

    private Context context;

    public BeaconScanHelper (Context context)  {
        this.context = context;
    }

    public IdlingResource getIdlingResource() {
        if (null == idlingResource) {
            idlingResource = new BeaconScanIdlingResource();
        }

        return idlingResource;
    }

    // Beacon scanning starts upon subscription using 'scanForNearbyBeacon(), the client
    // is responsible for stopping scanning, however, using 'stopBeaconScanning()'

    private Subject<Beacon> scanForRegionSubject;
    public Observable<Beacon> scanForNearbyBeacon(String beaconNamespaceId) {
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
            updateIdlingResource();
        }

        return scanForRegionSubject
                .timeout(NEARBY_PANEL_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS, observer -> {
                    Log.d(TAG, "Timed out scanning for beacon.");
                    observer.onComplete();
                })
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
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

            updateIdlingResource();
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

    private void updateIdlingResource() {
        if (null != idlingResource) {
            idlingResource.updateIdleState(!isScanning);
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
            Log.d(TAG, "Region exited= '" + region + "'.");
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
                beaconManager.addRangeNotifier(rangeNotifier);
                beaconManager.startRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to start ranging.", e);
            }
        }

        protected void regionExited(Region region) {
            try {
                beaconManager.stopRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to stop ranging.", e);
            }
        }
    };

    private RangeNotifier rangeNotifier = (nearbyBeacons, region) -> {

        Log.d(TAG, "Ranging update.  Nearby Beacons='" + nearbyBeacons + "', Region='" + region + "'.");

        for (Beacon nearbyBeacon : nearbyBeacons) {
            scanForRegionSubject.onNext(nearbyBeacon);
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

    private class BeaconScanIdlingResource implements IdlingResource {

        @Nullable
        private volatile ResourceCallback resourceCallback;

        private boolean isIdle;

        @Override
        public String getName() {
            return this.getClass().getName();
        }

        @Override
        public boolean isIdleNow() {
            return isIdle;
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback resourceCallback) {
            this.resourceCallback = resourceCallback;
        }

        public void updateIdleState(boolean isIdle) {
            this.isIdle = isIdle;
            if (isIdle && null != resourceCallback) {
                resourceCallback.onTransitionToIdle();
            }
        }
    }
}
