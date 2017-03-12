package com.kitty.geotracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.MeteorCallback;
import im.delight.android.ddp.MeteorSingleton;
import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.db.Database;
import im.delight.android.ddp.db.Document;
import im.delight.android.ddp.db.memory.InMemoryDatabase;

public class MeteorController implements MeteorCallback {

    private static MeteorController mInstance;
    private Meteor meteor;
    private Database database;

    // Collections
    public static final String COLLECTION_SESSIONS = "Sessions";
    public static final String COLLECTION_GPS_DATA = "GPSData";

    // Subscriptions
    public static final String SUBSCRIPTION_SESSION_LIST = "SessionsList";

    private MeteorController(Context context) {
        // Get meteor url from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String meteorUrl = String.format(Locale.US, "ws://%s/websocket",
                prefs.getString("meteor_ip", "geotracker-web.herokuapp.com"));

        // Create a new Meteor instance
        if (!MeteorSingleton.hasInstance()) {
            meteor = MeteorSingleton.createInstance(context, meteorUrl, new InMemoryDatabase());
            meteor.addCallback(this);
        } else {
            meteor = MeteorSingleton.getInstance();
        }
        database = meteor.getDatabase();
    }

    public synchronized static MeteorController createInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MeteorController(context);
        }
        return mInstance;
    }

    public synchronized static MeteorController getInstance() {
        if (mInstance == null) {
            throw new IllegalStateException("Please call `createInstance(...)` first");
        }
        return mInstance;
    }

    public synchronized static boolean hasInstance() {
        return mInstance != null;
    }

    public void connect() {
        meteor.connect();
    }

    public void disconnect() {
        meteor.disconnect();
    }

    public Meteor getMeteor() {
        return meteor;
    }

    public void createSession(final String sessionName) {
        Log.d(getClass().getSimpleName(), "Creating new session \"" + sessionName + "\"...");
        HashMap<String, Object> sessionData = new HashMap<>();
        sessionData.put("title", sessionName);
        meteor.insert(COLLECTION_SESSIONS, sessionData, new ResultListener() {
            @Override
            public void onSuccess(String result) {
                Log.i(getClass().getSimpleName(), "Created Session \"" + sessionName + "\": " + result);
                // TODO: Subscribe to the session
            }

            @Override
            public void onError(String error, String reason, String details) {
                Log.e(getClass().getSimpleName(), "Error creating session \"" + sessionName + "\"");
                Log.e(getClass().getSimpleName(), "Error: " + error);
                Log.e(getClass().getSimpleName(), "Reason: " + reason);
                Log.e(getClass().getSimpleName(), "Details: " + details);
            }
        });
    }

    public ArrayList<String> getSessions() {
        ArrayList<String> sessionList = new ArrayList<>();
        for (Document document : database.getCollection(COLLECTION_SESSIONS).find()) {
            String title = document.getField("title").toString();
            sessionList.add(title);
        }
        return sessionList;
    }

    public void joinSession(final String sessionName) {
        // TODO: Subscribe to the session
        Log.d(getClass().getSimpleName(), "Joining session \"" + sessionName + "\"");
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
}
