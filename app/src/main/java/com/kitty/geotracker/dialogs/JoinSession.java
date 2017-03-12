package com.kitty.geotracker.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.kitty.geotracker.MapsActivity;
import com.kitty.geotracker.R;

import java.util.ArrayList;
import java.util.HashMap;

import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.MeteorCallback;
import im.delight.android.ddp.MeteorSingleton;
import im.delight.android.ddp.db.Collection;
import im.delight.android.ddp.db.Database;
import im.delight.android.ddp.db.Document;


public class JoinSession extends DialogFragment implements MeteorCallback, DialogInterface.OnClickListener {

    JoinSessionListener mListener;

    private Meteor mMeteor;
    private Database database;
    private ArrayList<String> items = new ArrayList<>();
    private HashMap<String, String> documentMap = new HashMap<>();
    private ArrayAdapter<String> adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, items);
        mMeteor = MeteorSingleton.getInstance();
        mMeteor.addCallback(this);
        mMeteor.subscribe(MapsActivity.SUBSCRIPTION_SESSION_LIST);
        database = mMeteor.getDatabase();
        refreshData();
    }

    @Override
    public void onDestroy() {
        Log.d("JoinSession", "OnDestroy: Unsubscribing from " + MapsActivity.SUBSCRIPTION_SESSION_LIST);
        mMeteor.unsubscribe(MapsActivity.SUBSCRIPTION_SESSION_LIST);
        mMeteor.removeCallback(this);
        super.onDestroy();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.join_session)
                .setAdapter(adapter, this);
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Log.d("JoinSession", "Selected item " + String.valueOf(which));
        mListener.onSessionJoined(items.get(which));
    }

    // Override the Fragment.onAttach() method to instantiate the JoinSessionListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the JoinSessionListener so we can send events to the host
            mListener = (JoinSessionListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString() + " must implement JoinSessionListener");
        }
    }

    /* The activity that creates an instance of this dialog fragment must
         * implement this interface in order to receive event callbacks.
         * Each method passes the DialogFragment in case the host needs to query it. */
    public interface JoinSessionListener {
        public void onSessionJoined(final String sessionName);
    }

    private void refreshData() {
        Collection sessions = database.getCollection(MapsActivity.COLLECTION_SESSIONS2);
        items.clear();
        documentMap.clear();
        for (Document document : sessions.find()) {
            String title = document.getField("title").toString();
            items.add(title);
            documentMap.put(document.getId(), title);
        }
    }

    @Override
    public void onConnect(boolean signedInAutomatically) {
        Log.d("JoinSession", "Connected to Meteor server");
    }

    @Override
    public void onDisconnect() {
        Log.d("JoinSession", "Disconnected from Meteor server");
    }

    @Override
    public void onException(Exception e) {
        Log.d("JoinSession", "Received exception from Meteor: " + e.getMessage());
    }

    @Override
    public void onDataAdded(String collectionName, String documentID, String newValuesJson) {
        Log.d("JoinSession", "New data added to " + collectionName + ": " + documentID);
        if (collectionName.equals(MapsActivity.COLLECTION_SESSIONS2)) {
            Collection collection = database.getCollection(collectionName);
            if (!documentMap.containsKey(documentID)) {
                Document document = collection.getDocument(documentID);
                String title = document.getField("title").toString();
                items.add(title);
                documentMap.put(documentID, title);
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onDataRemoved(String collectionName, String documentID) {
        Log.d("JoinSession", "Data removed from " + collectionName + ": " + documentID);
        if (collectionName.equals(MapsActivity.COLLECTION_SESSIONS2)) {
            String title = documentMap.get(documentID);
            if (title != null) {
                items.remove(title);
                documentMap.remove(documentID);
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onDataChanged(String collectionName, String documentID, String updatedValuesJson,
                              String removedValuesJson) {
        Log.d("JoinSession", "Data changed in " + collectionName + ": " + documentID);
        if (collectionName.equals(MapsActivity.COLLECTION_SESSIONS2)) {
            String title = documentMap.get(documentID);
            if (title != null) {
                Collection collection = database.getCollection(collectionName);
                String newTitle = collection.getDocument(documentID).getField("title").toString();
                items.set(items.indexOf(title), newTitle);
                documentMap.put(documentID, newTitle);
                adapter.notifyDataSetChanged();
            }
        }
    }
}