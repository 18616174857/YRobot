package com.yrobot.exo.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.yrobot.exo.YRobotApplication;
import com.yrobot.exo.R;
import com.yrobot.exo.app.data.MockHardware;
import com.yrobot.exo.app.views.DataFragment;
import com.yrobot.exo.app.views.ParamFragment;
import com.yrobot.exo.app.views.UserStatusFragment;
import com.yrobot.exo.app.views.ControlFragment;
import com.yrobot.exo.ble.BleUtils;
import com.yrobot.exo.ble.central.BleManager;
import com.yrobot.exo.ble.central.BlePeripheral;
import com.yrobot.exo.dfu.DfuProgressFragmentDialog;
import com.yrobot.exo.dfu.DfuService;
import com.yrobot.exo.dfu.DfuUpdater;
import com.yrobot.exo.dfu.ReleasesParser;
import com.yrobot.exo.models.DfuViewModel;
import com.yrobot.exo.utils.DialogUtils;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;

import static com.yrobot.exo.app.YrConstants.MOCK_IDENTIFIER;
import static com.yrobot.exo.app.YrConstants.USE_MOCK_DEVICE;

public class MainActivity extends AppCompatActivity implements ScannerFragmentMinimal.ScannerFragmentListener, PeripheralModulesFragment.PeripheralModulesFragmentListener, DfuProgressFragmentDialog.Listener {

    // Constants
    private final static String TAG = "yr-" + MainActivity.class.getSimpleName();

    // Config
    private final static boolean kAvoidPoppingFragmentsWhileOnDfu = false;

    // Permission requests
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public final static int PERMISSION_REQUEST_FINE_LOCATION = 2;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    public static final int kActivityRequestCode_PlayServicesAvailability = 2;

    // Models
    private DfuViewModel mDfuViewModel;

    // UI
    private BottomNavigationView mNavigationView;

    // Data
//    private MainFragment mMainFragment;
    private ScannerFragmentMinimal mScannerFragment;
    private AlertDialog mRequestLocationDialog;
    private boolean hasUserAlreadyBeenAskedAboutBluetoothStatus = false;
    private String savedIdentifier = null;
    private String mPeripheralIdentifier = null;
    private String mPeripheralMacAddress = null;

    private String SAVED_INSTANCE_HAS_ALREADY_BEEN_ASKED_BT_STATUS = "hasUserAlreadyBeenAskedAboutBluetoothStatus";
    private String SAVED_INSTANCE_PERIPHERAL_IDENTIFIER = "peripheral_identifier";
    private String SAVED_INSTANCE_PERIPHERAL_MAC_ADDRESS = "peripheral_mac_address";

    // region Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        YrConstants.log(YrConstants.getCurrentMethodName());

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (savedInstanceState == null) {
            // Set mainmenu fragment
//            mMainFragment = MainFragment.newInstance();
//            fragmentManager.beginTransaction()
//                    .add(R.id.contentLayout, mMainFragment, "Main")
//                    .commit();

            String lastMacAddress = loadDeviceIdentifier();
            Log.v(TAG, "Pref: load device [" + lastMacAddress + "]");
            mScannerFragment = ScannerFragmentMinimal.newInstance(lastMacAddress);
            fragmentManager.beginTransaction()
                    .add(R.id.contentLayout, mScannerFragment, "Main")
                    .commit();

            Log.v(TAG, "SAVED INSTANCE IDENTIFIER NULL");

        } else {
            hasUserAlreadyBeenAskedAboutBluetoothStatus = savedInstanceState.getBoolean(SAVED_INSTANCE_HAS_ALREADY_BEEN_ASKED_BT_STATUS);
            String identifier = savedInstanceState.getString(SAVED_INSTANCE_PERIPHERAL_IDENTIFIER);
            String macAddress = savedInstanceState.getString(SAVED_INSTANCE_PERIPHERAL_MAC_ADDRESS);
            if (identifier != null) {
                Log.v(TAG, "SAVED INSTANCE IDENTIFIER: [" + identifier + "] [" + macAddress + "]");
            }
            String lastMacAddress = loadDeviceIdentifier();
            Log.v(TAG, "Pref: load2 device [" + lastMacAddress + "]");
            mScannerFragment = (ScannerFragmentMinimal) fragmentManager.findFragmentByTag("Main");
        }

        // Back navigation listener
        fragmentManager.addOnBackStackChangedListener(() -> {
            if (fragmentManager.getBackStackEntryCount() == 0) {        // Check if coming back
                mScannerFragment.disconnectAllPeripherals();
            }
        });

//        Context context = getActivity();
//        SharedPreferences sharedPref = context.getSharedPreferences(
//                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Setup bottom navigation view
        mNavigationView = findViewById(R.id.bottom_navigation);
        mNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.navigation_user_status:
                        startUserStatusFragment();
                        break;
                    case R.id.navigation_data:
                        startDataFragment();
                        break;
                    case R.id.navigation_system:
                        startSystemFragment();
                        break;
                    case R.id.navigation_info:
                        startInfoFragment();
                        break;
                }
                updateActionBarTitle(item.getItemId());
                return true;
            }
        });

        // ViewModels
        mDfuViewModel = ViewModelProviders.of(this).get(DfuViewModel.class);

        // Check if there is any update to the firmware database
        if (savedInstanceState == null) {
            updateAndroidSecurityProvider(this);        // Call this before refreshSoftwareUpdatesDatabase because SSL connections will fail on Android 4.4 if this is not executed:  https://stackoverflow.com/questions/29916962/javax-net-ssl-sslhandshakeexception-javax-net-ssl-sslprotocolexception-ssl-han
            DfuUpdater.refreshSoftwareUpdatesDatabase(this, success -> Log.d(TAG, "refreshSoftwareUpdatesDatabase completed. Success: " + success));
        }

        mNavigationView.setSelectedItemId(mStartingView);
    }

    //    public static final int mStartingView = R.id.navigation_info;
    public static final int mStartingView = R.id.navigation_system;
//    public static final int mStartingView = R.id.navigation_user_status;
//    public static final int mStartingView = R.id.navigation_data;

    public static final String kPreferencesDevice = "device";
    public static final String kPreferencesDeviceMac = "device_mac";

    private void saveDeviceIdentifier(String identifier) {
        Context context = getApplicationContext();
        if (context != null) {
            SharedPreferences.Editor preferencesEditor = context.getSharedPreferences(kPreferencesDevice, MODE_PRIVATE).edit();
            preferencesEditor.putString(kPreferencesDeviceMac, identifier);
            preferencesEditor.apply();
            Log.v(TAG, "Pref: saved device [" + identifier + "]");
        }
    }

    private String loadDeviceIdentifier() {
        SharedPreferences sharedPref = getSharedPreferences(kPreferencesDevice, Context.MODE_PRIVATE);
        return sharedPref.getString(kPreferencesDeviceMac, null);
    }

    public void startMainFragment(String peripheralIdentifier) {
        Log.v(TAG, "startMainFragment [" + peripheralIdentifier + "]");
        saveDeviceIdentifier(peripheralIdentifier);
        mPeripheralIdentifier = peripheralIdentifier;
        mNavigationView.setSelectedItemId(mStartingView);
    }

    private void loadFragment(Fragment fragment, String tag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.contentLayout, fragment, tag);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void startUserStatusFragment() {
        if (mPeripheralIdentifier != null) {
            loadFragment(UserStatusFragment.newInstance(mPeripheralIdentifier), "Profile");
        }
    }

    private void startSystemFragment() {
        if (mPeripheralIdentifier != null) {
            loadFragment(ControlFragment.newInstance(mPeripheralIdentifier), "System");
        }
    }

    private void startDataFragment() {
        if (mPeripheralIdentifier != null) {
            loadFragment(DataFragment.newInstance(mPeripheralIdentifier), "Data");
        }
    }

    private void startInfoFragment() {
        if (mPeripheralIdentifier != null) {
//            loadFragment(InfoFragment.newInstance(mPeripheralIdentifier), "Info");
            loadFragment(ParamFragment.newInstance(mPeripheralIdentifier), "Param");
        }
    }

    private void updateActionBarTitle(int navigationSelectedItem) {
        int titleId = 0;
        switch (navigationSelectedItem) {
            case R.id.navigation_user_status:
                titleId = R.string.main_tabbar_user_status;
                break;
            case R.id.navigation_data:
                titleId = R.string.main_tabbar_data;
                break;
            case R.id.navigation_system:
                titleId = R.string.main_tabbar_system;
                break;
            case R.id.navigation_info:
                titleId = R.string.main_tabbar_info;
                break;
        }
        if (titleId != 0) {
            setActionBarTitle(getString(titleId));
        }
    }

    private void setActionBarTitle(String title) {
        AppCompatActivity activity = this;
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setDisplayHomeAsUpEnabled(false);     // Don't show caret for MainFragment
            }
        }
    }

    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(SAVED_INSTANCE_HAS_ALREADY_BEEN_ASKED_BT_STATUS, hasUserAlreadyBeenAskedAboutBluetoothStatus);
        if (mPeripheralIdentifier != null) {
            savedInstanceState.putString(SAVED_INSTANCE_PERIPHERAL_IDENTIFIER, mPeripheralIdentifier);
        }
//        if (mPeripheralMacAddress != null) {
//            savedInstanceState.putString(SAVED_INSTANCE_PERIPHERAL_MAC_ADDRESS, mPeripheralMacAddress);
//        }
    }

    private void updateAndroidSecurityProvider(Activity callingActivity) {
        try {
            ProviderInstaller.installIfNeeded(this);
        } catch (GooglePlayServicesRepairableException e) {
            // Thrown when Google Play Services is not installed, up-to-date, or enabled
            // Show dialog to allow users to install, update, or otherwise enable Google Play services.
            GooglePlayServicesUtil.getErrorDialog(e.getConnectionStatusCode(), callingActivity, 0);
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e("SecurityException", "Google Play Services not available.");
        }
    }

    // endregion

    @Override
    protected void onResume() {
        super.onResume();

        YrConstants.log(YrConstants.getCurrentMethodName());

        YRobotApplication.activityResumed();
        checkPermissions();

        // Observe disconnections
        registerGattReceiver();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        popFragmentsIfNoPeripheralsConnected();         // check if peripherals were disconnected while the app was in background
    }

    @Override
    protected void onPause() {
        super.onPause();

        YrConstants.log(YrConstants.getCurrentMethodName());

        YRobotApplication.activityPaused();
        unregisterGattReceiver();

        // Remove location dialog if present
        if (mRequestLocationDialog != null) {
            mRequestLocationDialog.cancel();
            mRequestLocationDialog = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
//            case R.id.action_about:
//                FragmentManager fragmentManager = getSupportFragmentManager();
//                if (fragmentManager != null) {
//                    AboutFragment fragment = AboutFragment.newInstance();
//                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
//                            .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
//                            .replace(R.id.contentLayout, fragment, "About");
//                    fragmentTransaction.addToBackStack(null);
//                    fragmentTransaction.commit();
//                }
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkPermissions() {
        if (USE_MOCK_DEVICE) {
            mScannerFragment.onConnectedCallback(MOCK_IDENTIFIER);
            MockHardware.getInstance().start();
        } else {
            final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();
            if (!areLocationServicesReadyForScanning) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                mRequestLocationDialog = builder.setMessage(R.string.bluetooth_locationpermission_disabled_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                //DialogUtils.keepDialogOnOrientationChanges(mRequestLocationDialog);
            } else {
                if (mRequestLocationDialog != null) {
                    mRequestLocationDialog.cancel();
                    mRequestLocationDialog = null;
                }

                // Bluetooth state
                if (!hasUserAlreadyBeenAskedAboutBluetoothStatus) {     // Don't repeat the check if the user was already informed to avoid showing the "Enable Bluetooth" system prompt several times
                    final boolean isBluetoothEnabled = manageBluetoothAvailability();

                    if (isBluetoothEnabled) {
                        // Request Bluetooth scanning permissions
                        final boolean isLocationPermissionGranted = requestCoarseLocationPermissionIfNeeded();

                        if (isLocationPermissionGranted) {
                            // All good. Start Scanning
//                        BleManager.getInstance().start(MainActivity.this);
                            // Bluetooth was enabled, resume scanning
//                        mMainFragment.startScanning();
//                        startScanning();
                            mScannerFragment.startScanning();
                        }
                    }
                }
            }
        }

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted");
        } else {
            int REQUEST_CODE = 0x1234;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
        }
    }

    // region Permissions
    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }

        return areLocationServiceReady;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean requestCoarseLocationPermissionIfNeeded() {
        boolean permissionGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android Marshmallow Permission check 
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                mRequestLocationDialog = builder.setTitle(R.string.bluetooth_locationpermission_title)
                        .setMessage(R.string.bluetooth_locationpermission_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION))
                        .show();
            }
        }
        return permissionGranted;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");

                    checkPermissions();

                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.bluetooth_locationpermission_notavailable_title);
                    builder.setMessage(R.string.bluetooth_locationpermission_notavailable_text);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(dialog -> {
                    });
                    builder.show();
                }
                break;
            }
            default:
                break;
        }
    }
    // endregion

    // region Bluetooth Setup
    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.bluetooth_unsupported;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.bluetooth_poweredoff;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }

        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                checkPermissions();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (!isFinishing()) {
                    hasUserAlreadyBeenAskedAboutBluetoothStatus = true;     // Remember that
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    AlertDialog dialog = builder.setMessage(R.string.bluetooth_poweredoff)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                }
            }
        }
    }

    private void popFragmentsIfNoPeripheralsConnected() {
        final int numConnectedPeripherals = BleManager.getInstance().getConnectedDevices().size();
        final boolean isLastConnectedPeripheral = numConnectedPeripherals == 0;

        if (isLastConnectedPeripheral && (!kAvoidPoppingFragmentsWhileOnDfu || !isIsDfuInProgress())) {
            Log.d(TAG, "No peripherals connected. Pop all fragments");
            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager != null) {
                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                fragmentManager.executePendingTransactions();
            }
        }
    }
    // endregion

    // region ScannerFragmentListener
    public void bluetoothAdapterIsDisabled() {
        checkPermissions();
    }

    public void scannerRequestLocationPermissionIfNeeded() {
        requestCoarseLocationPermissionIfNeeded();
    }

    // region PeripheralModulesFragmentListener
    @Override
    public void startModuleFragment(Fragment fragment) {
        YrConstants.log(YrConstants.DEBUG, TAG + ".startModuleFragment");
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager != null) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                    .replace(R.id.contentLayout, fragment, "Module");
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    // endregion

    // region Broadcast Listener
    private void registerGattReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlePeripheral.kBlePeripheral_OnDisconnected);
        LocalBroadcastManager.getInstance(this).registerReceiver(mGattUpdateReceiver, filter);
    }

    private void unregisterGattReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGattUpdateReceiver);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BlePeripheral.kBlePeripheral_OnDisconnected.equals(action)) {
                popFragmentsIfNoPeripheralsConnected();
            }
        }
    };
    // endregion

    // region DFU
    private DfuProgressFragmentDialog mDfuProgressDialog;

    private void dismissDfuProgressDialog() {
        if (mDfuProgressDialog != null) {
            mDfuProgressDialog.dismiss();
            mDfuProgressDialog = null;
        }
    }

    private boolean mIsDfuInProgress = false;

    public boolean isIsDfuInProgress() {
        return mIsDfuInProgress;
    }

    public void startUpdate(@NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.BasicVersionInfo versionInfo) {
        dismissDfuProgressDialog();

        String message = getString(versionInfo.fileType == DfuService.TYPE_APPLICATION ? R.string.dfu_download_firmware_message : R.string.dfu_download_bootloader_message);
        mDfuProgressDialog = DfuProgressFragmentDialog.newInstance(blePeripheral.getDevice().getAddress(), message);
        FragmentManager fragmentManager = getSupportFragmentManager();
        mDfuProgressDialog.show(fragmentManager, null);
        fragmentManager.executePendingTransactions();

        mDfuProgressDialog.setIndeterminate(true);
        mDfuProgressDialog.setOnCancelListener(dialog -> {
            mDfuViewModel.cancelInstall();
            dfuFinished();
        });

        mIsDfuInProgress = true;
        mDfuViewModel.downloadAndInstall(this, blePeripheral, versionInfo, new DfuUpdater.DownloadStateListener() {
            @Override
            public void onDownloadStarted(int type) {
                mDfuProgressDialog.setIndeterminate(true);
                mDfuProgressDialog.setMessage(type == DfuUpdater.kDownloadOperation_Software_Hex ? R.string.dfu_download_hex_message : R.string.dfu_download_init_message);
            }

            @Override
            public void onDownloadProgress(int percent) {
                if (mDfuProgressDialog != null) {       // Check null (Google crash logs)
                    mDfuProgressDialog.setIndeterminate(false);
                    mDfuProgressDialog.setProgress(percent);
                }
            }

            @Override
            public void onDownloadFailed() {
                dismissDfuProgressDialog();

                android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.dfu_status_error).setMessage(R.string.dfu_download_error_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    // endregion

    private void dfuFinished() {

        if (kAvoidPoppingFragmentsWhileOnDfu) {
            popFragmentsIfNoPeripheralsConnected();
        } else {
//            mMainFragment.startScanning();
        }
    }

    // region DfuProgressFragmentDialog.Listener

    @Override
    public void onDeviceDisconnected(String deviceAddress) {
        mIsDfuInProgress = false;
        //dismissDfuProgressDialog();
        dfuFinished();
    }

    @Override
    public void onDfuCompleted(String deviceAddress) {
        dismissDfuProgressDialog();
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_completed).setMessage(R.string.dfu_updatecompleted_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDfuAborted(String deviceAddress) {
        dismissDfuProgressDialog();

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_error).setMessage(R.string.dfu_updateaborted_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onError(String deviceAddress, int error, int errorType, String message) {
        dismissDfuProgressDialog();

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_error).setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // endregion

}