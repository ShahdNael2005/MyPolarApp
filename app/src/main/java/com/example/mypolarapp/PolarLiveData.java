package com.example.mypolarapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrData;

import java.util.Collections;
import java.util.Locale;
import com.polar.androidcommunications.api.ble.model.DisInfo;
import com.polar.sdk.api.model.PolarHealthThermometerData;

import java.util.UUID;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class PolarLiveData {
    private static final String TAG = "PolarLiveData";

    /**
     * Sends Polar connection and heart-rate information back to MainActivity.
     */
    public interface Listener {

        void onStatusChanged(String status);
        void onHeartRateReceived(int heartRate);
        void onError(String errorMessage);
    }
    // Main Polar SDK object used to communicate with the Polar sensor
    private final PolarBleApi polarApi;
    // Listener used to send data back to MainActivity
    private final Listener listener;
    // Handler used to send updates to the main Android UI
    private final Handler mainHandler;
    private Disposable heartRateDisposable;
    // Stores the ID of the currently connected Polar device
    private String connectedDeviceId = "";

    /**
     * Creates the Polar API and registers its connection callbacks.
     */
    public PolarLiveData(
            @NonNull Context context,
            @NonNull Listener listener
    ) {
        // Save the listener so that data can be sent back to MainActivity
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Create the Polar API only enable the heart-rate feature because this class currently receives BPM data only
        polarApi = PolarBleApiDefaultImpl.defaultImplementation(
                context.getApplicationContext(),
                Collections.singleton(
                        PolarBleApi.PolarBleSdkFeature.FEATURE_HR
                )
        );
        // Register all Polar SDK callbacks
        setupPolarCallbacks();
    }

    /**
     * Sets up connection and feature callbacks from the Polar SDK.
     */
    private void setupPolarCallbacks() {

        polarApi.setApiCallback(new PolarBleApiCallback() {
            /**
             * Called when health thermometer data is received dont really need this right now cause only using heart rate
             * @param identifier
             * @param data
             */
            @Override
            public void htsNotificationReceived(
                    String identifier,
                    PolarHealthThermometerData data
            ) {
            }

            /**
             * Called when the phone's Bluetooth state changes
             * @param powered
             */
            @Override
            public void blePowerStateChanged(boolean powered) {
                if (powered) {
                    // Bluetooth has been turned on
                    sendStatus("Bluetooth is on");
                } else {
                    // Bluetooth has been turned off
                    sendStatus("Bluetooth is off");
                }
            }

            /**
             * Called when the app begins connecting to a Polar device
             * @param polarDeviceInfo
             */
            @Override
            public void deviceConnecting(
                    @NonNull PolarDeviceInfo polarDeviceInfo
            ) {
                // Display which Polar device is being connected to
                sendStatus(
                        "Connecting to " +
                                polarDeviceInfo.getDeviceId()
                );
            }

            /**
             * Called when the Polar device successfully connects
             * @param polarDeviceInfo
             */
            @Override
            public void deviceConnected(
                    @NonNull PolarDeviceInfo polarDeviceInfo
            ) {
                // Save the connected Polar device ID
                connectedDeviceId =
                        polarDeviceInfo.getDeviceId();

                // Tell MainActivity that the device is connected
                sendStatus(
                        "Connected to " + connectedDeviceId
                );
            }

            /**
             * Called when the Polar device disconnects
             * @param polarDeviceInfo
             */
            @Override
            public void deviceDisconnected(
                    @NonNull PolarDeviceInfo polarDeviceInfo
            ) {
                // Stop receiving heart-rate data
                stopHeartRateStreaming();
                // Clear the stored connected device ID
                connectedDeviceId = "";
                // Inform MainActivity that the sensor disconnected
                sendStatus(
                        "Disconnected from " +
                                polarDeviceInfo.getDeviceId()
                );
            }

            /**
             * Called when a Polar SDK feature becomes ready to use
             * @param identifier
             * @param feature
             */
            @Override
            public void bleSdkFeatureReady(
                    @NonNull String identifier,
                    @NonNull PolarBleApi.PolarBleSdkFeature feature
            ) {
                // Print the ready feature to Logcat for debugging
                Log.d(TAG, "Feature ready: " + feature);

                // Check if the heart-rate feature is ready once it is ready, live heart-rate streaming can begin
                if (feature ==
                        PolarBleApi.PolarBleSdkFeature.FEATURE_HR) {

                    sendStatus("Heart-rate feature is ready");
                    // Start listening for live heart-rate values
                    startHeartRateStreaming(identifier);
                }
            }

            /**
             * Called when device information is received from the sensor
             * @param identifier
             * @param disInfo
             */
            @Override
            public void disInformationReceived(
                    String identifier,
                    DisInfo disInfo
            ) {
                // Print device information to Logcat
                Log.d(
                        TAG,
                        "Device information received from "
                                + identifier + ": " + disInfo
                );
            }

            /**
             * Called when specific device information is received
             * @param identifier
             * @param uuid
             * @param value
             */
            @Override
            public void disInformationReceived(
                    String identifier,
                    UUID uuid,
                    String value
            ) {
                // Print the device information UUID and value to Logcat
                Log.d(
                        TAG,
                        "Device information received: "
                                + uuid + " = " + value
                );
            }

            /**
             * Called when the Polar sensor gives its battery level
             * @param identifier
             * @param level
             */
            @Override
            public void batteryLevelReceived(
                    String identifier,
                    int level
            ) {
                // Print the battery percentage to Logcat
                Log.d(
                        TAG,
                        "Polar battery level: " + level + "%"
                );
            }
        });
    }

    /**
     * Connects to a Polar sensor using the printed device ID
     * @param deviceId
     */
    public void connect(@NonNull String deviceId) {
        // Clean the device ID before connecting
        String cleanedDeviceId = deviceId
                .trim()
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        // Check that a valid device ID has been given
        if (cleanedDeviceId.isEmpty()
                || cleanedDeviceId.equals("YOUR_DEVICE_ID")) {

            sendError(
                    "Replace YOUR_DEVICE_ID with the ID printed on your Polar sensor"
            );

            return;
        }

        try {
            // Tell MainActivity that there is a connection attempt
            sendStatus(
                    "Starting connection to " + cleanedDeviceId
            );
            // Ask the Polar SDK to connect to the sensor
            polarApi.connectToDevice(cleanedDeviceId);

        } catch (Exception exception) {
            // Send any connection error back to MainActivity
            sendError(
                    "Connection error: " +
                            getErrorMessage(exception)
            );
        }
    }

    /**
     * Starts receiving live BPM values
     * @param identifier
     */
    private void startHeartRateStreaming(
            @NonNull String identifier
    ) {
        // Prevent more than one heart-rate stream from running
        if (heartRateDisposable != null
                && !heartRateDisposable.isDisposed()) {
            return;
        }
        // Tell MainActivity that heart-rate streaming is starting
        sendStatus("Starting live heart-rate data");
        // Start listening to live heart-rate data from the Polar device
        heartRateDisposable = polarApi
                .startHrStreaming(identifier)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        heartRateData -> {
                            // A PolarHrData object can contain one or more heart-rate samples
                            for (PolarHrData.PolarHrSample sample
                                    : heartRateData.getSamples()) {
                                // get the BPM value from sample
                                int heartRate = sample.getHr();
                                // Print the heart rate to Logcat
                                Log.d(
                                        TAG,
                                        "Live heart rate: " +
                                                heartRate +
                                                " BPM"
                                );
                                // Send the heart-rate value back to MainActivity
                                sendHeartRate(heartRate);
                            }
                        },
                        // If an error occurs while streaming send the error back to MainActivity
                        error -> sendError(
                                "Heart-rate stream error: " +
                                        getErrorMessage(error)
                        )
                );
    }

    /**
     * Stops the current live heart-rate stream
     */
    private void stopHeartRateStreaming() {
        //  Check that a stream exists and has not already been stopped.
        if (heartRateDisposable != null
                && !heartRateDisposable.isDisposed()) {

            heartRateDisposable.dispose();
        }
        // Clear the stored stream reference
        heartRateDisposable = null;
    }

    /**
     * Disconnects from the Polar sensor
     */
    public void disconnect() {
        // Stop heart-rate streaming before disconnecting
        stopHeartRateStreaming();
        // If no Polar device is connected nothing
        if (connectedDeviceId.isEmpty()) {
            return;
        }
        // Ask the Polar SDK to disconnect from the sensor
        try {
            polarApi.disconnectFromDevice(
                    connectedDeviceId
            );

        } catch (Exception exception) {
            // report disconnection errors
            sendError(
                    "Disconnection error: " +
                            getErrorMessage(exception)
            );
        }
    }

    /**
     * Shuts down the Polar SDK
     */
    public void shutdown() {

        stopHeartRateStreaming();
        polarApi.shutDown();
    }

    /**
     * Sends a status message back to MainActivity
     * @param status
     */
    private void sendStatus(String status) {
        // Print the status message to Logcat
        Log.d(TAG, status);
        // Post the callback to the main UI before sending it to MainActivity
        mainHandler.post(
                () -> listener.onStatusChanged(status)
        );
    }

    /**
     * Sends a heart-rate BPM value back to MainActivity
     * @param heartRate
     */
    private void sendHeartRate(int heartRate) {
        // Send the heart-rate value
        mainHandler.post(
                () -> listener.onHeartRateReceived(heartRate)
        );
    }

    /**
     * Sends an error message back to MainActivity
     * @param errorMessage
     */
    private void sendError(String errorMessage) {
        // Print the error to Logcat
        Log.e(TAG, errorMessage);
        // Send the error message to MainActivity
        mainHandler.post(
                () -> listener.onError(errorMessage)
        );
    }

    /**
     * Gets a readable error messagee
     * @param throwable
     * @return
     */
    private String getErrorMessage(Throwable throwable) {
        if (throwable.getMessage() == null) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }
}
