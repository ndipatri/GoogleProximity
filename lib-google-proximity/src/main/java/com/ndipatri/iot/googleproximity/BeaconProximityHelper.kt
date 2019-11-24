package com.ndipatri.iot.googleproximity

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log

import com.google.android.gms.auth.GoogleAuthUtil
import com.ndipatri.iot.googleproximity.activities.AuthenticationActivity
import com.ndipatri.iot.googleproximity.models.api.AttachmentInfo
import com.ndipatri.iot.googleproximity.models.api.BatchDeleteResponse
import com.ndipatri.iot.googleproximity.models.api.Beacon
import io.reactivex.*

import org.apache.commons.io.output.ByteArrayOutputStream
import org.joda.time.DateTime

import java.io.IOException
import java.io.ObjectOutputStream
import java.security.InvalidParameterException
import java.util.HashMap
import java.util.concurrent.TimeUnit

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException

class BeaconProximityHelper(val context: Context,
                            trustAllConnections: Boolean) {

    private val namespaceSingle: Single<String>

    private val beaconProximityAPI: BeaconProximityAPI = BeaconProximityAPI(trustAllConnections)

    private val attachmentCache = AttachmentCache()

    private val oAuthToken: Single<String>
        get() = getOAuthToken(null)

    init {
        namespaceSingle = oAuthToken
                .flatMap { googleAccountToken -> beaconProximityAPI.getNamespace(googleAccountToken) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .cache()
    }

    /**
     * Declares that the given Activity will be making ProximityAPI calls that require
     * authentication.  It's best to bug user for authentication only when a specific Activity
     * requires it.
     *
     *
     * Activities that will be calling methods on this helper that require user account
     * authorization should first call this method.
     */
    fun redirectToAuthenticationActivityIfNecessary(activity: Context) {

        // A token has yet to be created for a given Google Account.  This requires
        // user interaction so do that now...
        if (!GoogleProximity.instance!!.hasGoogleAccountForOAuth()) {
            val intent = Intent(context, AuthenticationActivity::class.java)
            activity.startActivity(intent)

            /**
             * Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
             * ComponentName cName = new ComponentName("com.ndipatri.iot.googleproximity", "com.ndipatri.iot.googleproximity.activities.AuthenticationActivity");
             * intent.setComponent(;
             * startActivity(intent);
             */
        }
    }

    /**
     * This does not require the user to authorize with their google account.  Any user can retrieve
     * attachment information.
     *
     *
     * A timed-cache is used here so multiple calls can be made in a short period of time.
     *
     * @param advertiseId
     */
    fun retrieveAttachment(advertiseId: ByteArray): Maybe<Array<String>> {

        val apiBeacon = Beacon(advertiseId)

        val cachedAttachment = attachmentCache.getAttachment(apiBeacon)

        if (null != cachedAttachment) {

            Log.d(TAG, "Using cached attachment data.")
            return Maybe.just(cachedAttachment)

        } else {

            return beaconProximityAPI.getBeaconAttachment(apiBeacon.advertisedId)
                    .flatMap { attachment ->
                        Maybe.create { subscriber: MaybeEmitter<Array<String>> ->

                            attachmentCache.cacheAttachment(apiBeacon, attachment)

                            subscriber.onSuccess(attachment)
                        }
                    }

                    .timeout(PROXIMITY_API_CALL_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)

                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
        }
    }

    // Please be sure the passed Beacon is a DTO (detached) not DAO (attached to Realm)
    fun createAttachment(advertiseId: ByteArray,
                         attachment: Array<String>): Completable {

        val apiBeacon = Beacon(advertiseId)

        // The API isn't CRUD for attachments.  There is no way to createOrUpdate an attachment.  You can only create, delete, list.
        // So for now, we delete all existing and just add one.. so retrieval is therefore easy.
        return initializeProximityAPIForBeacon(apiBeacon)
                .andThen(oAuthToken)
                .flatMap<BatchDeleteResponse> { googleAccountToken -> beaconProximityAPI.batchDeleteAttachments(apiBeacon.beaconName, googleAccountToken) }
                .flatMap { oAuthToken }
                .zipWith(renderAttachmentInfo(attachment),
                        BiFunction { googleAccountToken: String, attachmentInfo: AttachmentInfo ->
                            beaconProximityAPI.createAttachment(attachmentInfo, apiBeacon.beaconName, googleAccountToken)
                        })
                .flatMap { attachmentInfoObservable -> attachmentInfoObservable }
                .flatMapCompletable {
                    Completable.create {
                        Log.d(TAG, "Updated Beacon '$advertiseId'")

                        attachmentCache.cacheAttachment(apiBeacon, attachment)
                    }
                }

                .timeout(PROXIMITY_API_CALL_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)

                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun getOAuthToken(_selectedGoogleAccount: String? = null): Single<String> {
        return Single.create { subscriber: SingleEmitter<String> ->

            var selectedGoogleAccount = _selectedGoogleAccount
            if (null == selectedGoogleAccount) {
                selectedGoogleAccount = GoogleProximity.instance!!.googleAccountForOAuth.get()
            }

            var oauthToken: String? = null
            try {
                Log.d(TAG, "Retrieving Google OAuth token for Account '$selectedGoogleAccount'...")
                // This is usually not a network call, but sometimes it can be...
                oauthToken = GoogleAuthUtil.getToken(context,
                        selectedGoogleAccount,
                        OAUTH_SCOPE_PROXIMITY)

                if (null == oauthToken) {
                    Log.e(TAG, "Retrieved null OAuth token.")
                    if (!subscriber.isDisposed) {
                        subscriber.onError(InvalidParameterException())
                    }
                } else {
                    Log.e(TAG, "Retrieved OAuth token '$oauthToken'.")
                    subscriber.onSuccess(oauthToken)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception retrieving OAuth token.", ex)

                if (!subscriber.isDisposed) {
                    subscriber.onError(ex)
                }
            }
        }

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun registerAndActivateBeacon(apiBeacon: Beacon): Completable {

        Log.d(TAG, "New beacon found.  Registering and activating...")

        return getOAuthToken()
                .flatMap { googleAccountToken -> beaconProximityAPI.registerBeacon(apiBeacon, googleAccountToken) }
                .flatMap { beacon -> oAuthToken }
                .flatMapCompletable { googleAccountToken -> beaconProximityAPI.activateBeacon(apiBeacon.beaconName, googleAccountToken) }

                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    private fun renderAttachmentInfo(attachment: Array<String>): Single<AttachmentInfo> {

        return namespaceSingle
                .flatMap { namespace ->
                    Single.create { subscriber: SingleEmitter<AttachmentInfo> ->

                        val attachmentInfo = AttachmentInfo()
                        try {
                            val byteArrayOutputStream = ByteArrayOutputStream()
                            val objectOut = ObjectOutputStream(byteArrayOutputStream)
                            objectOut.writeObject(attachment)

                            attachmentInfo.namespacedType = String.format("%s/%s", namespace, "string")
                            val beaconAttachmentBytes = byteArrayOutputStream.toByteArray()
                            attachmentInfo.data = Base64.encodeToString(beaconAttachmentBytes, Base64.NO_WRAP)
                        } catch (e: IOException) {
                            Log.e(TAG, "Unable to serialize BeaconAttachment.", e)
                            if (!subscriber.isDisposed) {
                                subscriber.onError(e)
                            }
                        }

                        Log.d(TAG, "Rendered beacon attachment '$attachmentInfo'.")

                        subscriber.onSuccess(attachmentInfo)
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * We lazy-initialize and register any beacons we encounter.  They do not need provisioning
     * ahead of time.
     *
     *
     * Requires user google account to be authorized. We assume this has already been
     * done.
     *
     * @param apiBeacon
     */
    private fun initializeProximityAPIForBeacon(apiBeacon: Beacon): Completable {

        return getOAuthToken()

                .flatMap { googleAccountToken -> beaconProximityAPI.getBeacon(apiBeacon.beaconName, googleAccountToken) }

                // Evaluate state of beacon and provision if necessary ...
                .flatMapCompletable { retrievedBeacon ->
                    if (retrievedBeacon.status == Beacon.STATUS_INACTIVE) {

                        getOAuthToken().flatMapCompletable { googleAccountToken: String ->
                            Completable.create {
                                beaconProximityAPI.getBeacon(apiBeacon.beaconName, googleAccountToken)
                            }
                        }

                    } else if (retrievedBeacon.status == Beacon.STATUS_UNREGISTERED ||
                            // at one point, the Proximity API would return a Beacon object
                            // with NOT_FOUND status, but eventually the API began returning
                            // 404 HTTP response.. I keep this here just in case.
                            retrievedBeacon.status == Beacon.STATUS_NOT_FOUND) {

                        registerAndActivateBeacon(apiBeacon)
                    } else {

                        // beacon already provisioned.. just complete
                        Completable.complete()
                    }
                }

                .onErrorResumeNext(Function { throwable: Throwable ->
                    if ((throwable as HttpException).code() == 404) {
                        registerAndActivateBeacon(apiBeacon);
                    } else {
                        Completable.error(throwable);
                    }
                })

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private class AttachmentCache {

        // Every 30 minutes to save on API calls... The only way this is a problem is if
        // you have two devices changing the same beacon attachment info.
        protected var attachmentLifeMillis = (30 * 60 * 1000).toLong()

        private val attachmentCache = HashMap<String, TimedAttachment>()

        private class TimedAttachment(internal var attachment: Array<String>) {
            internal var timestamp: Long? = null

            init {
                this.timestamp = DateTime.now().millis
            }
        }

        fun cacheAttachment(apiBeacon: Beacon, attachment: Array<String>) {
            attachmentCache[generateKey(apiBeacon)] = TimedAttachment(attachment)
        }

        fun getAttachment(apiBeacon: Beacon): Array<String>? {

            val beaconKey = generateKey(apiBeacon)

            var cachedAttachment: Array<String>? = null

            val timedAttachment = attachmentCache[beaconKey]
            if (null != timedAttachment) {
                if (DateTime.now().millis - timedAttachment.timestamp!! < attachmentLifeMillis) {
                    cachedAttachment = timedAttachment.attachment
                } else {
                    attachmentCache.remove(beaconKey)
                }
            }

            return cachedAttachment
        }

        private fun generateKey(apiBeacon: Beacon): String {
            return apiBeacon.beaconName
        }
    }

    companion object {

        private val TAG = BeaconProximityHelper::class.java!!.getSimpleName()

        private val PROXIMITY_API_CALL_TIMEOUT_MILLIS = 20000

        private val OAUTH_SCOPE_PROXIMITY = "oauth2:https://www.googleapis.com/auth/userlocation.beacon.registry"
    }
}
