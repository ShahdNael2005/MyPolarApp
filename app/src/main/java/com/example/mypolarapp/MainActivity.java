package com.example.mypolarapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.example.mypolarapp.BeatScheduler;
import com.example.mypolarapp.EcgWaveformView;
import com.example.mypolarapp.HeartRate;
import com.example.mypolarapp.HeartVisualizerView;
import com.example.mypolarapp.HeartbeatSoundPlayer;

public class MainActivity extends AppCompatActivity {
    // This ID is used to connect directly to the correct Polar device
    private static final String POLAR_DEVICE_ID = "7B688B23";
    // Handles the connection to the Polar sensor and receives live heart rate data
    private PolarLiveData polarLiveData;
    // TextView used to display connection status
    private TextView statusText;

    // used to identify the Bluetooth permission and when asking the user to enable Bluetooth
    private static final int BLUETOOTH_PERMISSION_REQUEST = 100;
    private static final int ENABLE_BLUETOOTH_REQUEST = 101;
    // Provide access to phone blutooth
    private BluetoothAdapter bluetoothAdapter;

    // visualizer front end
    private HeartVisualizerView heartVisualizer;
    private EcgWaveformView ecgWave;
    private ImageView heartImage;
    private TextView hrValue;
    private TextView zoneStatus;
    //private Button playButton;
    //private Button soundButton;
    private Button infoButton;

    private final HeartbeatSoundPlayer soundPlayer = new HeartbeatSoundPlayer();
    private BeatScheduler beatScheduler;
    //private boolean isRunning = true;
    private boolean isSoundEnabled = true;

    /**
     * Called when the activity is first created
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Connect to xml
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        setUpVisualizer();

        // Get the BluetoothManager used to access the BluetoothAdapter
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        // get phones bluetooth adapter
        bluetoothAdapter = bluetoothManager.getAdapter();

        // If the Bluetooth adapter is null cannot connect to the Polar device Without Bluetooth, the app cannot connect to the Polar sensor
        if (bluetoothAdapter == null) {
            Toast.makeText(
                    this,
                    "Bluetooth is not supported on this phone",
                    Toast.LENGTH_LONG
            ).show();
            // Stop the remaining setup
            return;
        }
        // check and request bluetooth permission
        requestBluetoothPermissions();
    }

    /**
     * Wires up the heart-rate visualizer: the animated backdrop, the ECG waveform, the panel, and the play/pause + sound buttons
     * Runs with a default 72 BPM until live Polar data arrives, so the screen is already alive before a sensor connects
     */
    private void setUpVisualizer() {
        heartVisualizer = findViewById(R.id.heartVisualizer);
        ecgWave = findViewById(R.id.ecgWave);
        heartImage = findViewById(R.id.heartImage);
        hrValue = findViewById(R.id.hrValue);
        zoneStatus = findViewById(R.id.zoneStatus);
        //playButton = findViewById(R.id.playButton);
        //soundButton = findViewById(R.id.soundButton);
        infoButton = findViewById(R.id.infoButton);

        beatScheduler = new BeatScheduler(this::onBeat);
        beatScheduler.start();

        /*playButton.setOnClickListener(v -> {
            isRunning = !isRunning;
            heartVisualizer.setRunning(isRunning);
            ecgWave.setRunning(isRunning);
            if (isRunning) {
                beatScheduler.start();
            } else {
                beatScheduler.stop();
            }
        });**/
        infoButton.setOnClickListener(v -> showInfoDialog());
        //soundButton.setOnClickListener(v -> isSoundEnabled = !isSoundEnabled);
    }
    /**
     * Shows the info popup with the heart rate zones and the sound on/off switch
     */
    private void showInfoDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.infobox, null);

        // Show the current sound setting and update it when the switch changes
        SwitchCompat soundSwitch = dialogView.findViewById(R.id.soundSwitch);
        soundSwitch.setChecked(isSoundEnabled);
        soundSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> isSoundEnabled = isChecked);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.closeButton)
                .setOnClickListener(v -> dialog.dismiss());

        // Remove the default white dialog box so the rounded bg_panel shows instead
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    /**
     * Once per heartbeat by BeatScheduler pulses the heart image and plays the sound
     */
    private void onBeat(float currentHeartRate) {
        pulseHeart(currentHeartRate);
        if (isSoundEnabled) {
            soundPlayer.play(currentHeartRate);
        }
    }

    /**
     * Quick pulse on the heart image, timed to the current beat length
     * Replaces the CSS "beat" keyframe animation, which ran on a fixed loop rather than being triggered per beat
     */
    private void pulseHeart(float currentHeartRate) {
        long beatDurationMs = (long) (60000f / currentHeartRate);
        long rampMs = Math.max(1, (long) (beatDurationMs * 0.12f));

        heartImage.animate().cancel();
        heartImage.setScaleX(1f);
        heartImage.setScaleY(1f);
        heartImage.animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(rampMs)
                .withEndAction(() -> heartImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(rampMs)
                        .start())
                .start();
    }

    /**
     * Pushes a new live BPM reading into every part of the visualizer.
     * Called from the Polar listener's onHeartRateReceived() below.
     */
    private void updateHeartRate(int heartRate) {
        heartVisualizer.setHeartRate(heartRate);
        ecgWave.setHeartRate(heartRate);
        beatScheduler.setHeartRate(heartRate);

        hrValue.setText(String.valueOf(heartRate));
        zoneStatus.setText(HeartRate.getZone(heartRate));
        zoneStatus.setTextColor(HeartRate.getColor(heartRate));
    }

    /**
     * Checks which Bluetooth permissions are required
     */
    private void requestBluetoothPermissions() {

        // on Android 12 (cause my phone is 22+) BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check whether permission to scan for Bluetooth devices is granted
            boolean scanPermissionGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED;
            // Check whether permission to connect to Bluetooth devices is granted
            boolean connectPermissionGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED;
            // Request the permissions if either Bluetooth permission not gratted
            if (!scanPermissionGranted || !connectPermissionGranted) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        BLUETOOTH_PERMISSION_REQUEST
                );

            } else {
                // Permissions already exist (so if bluetooth is on)
                checkBluetoothEnabled();
            }

        }
    }

    /**
     *  Checks whether Bluetooth is currently enabled on the phone if Bluetooth is disabled the user is asked to enable it.
     */
    private void checkBluetoothEnabled() {
        // BLUETOOTH_CONNECT permission is required before checking the Bluetooth adapter's state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            // Exit because the required permission is missing
            return;
        }
        // Check whether Bluetooth is off
        if (!bluetoothAdapter.isEnabled()) {
            // ask user to turn on bluetooth
            Intent enableBluetoothIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            startActivityForResult(
                    enableBluetoothIntent,
                    ENABLE_BLUETOOTH_REQUEST
            );

        } else {
            // Bluetooth is already on
            bluetoothReady();
        }
    }
    /**
     * Runs after Bluetooth permissions have been granted and Bluetooth is on
     */
    private void bluetoothReady() {
        // tell user that Bluetooth setup was connected
        Toast.makeText(
                this,
                "Bluetooth is ready for Polar connection",
                Toast.LENGTH_LONG
        ).show();

        // Create the PolarLiveData object only once.
        if (polarLiveData == null) {
            // PolarLiveData manages the Polar SDK connection and sends updates back to this activity through its Listener
            polarLiveData =
                    new PolarLiveData(
                            this,
                            new PolarLiveData.Listener() {

                                /**
                                 * Called whenever the Polar connection status changes
                                 * @param status
                                 */
                                @Override
                                public void onStatusChanged(
                                        String status
                                ) {
                                    // Display the current connection status on the screen
                                    statusText.setText(status);
                                }

                                /**
                                 * Called whenever a new heart rate value is received from the Polar sensor.
                                 * @param heartRate
                                 */
                                @Override
                                public void onHeartRateReceived(
                                        int heartRate
                                ) {
                                    // Push the live BPM into the visualizer
                                    updateHeartRate(heartRate);
                                }

                                /**
                                 * Called if an error occurs while using the Polar connecting
                                 * @param errorMessage
                                 */
                                @Override
                                public void onError(
                                        String errorMessage
                                ) {
                                    // Display the error message in the TextView
                                    statusText.setText(
                                            errorMessage
                                    );
                                    // show the error as a Toast message
                                    Toast.makeText(
                                            MainActivity.this,
                                            errorMessage,
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    );
        }

        // Connect using the ID printed on the Polar sensor.
        polarLiveData.connect(POLAR_DEVICE_ID);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (polarLiveData != null) {
            polarLiveData.shutdown();
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );
        // Check that this is the Bluetooth permission request
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            // assume that every requested permission was granted
            boolean allPermissionsGranted = true;
            // If no results were returned then false
            if (grantResults.length == 0) {
                allPermissionsGranted = false;
            }
            // Check the result of every requested permission
            for (int result : grantResults) {
                // If one permission was denied stop setup
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                checkBluetoothEnabled();
            } else {
                // Tell the user that the app requires Bluetooth
                Toast.makeText(
                        this,
                        "Bluetooth permission is required to connect to Polar",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ENABLE_BLUETOOTH_REQUEST) {

            if (resultCode == RESULT_OK) {
                // bluetooth is on
                bluetoothReady();
            } else {
                // The user closed the Bluetooth request
                Toast.makeText(
                        this,
                        "Please enable Bluetooth to connect to Polar",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }
}