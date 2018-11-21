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

package com.ndipatri.iot.googleproximity;

import android.util.Base64;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.ndipatri.iot.googleproximity.models.api.AdvertisedId;
import com.ndipatri.iot.googleproximity.models.api.AttachmentInfo;
import com.ndipatri.iot.googleproximity.models.api.BatchDeleteResponse;
import com.ndipatri.iot.googleproximity.models.api.Beacon;
import com.ndipatri.iot.googleproximity.models.api.GetForObservedRequest;
import com.ndipatri.iot.googleproximity.models.api.GetForObservedResponse;
import com.ndipatri.iot.googleproximity.models.api.ListNamespacesResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Url;

public class BeaconProximityAPI {

    private static final String TAG = BeaconProximityAPI.class.getSimpleName();

    private static final String ENDPOINT = "https://proximitybeacon.googleapis.com/v1beta1/";

    // not this is the 'browser key' from the Google Dev Console, NOT
    // the 'android key'.. many bothan spies died obtaining this
    // information.
    String API_KEY = "AIzaSyBRQezTd3GBi-az6US93nE_DfQj6kUkmvc";

    private final GoogleProximityApiInterface service;

    public BeaconProximityAPI(boolean trustAllConnections) {

        Retrofit retrofit = null;
        try {
            Retrofit.Builder builder  = new Retrofit.Builder()
                    .baseUrl(ENDPOINT)
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                    .addConverterFactory(GsonConverterFactory.create());

            if (trustAllConnections) {
                builder.client(getTrustAllSSLConnectionsClient());
            } else {
                builder.client(new OkHttpClient());
            }

            retrofit = builder.build();
        } catch (Exception e) {
            Log.e(TAG,  "Exception while constructing Retrofit.", e);
        }

        service = retrofit.create(GoogleProximityApiInterface.class) ;
    }

    /**
     * @param advertisedId
     * @return BeaconAttachment associated with given beacon.
     * @throws CorruptAttachmentException if the retrieved attachment bytes can not be marshalled into
     *         a BeaconAttachment. (e.g. change in code)
     */
    public Maybe<String[]> getBeaconAttachment(AdvertisedId advertisedId) {

        final GetForObservedRequest getForObservedRequest = new GetForObservedRequest();
        getForObservedRequest.namespacedTypes = ImmutableList.of("*");
        getForObservedRequest.observations = ImmutableList.of(new GetForObservedRequest.Observation(advertisedId));

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        String url = ENDPOINT + "beaconinfo:getforobserved?key=" + API_KEY;

        return service.getForObserved(url, getForObservedRequest)
                .flatMapMaybe(getForObservedResponse -> Maybe.create((MaybeEmitter<String[]> subscriber) -> {

                    if (getForObservedResponse.beacons != null &&
                        getForObservedResponse.beacons.size() > 0 &&
                        null != getForObservedResponse.beacons.get(0).attachments) {

                        try {

                            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Base64.decode(getForObservedResponse.beacons.get(0).attachments.get(0).data, Base64.DEFAULT));
                            ObjectInputStream objectIn = new ObjectInputStream(byteArrayInputStream);

                            String[] payload = (String[]) objectIn.readObject();

                            subscriber.onSuccess(payload);
                        } catch (InvalidClassException | ClassNotFoundException e) {
                            Log.e(TAG, "Exception while fetching attachments (corruption)", e);
                            // This means the attachment is dead to us, so delete it...
                            if (!subscriber.isDisposed()) {
                                subscriber.onError(new CorruptAttachmentException());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Exception while fetching attachments", e);

                            if (!subscriber.isDisposed()) {
                                subscriber.onError(e);
                            }
                        }
                    } else {
                        subscriber.onComplete();
                    }
                }))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Single<String> getNamespace(String googleAccountToken) {
        return service.listNamespaces("Bearer " + googleAccountToken)
                .flatMap(listNamespacesResponse -> {
                    String namespaceValue = (listNamespacesResponse.namespaces).get(0).namespaceName;
                    if (namespaceValue.startsWith("namespaces/")) {
                        namespaceValue = namespaceValue.substring("namespaces/".length());
                    }

                    return Single.just(namespaceValue);
                })
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Single<Beacon> registerBeacon(Beacon beacon, String googleAccountToken) {

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        String url = ENDPOINT + "beacons:register";

        return service.registerBeacon(url, beacon, "Bearer " + googleAccountToken);
    }

    public Completable activateBeacon(String beaconName, String googleAccountToken) {

        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        String url = ENDPOINT + "beacons/" + beaconName + ":activate";

        return service.activateBeacon(url, "Bearer " + googleAccountToken);
    }

    public Single<Beacon> getBeacon(String beaconName, String googleAccountToken) {
        return service.getBeacon(beaconName, "Bearer " + googleAccountToken);
    }

    public Single<BatchDeleteResponse> batchDeleteAttachments(String beaconName, String googleAccountToken) {
        // Currently, a ':' in the URL is confusing for the Retrofit library (it rejects the URL completely)
        String url = ENDPOINT + "beacons/" + beaconName + "/attachments:batchDelete";

        return service.batchDeleteAttachments(url, "Bearer " + googleAccountToken);
    }

    public Single<AttachmentInfo> createAttachment(AttachmentInfo attachment,
                                                   String beaconName,
                                                   String googleAccountToken) {

        return service.createAttachment(attachment, beaconName, "Bearer " + googleAccountToken);
    }

    public static class CorruptAttachmentException extends Exception {}

    private interface GoogleProximityApiInterface {

        @POST
        Single<GetForObservedResponse> getForObserved(@Url String url, @Body GetForObservedRequest getForObservedRequest);

        @GET("namespaces")
        Single<ListNamespacesResponse> listNamespaces(@Header("Authorization") String authorization);

        @POST
        Single<Beacon> registerBeacon(@Url String url, @Body Beacon beacon, @Header("Authorization") String authorization);

        @POST
        Completable activateBeacon(@Url String url, @Header("Authorization") String authorization);

        @GET("beacons/{beaconName}")
        Single<Beacon> getBeacon(@Path("beaconName") String beaconName, @Header("Authorization") String authorization);

        @POST
        Single<BatchDeleteResponse> batchDeleteAttachments(@Url String url, @Header("Authorization") String authorization);

        @POST("beacons/{beaconName}/attachments")
        Single<AttachmentInfo> createAttachment(@Body AttachmentInfo attachment,
                                                @Path("beaconName") String beaconName,
                                                @Header("Authorization") String authorization);
    }

    private OkHttpClient getTrustAllSSLConnectionsClient() throws Exception {

        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, getTrustAllCertsTrustManager(), new java.security.SecureRandom());

        // Create an ssl socket factory with our all-trusting manager
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        OkHttpClient.Builder authClientBuilder = new OkHttpClient.Builder();
        authClientBuilder.sslSocketFactory(sslSocketFactory);

        return authClientBuilder.build();
    }

    // Create a trust manager that does not validate certificate chains
    private static TrustManager[] getTrustAllCertsTrustManager() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        // this must never throw the CertificateException
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        // this must never throw the CertificateException
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
    }
}
