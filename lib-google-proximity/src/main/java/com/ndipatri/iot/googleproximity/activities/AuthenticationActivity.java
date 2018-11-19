package com.ndipatri.iot.googleproximity.activities;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SignInButton;
import com.ndipatri.iot.googleproximity.GoogleProximity;
import com.ndipatri.iot.googleproximity.R;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

public class AuthenticationActivity extends AppCompatActivity {

    private static final String TAG = AuthenticationActivity.class.getSimpleName();

    private static final int REQUEST_PICK_ACCOUNT = 42;
    private static final int REQUEST_ERROR_RECOVER = 43;

    SignInButton chooseGoogleAccountButton;

    protected String selectedGoogleAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_authentiation);

        chooseGoogleAccountButton = (SignInButton) findViewById(R.id.chooseGoogleAccountButton);

        chooseGoogleAccountButton.setOnClickListener(v -> pickUserAccount());
    }

    private void pickUserAccount() {
        String[] accountTypes = new String[]{"com.google"};
        Intent intent = AccountPicker.newChooseAccountIntent(null, null, accountTypes, true, null, null, null, null);
        startActivityForResult(intent, REQUEST_PICK_ACCOUNT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_PICK_ACCOUNT) {
            // Receiving a result from the AccountPicker
            if (resultCode == RESULT_OK) {

                // With the account name acquired, go get the auth token
                this.selectedGoogleAccount = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                getFirstTokenForAccount();

            } else if (resultCode == RESULT_CANCELED) {
                // The account picker dialog closed without selecting an account.
                // Notify users that they must pick an account to proceed.
                // and let them try again...
                Toast.makeText(this, "You have to select an account.", Toast.LENGTH_SHORT).show();
            }
        }

        // Handle the result from exceptions.. the google account has been selected and
        // user has authorized account
        if (requestCode == REQUEST_ERROR_RECOVER && resultCode == RESULT_OK) {

            Log.d(TAG, "All permissions have been granted with this application for selected Google account('" + selectedGoogleAccount + "').");

            // Preemptively get a token so we have it when it's needed
            getFirstTokenForAccount();
        }
    }

    protected void getFirstTokenForAccount() {
        GoogleProximity.getInstance().getOAuthToken(selectedGoogleAccount).subscribe(new SingleObserver<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(String token) {
                // We only do this once we've successfully retrieved an oauth token for this
                // account
                //
                // We don't actually use the token now, we just want to 'prime' the
                // GoogleAuthUtil so it can produce future tokens with very little
                // likelihood of user interaction.
                Log.e(TAG, "OAuth token successfully retrieved.");
                GoogleProximity.getInstance().setGoogleAccountForOAuth(selectedGoogleAccount);

                finish();
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof UserRecoverableAuthException) {
                    promptUserToResolveException((Exception) e);
                } else {
                    Log.e(TAG, "Fatal Exception", e);
                }
            }
        });
    }

    public void promptUserToResolveException(final Exception exception) {
        if (exception instanceof GooglePlayServicesAvailabilityException) {
            // The Google Play services APK is old, disabled, or not present.
            // Show a dialog created by Google Play services that allows
            // the user to createOrUpdate the APK
            int statusCode = ((GooglePlayServicesAvailabilityException) exception).getConnectionStatusCode();

            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(statusCode,
                    AuthenticationActivity.this,
                    REQUEST_ERROR_RECOVER);
            dialog.show();
        } else if (exception instanceof UserRecoverableAuthException) {
            // Unable to authenticate, such as when the user has not yet granted
            // the app access to the account, but the user can fix this.
            // Forward the user to an activity in Google Play services.
            Intent intent = ((UserRecoverableAuthException) exception).getIntent();
            startActivityForResult(intent, REQUEST_ERROR_RECOVER);
        }
    }
}
