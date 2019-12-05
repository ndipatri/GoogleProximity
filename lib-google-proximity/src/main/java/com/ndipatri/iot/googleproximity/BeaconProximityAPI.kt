// Copyright 2015 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ndipatri.iot.googleproximity

import android.util.Base64
import android.util.Log

import com.google.common.collect.ImmutableList
import com.ndipatri.iot.googleproximity.models.api.AdvertisedId
import com.ndipatri.iot.googleproximity.models.api.AttachmentInfo
import com.ndipatri.iot.googleproximity.models.api.BatchDeleteResponse
import com.ndipatri.iot.googleproximity.models.api.Beacon
import com.ndipatri.iot.googleproximity.models.api.GetForObservedRequest
import com.ndipatri.iot.googleproximity.models.api.GetForObservedResponse
import com.ndipatri.iot.googleproximity.models.api.ListNamespacesResponse

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.security.cert.CertificateException

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

class BeaconProximityAPI(trustAllConnections: Boolean) {

    // note this is the 'browser key' from the Google Dev Console, NOT
    // the 'android key'.. many bothan spies died obtaining this
    // information.
    internal var API_KEY = "AIzaSyBRQezTd3GBi-az6US93nE_DfQj6kUkmvc"

    private val service: GoogleProximityApiInterface

    private// Create an ssl socket factory with our all-trusting manager
    val trustAllSSLConnectionsClient: OkHttpClient
        @Throws(Exception::class)
        get() {

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCertsTrustManager, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val authClientBuilder = OkHttpClient.Builder()
            authClientBuilder.sslSocketFactory(sslSocketFactory)

            return authClientBuilder.build()
        }

    init {

        var retrofit: Retrofit? = null
        try {
            val builder = Retrofit.Builder()
                    .baseUrl(ENDPOINT)
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                    .addConverterFactory(GsonConverterFactory.create())

            if (trustAllConnections) {
                builder.client(trustAllSSLConnectionsClient)
            } else {
                builder.client(OkHttpClient())
            }

            retrofit = builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while constructing Retrofit.", e)
        }

        service = retrofit!!.create(GoogleProximityApiInterface::class.java!!)
    }

    /**
     * @param advertisedId
     * @return BeaconAttachment associated with given beacon.
     * @throws CorruptAttachmentException if the retrieved attachment bytes can not be marshalled into
     * a BeaconAttachment. (e.g. change in code)
     */
    fun getBeaconAttachment(advertisedId: AdvertisedId): Maybe<Array<String?>> {

        val getForObservedRequest = GetForObservedRequest()
        getForObservedRequest.namespacedTypes = ImmutableList.of("*")
        getForObservedRequest.observations = ImmutableList.of(GetForObservedRequest.Observation(advertisedId))

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        val url = ENDPOINT + "beaconinfo:getforobserved?key=" + API_KEY

        return service.getForObserved(url, getForObservedRequest)
                .flatMapMaybe { getForObservedResponse ->
                    Maybe.create { subscriber: MaybeEmitter<Array<String?>> ->

                        if (getForObservedResponse.beacons != null &&
                                getForObservedResponse.beacons!!.size > 0 &&
                                null != getForObservedResponse.beacons!![0].attachments) {

                            try {

                                val byteArrayInputStream = ByteArrayInputStream(Base64.decode(getForObservedResponse.beacons!![0].attachments!![0].data, Base64.DEFAULT))
                                val objectIn = ObjectInputStream(byteArrayInputStream)

                                val payload = objectIn.readObject() as Array<String?>

                                subscriber.onSuccess(payload)
                            } catch (e: InvalidClassException) {
                                Log.e(TAG, "Exception while fetching attachments (corruption)", e)
                                // This means the attachment is dead to us, so delete it...
                                if (!subscriber.isDisposed) {
                                    subscriber.onError(CorruptAttachmentException())
                                }
                            } catch (e: ClassNotFoundException) {
                                Log.e(TAG, "Exception while fetching attachments (corruption)", e)
                                if (!subscriber.isDisposed) {
                                    subscriber.onError(CorruptAttachmentException())
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Exception while fetching attachments", e)

                                if (!subscriber.isDisposed) {
                                    subscriber.onError(e)
                                }
                            }

                        } else {
                            subscriber.onComplete()
                        }
                    }
                }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun getNamespace(googleAccountToken: String): Single<String> {
        return service.listNamespaces("Bearer $googleAccountToken")
                .flatMap { listNamespacesResponse ->
                    var namespaceValue = listNamespacesResponse?.namespaces?.get(0)?.namespaceName
                    if (namespaceValue!!.startsWith("namespaces/")) {
                        namespaceValue = namespaceValue.substring("namespaces/".length)
                    }

                    Single.just(namespaceValue)
                }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun registerBeacon(beacon: Beacon, googleAccountToken: String): Single<Beacon> {

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        val url = ENDPOINT + "beacons:register"

        return service.registerBeacon(url, beacon, "Bearer $googleAccountToken")
    }

    fun activateBeacon(beaconName: String, googleAccountToken: String): Completable {

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        val url = ENDPOINT + "beacons/" + beaconName + ":activate"

        return service.activateBeacon(url, "Bearer $googleAccountToken")
    }

    fun getBeacon(beaconName: String, googleAccountToken: String): Single<Beacon> {
        return service.getBeacon(beaconName, "Bearer $googleAccountToken")
    }

    fun batchDeleteAttachments(beaconName: String, googleAccountToken: String): Single<BatchDeleteResponse> {
        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        val url = ENDPOINT + "beacons/" + beaconName + "/attachments:batchDelete"

        return service.batchDeleteAttachments(url, "Bearer $googleAccountToken")
    }

    fun createAttachment(attachment: AttachmentInfo,
                         beaconName: String,
                         googleAccountToken: String): Single<AttachmentInfo> {

        return service.createAttachment(attachment, beaconName, "Bearer $googleAccountToken")
    }

    class CorruptAttachmentException : Exception()

    private interface GoogleProximityApiInterface {

        @POST
        fun getForObserved(@Url url: String, @Body getForObservedRequest: GetForObservedRequest): Single<GetForObservedResponse>

        @GET("namespaces")
        fun listNamespaces(@Header("Authorization") authorization: String): Single<ListNamespacesResponse>

        @POST
        fun registerBeacon(@Url url: String, @Body beacon: Beacon, @Header("Authorization") authorization: String): Single<Beacon>

        @POST
        fun activateBeacon(@Url url: String, @Header("Authorization") authorization: String): Completable

        @GET("beacons/{beaconName}")
        fun getBeacon(@Path("beaconName") beaconName: String, @Header("Authorization") authorization: String): Single<Beacon>

        @POST
        fun batchDeleteAttachments(@Url url: String, @Header("Authorization") authorization: String): Single<BatchDeleteResponse>

        @POST("beacons/{beaconName}/attachments")
        fun createAttachment(@Body attachment: AttachmentInfo,
                             @Path("beaconName") beaconName: String,
                             @Header("Authorization") authorization: String): Single<AttachmentInfo>
    }

    companion object {

        private val TAG = BeaconProximityAPI::class.java!!.getSimpleName()

        private val ENDPOINT = "https://proximitybeacon.googleapis.com/v1beta1/"

        // Create a trust manager that does not validate certificate chains
        private// this must never throw the CertificateException
        // this must never throw the CertificateException
        val trustAllCertsTrustManager: Array<TrustManager>
            get() = arrayOf(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            })
    }
}
