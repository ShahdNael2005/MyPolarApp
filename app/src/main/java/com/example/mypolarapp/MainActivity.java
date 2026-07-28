package com.example.mypolarapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    // used to identify the Bluetooth permission and when asking the user to enable Bluetooth
    private static final int BLUETOOTH_PERMISSION_REQUEST = 100;
    private static final int ENABLE_BLUETOOTH_REQUEST = 101;
    // Provide access to phone blutooth
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Connect to xml
        setContentView(R.layout.activity_main);

        /*
         * Get the BluetoothManager used to access the BluetoothAdapter.
         */
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        // get phones bluetooth adapter
        bluetoothAdapter = bluetoothManager.getAdapter();

        /*
         * If the Bluetooth adapter is null cannot connect to the Polar device.
         */
        if (bluetoothAdapter == null) {
            Toast.makeText(
                    this,
                    "Bluetooth is not supported on this phone",
                    Toast.LENGTH_LONG
            ).show();
            // Stop the remaining Bluetooth setup
            return;
        }
        // check and request bluetooth permission
        requestBluetoothPermissions();
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
            //  Request the permissions if either Bluetooth permission not gratted
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
                // // Permissions already exist (so if bluetooth is on)
                checkBluetoothEnabled();
            }

        }
    }

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
        // // Check whether Bluetooth is off
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
     * Runs after Bluetooth permissions have been granted and Bluetooth has been on
     */
    private void bluetoothReady() {
        // tell user that Bluetooth setup was connected
        Toast.makeText(
                this,
                "Bluetooth is ready for Polar connection",
                Toast.LENGTH_LONG
        ).show();


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