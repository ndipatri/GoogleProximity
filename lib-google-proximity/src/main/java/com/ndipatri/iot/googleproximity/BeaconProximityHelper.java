package com.ndipatri.iot.googleproximity;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;
import com.ndipatri.iot.googleproximity.activities.AuthenticationActivity;
import com.ndipatri.iot.googleproximity.models.api.AttachmentInfo;
import com.ndipatri.iot.googleproximity.models.api.Beacon;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BeaconProximityHelper {

    private static final String TAG = BeaconProximityHelper.class.getSimpleName();

    private static final int PROXIMITY_API_CALL_TIMEOUT_MILLIS = 20000;

    private static final String OAUTH_SCOPE_PROXIMITY =
            "oauth2:https://www.googleapis.com/auth/userlocation.beacon.registry";

    private Single<String> namespaceSingle;

    private Context context;

    private BeaconProximityAPI beaconProximityAPI;

    private AttachmentCache attachmentCache = new AttachmentCache();

    public BeaconProximityHelper(final Context context,
                                 final boolean trustAllConnections) {
        this.context = context;

        this.beaconProximityAPI = new BeaconProximityAPI(trustAllConnections);

        namespaceSingle = getOAuthToken()
                .flatMap(googleAccountToken -> beaconProximityAPI.getNamespace(googleAccountToken))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .cache();
    }

    /**
     * Declares that the given Activity will be making ProximityAPI calls that require
     * authentication.  It's best to bug user for authentication only when a specific Activity
     * requires it.
     * <p>
     * Activities that will be calling methods on this helper that require user account
     * authorization should first call this method.
     */
    public void redirectToAuthenticationActivityIfNecessary(Context activity) {

        // A token has yet to be created for a given Google Account.  This requires
        // user interaction so do that now...
        if (!GoogleProximity.getInstance().hasGoogleAccountForOAuth()) {
            Intent intent = new Intent(context, AuthenticationActivity.class);
            activity.startActivity(intent);

            /**
            Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
            ComponentName cName = new ComponentName("com.ndipatri.iot.googleproximity", "com.ndipatri.iot.googleproximity.activities.AuthenticationActivity");
            intent.setComponent(;
            startActivity(intent);
             **/
        }
    }

    /**
     * This does not require the user to authorize with their google account.  Any user can retrieve
     * attachment information.
     * <p>
     * A timed-cache is used here so multiple calls can be made in a short period of time.
     *
     * @param advertiseId
     */
    public Maybe<String[]> retrieveAttachment(final byte[] advertiseId) {

        final Beacon apiBeacon = new Beacon(advertiseId);

        String[] cachedAttachment = attachmentCache.getAttachment(apiBeacon);

        if (null != cachedAttachment) {

            Log.d(TAG, "Using cached attachment data.");
            return Maybe.just(cachedAttachment);

        } else {

            return beaconProximityAPI.getBeaconAttachment(apiBeacon.advertisedId)
                    .flatMap(attachment -> Maybe.create((MaybeEmitter<String[]> subscriber) -> {

                        attachmentCache.cacheAttachment(apiBeacon, attachment);

                        subscriber.onSuccess(attachment);
                    }))

                    .timeout(PROXIMITY_API_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());
        }
    }

    // Please be sure the passed Beacon is a DTO (detached) not DAO (attached to Realm)
    public Completable createAttachment(final byte[] advertiseId,
                                        final String[] attachment) {

        final Beacon apiBeacon = new Beacon(advertiseId);

        // The API isn't CRUD for attachments.  There is no way to createOrUpdate an attachment.  You can only create, delete, list.
        // So for now, we delete all existing and just add one.. so retrieval is therefore easy.
        return initializeProximityAPIForBeacon(apiBeacon)
                .andThen(getOAuthToken())
                .flatMap(googleAccountToken -> beaconProximityAPI.batchDeleteAttachments(apiBeacon.getBeaconName(), googleAccountToken))
                .flatMap(batchDeleteResponse -> getOAuthToken())
                .zipWith(renderAttachmentInfo(attachment),
                        (googleAccountToken, attachmentInfo) -> beaconProximityAPI.createAttachment(attachmentInfo, apiBeacon.getBeaconName(), googleAccountToken))
                .flatMap(attachmentInfoObservable -> attachmentInfoObservable)
                .flatMapCompletable(attachmentInfo -> subscriber -> {
                    Log.d(TAG, "Updated Beacon '" + advertiseId + "'");

                    attachmentCache.cacheAttachment(apiBeacon, attachment);

                    subscriber.onComplete();
                })

                .timeout(PROXIMITY_API_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Single<String> getOAuthToken(final String _selectedGoogleAccount) {
        return Single.create((SingleEmitter<String> subscriber) -> {

            String selectedGoogleAccount = _selectedGoogleAccount;
            if (null == selectedGoogleAccount) {
                selectedGoogleAccount = GoogleProximity.getInstance().getGoogleAccountForOAuth().get();
            }

            String oauthToken = null;
            try {
                Log.d(TAG, "Retrieving Google OAuth token for Account '" + selectedGoogleAccount + "'...");
                // This is usually not a network call, but sometimes it can be...
                oauthToken = GoogleAuthUtil.getToken(context,
                        selectedGoogleAccount,
                        OAUTH_SCOPE_PROXIMITY);

                if (null == oauthToken) {
                    Log.e(TAG, "Retrieved null OAuth token.");
                    subscriber.onError(new InvalidParameterException());
                } else {
                    Log.e(TAG, "Retrieved OAuth token '" + oauthToken + "'.");
                    subscriber.onSuccess(oauthToken);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Exception retrieving OAuth token.", ex);

                subscriber.onError(ex);
            }
        })

        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
    }

    private Single<String> getOAuthToken() {
        return getOAuthToken(null);
    }

    private Completable registerAndActivateBeacon(final Beacon apiBeacon) {

        Log.d(TAG, "New beacon found.  Registering and activating...");

        return getOAuthToken()
                .flatMap(googleAccountToken -> beaconProximityAPI.registerBeacon(apiBeacon, googleAccountToken))
                .flatMap(beacon -> getOAuthToken())
                .flatMapCompletable(googleAccountToken -> beaconProximityAPI.activateBeacon(apiBeacon.getBeaconName(), googleAccountToken))

                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    private Single<AttachmentInfo> renderAttachmentInfo(final String[] attachment) {

        return namespaceSingle
                .flatMap(namespace -> Single.create((SingleEmitter<AttachmentInfo> subscriber) -> {

                    final AttachmentInfo attachmentInfo = new AttachmentInfo();
                    try {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ObjectOutputStream objectOut = new ObjectOutputStream(byteArrayOutputStream);
                        objectOut.writeObject(attachment);

                        attachmentInfo.namespacedType = String.format("%s/%s", namespace, "string");
                        byte[] beaconAttachmentBytes = byteArrayOutputStream.toByteArray();
                        attachmentInfo.data = Base64.encodeToString(beaconAttachmentBytes, Base64.NO_WRAP);
                    } catch (IOException e) {
                        Log.e(TAG, "Unable to serialize BeaconAttachment.", e);
                        subscriber.onError(e);
                    }

                    Log.d(TAG, "Rendered beacon attachment '" + attachmentInfo + "'.");

                    subscriber.onSuccess(attachmentInfo);
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * We lazy-initialize and register any beacons we encounter.  They do not need provisioning
     * ahead of time.
     * <p>
     * Requires user google account to be authorized. We assume this has already been
     * done.
     *
     * @param apiBeacon
     */
    private Completable initializeProximityAPIForBeacon(final Beacon apiBeacon) {

        return getOAuthToken()

                .flatMap(googleAccountToken -> beaconProximityAPI.getBeacon(apiBeacon.getBeaconName(), googleAccountToken))

                // Evaluate state of beacon and provision if necessary ...
                .flatMapCompletable(retrievedBeacon -> {

                    if (retrievedBeacon.status.equals(Beacon.STATUS_INACTIVE)) {

                        return getOAuthToken()
                                .flatMapCompletable(googleAccountToken -> beaconProximityAPI.activateBeacon(apiBeacon.getBeaconName(), googleAccountToken));

                    } else if (retrievedBeacon.status.equals(Beacon.STATUS_UNREGISTERED) ||
                            // at one point, the Proximity API would return a Beacon object
                            // with NOT_FOUND status, but eventually the API began returning
                            // 404 HTTP response.. I keep this here just in case.
                            retrievedBeacon.status.equals(Beacon.STATUS_NOT_FOUND)) {

                        return registerAndActivateBeacon(apiBeacon);
                    } else {

                        // beacon already provisioned.. just complete
                        return Completable.complete();
                    }
                })

                .onErrorResumeNext(throwable -> {
                    if (((HttpException) throwable).code() == 404) {
                        return registerAndActivateBeacon(apiBeacon);
                    } else {
                        return Completable.error(throwable);
                    }
                })

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private class AttachmentCache {

        // Every 30 minutes to save on API calls... The only way this is a problem is if
        // you have two devices changing the same beacon attachment info.
        protected long attachmentLifeMillis = 30 * 60 * 1000;

        private class TimedAttachment {
            String[] attachment;
            Long timestamp;

            public TimedAttachment(String[] attachment) {
                this.attachment = attachment;
                this.timestamp = DateTime.now().getMillis();
            }
        }

        private Map<String, TimedAttachment> attachmentCache = new HashMap<>();

        public void cacheAttachment(final Beacon apiBeacon, final String[] attachment) {
            attachmentCache.put(generateKey(apiBeacon), new TimedAttachment(attachment));
        }

        public String[] getAttachment(final Beacon apiBeacon) {

            String beaconKey = generateKey(apiBeacon);

            String[] cachedAttachment = null;

            TimedAttachment timedAttachment = attachmentCache.get(beaconKey);
            if (null != timedAttachment) {
                if (DateTime.now().getMillis() - timedAttachment.timestamp < attachmentLifeMillis) {
                    cachedAttachment = timedAttachment.attachment;
                } else {
                    attachmentCache.remove(beaconKey);
                }
            }

            return cachedAttachment;
        }

        private String generateKey(final Beacon apiBeacon) {
            return apiBeacon.getBeaconName();
        }
    }
}
