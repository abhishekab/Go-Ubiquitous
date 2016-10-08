package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.R.attr.textSize;
import static android.R.attr.x;

public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final long NORMAL_UPDATE_RATE_MS = 500;
    public static final String PATH_WITH_FEATURE = "/sunshine/weather";
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_ICON="icon";

    @Override
    public Engine onCreateEngine() {
        return new SimpleEngine();
    }

    private class SimpleEngine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private static final long TIMEOUT_MS = 2000;
        SimpleDateFormat sdf=new SimpleDateFormat("EEE, MMM dd yyyy");

        static final int MSG_UPDATE_TIME = 0;
        static final String COLON_STRING = ":";

        Resources resources = SunshineWatchFaceService.this.getResources();

        private  final int BACKGROUND_COLOR_AMBIENT = Color.BLACK;
        private  final int BACKGROUND_COLOR = resources.getColor(R.color.background_primary);
        private  final int TEXT_DATE_COLOR = resources.getColor(R.color.primary_light);
        private  final int TEXT_DATE_COLOR_AMBIENT = Color.WHITE;
        private  final int TEXT_HOURS_MINS_COLOR = Color.WHITE;
        private  final int TEXT_COLON_COLOR = Color.WHITE;
        private final int TEXT_HIGH_COLOR=Color.WHITE;
        private final int TEXT_LOW_COLOR=resources.getColor(R.color.primary_light);
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;


        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };


        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mColonPaint;

        Paint mHighPaint;
        Paint mLowPaint;

        boolean mLowBitAmbient;
        private Calendar mCalendar;
        private Date mDate;
        private boolean mShouldDrawColons;

        float mXOffset;
        float mYOffset;
        private float mColonWidth;
        private float mLineHeight;
        private float mIconSizePixel;
        private float mIconBottomMargin;
        //private static final int DEFAULT_TEMP=-999;

        private String mHigh=null;
        private String mLow=null;
        private Bitmap mIcon=null;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(BACKGROUND_COLOR);
            mDatePaint = createTextPaint(TEXT_DATE_COLOR,Typeface.DEFAULT);
            mHourPaint = createTextPaint(TEXT_HOURS_MINS_COLOR,Typeface.DEFAULT);
            mMinutePaint = createTextPaint(TEXT_HOURS_MINS_COLOR,Typeface.DEFAULT);
            mColonPaint = createTextPaint(TEXT_COLON_COLOR,Typeface.DEFAULT);
            mHighPaint = createTextPaint(TEXT_HIGH_COLOR,Typeface.DEFAULT);
            mLowPaint = createTextPaint(TEXT_LOW_COLOR,Typeface.DEFAULT);
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mColonWidth=mColonPaint.measureText(COLON_STRING);
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mIconSizePixel=getResources().getDimension(R.dimen.icon_size_pixel);
            mIconBottomMargin=getResources().getDimension(R.dimen.icon_margin_bottom);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));

            
            //initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }


        @Override
        public void onVisibilityChanged(boolean visible) {

            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();


            } else {


                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {

            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float textSizeTemp = resources.getDimension(isRound
                    ? R.dimen.digital_temp_size_round : R.dimen.digital_temp_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mHighPaint.setTextSize(textSizeTemp);
            mLowPaint.setTextSize(textSizeTemp);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            //mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            adjustPaintColorToCurrentMode(mBackgroundPaint, BACKGROUND_COLOR,
                   BACKGROUND_COLOR_AMBIENT);
            adjustPaintColorToCurrentMode(mDatePaint, TEXT_DATE_COLOR,
                    TEXT_DATE_COLOR_AMBIENT);


            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
                mLowPaint.setAntiAlias(antiAlias);
                mHighPaint.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }



        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }


        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            float x = bounds.centerX() - (mHourPaint.measureText("00:00"))/2;
            String hourString;

                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));

            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode()  || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

                String dateString=sdf.format(mDate).toUpperCase();

                canvas.drawText(dateString,
                        bounds.centerX() - (mDatePaint.measureText(dateString))/2, mYOffset + mLineHeight, mDatePaint);

            // Show temperature details only when not in ambient mode, also keep hidden if peek card being displayed
            if( getPeekCardPosition().isEmpty() && !isInAmbientMode() && mHigh!=null && mLow!= null){
                String highLowString= mHigh+mLow;
                if(mIcon!=null){
                    canvas.drawBitmap(mIcon, bounds.centerX() -mIconSizePixel/2 , mYOffset + (mLineHeight),null);
                }
                float  temp_offset=bounds.centerX() - (mHighPaint.measureText(highLowString))/2;

                canvas.drawText(mHigh,temp_offset
                        , mYOffset + (2*mLineHeight)+mIconSizePixel+mIconBottomMargin, mHighPaint);
                canvas.drawText(mLow,temp_offset + mHighPaint.measureText(mHigh)
                        , mYOffset + (2*mLineHeight)+mIconSizePixel+mIconBottomMargin, mLowPaint);


            }

        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
           // mHigh=25;
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap weatherData = dataMapItem.getDataMap();
                updateWeatherData(weatherData);
            }
        }

        private void updateWeatherData( DataMap weatherData) {
            if(weatherData.containsKey(KEY_HIGH)){
                mHigh=weatherData.getString(KEY_HIGH);
            }
            if(weatherData.containsKey(KEY_LOW)){
                mLow=weatherData.getString(KEY_LOW);
            }
            if(weatherData.containsKey(KEY_ICON))
            {
                int weatherId=weatherData.getInt(KEY_ICON);
                int icon_id= getIconResourceForWeatherCondition(weatherId);
                mIcon=BitmapFactory.decodeResource(getResources(),icon_id);

            }
        }



        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {

            Wearable.DataApi.addListener(mGoogleApiClient, SimpleEngine.this);
            //updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {

        }

    }

    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }
}