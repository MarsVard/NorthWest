package com.marsvard.northwest;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

public class NorthWest {
    private static final String TAG = NorthWest.class.getSimpleName();

    /**
     * Creates and returns an observable that emits degrees from north.
     * @param context
     * @return
     */
    public static Observable<Double> create(Context context) {

        final SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        final GeomagneticField magneticField = getLastKnownLocation(context);

        // create and return observable
        // Todo: replace with Emitter when it's out of experimental phase
        return Observable.create(new Observable.OnSubscribe<Double>() {
            @Override
            public void call(final Subscriber<? super Double> subscriber) {

                final SensorEventListener sensorListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(calculateAngle(event, magneticField));
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* chew bubblegum */ }
                };

                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        sensorManager.unregisterListener(sensorListener);
                    }
                }));

                // register for updates
                sensorManager.registerListener(sensorListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            }
        });
    }

    /**
     * Try to get last known location, this might return null if app does not have permission to
     * user location.
     * @param context
     * @return
     */
    @SuppressWarnings({"MissingPermission"})
    private static GeomagneticField getLastKnownLocation(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            GeomagneticField magneticField = new GeomagneticField(
                    (float) location.getLatitude(),
                    (float) location.getLongitude(),
                    (float) location.getAltitude(),
                    System.currentTimeMillis()
            );
        } catch (Exception ex) {
            Log.i(TAG, "RxCompass: not calculating magnetic field declination because app has no permission for location");
        }

        return null;
    }

    /**
     * returns azimuth from sensor data.
     *
     * @param event
     * @return
     */
    private static float getAzimuth(SensorEvent event) {
        float[] mRotationMatrix = new float[16];
        float[] mTruncatedRotationVector = new float[4];

        if (event.values.length > 4) {
            // On some Samsung devices, an exception is thrown if this vector > 4 (see #39)
            // Truncate the array, since we only care about the first 4 values anyway
            System.arraycopy(event.values, 0, mTruncatedRotationVector, 0, 4);
            SensorManager.getRotationMatrixFromVector(mRotationMatrix, mTruncatedRotationVector);
        } else {
            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
        }

        float[] orientation = new float[3];
        SensorManager.getOrientation(mRotationMatrix, orientation);

        return orientation[0];
    }

    /**
     * Calculate angle to north depending on SensorEvent data
     * @param event
     * @param magneticField
     * @return
     */
    private static double calculateAngle(SensorEvent event, GeomagneticField magneticField) {

        // get azimuth from sensor data
        float azimuth = getAzimuth(event);
        double angle;

        // if we were able to get the magnetic field based on the user's last know location
        // we should take that in consideration otherwise we are left with the sensor data
        // which might be a little off
        if (magneticField != null) {
            angle = Math.toDegrees(azimuth) + magneticField.getDeclination();
        } else {
            angle = Math.toDegrees(azimuth);
        }

        angle = (angle + 360) % 360;

        return angle;
    }
}
