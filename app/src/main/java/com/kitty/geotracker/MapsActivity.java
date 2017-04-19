package com.kitty.geotracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.kitty.geotracker.dialogs.JoinSession;
import com.kitty.geotracker.dialogs.StartSession;

import java.util.HashMap;

import im.delight.android.ddp.db.Document;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, View.OnClickListener,
        LocationListener, JoinSession.JoinSessionListener, MeteorController.GPSListener {

    private GoogleMap mMap;
    private FloatingActionsMenu floatingMenu;
    private MeteorController meteorController;
    private LocationManager locationManager;
    private HashMap<String, Marker> mapMarkers = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        floatingMenu = (FloatingActionsMenu) findViewById(R.id.floatingMenu);
        FloatingActionButton btnSettings = (FloatingActionButton) findViewById(R.id.btn_settings);
        FloatingActionButton btnJoinSession = (FloatingActionButton) findViewById(R.id.btn_join_session);
        FloatingActionButton btnStartSession = (FloatingActionButton) findViewById(R.id.btn_start_session);
        FloatingActionButton btnLeaveSession = (FloatingActionButton) findViewById(R.id.btn_leave_session);
        FloatingActionButton btnManageSession = (FloatingActionButton) findViewById(R.id.btn_manage_session);
        FloatingActionButton btnEndSession = (FloatingActionButton) findViewById(R.id.btn_end_session);
        btnSettings.setOnClickListener(this);
        btnJoinSession.setOnClickListener(this);
        btnStartSession.setOnClickListener(this);
        btnLeaveSession.setOnClickListener(this);
        btnManageSession.setOnClickListener(this);
        btnEndSession.setOnClickListener(this);

        // Get map fragment and register callback
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Create instance of our meteor controller
        if (!MeteorController.hasInstance()) {
            MeteorController.createInstance(this);
        }
        meteorController = MeteorController.getInstance();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    public void onClick(View v) {
        floatingMenu.collapse();

        switch (v.getId()) {
            case R.id.btn_settings:
                // Open settings activity
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.btn_start_session:
                // Create the start session dialog
                StartSession startSession = new StartSession();
                startSession.show(getSupportFragmentManager(), startSession.getClass().getSimpleName());
                break;

            case R.id.btn_join_session:
                // Create the join session dialog
                JoinSession joinSession = new JoinSession();
                joinSession.show(getSupportFragmentManager(), joinSession.getClass().getSimpleName());
                break;

            case R.id.btn_leave_session:
                // Leave the session
                leaveSession();
                break;
        }
    }

    @Override
    public void onDestroy() {
        meteorController.disconnect();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != 0) {
            return;
        }

        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[i] != 0) {
                // Permission denied - throw an error
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        UiSettings mUiSettings = mMap.getUiSettings();

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }

        mMap.setMyLocationEnabled(true);
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setCompassEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(true);

        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Getting Current Location
        Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));

        if (location != null) {
            // Getting latitude of the current location
            double latitude = location.getLatitude();

            // Getting longitude of the current location
            double longitude = location.getLongitude();

            LatLng myPosition = new LatLng(latitude, longitude);

            mMap.moveCamera(CameraUpdateFactory.newLatLng(myPosition));
        }
    }

    /**
     * Triggered on every location update.
     *
     * @param location Location
     */
    @Override
    public void onLocationChanged(Location location) {
        // Tell meteor about the location
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

    /**
     * Triggered when a session has been selected in the Join Session dialog
     *
     * @param sessionName Session to join
     */
    @Override
    public void onSessionJoined(final String sessionName) {
        // Join the session
        meteorController.joinSession(sessionName);

        // Confirm that permissions have been granted
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }

        // Request location updates
        locationManager.requestLocationUpdates(locationManager.getBestProvider(new Criteria(), true), 0, 0, this);
    }

    /**
     * Leave a session
     */
    public void leaveSession() {
        // Stop location updates
        locationManager.removeUpdates(this);

        // Unsubscribe from the session
        meteorController.getMeteor().unsubscribe(MeteorController.SUBSCRIPTION_SESSION_LIST);

        // Clear data
        meteorController.clearSession();
        mMap.clear();
        mapMarkers.clear();
    }

    /**
     * Triggered when GPS data is received from Meteor
     *
     * @param documentID GPS document that was received
     */
    @Override
    public void onReceivedGPSData(String documentID) {
        // Get the document
        Document document = meteorController.getMeteor()
                .getDatabase()
                .getCollection(MeteorController.COLLECTION_GPS_DATA)
                .getDocument(documentID);

        try {
            // Retrieve latitude
            double latitude = Double.parseDouble(
                    document.getField(MeteorController.COLLECTION_GPS_DATA_COLUMN_LATITUDE).toString()
            );

            // Retrieve longitude
            double longitude = Double.parseDouble(
                    document.getField(MeteorController.COLLECTION_GPS_DATA_COLUMN_LONGITUDE).toString()
            );

            LatLng location = new LatLng(latitude, longitude);

            // Get the user's id
            String userId = document.getField(MeteorController.COLLECTION_GPS_DATA_COLUMN_USER_ID).toString();

            // Use existing marker for this user if it already exists, otherwise, create a new one.
            if (mapMarkers.containsKey(userId)) {
                Marker marker = mapMarkers.get(userId);
                marker.setPosition(location);
            } else {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title(userId)
                );
                mapMarkers.put(userId, marker);
            }
        } catch (NullPointerException e) {
            // Ignoring bad entry
        }
    }
}
