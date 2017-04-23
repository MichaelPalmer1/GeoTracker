package com.kitty.geotracker;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
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
    private static final long UPDATE_INTERVAL = 1000;

    @Override
    public void onCreate() {
        Log.d(getClass().getSimpleName(), "onCreate()");
        super.onCreate();

        // Create instance of our meteor controller
        if (!MeteorController.hasInstance()) {
            Log.d(getClass().getSimpleName(), "Creating meteor instance from service");
            MeteorController.createInstance(this);
        }
        meteorController = MeteorController.getInstance();
        Log.d(getClass().getSimpleName(), "Getting location manager from service");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.d(getClass().getSimpleName(), "onDestroy()");
        locationManager.removeUpdates(this);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(getClass().getSimpleName(), "Start command called.");
        try {
            locationManager.requestLocationUpdates(
                    locationManager.getBestProvider(new Criteria(), true), UPDATE_INTERVAL, 0, this);
            Log.d(getClass().getSimpleName(), "Location updates started in service");
            return super.onStartCommand(intent, flags, startId);
        } catch (SecurityException e) {
            Log.e(getClass().getSimpleName(), "Service failed to start");
            return Service.START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(getClass().getSimpleName(), "onBind()");
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(getClass().getSimpleName(), "Service posting location to Meteor: " + location.toString());
        meteorController.postLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
