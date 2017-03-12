package com.kitty.geotracker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.kitty.geotracker.dialogs.JoinSession;
import com.kitty.geotracker.dialogs.StartSession;

import java.util.HashMap;

import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.MeteorCallback;
import im.delight.android.ddp.MeteorSingleton;
import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.db.memory.InMemoryDatabase;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, MeteorCallback,
        View.OnClickListener, StartSession.StartSessionListener, JoinSession.JoinSessionListener {

    private GoogleMap mMap;
    private LatLng myPosition;

    private FloatingActionsMenu floatingMenu;

    // Meteor
    private Meteor mMeteor;
    public static final String COLLECTION_SESSIONS = "Sessions";
    public static final String COLLECTION_SESSIONS2 = "sessions";
    public static final String COLLECTION_GPS_DATA = "GPSData";
    public static final String SUBSCRIPTION_SESSION_LIST = "SessionsList";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        floatingMenu = (FloatingActionsMenu) findViewById(R.id.floatingMenu);
        FloatingActionButton btnSettings = (FloatingActionButton) findViewById(R.id.btn_settings);
        FloatingActionButton btnJoinSession = (FloatingActionButton) findViewById(R.id.btn_join_session);
        FloatingActionButton btnStartSession = (FloatingActionButton) findViewById(R.id.fab_start_session);
        btnSettings.setOnClickListener(this);
        btnJoinSession.setOnClickListener(this);
        btnStartSession.setOnClickListener(this);

        // Get map fragment and register callback
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get the Meteor server ip from settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String meteorIp = prefs.getString("meteor_ip", "127.0.0.1:3000");

        // Create a new Meteor instance
        if (!MeteorSingleton.hasInstance()) {
            MeteorSingleton.createInstance(this, "ws://" + meteorIp + "/websocket", new InMemoryDatabase());
        }
        mMeteor = MeteorSingleton.getInstance();
        mMeteor.addCallback(this);
        mMeteor.connect();
    }

    /**
     * Take action when data is added on the Meteor server
     *
     * @param collectionName Name of the collection
     * @param documentID Document that was added
     * @param newValuesJson Values as JSON
     */
    public void onDataAdded(String collectionName, String documentID, String newValuesJson) {
        Log.d("MapsActivity", "Document added to " + collectionName + ": " + documentID);
        Log.d("MapsActivity", "Data: " + newValuesJson);
    }

    /**
     * Take action when data is changed on the Meteor server
     *
     * @param collectionName Name of the collection
     * @param documentID Document that was changed
     * @param updatedValuesJson Modified values as JSON
     * @param removedValuesJson Removed values as JSON
     */
    public void onDataChanged(String collectionName, String documentID,
                              String updatedValuesJson, String removedValuesJson) {
        Log.d("MapsActivity", "Document changed in " + collectionName + ": " + documentID);
        Log.d("MapsActivity", "Updated: " + updatedValuesJson);
        Log.d("MapsActivity", "Removed: " + removedValuesJson);
    }

    /**
     * Take action when data is removed on the Meteor server
     *
     * @param collectionName Name of the collection
     * @param documentID Document that was removed
     */
    public void onDataRemoved(String collectionName, String documentID) {
        Log.d("MapsActivity", "Document removed from " + collectionName + ": " + documentID);
    }

    public void onConnect(boolean signedInAutomatically) {
        Log.d("MapsActivity", "Connected to Meteor");
    }

    public void onDisconnect() {
        Log.d("MapsActivity", "Disconnected from Meteor");
    }

    public void onException(Exception e) {
        Log.d("MapsActivity", "Received exception from Meteor: " + e.getMessage());
    }

    public void onClick(View v) {
        floatingMenu.collapse();

        switch (v.getId()) {
            case R.id.btn_settings:
                // Open settings activity
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.fab_start_session:
                // Create the start session dialog
                StartSession startSession = new StartSession();
                startSession.show(getSupportFragmentManager(), startSession.getClass().getSimpleName());
                break;

            case R.id.btn_join_session:
                // Create the join session dialog
                JoinSession joinSession = new JoinSession();
                joinSession.show(getSupportFragmentManager(), joinSession.getClass().getSimpleName());
                break;
        }
    }

    @Override
    public void onDestroy() {
        mMeteor.disconnect();
        mMeteor.removeCallback(this);
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

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();

        // Getting the name of the best provider
        String provider = locationManager.getBestProvider(criteria, true);

        // Getting Current Location
        Location location = locationManager.getLastKnownLocation(provider);

        if (location != null) {
            // Getting latitude of the current location
            double latitude = location.getLatitude();

            // Getting longitude of the current location
            double longitude = location.getLongitude();

            myPosition = new LatLng(latitude, longitude);

            mMap.addMarker(new MarkerOptions().position(myPosition).title("Charles"));

            mMap.moveCamera(CameraUpdateFactory.newLatLng(myPosition));
        }
    }

    @Override
    public void onSessionStarted(final String sessionName) {
        Log.d("MapsActivity", "Creating new session \"" + sessionName + "\"...");
        HashMap<String, Object> sessionData = new HashMap<>();
        sessionData.put("title", sessionName);

        mMeteor.insert(COLLECTION_SESSIONS, sessionData, new ResultListener() {
            @Override
            public void onSuccess(String result) {
                Log.i("MapsActivity", "Created Session \"" + sessionName + "\": " + result);
                // TODO: Subscribe to the session
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e("MapsActivity", "Error creating session \"" + sessionName + "\"");
                Log.e("MapsActivity", "Error: " + error);
                Log.e("MapsActivity", "Reason: " + reason);
                Log.e("MapsActivity", "Details: " + details);
            }
        });
    }

    @Override
    public void onSessionJoined(final String sessionName) {
        Log.d("MapsActivity", "Joining session \"" + sessionName + "\"");
        // TODO: Subscribe to the session
    }
}
