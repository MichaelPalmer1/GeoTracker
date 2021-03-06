package com.kitty.geotracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.MeteorCallback;
import im.delight.android.ddp.MeteorSingleton;
import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.SubscribeListener;
import im.delight.android.ddp.db.Collection;
import im.delight.android.ddp.db.Database;
import im.delight.android.ddp.db.Document;
import im.delight.android.ddp.db.memory.InMemoryDatabase;

public class MeteorController implements MeteorCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private static MeteorController mInstance;
    private Meteor meteor;
    private Database database;
    private static String userId, displayName;
    private String session = null, sessionDocumentId = null;
    private int state = STATE_NO_SESSION;
    private static final String DEFAULT_METEOR_URL = "geotracker-web.herokuapp.com";
    private MeteorControllerListener mListener = null;
    private final String TAG = getClass().getSimpleName();

    // States
    public static final int STATE_NO_SESSION = 0;
    public static final int STATE_CREATED_SESSION = 1;
    public static final int STATE_JOINED_SESSION = 2;

    // Sessions
    public static final String COLLECTION_SESSIONS = "Sessions";
    public static final String COLLECTION_SESSIONS_COLUMN_TITLE = "title";
    public static final String COLLECTION_SESSIONS_COLUMN_MANAGER = "manager";
    public static final String COLLECTION_SESSIONS_COLUMN_ACTIVE = "active";

    // GPS Data
    public static final String COLLECTION_GPS_DATA = "GPSData";
    public static final String COLLECTION_GPS_DATA_COLUMN_PROVIDER = "provider";
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
    public static final String COLLECTION_USERS_COLUMN_NAME = "name";

    // Subscriptions
    public static final String SUBSCRIPTION_SESSION_LIST = "SessionsList";
    public static final String SUBSCRIPTION_USERS = "Users";

    public interface MeteorControllerListener {
        void onReceivedGPSData(final String documentID);
        void onSessionClosed(String sessionName);
        void onSessionManage(String sessionName);
        void onSessionMessage(String message, boolean toast);
    }

    /**
     * Initialize everything necessary to communicate with Meteor
     *
     * @param context Context
     */
    private MeteorController(Context context) {
        // Get meteor url from preferences
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Get user id and store it
        userId = InstanceID.getInstance(context).getId();
        displayName = prefs.getString("display_name", null);

        String meteorUrl = String.format(Locale.US, "ws://%s/websocket",
                prefs.getString("meteor_ip", DEFAULT_METEOR_URL));

        // Bind the meteor controller listener
        if (context instanceof MeteorControllerListener) {
            mListener = (MeteorControllerListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement MeteorControllerListener");
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
                Log.i(TAG, "[Subscribe Users] Subscribed to " + SUBSCRIPTION_USERS);
                initializeUser();
                prefs.registerOnSharedPreferenceChangeListener(MeteorController.this);
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(TAG, "[Subscribe Users] Error subscribing to " + SUBSCRIPTION_USERS);
                Log.e(TAG, "[Subscribe Users] Error: " + error);
                Log.e(TAG, "[Subscribe Users] Reason: " + reason);
                Log.e(TAG, "[Subscribe Users] Details: " + details);
            }
        });

        // Subscribe to sessions
        meteor.subscribe(SUBSCRIPTION_SESSION_LIST, null, new SubscribeListener() {
            @Override
            public void onSuccess() {
                Log.d(getClass().getSimpleName(), "Subscribe to session list successful");
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(getClass().getSimpleName(), "Failed to subscribe to session list");
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
            userData.put(COLLECTION_USERS_COLUMN_NAME, displayName);
            meteor.insert(COLLECTION_USERS, userData, new ResultListener() {
                @Override
                public void onSuccess(String result) {
                    Log.i(TAG, "[InitializeUser] Created new user \"" + userId + "\": " + result);
                }

                @Override
                public void onError(String error, String reason, String details) {
                    Log.e(TAG, "[InitializeUser] Could not create user \"" + userId + "\"");
                    Log.e(TAG, "[InitializeUser] Error: " + error);
                    Log.e(TAG, "[InitializeUser] Reason: " + reason);
                    Log.e(TAG, "[InitializeUser] Details: " + details);
                }
            });
        } else {
            Log.i(TAG, "[InitializeUser] Found existing user: " + user);
            if (displayName != null) {
                Log.d(TAG, "[InitializeUser] Changing display name");
                changeDisplayName(displayName);
            }
        }
    }

    /**
     * Add the user to the Users collection if they do not exist yet
     */
    private void changeDisplayName(String name) {
        if (name == null) {
            return;
        }

        Log.d(TAG, "Changing display name...");

        Collection collection = database.getCollection(COLLECTION_USERS);
        Document user = collection.whereEqual(COLLECTION_USERS_COLUMN_USER, userId).findOne();
        if (user != null) {
            HashMap<String, Object>
                    query = new HashMap<>(),
                    dataToUpdate = new HashMap<>(),
                    data = new HashMap<>(),
                    options = new HashMap<>();

            // Build query
            query.put("_id", user.getId());
            data.put(COLLECTION_USERS_COLUMN_NAME, name);
            dataToUpdate.put("$set", data);

            meteor.update(COLLECTION_USERS, query, dataToUpdate, options, new ResultListener() {
                @Override
                public void onSuccess(String result) {
                    Log.d(TAG, "Updated display name successfully");
                }

                @Override
                public void onError(String error, String reason, String details) {
                    Log.e(TAG, "Failed to update display name.");
                }
            });
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
     * Get the document id of the current session
     *
     * @return Session document id
     */
    public String getSessionDocumentId() {
        return sessionDocumentId;
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
        Log.d(TAG, "[Create Session] Creating new session \"" + sessionName + "\"...");
        HashMap<String, Object> sessionData = new HashMap<>();
        sessionData.put(COLLECTION_SESSIONS_COLUMN_TITLE, sessionName);
        sessionData.put(COLLECTION_SESSIONS_COLUMN_MANAGER, userId);
        sessionData.put(COLLECTION_SESSIONS_COLUMN_ACTIVE, true);
        meteor.insert(COLLECTION_SESSIONS, sessionData, new ResultListener() {
            @Override
            public void onSuccess(String result) {
                Log.d(TAG, "[Create Session] Created Session \"" + sessionName + "\": " + result);

                // Extract the session's document id
                String id;
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray insertedIds = jsonObject.getJSONArray("insertedIds");
                    id = insertedIds.getString(0);
                } catch (JSONException e) {
                    id = null;
                }

                // Create a final variable to pass to the subscribe listener
                final String documentId = id;

                // Update state
                setState(STATE_CREATED_SESSION);
                session = sessionName;
                sessionDocumentId = documentId;

                // Subscribe to the session
                meteor.subscribe(sessionName, null, new SubscribeListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Subscribed to \"" + sessionName + "\" successfully");
                        MapsActivity.canUpdateHeatMap = true;
                    }

                    @Override
                    public void onError(String error, String reason, String details) {
                        Log.e("StartSessionSubscribe",
                                "[Subscribe to \"" + sessionName + "\"] Failed to subscribe.");
                        Log.e("StartSessionSubscribe", "[Subscribe to \"" + sessionName + "\"] Error: " +
                                error);
                        Log.e("StartSessionSubscribe", "[Subscribe to \"" + sessionName + "\"] Reason: " +
                                reason);
                        Log.e("StartSessionSubscribe", "[Subscribe to \"" + sessionName + "\"] Details: " +
                                details);
                        clearSession();

                        // Show error dialog
                        mListener.onSessionMessage("Session subscription failed", false);
                    }
                });
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e("CreateSession", "[Create Session] Error creating session \"" + sessionName + "\"");
                Log.e("CreateSession", "[Create Session] Error: " + error);
                Log.e("CreateSession", "[Create Session] Reason: " + reason);
                Log.e("CreateSession", "[Create Session] Details: " + details);

                // Show error dialog
                mListener.onSessionMessage("Session creation failed", false);
            }
        });
    }

    /**
     * Join an existing session
     *
     * @param sessionName Session to join
     */
    public void joinSession(final String sessionName) {
        Log.d(TAG, "[Join Session] Joining session \"" + sessionName + "\"");

        // Set session
        session = sessionName;

        // Check if this user created the session
        Document document = meteor.getDatabase().getCollection(COLLECTION_SESSIONS)
                .whereEqual(COLLECTION_SESSIONS_COLUMN_TITLE, sessionName)
                .whereEqual(COLLECTION_SESSIONS_COLUMN_ACTIVE, true)
                .findOne();

        // Stop processing if this user is not the original manager
        Object manager = document.getField(COLLECTION_SESSIONS_COLUMN_MANAGER);
        if (manager == null || !manager.equals(userId)) {
            setState(STATE_JOINED_SESSION);
            return;
        }

        // Restore session state
        setState(STATE_CREATED_SESSION);
        sessionDocumentId = document.getId();

        // Subscribe to the session
        mListener.onSessionMessage("Loading data. Please wait...", true);
        meteor.subscribe(sessionName, null, new SubscribeListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Re-manage \"" + sessionName + "\" successfully");
                mListener.onSessionMessage("Rejoined session \"" + sessionName + "\" as manager", false);
                mListener.onSessionManage(sessionName);
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(TAG, "[Re-manage \"" + sessionName + "\"] Failed to subscribe.");
                Log.e(TAG, "[Re-manage \"" + sessionName + "\"] Error: " + error);
                Log.e(TAG, "[Re-manage \"" + sessionName + "\"] Reason: " + reason);
                Log.e(TAG, "[Re-manage \"" + sessionName + "\"] Details: " + details);
                clearSession();

                // Show error dialog
                mListener.onSessionMessage("Failed to re-join session as manager", false);
            }
        });
    }

    /**
     * Leave the current session
     */
    public void leaveSession() {
        // Validate a session is joined
        if (getState() != STATE_JOINED_SESSION || session == null) {
            return;
        }

        Log.d(TAG, "[Leave Session] Leaving session \"" + session + "\"");
        clearSession();
    }

    /**
     * Clear the current session
     */
    public void clearSession() {
        setState(STATE_NO_SESSION);
        session = null;
        sessionDocumentId = null;
    }

    /**
     * Get all sessions
     *
     * @return List of sessions
     */
    public Document[] getSessions() {
        return database.getCollection(COLLECTION_SESSIONS).whereEqual(COLLECTION_SESSIONS_COLUMN_ACTIVE, true).find();
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

        // TODO: Remove this
//        mListener.onSessionMessage("Posting " + location.getProvider() + " data", true);

        // Make sure there is a connection to Meteor
        if (!meteor.isConnected()) {
            Log.d(TAG, "[Post Location] Reconnecting to Meteor");
            meteor.reconnect();
        }

        HashMap<String, Object> data = new HashMap<>();
        data.put(COLLECTION_GPS_DATA_COLUMN_SESSION_ID, getSession());
        data.put(COLLECTION_GPS_DATA_COLUMN_USER_ID, getUserId());
        data.put(COLLECTION_GPS_DATA_COLUMN_PROVIDER, location.getProvider());
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
        Log.d(TAG, "Connected to Meteor. Auto-signed in: " + signedInAutomatically);
        // TODO: Remove this
//        mListener.onSessionMessage("Connected to Meteor", true);

        // Try to restore subscription if it exists
        if (getState() == STATE_CREATED_SESSION && session != null) {
            // Subscribe to the session
            meteor.subscribe(session, null, new SubscribeListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Resubscribed to \"" + session + "\" successfully");
                }

                @Override
                public void onError(String error, String reason, String details) {
                    Log.e(TAG,
                            "[Resubscribe to \"" + session + "\"] Failed to resubscribe.");
                    Log.e(TAG, "[Resubscribe to \"" + session + "\"] Error: " +
                            error);
                    Log.e(TAG, "[Resubscribe to \"" + session + "\"] Reason: " +
                            reason);
                    Log.e(TAG, "[Resubscribe to \"" + session + "\"] Details: " +
                            details);

                    // Show error dialog
                    mListener.onSessionMessage("Session re-subscription failed", false);
                }
            });
        }
    }

    @Override
    public void onDisconnect() {
        Log.d(TAG, "Disconnected from Meteor");
        // TODO: Remove this
//        mListener.onSessionMessage("Disconnected from Meteor", true);
    }

    @Override
    public void onException(Exception e) {
        Log.d(TAG, "Meteor error: " + e.getMessage());
        // TODO: Remove this
//        mListener.onSessionMessage("Meteor error: " + e.getMessage(), false);
    }

    @Override
    public void onDataAdded(String collectionName, String documentID, String newValuesJson) {
        Log.d(TAG,
                String.format(Locale.US, "Document \"%s\" added to collection \"%s\"", documentID, collectionName));
        Log.d(TAG, "Data: " + newValuesJson);

        // Only trigger GPS data listener if the user created the session
        if (collectionName.equals(COLLECTION_GPS_DATA)) {
            if (getState() == STATE_CREATED_SESSION && getSession() != null) {
                mListener.onReceivedGPSData(documentID);
            }
        }
    }

    @Override
    public void onDataChanged(String collectionName, String documentID,
                              String updatedValuesJson, String removedValuesJson) {
        Log.d(TAG,
                String.format(Locale.US, "Document \"%s\" changed in collection \"%s\"", documentID, collectionName));
        Log.d(TAG, "Updated: " + updatedValuesJson);
        Log.d(TAG, "Removed: " + removedValuesJson);

        if (collectionName.equals(COLLECTION_SESSIONS)) {
            Collection collection = database.getCollection(collectionName);
            Document document = collection.getDocument(documentID);

            boolean active = (boolean) document.getField(COLLECTION_SESSIONS_COLUMN_ACTIVE);
            String title = (String) document.getField(COLLECTION_SESSIONS_COLUMN_TITLE);

            // Current session becomes inactive
            if (getState() == STATE_JOINED_SESSION && !active && session.equals(title)) {
                mListener.onSessionClosed(title);
            }
        }
    }

    @Override
    public void onDataRemoved(String collectionName, String documentID) {
        Log.d(TAG,
                String.format(Locale.US, "Document \"%s\" removed from collection \"%s\"", documentID, collectionName));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "display_name":
                changeDisplayName(sharedPreferences.getString(key, null));
                break;
        }
    }
}
