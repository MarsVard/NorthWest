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
import rx.subjects.PublishSubject;

public class NorthWest implements SensorEventListener {
    private final String TAG = NorthWest.class.getSimpleName();
    private final PublishSubject<Double> subject;
    private final SensorManager sensorManager;
    private final Sensor rotationVectorSensor;
    private GeomagneticField magneticField;
    private double angle;

    public NorthWest(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            magneticField = new GeomagneticField(
                    (float) location.getLatitude(),
                    (float) location.getLongitude(),
                    (float) location.getAltitude(),
                    System.currentTimeMillis()
            );

        } catch (Exception ex) {
            Log.i(TAG, "RxCompass: not calculating magnetic field declination because app has no permission for location");
        }

        subject = PublishSubject.create();
    }

    public void dispose() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // calculate degrees to north
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

        // if we were able to get the magnetic field based on the user's last know location we should take that in consideration
        // otherwise we are left with the sensor data which might be a little off
        if (magneticField != null) {
            angle = Math.toDegrees(orientation[0]) + magneticField.getDeclination();
        } else {
            angle = Math.toDegrees(orientation[0]);
        }

        angle = (angle + 360 ) % 360;

        // send update to observable
        subject.onNext(angle);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }


    public Observable<Double> getObservable() {
        return subject;
    }
}
