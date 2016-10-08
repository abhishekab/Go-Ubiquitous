package com.example.android.sunshine.app.sync;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class WearUpdateService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataApi.DataItemResult> {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[]{
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };
    private static final String TAG = WearUpdateService.class.getSimpleName();
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_ICON="icon";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
        return super.onStartCommand(intent, flags, startId);
    }




    @Override
    public void onConnected(Bundle bundle) {

        updateWear();

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void updateWear() {
        String locationQuery = Utility.getPreferredLocation(this);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);
            double high = cursor.getDouble(INDEX_MAX_TEMP);
            double low = cursor.getDouble(INDEX_MIN_TEMP);
            //int iconId = Utility.getIconResourceForWeatherCondition(weatherId);


            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/sunshine/weather");
            putDataMapReq.getDataMap().putString(KEY_HIGH,Utility.formatTemperature(WearUpdateService.this,high));
            putDataMapReq.getDataMap().putString(KEY_LOW,Utility.formatTemperature(WearUpdateService.this,low));
            putDataMapReq.getDataMap().putInt(KEY_ICON,weatherId);
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            pendingResult.setResultCallback(this);
//            DataApi.DataItemResult result = pendingResult.await();
//            if(result.getStatus().isSuccess()) {
//                Log.d(TAG, "Data item set: " + result.getDataItem().getUri());
//            }


        }


    }


    private static Asset createAssetFromBitmap(Bitmap bitmap) {

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
        if(dataItemResult.getStatus().isSuccess()) {
               Log.d(TAG, "Data item set: " + dataItemResult.getDataItem().getUri());
            }
        else{
            Toast.makeText(this,"OOps",Toast.LENGTH_LONG).show();
        }

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        stopSelf();
    }
}
