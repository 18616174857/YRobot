package com.yrobot.exo.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.le.ScanCallback;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yrobot.exo.R;
import com.yrobot.exo.ble.BleUtils;
import com.yrobot.exo.ble.central.BleManager;
import com.yrobot.exo.ble.central.BlePeripheral;
import com.yrobot.exo.dfu.DfuUpdater;
import com.yrobot.exo.dfu.ReleasesParser;
import com.yrobot.exo.models.DfuViewModel;
import com.yrobot.exo.models.ScannerViewModel;
import com.yrobot.exo.style.StyledSnackBar;
import com.yrobot.exo.utils.DialogUtils;
import com.yrobot.exo.utils.KeyboardUtils;
import com.yrobot.exo.utils.LocalizationManager;

import java.util.Locale;

import no.nordicsemi.android.support.v18.scanner.ScanRecord;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static com.yrobot.exo.app.ConnectedPeripheralFragment.ARG_SINGLEPERIPHERALIDENTIFIER;

public class ScannerFragmentMinimal extends Fragment implements ScannerStatusFragmentDialog.onScannerStatusCancelListener {
    // Constants
    private final static String TAG = "yr-" + ScannerFragmentMinimal.class.getSimpleName();

    private final static String kPreferences = "Scanner";
    private final static String kPreferences_filtersPanelOpen = "filtersPanelOpen";

    // Models
    private DfuViewModel mDfuViewModel;

    // Data -  Scanned Devices
    private ScannerFragmentListener mListener;
    private ScannerViewModel mScannerViewModel;
    private BlePeripheralsAdapter mBlePeripheralsAdapter;

    // Data - Dialogs
    private ScannerStatusFragmentDialog mConnectingDialog;

    ProgressBar progressBarScanning;
    TextView tvScanningStatus;

    // region Fragment lifecycle
    public static ScannerFragmentMinimal newInstance(String identifier) {
        ScannerFragmentMinimal fragment = new ScannerFragmentMinimal();
        Bundle args = new Bundle();
        args.putString(ARG_SINGLEPERIPHERALIDENTIFIER, identifier);
        fragment.setArguments(args);
        return fragment;
    }

    public String mIdentifier = null;

    public ScannerFragmentMinimal() {
//        mIdentifier = identifier;
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mListener = (ScannerFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ScannerFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mIdentifier = getArguments().getString(ARG_SINGLEPERIPHERALIDENTIFIER);
        }

        setHasOptionsMenu(true);

        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        YrConstants.log(YrConstants.DEBUG, "ScannerFragment.onCreateView");
        View view = inflater.inflate(R.layout.fragment_scanner_minimal, container, false);

        progressBarScanning = view.findViewById(R.id.progressScanning);
        progressBarScanning.setVisibility(View.VISIBLE);

        tvScanningStatus = view.findViewById(R.id.tvScanningStatus);
        tvScanningStatus.setText("Started Scanning");

        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        YrConstants.log(YrConstants.DEBUG, "ScannerFragment.onViewCreated");
        final Context context = getContext();

        if (context != null) {
//
//            // Adapter
            mBlePeripheralsAdapter = new BlePeripheralsAdapter(context, blePeripheral -> {
                ScanRecord scanRecord = blePeripheral.getScanRecord();
                YrConstants.log(YrConstants.DEBUG, TAG + "new BlePeripheralsAdapter [" + blePeripheral.getIdentifier() + "]");
//                if (scanRecord != null) {
//                    final byte[] advertisementBytes = scanRecord.getBytes();
//                    final String packetText = BleUtils.bytesToHexWithSpaces(advertisementBytes);
//                    final String clipboardLabel = context.getString(R.string.scanresult_advertisement_rawdata_title);
//
//                    new AlertDialog.Builder(context)
//                            .setTitle(R.string.scanresult_advertisement_rawdata_title)
//                            .setMessage(packetText)
//                            .setPositiveButton(android.R.string.ok, null)
//                            .setNeutralButton(android.R.string.copy, (dialog, which) -> {
//                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
//                                if (clipboard != null) {
//                                    ClipData clip = ClipData.newPlainText(clipboardLabel, packetText);
//                                    clipboard.setPrimaryClip(clip);
//                                }
//                            })
//                            .show();
//                }
            });
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ViewModel
        FragmentActivity activity = getActivity();
        if (activity != null) {
            mDfuViewModel = ViewModelProviders.of(activity).get(DfuViewModel.class);
        }
        mScannerViewModel = ViewModelProviders.of(this).get(ScannerViewModel.class);
        mScannerViewModel.setScannerFragment(this);

        // Scan results
        mScannerViewModel.getFilteredBlePeripherals().observe(this, blePeripherals -> mBlePeripheralsAdapter.setBlePeripherals(blePeripherals));

        // Scanning
        mScannerViewModel.getScanningErrorCode().observe(this, errorCode -> {
            Log.d(TAG, "Scanning error: " + errorCode);

            if (errorCode != null && errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {       // Check for known errors
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                AlertDialog dialog = builder.setTitle(R.string.dialog_error).setMessage(R.string.bluetooth_scanner_errorregisteringapp)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
            } else {        // Ask for location permission
                mListener.scannerRequestLocationPermissionIfNeeded();
            }
        });


        mScannerViewModel.getBlePeripheralsConnectionChanged().observe(this, blePeripheral -> {
            mBlePeripheralsAdapter.notifyDataSetChanged();
//            if (blePeripheral != null) {
//                showConnectionStateDialog(blePeripheral);
//            }
        });

        // Dfu Update
        mDfuViewModel.getDfuCheckResult().observe(this, dfuCheckResult -> {
            if (dfuCheckResult != null) {
                YrConstants.log(YrConstants.SCANNER, TAG + ".getDfuCheckResult");
                onDfuUpdateCheckResultReceived(dfuCheckResult.blePeripheral, dfuCheckResult.isUpdateAvailable, dfuCheckResult.dfuInfo, dfuCheckResult.firmwareInfo);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

            // Automatically starts scanning
            boolean isDfuInProgress = activity instanceof MainActivity && ((MainActivity) activity).isIsDfuInProgress();
            if (!isDfuInProgress) {
                startScanning();
            } else {
                Log.d(TAG, "Don't start scanning because DFU  is in progress");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerViewModel.stop();
    }

    @Override
    public void onDestroy() {
        mScannerViewModel.saveFilters();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_centralmode, menu);
    }

    // endregion

    // region Actions
    public void startScanning() {
        mScannerViewModel.start();
    }

    public void disconnectAllPeripherals() {
        mScannerViewModel.disconnectAllPeripherals();
    }

    // endregion


    private void removeConnectionStateDialog() {
        if (mConnectingDialog != null) {
            mConnectingDialog.dismiss();
//            mConnectingDialog.cancel();

            mConnectingDialog = null;
        }
    }

    @Override
    public void scannerStatusCancelled(@NonNull String blePeripheralIdentifier) {
        Log.d(TAG, "Connecting dialog cancelled");

        final BlePeripheral blePeripheral = mScannerViewModel.getPeripheralWithIdentifier(blePeripheralIdentifier);
        if (blePeripheral != null) {
            blePeripheral.disconnect();
        } else {
            Log.w(TAG, "status dialog cancelled for unknown peripheral");
        }
    }

    private void showConnectionStateDialog(@StringRes int messageId, final BlePeripheral blePeripheral) {
        // Show dialog
        final String message = getString(messageId);
        if (mConnectingDialog == null || !mConnectingDialog.isInitialized()) {
            removeConnectionStateDialog();

            FragmentActivity activity = getActivity();
            if (activity != null) {
                FragmentManager fragmentManager = getFragmentManager();
                if (fragmentManager != null) {
                    mConnectingDialog = ScannerStatusFragmentDialog.newInstance(message, blePeripheral.getIdentifier());
                    mConnectingDialog.setTargetFragment(this, 0);
                    mConnectingDialog.show(fragmentManager, "ConnectingDialog");
                }
            }
        } else {
            mConnectingDialog.setMessage(message);
        }
    }

    private void showServiceDiscoveredStateDialog(BlePeripheral blePeripheral) {
        Context context = getContext();

        if (blePeripheral != null && context != null) {

            if (blePeripheral.isDisconnected()) {
                Log.d(TAG, "Abort connection sequence. Peripheral disconnected");
            } else {
                final boolean isMultiConnectEnabled = mScannerViewModel.isMultiConnectEnabledValue();
                if (isMultiConnectEnabled) {
                    removeConnectionStateDialog();
                    //  Nothing to do, wait for more connections or start
                } else {
                    // Check updates if needed
                    Log.d(TAG, "Check firmware updates");
                    showConnectionStateDialog(R.string.peripheraldetails_checkingupdates, blePeripheral);
                    mDfuViewModel.startUpdatesCheck(context, blePeripheral);
                }
            }
        }
    }

    private void showConnectionStateError(String message) {
        removeConnectionStateDialog();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            View view = activity.findViewById(android.R.id.content);
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
            StyledSnackBar.styleSnackBar(snackbar, activity);
            snackbar.show();
        }
    }

    public void onConnectedCallback(@NonNull BlePeripheral blePeripheral) {
        if (mListener != null) {
            progressBarScanning.setVisibility(View.GONE);
//            tvScanningStatus.setText("Connected to device\n[" + blePeripheral.getIdentifier() + "]");
            tvScanningStatus.setText("Connected to device\n");

            new Handler(Looper.getMainLooper())
                    .postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            tvScanningStatus.setText(tvScanningStatus.getText() + "\n\nSyncing ...");
                        }
                    }, 100);

            new Handler(Looper.getMainLooper())
                    .postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mListener.startMainFragment(blePeripheral.getIdentifier());
                        }
                    }, 100);
        }
    }
    // endregion

    // region Dfu
    @MainThread
    private void onDfuUpdateCheckResultReceived(@NonNull BlePeripheral blePeripheral, boolean isUpdateAvailable, @NonNull DfuUpdater.DeviceDfuInfo deviceDfuInfo, @Nullable ReleasesParser.FirmwareInfo latestRelease) {
        Log.d(TAG, "Update available: " + isUpdateAvailable);
        removeConnectionStateDialog();

        YrConstants.log(YrConstants.SCANNER, TAG + ".onDfuUpdateCheckResultReceived [" + blePeripheral.getIdentifier() + "]");

        Context context = getContext();
        if (isUpdateAvailable && latestRelease != null && context != null) {
            // Ask user if should update
            String message = String.format(getString(R.string.autoupdate_description_format), latestRelease.version);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.autoupdate_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.autoupdate_startupdate, (dialog, which) -> {
                        startFirmwareUpdate(blePeripheral, latestRelease);
                    })
                    .setNeutralButton(R.string.autoupdate_later, (dialog, which) -> {
                        if (mListener != null) {
//                            mListener.startPeripheralModules(blePeripheral.getIdentifier());
                        }
                    })
                    .setNegativeButton(R.string.autoupdate_ignore, (dialog, which) -> {
                        mDfuViewModel.setIgnoredVersion(context, latestRelease.version);
                        if (mListener != null) {
//                            mListener.startPeripheralModules(blePeripheral.getIdentifier());
                        }
                    })
                    .setCancelable(false)
                    .show();
        } else {
            // Go to peripheral modules
            if (mListener != null) {
//                mListener.startPeripheralModules(blePeripheral.getIdentifier());
            }
        }
    }
    // endregion

    private void startFirmwareUpdate(@NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.FirmwareInfo firmwareInfo) {
        removeConnectionStateDialog();       // hide current dialogs because software update will display a dialog
        mScannerViewModel.stop();

        FragmentActivity activity = getActivity();
        if (activity != null && activity instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.startUpdate(blePeripheral, firmwareInfo);
        }
    }

    // region Listeners
    interface ScannerFragmentListener {
        void bluetoothAdapterIsDisabled();

        void scannerRequestLocationPermissionIfNeeded();

        void startMainFragment(String singlePeripheralIdentifier);
    }

    // endregion
}