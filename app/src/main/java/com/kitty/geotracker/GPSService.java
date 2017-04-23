package com.kitty.geotracker;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class GPSService extends Service implements LocationListener {

    private MeteorController meteorController;
    private LocationManager locationManager;
    private static final long MIN_UPDATE_INTERVAL = 1000; // in milliseconds
    private final String TAG = getClass().getSimpleName();

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        super.onCreate();

        // Create instance of our meteor controller
        if (!MeteorController.hasInstance()) {
            MeteorController.createInstance(this);
        }
        meteorController = MeteorController.getInstance();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        locationManager.removeUpdates(this);
        Log.d(TAG, "Stopped location updates");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        try {
//            String provider = locationManager.getBestProvider(new Criteria(), true);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_UPDATE_INTERVAL, 0, this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_UPDATE_INTERVAL, 0, this);
            Log.d(TAG, "Started location updates");
        } catch (SecurityException e) {
            Log.e(TAG, "Could not start service due to missing permissions. Stopping...");
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Posting location: " + location.toString());
        meteorController.postLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Provider \"" + provider + "\" changed status to \"" + status + "\": " + extras.toString());
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider \"" + provider + "\" enabled.");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider \"" + provider + "\" disabled.");
    }
}
