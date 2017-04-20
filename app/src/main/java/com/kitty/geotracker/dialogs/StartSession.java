package com.kitty.geotracker.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import com.kitty.geotracker.R;


public class StartSession extends DialogFragment implements DialogInterface.OnClickListener {

    private StartSessionListener mListener;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(R.string.start_session)
                .setView(R.layout.dialog_start_session)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this);

        return builder.create();
    }

    /**
     * Override the positive button on click listener to perform validation.
     */
    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StartSession.this.onClick(getDialog(), AlertDialog.BUTTON_POSITIVE);
                }
            });
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                EditText sessionName = (EditText) getDialog().findViewById(R.id.start_session_name);
                if (sessionName.getText().length() == 0) {
                    sessionName.setError("Enter a session name");
                    break;
                }

                mListener.onSessionStarted(sessionName.getText().toString());
                dismiss();
                break;
        }
    }

    // Override the Fragment.onAttach() method to instantiate the StartSessionListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the StartSessionListener so we can send events to the host
            mListener = (StartSessionListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString() + " must implement StartSessionListener");
        }
    }

    public interface StartSessionListener {
        public void onSessionStarted(final String sessionName);
    }
}
