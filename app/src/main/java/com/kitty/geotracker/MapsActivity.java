package com.kitty.geotracker;

import android.Manifest;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.kitty.geotracker.dialogs.JoinSession;
import com.kitty.geotracker.dialogs.StartSession;

import java.util.ArrayList;
import java.util.HashMap;

import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.db.Document;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        View.OnClickListener,
        StartSession.StartSessionListener,
        JoinSession.JoinSessionListener,
        MeteorController.MeteorControllerListener {

    private GoogleMap mMap;
    private FloatingActionsMenu floatingMenu;
    private FloatingActionButton btnJoinSession, btnStartSession, btnLeaveSession, btnEndSession;
    private MeteorController meteorController;
    private HashMap<String, Marker> mapMarkers = new HashMap<>();
    private Intent serviceIntent;

    // Permission request codes
    private static final int REQUEST_CODE_JOIN_SESSION = 0, REQUEST_CODE_CAMERA_OVERHEAD = 1;

    ArrayList<LatLng> mapData = new ArrayList<>();
    HeatmapTileProvider mProvider;
    TileOverlay mOverlay;

    static boolean canUpdateHeatMap = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        floatingMenu = (FloatingActionsMenu) findViewById(R.id.floatingMenu);
        FloatingActionButton btnSettings = (FloatingActionButton) findViewById(R.id.btn_settings);
        btnJoinSession = (FloatingActionButton) findViewById(R.id.btn_join_session);
        btnStartSession = (FloatingActionButton) findViewById(R.id.btn_start_session);
        btnLeaveSession = (FloatingActionButton) findViewById(R.id.btn_leave_session);
        btnEndSession = (FloatingActionButton) findViewById(R.id.btn_end_session);
        btnSettings.setOnClickListener(this);
        btnJoinSession.setOnClickListener(this);
        btnStartSession.setOnClickListener(this);
        btnLeaveSession.setOnClickListener(this);
        btnEndSession.setOnClickListener(this);

        // Get map fragment and register callback
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Create instance of meteor controller
        if (!MeteorController.hasInstance()) {
            MeteorController.createInstance(this);
        }
        meteorController = MeteorController.getInstance();
    }

    public void onClick(View v) {
        // Close the floating menu
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
                // Request location permission
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_CODE_JOIN_SESSION
                    );
                    return;
                }

                // Open the join session dialog
                openJoinSessionDialog();
                break;

            case R.id.btn_leave_session:
                leaveSession();
                break;

            case R.id.btn_end_session:
                endSession();
                break;
        }
    }

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        meteorController.disconnect();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.d(getClass().getSimpleName(), "onPause()");
        super.onPause();

        if (meteorController.getState() == MeteorController.STATE_CREATED_SESSION) {
            meteorController.disconnect();
        }
    }

    @Override
    protected void onResume() {
        Log.d(getClass().getSimpleName(), "onResume()");
        super.onResume();

        if (meteorController != null && !meteorController.getMeteor().isConnected()) {
            Log.d(getClass().getSimpleName(), "Reconnecting...");
            meteorController.getMeteor().reconnect();
        }
    }

    /**
     * Callback for the result from requesting permissions.
     *
     * @param requestCode The request code
     * @param permissions The requested permissions.
     * @param results     The grant results for the corresponding permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                switch (requestCode) {
                    case REQUEST_CODE_JOIN_SESSION:
                        if (results[i] == PERMISSION_GRANTED) {
                            // Open the join session dialog
                            openJoinSessionDialog();
                        } else {
                            // Show error message
                            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show();
                        }
                        break;

                    case REQUEST_CODE_CAMERA_OVERHEAD:
                        if (results[i] == PERMISSION_GRANTED) {
                            moveCameraToMyLocation();
                        }
                        break;
                }
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
        mUiSettings.setMyLocationButtonEnabled(true);
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setCompassEnabled(true);

        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_CAMERA_OVERHEAD
            );
            return;
        }

        // Enable my location on the map
        mMap.setMyLocationEnabled(true);

        // Move camera overhead
        moveCameraToMyLocation();
    }

    /**
     * Move the camera to my location
     */
    private void moveCameraToMyLocation() {
        try {
            // Make sure map has been defined
            if (mMap == null) {
                return;
            }

            // Enable my location
            mMap.setMyLocationEnabled(true);

            // Get current location
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location =
                    locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));

            if (location != null) {
                // Move the camera
                mMap.moveCamera(CameraUpdateFactory.newLatLng(
                        new LatLng(location.getLatitude(), location.getLongitude())
                ));
            }
        } catch (SecurityException e) {
            // This should have been handled already
        }
    }

    /**
     * Triggered when a session has been created in the Start Session dialog
     *
     * @param sessionName Name of the session that was created
     */
    @Override
    public void onSessionStarted(String sessionName) {
        // Create the session
        meteorController.createSession(sessionName);

        // Hide the start and join session buttons, show the end session button.
        btnStartSession.setVisibility(View.GONE);
        btnJoinSession.setVisibility(View.GONE);
        btnEndSession.setVisibility(View.VISIBLE);
    }

    /**
     * Triggered when a session has been selected in the Join Session dialog
     *
     * @param sessionName Session to join
     */
    @Override
    public void onSessionJoined(final String sessionName) {
        // Start GPS service
        startLocationUpdates();

        // Join the session
        meteorController.joinSession(sessionName);

        // Hide the start and join session buttons, show the leave session button.
        btnStartSession.setVisibility(View.GONE);
        btnJoinSession.setVisibility(View.GONE);
        btnLeaveSession.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the session is closed by the session manager
     *
     * @param sessionName Session name
     */
    @Override
    public void onSessionClosed(String sessionName) {
        Log.d(getClass().getSimpleName(), "Session \"" + sessionName + "\" has been closed");
        leaveSession();

        new AlertDialog.Builder(this)
                .setMessage("Session manager has ended this session.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Called when the meteor controller received a informational or error message.
     *
     * @param message Message
     */
    @Override
    public void onSessionMessage(String message, boolean toast) {
        if (toast) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    /**
     * Show join session dialog
     */
    private void openJoinSessionDialog() {
        JoinSession joinSession = new JoinSession();
        joinSession.show(getSupportFragmentManager(), joinSession.getClass().getSimpleName());
    }

    /**
     * Leave a session
     */
    public void leaveSession() {
        Log.d(getClass().getSimpleName(), "Leaving session...");

        // Hide the leave session button, show the start and join session buttons
        btnStartSession.setVisibility(View.VISIBLE);
        btnJoinSession.setVisibility(View.VISIBLE);
        btnLeaveSession.setVisibility(View.GONE);

        // Stop location updates
        stopLocationUpdates();

        // Leave the session
        meteorController.leaveSession();

        // Clear data
        mMap.clear();
        mapMarkers.clear();
    }

    /**
     * End a session
     */
    public void endSession() {
        HashMap<String, Object>
                query = new HashMap<>(),
                dataToUpdate = new HashMap<>(),
                data = new HashMap<>(),
                options = new HashMap<>();

        // Make sure a connection is established
        if (!meteorController.getMeteor().isConnected()) {
            meteorController.getMeteor().reconnect();
        }

        // Build query
        query.put("_id", meteorController.getSessionDocumentId());
        data.put(MeteorController.COLLECTION_SESSIONS_COLUMN_ACTIVE, false);
        dataToUpdate.put("$set", data);

        // Deactivate the session
        Log.d(getClass().getSimpleName(), "Ending session...");
        meteorController.getMeteor().update(MeteorController.COLLECTION_SESSIONS, query, dataToUpdate, options,
            new ResultListener() {
                @Override
                public void onSuccess(String result) {
                    Log.d(getClass().getSimpleName(),
                            "Session \"" + meteorController.getSession() + "\" " + "stopped: " + result);

                    // Unsubscribe from the session
                    meteorController.getMeteor().unsubscribe(meteorController.getSession());

                    // Clear data
                    meteorController.clearSession();

                    // Hide/show buttons
                    btnStartSession.setVisibility(View.VISIBLE);
                    btnJoinSession.setVisibility(View.VISIBLE);
                    btnEndSession.setVisibility(View.GONE);

                    // Show confirmation
                    new AlertDialog.Builder(MapsActivity.this)
                            .setMessage("Session has ended")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }

                @Override
                public void onError(String error, String reason, String details) {
                    Log.e(getClass().getSimpleName(),
                            "Session \"" + meteorController.getSession() + "\" " + "failed to stop with " +
                                    "error: \"" + error + "\", reason: \"" + reason +
                                    "\", details: \"" + details + "\".");

                    // Show error
                    new AlertDialog.Builder(MapsActivity.this)
                            .setMessage("Failed to end session")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        );
    }

    /**
     * Start the location update service
     */
    private void startLocationUpdates() {
        Log.d(getClass().getSimpleName(), "Starting location updates...");
        if (serviceIntent == null) {
            serviceIntent = new Intent(this, GPSService.class);
        }
        startService(serviceIntent);
    }

    /**
     * Stop the location update service
     */
    private void stopLocationUpdates() {
        Log.d(getClass().getSimpleName(), "Stopping location updates...");
        try {
            stopService(serviceIntent);
        } catch (NullPointerException e) {
            Log.e(getClass().getSimpleName(), "Failed to stop GPS Service. It may not be running.");
        }
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
            mapData.add(location);

            if (mProvider == null) {
                mProvider = new HeatmapTileProvider.Builder()
                        .data(mapData)
                        .build();

                mProvider.setOpacity(.4);

                mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
            }


            if (canUpdateHeatMap)
            {
                mProvider.setData(mapData);
                mOverlay.clearTileCache();
            }


            // Get the user's id
            String userId = document.getField(MeteorController.COLLECTION_GPS_DATA_COLUMN_USER_ID).toString();

            Document userDocument = meteorController
                    .getMeteor()
                    .getDatabase()
                    .getCollection(MeteorController.COLLECTION_USERS)
                    .whereEqual(MeteorController.COLLECTION_USERS_COLUMN_USER, userId)
                    .findOne();

            String displayName = null;
            if (userDocument != null) {
                displayName = (String) userDocument.getField(MeteorController.COLLECTION_USERS_COLUMN_NAME);
            }

            displayName = displayName != null ? displayName : userId;

            // Use existing marker for this user if it already exists, otherwise, create a new one.
            if (mapMarkers.containsKey(userId)) {
                Marker marker = mapMarkers.get(userId);
                marker.setPosition(location);
                marker.setTitle(displayName);
            } else {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title(displayName)
                );
                mapMarkers.put(userId, marker);
            }
        } catch (NullPointerException e) {
            Log.e(getClass().getSimpleName(), "Error on GPS data receipt: " + e.getMessage());
        }
    }
}
