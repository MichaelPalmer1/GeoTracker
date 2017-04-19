package com.kitty.geotracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.MeteorCallback;
import im.delight.android.ddp.MeteorSingleton;
import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.SubscribeListener;
import im.delight.android.ddp.db.Collection;
import im.delight.android.ddp.db.Database;
import im.delight.android.ddp.db.Document;
import im.delight.android.ddp.db.memory.InMemoryDatabase;

public class MeteorController implements MeteorCallback, SubscribeListener {

    private static MeteorController mInstance;
    private Meteor meteor;
    private Database database;
    private static String userId;
    private String session = null;
    private int state = STATE_NO_SESSION;
    private static final String DEFAULT_METEOR_URL = "geotracker-web.herokuapp.com";
    private GPSListener mGPSListener = null;

    // States
    public static final int STATE_NO_SESSION = 0;
    public static final int STATE_CREATED_SESSION = 1;
    public static final int STATE_JOINED_SESSION = 2;

    // Sessions
    public static final String COLLECTION_SESSIONS = "Sessions";
    public static final String COLLECTION_SESSIONS_COLUMN_TITLE = "title";

    // GPS Data
    public static final String COLLECTION_GPS_DATA = "GPSData";
    public static final String COLLECTION_GPS_DATA_COLUMN_SESSION_ID = "sessionID";
    public static final String COLLECTION_GPS_DATA_COLUMN_USER_ID = "userID";
    public static final String COLLECTION_GPS_DATA_COLUMN_LONGITUDE = "long";
    public static final String COLLECTION_GPS_DATA_COLUMN_LATITUDE = "lat";
    public static final String COLLECTION_GPS_DATA_COLUMN_ALTITUDE = "altitude";
    public static final String COLLECTION_GPS_DATA_COLUMN_BEARING = "bearing";
    public static final String COLLECTION_GPS_DATA_COLUMN_SPEED = "speed";
    public static final String COLLECTION_GPS_DATA_COLUMN_TIME = "time";

    // Users
    public static final String COLLECTION_USERS = "Users";
    public static final String COLLECTION_USERS_COLUMN_USER = "user";

    // Subscriptions
    public static final String SUBSCRIPTION_SESSION_LIST = "SessionsList";
    public static final String SUBSCRIPTION_USERS = "Users";

    public interface GPSListener {
        public void onReceivedGPSData(final String documentID);
    }

    /**
     * Initialize everything necessary to communicate with Meteor
     *
     * @param context Context
     */
    private MeteorController(Context context) {
        // Get user id and store it
        userId = InstanceID.getInstance(context).getId();

        // Get meteor url from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String meteorUrl = String.format(Locale.US, "ws://%s/websocket",
                prefs.getString("meteor_ip", DEFAULT_METEOR_URL));

        // Bind the GPS listener
        if (context instanceof GPSListener) {
            mGPSListener = (GPSListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement GPSListener");
        }

        // Create a new Meteor instance
        if (!MeteorSingleton.hasInstance()) {
            meteor = MeteorSingleton.createInstance(context, meteorUrl, new InMemoryDatabase());
            meteor.addCallback(this);
        } else {
            meteor = MeteorSingleton.getInstance();
        }

        // Connect
        if (!meteor.isConnected()) {
            connect();
        }

        // Store database in local variable
        database = meteor.getDatabase();

        // Subscribe to users
        meteor.subscribe(SUBSCRIPTION_USERS, null, new SubscribeListener() {
            @Override
            public void onSuccess() {
                Log.i(getClass().getSimpleName(), "[Subscribe Users] Subscribed to " + SUBSCRIPTION_USERS);
                initializeUser();
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(getClass().getSimpleName(), "[Subscribe Users] Error subscribing to " + SUBSCRIPTION_USERS);
                Log.e(getClass().getSimpleName(), "[Subscribe Users] Error: " + error);
                Log.e(getClass().getSimpleName(), "[Subscribe Users] Reason: " + reason);
                Log.e(getClass().getSimpleName(), "[Subscribe Users] Details: " + details);
            }
        });
    }

    /**
     * Create a new singleton instance
     *
     * @param context Context
     * @return Instance
     */
    public synchronized static MeteorController createInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MeteorController(context);
        }
        return mInstance;
    }

    /**
     * Get singleton instance of MeteorController
     *
     * @return Meteor controller instance
     */
    public synchronized static MeteorController getInstance() {
        if (mInstance == null) {
            throw new IllegalStateException("Please call `createInstance(...)` first");
        }
        return mInstance;
    }

    /**
     * Check if a singleton instance already exists
     *
     * @return Boolean
     */
    public synchronized static boolean hasInstance() {
        return mInstance != null;
    }

    /**
     * Add the user to the Users collection if they do not exist yet
     */
    private void initializeUser() {
        Collection cUsers = database.getCollection(COLLECTION_USERS);
        Document user = cUsers.whereEqual(COLLECTION_USERS_COLUMN_USER, userId).findOne();
        if (user == null) {
            HashMap<String, Object> userData = new HashMap<>();
            userData.put(COLLECTION_USERS_COLUMN_USER, userId);
            meteor.insert(COLLECTION_USERS, userData, new ResultListener() {
                @Override
                public void onSuccess(String result) {
                    Log.i(getClass().getSimpleName(), "[InitializeUser] Created new user \"" + userId + "\": " +
                            result);
                }

                @Override
                public void onError(String error, String reason, String details) {
                    Log.e(getClass().getSimpleName(), "[InitializeUser] Could not create user \"" + userId + "\"");
                    Log.e(getClass().getSimpleName(), "[InitializeUser] Error: " + error);
                    Log.e(getClass().getSimpleName(), "[InitializeUser] Reason: " + reason);
                    Log.e(getClass().getSimpleName(), "[InitializeUser] Details: " + details);
                }
            });
        } else {
            Log.i(getClass().getSimpleName(), "[InitializeUser] Found existing user: " + user);
        }
    }

    /**
     * Set the session state
     *
     * @param state Session state
     */
    public void setState(int state) {
        this.state = state;
    }

    /**
     * Get the current connection state with the server.
     *
     * @return Connection state
     */
    public int getState() {
        return state;
    }

    /**
     * Get the current session
     *
     * @return Session name
     */
    public String getSession() {
        return session;
    }

    /**
     * Get user id
     *
     * @return User id
     */
    public static String getUserId() {
        return userId;
    }

    /**
     * Connect to Meteor
     */
    public void connect() {
        meteor.connect();
    }

    /**
     * Disconnect from Meteor
     */
    public void disconnect() {
        meteor.disconnect();
    }

    /**
     * Get Meteor instance
     *
     * @return Instance of Meteor
     */
    public Meteor getMeteor() {
        return meteor;
    }

    /**
     * Create a session
     *
     * @param sessionName Name of the session
     */
    public void createSession(final String sessionName) {
        Log.d(getClass().getSimpleName(), "[Create Session] Creating new session \"" + sessionName + "\"...");
        HashMap<String, Object> sessionData = new HashMap<>();
        sessionData.put(COLLECTION_SESSIONS_COLUMN_TITLE, sessionName);
        meteor.insert(COLLECTION_SESSIONS, sessionData, new ResultListener() {
            @Override
            public void onSuccess(String result) {
                Log.i(getClass().getSimpleName(),
                        "[Create Session] Created Session \"" + sessionName + "\": " + result);
                meteor.subscribe(sessionName);
                setState(STATE_CREATED_SESSION);
                session = sessionName;
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(getClass().getSimpleName(), "[Create Session] Error creating session \"" + sessionName + "\"");
                Log.e(getClass().getSimpleName(), "[Create Session] Error: " + error);
                Log.e(getClass().getSimpleName(), "[Create Session] Reason: " + reason);
                Log.e(getClass().getSimpleName(), "[Create Session] Details: " + details);
            }
        });
    }

    /**
     * Clear the current session
     */
    public void clearSession() {
        setState(STATE_NO_SESSION);
        session = null;
    }

    /**
     * Get all sessions
     *
     * @return List of sessions
     */
    public ArrayList<String> getSessions() {
        ArrayList<String> sessionList = new ArrayList<>();
        for (Document document : database.getCollection(COLLECTION_SESSIONS).find()) {
            String title = document.getField(COLLECTION_SESSIONS_COLUMN_TITLE).toString();
            sessionList.add(title);
        }
        return sessionList;
    }

    /**
     * Join an existing session
     *
     * @param sessionName Session to join
     */
    public void joinSession(final String sessionName) {
        Log.d(getClass().getSimpleName(), "[Join Session] Joining session \"" + sessionName + "\"");
        meteor.subscribe(sessionName);
        setState(STATE_JOINED_SESSION);
        session = sessionName;
    }

    /**
     * Post location to Meteor
     *
     * @param location Location
     */
    public void postLocation(Location location) {
        // Only post location if the user is a member of a session (and not a session owner)
        if (getState() != STATE_JOINED_SESSION || session == null) {
            return;
        }

        HashMap<String, Object> data = new HashMap<>();
        data.put(COLLECTION_GPS_DATA_COLUMN_SESSION_ID, getSession());
        data.put(COLLECTION_GPS_DATA_COLUMN_USER_ID, getUserId());
        data.put(COLLECTION_GPS_DATA_COLUMN_TIME, location.getTime());
        data.put(COLLECTION_GPS_DATA_COLUMN_ALTITUDE, location.getAltitude());
        data.put(COLLECTION_GPS_DATA_COLUMN_BEARING, location.getBearing());
        data.put(COLLECTION_GPS_DATA_COLUMN_LATITUDE, location.getLatitude());
        data.put(COLLECTION_GPS_DATA_COLUMN_LONGITUDE, location.getLongitude());
        data.put(COLLECTION_GPS_DATA_COLUMN_SPEED, location.getSpeed());
        meteor.insert(COLLECTION_GPS_DATA, data);
    }

    @Override
    public void onConnect(boolean signedInAutomatically) {
        Log.d(getClass().getSimpleName(), "Connected to Meteor. Auto-signed in: " + signedInAutomatically);
    }

    @Override
    public void onDisconnect() {
        Log.d(getClass().getSimpleName(), "Disconnected from Meteor");
    }

    @Override
    public void onException(Exception e) {
        Log.d(getClass().getSimpleName(), "Meteor error: " + e.getMessage());
    }

    @Override
    public void onDataAdded(String collectionName, String documentID, String newValuesJson) {
        Log.d(getClass().getSimpleName(),
                String.format(Locale.US, "Document \"%s\" added to collection \"%s\"", documentID, collectionName));
        Log.d(getClass().getSimpleName(), "Data: " + newValuesJson);

        // Only trigger GPS data listener if the user created the session
        if (collectionName.equals(COLLECTION_GPS_DATA) && getState() == STATE_CREATED_SESSION && getSession() != null) {
            mGPSListener.onReceivedGPSData(documentID);
        }
    }

    @Override
    public void onDataChanged(String collectionName, String documentID,
                              String updatedValuesJson, String removedValuesJson) {
        Log.d(getClass().getSimpleName(),
                String.format(Locale.US, "Document \"%s\" changed in collection \"%s\"", documentID, collectionName));
        Log.d(getClass().getSimpleName(), "Updated: " + updatedValuesJson);
        Log.d(getClass().getSimpleName(), "Removed: " + removedValuesJson);
    }

    @Override
    public void onDataRemoved(String collectionName, String documentID) {
        Log.d(getClass().getSimpleName(),
                String.format(Locale.US, "Document \"%s\" removed from collection \"%s\"", documentID, collectionName));
    }

    /**
     * Called on successful subscribe
     */
    @Override
    public void onSuccess() {
        Log.d(getClass().getSimpleName(), "Subscription successful.");
    }

    /**
     * Called on failed subscribe
     *
     * @param error Error code
     * @param reason Reason
     * @param details Details
     */
    @Override
    public void onError(String error, String reason, String details) {
        Log.e(getClass().getSimpleName(), String.format(Locale.US,
                "Subscription errored with error \"%s\" due to \"%s\": %s\"",
                error, reason, details
        ));
    }
}
