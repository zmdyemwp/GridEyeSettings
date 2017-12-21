package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Switch;
import android.widget.TextView;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.Toast;

import java.util.UUID;

import android.os.Handler;

import org.w3c.dom.Text;

import java.lang.Runnable;

import static android.R.attr.value;
import static android.R.attr.y;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.V;

/**
 * For a given BLE device, this Activity provides the user interface to configure the settings
 * of the device. The Activity communicates with {@code BluetoothLeService}, which in turn
 * interacts with the Bluetooth LE API.
 */
public class DeviceConfigureActivity extends Activity {
    private final static String TAG = DeviceConfigureActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final int BLE_BYTE_ARR_LENGTH = 14;
    private static final int BLE_BYTE_LENGTH_CMD_2 = 12;

    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private EditText mET_DiffTempThresh, mET_LabelingThresh, mET_FNMVFrame, mET_EdgeFrame,
            mET_CheckSumMove, mET_CheckDiffArea, mET_HumanThresh, mET_QuiltMoveThresh, mET_QuiltAreaThresh,
            mET_BedLeftBoardPoint, mET_BedRightBoardPoint, mET_LeftX, mET_RightX, mET_TopY, mET_BottomY,
            mIPCamAddress_1, mIPCamAddress_2, mIPCamAddress_3, mIPCamAddress_4,
            mFTPAddress_1, mFTPAddress_2, mFTPAddress_3, mFTPAddress_4;
    private Button mReset,
            mResetSettings,
            mDeleteLog;

    private Button mSetBtn;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());

                readCurrentConfig();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                Bundle bundle = intent.getExtras();
                if(bundle != null) {
                    byte[] value = bundle.getByteArray(BluetoothLeService.EXTRA_DATA);
                    displayCurrentConfig(value);
                    for(int i = 0; i < value.length; i++) {
                        Log.d(TAG, String.format("[%d] 0x%02x", i, value[i]));
                    }
                }
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                            Log.d(TAG, "onChildClick : PROPERTY_WRITE");
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }

                            Log.d(TAG, "characteristic.getService().getUuid() = " + characteristic.getService().getUuid());
                            Log.d(TAG, "characteristic.getUuid() = " + characteristic.getUuid());

                            //byte[] value = "1".getBytes();
                            //byte[] value = bigIntToByteArray(123);
                            byte[] value = getConfigureByteArray1();
                            characteristic.setValue(value);
                            mBluetoothLeService.writeCharacteristic(characteristic);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private byte[] bigIntToByteArray(final int i) {
        BigInteger bigInt = BigInteger.valueOf(i);
        return bigInt.toByteArray();
    }


    private byte[] getConfigureByteArray2() {
        byte[] value = new byte[BLE_BYTE_LENGTH_CMD_2];
        value[0] = 0x02;
        try {
            value[1] = (byte)Integer.parseInt(mIPCamAddress_1.getText().toString());
            value[2] = (byte)Integer.parseInt(mIPCamAddress_2.getText().toString());
            value[3] = (byte)Integer.parseInt(mIPCamAddress_3.getText().toString());
            value[4] = (byte)Integer.parseInt(mIPCamAddress_4.getText().toString());

            value[5] = (byte)Integer.parseInt(mFTPAddress_1.getText().toString());
            value[6] = (byte)Integer.parseInt(mFTPAddress_2.getText().toString());
            value[7] = (byte)Integer.parseInt(mFTPAddress_3.getText().toString());
            value[8] = (byte)Integer.parseInt(mFTPAddress_4.getText().toString());

            value[9] = 0;
            value[10] = 0;
            value[11] = 0;
            // value[9] = (mReset.isChecked())? (byte)0x01:0x00;
            // value[10] = (mResetSettings.isChecked())? (byte)0x01:0x00;
            // value[11] = (mDeleteLog.isChecked())? (byte)0x01:0x00;
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
        return value;
    }

    private byte[] getConfigureByteArray1() {
        byte[] value = new byte[BLE_BYTE_ARR_LENGTH];

        value[0] = 0x01;
        String str = mET_DiffTempThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            value[1] = (byte)b;
            Log.d(TAG, "value[0] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            value[2] = (byte)c;
            Log.d(TAG, "value[1] = " + c);
        }
        str = mET_LabelingThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[3] = (byte)a;
            Log.d(TAG, "value[2] = " + a);
        }
        str = mET_FNMVFrame.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[4] = (byte)a;
            Log.d(TAG, "value[3] = " + a);
        }
        str = mET_EdgeFrame.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[5] = (byte)(a >> 8);
            value[6] = (byte)(a);
            Log.d(TAG, "value[4] + value[5] = " + a);
        }
        /*str = mET_CheckSumMove.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            value[6] = (byte)b;
            Log.d(TAG, "value[6] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            value[7] = (byte)c;
            Log.d(TAG, "value[7] = " + c);
        }
        str = mET_CheckDiffArea.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            value[8] = (byte)b;
            Log.d(TAG, "value[8] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            value[9] = (byte)c;
            Log.d(TAG, "value[9] = " + c);
        }*/
        str = mET_HumanThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[7] = (byte)a;
            Log.d(TAG, "value[6] = " + a);
        }
        /*str = mET_QuiltMoveThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            value[11] = (byte)b;
            Log.d(TAG, "value[11] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            value[12] = (byte)c;
            Log.d(TAG, "value[12] = " + c);
        }
        str = mET_QuiltAreaThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            value[13] = (byte)b;
            Log.d(TAG, "value[13] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            value[14] = (byte)c;
            Log.d(TAG, "value[14] = " + c);
        }*/
        str = mET_BedLeftBoardPoint.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[8] = (byte)a;
            Log.d(TAG, "value[7] = " + a);
        }
        str = mET_BedRightBoardPoint.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[9] = (byte)a;
            Log.d(TAG, "value[8] = " + a);
        }
        str = mET_LeftX.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[10] = (byte)a;
            Log.d(TAG, "value[9] = " + a);
        }
        str = mET_RightX.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[11] = (byte)a;
            Log.d(TAG, "value[10] = " + a);
        }
        str = mET_TopY.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[12] = (byte)a;
            Log.d(TAG, "value[11] = " + a);
        }
        str = mET_BottomY.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            value[13] = (byte)a;
            Log.d(TAG, "value[12] = " + a);
        }

        return value;
    }

    private void clearUI() {
        // mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* MinSMChien - New Fusion Guard Settings */
        // setContentView(R.layout.device_configuration);
        setContentView(R.layout.fusion_guard_settings);
        initUI();
        /* MinSMChien - New Fusion Guard Settings */

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        /*
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        mET_DiffTempThresh = (EditText) findViewById(R.id.ET_DiffTempThresh);
        mET_LabelingThresh = (EditText) findViewById(R.id.ET_LabelingThresh);
        mET_FNMVFrame = (EditText) findViewById(R.id.ET_FNMVFrame);
        mET_EdgeFrame = (EditText) findViewById(R.id.ET_EdgeFrame);
        mET_CheckSumMove = (EditText) findViewById(R.id.ET_CheckSumMove);
        mET_CheckDiffArea = (EditText) findViewById(R.id.ET_CheckDiffArea);
        mET_HumanThresh = (EditText) findViewById(R.id.ET_HumanThresh);
        mET_QuiltMoveThresh = (EditText) findViewById(R.id.ET_QuiltMoveThresh);
        mET_QuiltAreaThresh = (EditText) findViewById(R.id.ET_QuiltAreaThresh);
        mET_BedLeftBoardPoint = (EditText) findViewById(R.id.ET_BedLeftBoardPoint);
        mET_BedRightBoardPoint = (EditText) findViewById(R.id.ET_BedRightBoardPoint);
        mET_LeftX = (EditText) findViewById(R.id.ET_LeftX);
        mET_RightX = (EditText) findViewById(R.id.ET_RightX);
        mET_TopY = (EditText) findViewById(R.id.ET_TopY);
        mET_BottomY = (EditText) findViewById(R.id.ET_BottomY);

        mET_LabelingThresh.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_FNMVFrame.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_EdgeFrame.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "65535")});
        mET_HumanThresh.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_BedLeftBoardPoint.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_BedRightBoardPoint.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_LeftX.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_RightX.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_TopY.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mET_BottomY.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});

        mIPCamAddress_1 = (EditText) findViewById(R.id.ip_cam_addr_1);
        mIPCamAddress_1.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mIPCamAddress_2 = (EditText) findViewById(R.id.ip_cam_addr_2);
        mIPCamAddress_2.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mIPCamAddress_3 = (EditText) findViewById(R.id.ip_cam_addr_3);
        mIPCamAddress_3.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mIPCamAddress_4 = (EditText) findViewById(R.id.ip_cam_addr_4);
        mIPCamAddress_4.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});

        mFTPAddress_1 = (EditText) findViewById(R.id.ftp_addr_1);
        mFTPAddress_1.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mFTPAddress_2 = (EditText) findViewById(R.id.ftp_addr_2);
        mFTPAddress_2.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mFTPAddress_3 = (EditText) findViewById(R.id.ftp_addr_3);
        mFTPAddress_3.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});
        mFTPAddress_4 = (EditText) findViewById(R.id.ftp_addr_4);
        mFTPAddress_4.setFilters(new InputFilter[]{ new InputFilterMinMax("0", "255")});

        mReset = (Button)findViewById(R.id.btn_reset);
        mResetSettings = (Button)findViewById(R.id.btn_reset_settings);
        mDeleteLog = (Button)findViewById(R.id.btn_delete_logs);
        mReset.setOnClickListener(new BtnOnClickListener());
        mResetSettings.setOnClickListener(new BtnOnClickListener());
        mDeleteLog.setOnClickListener(new BtnOnClickListener());

        mSetBtn = (Button) findViewById(R.id.btn_set);
        mSetBtn.setOnClickListener(new BtnOnClickListener());
        */
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }




    Switch mIPCameraEnable;
    TextView mIPCameraIPAddress_1;
    TextView mIPCameraIPAddress_2;
    TextView mIPCameraIPAddress_3;
    TextView mIPCameraIPAddress_4;
    TextView mIPCameraCaptureFreq;
    TextView mFTPIPAddress_1;
    TextView mFTPIPAddress_2;
    TextView mFTPIPAddress_3;
    TextView mFTPIPAddress_4;
    TextView mSocketServerIPAddress_1;
    TextView mSocketServerIPAddress_2;
    TextView mSocketServerIPAddress_3;
    TextView mSocketServerIPAddress_4;
    TextView mLogUploadFreq;
    TextView mLogUploadSize;
    Switch mFusionGuardEnable;

    RadioButton mLeavingRadio;
    RadioButton mStayRadio;
    RadioButton mPatrolRadio;
    Button mResetFusionGuard;

    TextView mPatrolInterval;
    TextView mPatrolTempDiffThreshold;
    TextView mPatrolObjectSize;
    TextView mPatrolCeilingHeight;

    TextView mStayAlarmTime;
    TextView mStayTempDiffThreshold;
    TextView mStayObjctSize;
    TextView mBathroomLength;
    TextView mBathroomWidth;
    TextView mBathroomHeight;

    TextView mWardTempDiffThreshold;
    TextView mWardObjctSize;
    TextView mBedLength;
    TextView mBedWidth;
    TextView mWardCeilingHeight;

    Button mCommitSetting;
    void initUI() {
        LinearLayout main = (LinearLayout)findViewById(R.id.fusion_guard_basic_settings);
        mIPCameraEnable = (Switch)main.findViewById(R.id.ip_camera_switch);
        mIPCameraIPAddress_1 = (TextView)main.findViewById(R.id.ip_camera_ip_address_1);
        mIPCameraIPAddress_2 = (TextView)main.findViewById(R.id.ip_camera_ip_address_2);
        mIPCameraIPAddress_3 = (TextView)main.findViewById(R.id.ip_camera_ip_address_3);
        mIPCameraIPAddress_4 = (TextView)main.findViewById(R.id.ip_camera_ip_address_4);
        mIPCameraCaptureFreq = (TextView)main.findViewById(R.id.ip_camera_capture_freq);
        mFTPIPAddress_1 = (TextView)main.findViewById(R.id.ftp_addr_1);
        mFTPIPAddress_2 = (TextView)main.findViewById(R.id.ftp_addr_2);
        mFTPIPAddress_3 = (TextView)main.findViewById(R.id.ftp_addr_3);
        mFTPIPAddress_4 = (TextView)main.findViewById(R.id.ftp_addr_4);
        mLogUploadFreq = (TextView)main.findViewById(R.id.log_upload_freq);
        mLogUploadSize = (TextView)main.findViewById(R.id.log_upload_size);
        mSocketServerIPAddress_1 = (TextView)main.findViewById(R.id.socket_ip_1);
        mSocketServerIPAddress_2 = (TextView)main.findViewById(R.id.socket_ip_2);
        mSocketServerIPAddress_3 = (TextView)main.findViewById(R.id.socket_ip_3);
        mSocketServerIPAddress_4 = (TextView)main.findViewById(R.id.socket_ip_4);
        mFusionGuardEnable = (Switch)main.findViewById(R.id.fusion_guard_switch);

        LinearLayout command = (LinearLayout)findViewById(R.id.fusion_guard_command_settings);
        mLeavingRadio = (RadioButton)command.findViewById(R.id.detect_leaving);
        mStayRadio = (RadioButton)command.findViewById(R.id.detect_stay);
        mPatrolRadio = (RadioButton)command.findViewById(R.id.detect_patrol);
        mResetFusionGuard = (Button)command.findViewById(R.id.reset_fusion_guard);
        mResetFusionGuard.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setmResetFusionGuard();
            }
        });

        LinearLayout protection = (LinearLayout)findViewById(R.id.fusion_guard_protection_room);
        mPatrolInterval = (TextView)protection.findViewById(R.id.patrol_interval);
        mPatrolTempDiffThreshold = (TextView)protection.findViewById(R.id.protect_temp_diff_threshold);
        mPatrolObjectSize = (TextView)protection.findViewById(R.id.protect_object_size_threshold);
        mPatrolCeilingHeight = (TextView)protection.findViewById(R.id.protection_room_ceiling_height);

        LinearLayout bathroom = (LinearLayout)findViewById(R.id.fusion_guard_bath_room);
        mStayAlarmTime = (TextView)bathroom.findViewById(R.id.stay_alarm_time);
        mStayTempDiffThreshold = (TextView)bathroom.findViewById(R.id.stay_temp_diff_threshold);
        mStayObjctSize = (TextView)bathroom.findViewById(R.id.stay_object_size_threshold);
        mBathroomLength = (TextView)bathroom.findViewById(R.id.bathroom_length);
        mBathroomWidth = (TextView)bathroom.findViewById(R.id.bathroom_width);
        mBathroomHeight = (TextView)bathroom.findViewById(R.id.bathroom_height);

        LinearLayout ward = (LinearLayout)findViewById(R.id.fusion_guard_ward);
        mWardTempDiffThreshold = (TextView)ward.findViewById(R.id.leaving_temp_diff_threshold);
        mWardObjctSize = (TextView)ward.findViewById(R.id.leaving_object_size_threshold);
        mBedLength = (TextView)ward.findViewById(R.id.bed_length);
        mBedWidth = (TextView)ward.findViewById(R.id.bed_width);
        mWardCeilingHeight = (TextView)ward.findViewById(R.id.ward_ceiling_height);

        mCommitSetting = (Button)findViewById(R.id.commit_settings);
        mCommitSetting.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                commit();
            }
        });
    }

    void setmResetFusionGuard() {
        writeConfigGroup(4);
    }

    void commit() {
        writeConfigGroup(1);
        sleep(500);
        writeConfigGroup(2);
        sleep(500);
        writeConfigGroup(3);
        sleep(500);
        // writeConfigGroup(4);
        writeConfigGroup(5);
        sleep(500);
        writeConfigGroup(6);
        sleep(500);
        writeConfigGroup(7);
    }

    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_1 = 13;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_2 = 9;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_3 = 2;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_4 = 2;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_5 = 8;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_6 = 12;
    private static final int FUSION_GUARD_CONFIG_LENGTH_GROUP_7 = 10;


    void writeConfigGroup(int group) {
        byte[] values;
        String str;
        switch(group) {
            case 1:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_1];
                // IP Camera IP Address
                str = mIPCameraIPAddress_1.getText().toString();
                get1ByteInteger(str, values, 1);
                str = mIPCameraIPAddress_2.getText().toString();
                get1ByteInteger(str, values, 2);
                str = mIPCameraIPAddress_3.getText().toString();
                get1ByteInteger(str, values, 3);
                str = mIPCameraIPAddress_4.getText().toString();
                get1ByteInteger(str, values, 4);

                //  FTP IP Address
                str = mFTPIPAddress_1.getText().toString();
                get1ByteInteger(str, values, 5);
                str = mFTPIPAddress_2.getText().toString();
                get1ByteInteger(str, values, 6);
                str = mFTPIPAddress_3.getText().toString();
                get1ByteInteger(str, values, 7);
                str = mFTPIPAddress_4.getText().toString();
                get1ByteInteger(str, values, 8);

                //  Socket Server IP Address
                str = mSocketServerIPAddress_1.getText().toString();
                get1ByteInteger(str, values, 9);
                str = mSocketServerIPAddress_2.getText().toString();
                get1ByteInteger(str, values, 10);
                str = mSocketServerIPAddress_3.getText().toString();
                get1ByteInteger(str, values, 11);
                str = mSocketServerIPAddress_4.getText().toString();
                get1ByteInteger(str, values, 12);
                break;
            case 2:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_2];
                values[1] = mIPCameraEnable.isChecked()? (byte)0x01:0x00;
                str = mIPCameraCaptureFreq.getText().toString();
                get2ByteInteger(str, values, 2);
                str = mLogUploadFreq.getText().toString();
                get2ByteInteger(str, values, 4);
                str = mLogUploadSize.getText().toString();
                get2ByteInteger(str, values, 6);
                values[8] = mFusionGuardEnable.isChecked()? (byte)0x01:0x00;
                break;
            case 3:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_3];
                values[1] = 0;
                if(mLeavingRadio.isChecked()) {
                    values[1] = 0;
                } else if(mStayRadio.isChecked()) {
                    values[1] = (byte)1;
                } else if(mPatrolRadio.isChecked()) {
                    values[1] = (byte)2;
                }
                break;
            case 4:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_4];
                values[1] = (byte)1;
                break;
            case 5:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_5];
                str = mPatrolInterval.getText().toString();
                get2ByteInteger(str, values, 1);
                str = mPatrolTempDiffThreshold.getText().toString();
                get2ByteFloat(str, values, 3);
                str = mPatrolObjectSize.getText().toString();
                get1ByteInteger(str, values, 5);
                str = mPatrolCeilingHeight.getText().toString();
                get2ByteFloat(str, values, 6);
                break;
            case 6:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_6];
                str = mStayAlarmTime.getText().toString();
                get2ByteInteger(str, values, 1);
                str = mStayTempDiffThreshold.getText().toString();
                get2ByteFloat(str, values, 3);
                str = mStayObjctSize.getText().toString();
                get1ByteInteger(str, values, 5);
                str = mBathroomLength.getText().toString();
                get2ByteFloat(str, values, 6);
                str = mBathroomWidth.getText().toString();
                get2ByteFloat(str, values, 8);
                str = mBathroomHeight.getText().toString();
                get2ByteFloat(str, values,10);
                break;
            case 7:
                values = new byte[FUSION_GUARD_CONFIG_LENGTH_GROUP_7];
                str = mWardTempDiffThreshold.getText().toString();
                get2ByteFloat(str, values, 1);
                str = mWardObjctSize.getText().toString();
                get1ByteInteger(str, values, 3);
                str = mBedLength.getText().toString();
                get2ByteFloat(str, values, 4);
                str = mBedWidth.getText().toString();
                get2ByteFloat(str, values, 6);
                str = mWardCeilingHeight.getText().toString();
                get2ByteFloat(str, values, 8);
                break;
            default:
                values = null;
                break;
        }
        if(null == values) {
            return;
        }
        values[0] = (byte)group;
        for(int x = 0; x < values.length; x++) {
            Log.d("MIN", String.format("[%02d] (0x%02x)", x, values[x]));
        }

        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(characteristic != null) {
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                mBluetoothLeService.setSendByteArr(values);
                mBluetoothLeService.writeCharacteristic(characteristic);
            } else {
                Log.d(TAG, "The target characteristic has no write property !");
                Toast.makeText(this, "The target characteristic has no write property !", Toast.LENGTH_SHORT).show();
            }
        }

    }

    void get2ByteFloat(String str, byte[] values, int offset) {
        if(null == values || 0 > offset || values.length <= 1+offset) {
            return;
        }
        if(null == str || str.isEmpty()) {
            values[offset] = 0;
            values[offset + 1] = 0;
            return;
        }
        Float f = Float.parseFloat(str);
        values[offset] = (byte)f.intValue();
        int i = str.indexOf('.');
        if(-1 == i || str.length() == 1+i) {
            values[1+offset] = 0;
        } else {
            values[1+offset] = (byte)Integer.parseInt(str.substring(1 + i));
        }
    }
    void get2ByteInteger(String str, byte[] values, int offset) {
        if(null == values || 0 > offset || values.length <= 1+offset) {
            return;
        }
        if(null == str || str.isEmpty()) {
            values[offset] = 0;
            values[offset + 1] = 0;
            return;
        }
        int i = Integer.parseInt(str);
        values[offset] = (byte)i;
        i >>= 8;
        values[offset + 1] = (byte)i;
    }
    void get1ByteInteger(String str, byte[] values, int offset) {
        if(null == values || 0 > offset || values.length <= offset) {
            return;
        }
        if(null == str || str.isEmpty()) {
            values[offset] = 0;
            return;
        }
        values[offset] = (byte)Integer.parseInt(str);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    class BtnOnClickListener implements View.OnClickListener {
        public void onClick(View v){
            switch(v.getId()){
                case R.id.btn_set:
                    writeConfig2Dev();
                    break;
                case R.id.btn_reset:
                    writeCMD(3);
                    break;
                case R.id.btn_reset_settings:
                    writeCMD(4);
                    break;
                case R.id.btn_delete_logs:
                    writeCMD(5);
                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // mConnectionState.setText(resourceId);
            }
        });
    }

    private void writeCMD(int cmd) {
        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(null != characteristic) {
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                byte[] value = new byte[1];
                value[0] = (byte) (0xff & cmd);
                mBluetoothLeService.setSendByteArr(value);
                mBluetoothLeService.writeCharacteristic(characteristic);
            }
        }
    }

    private void writeConfig2Dev() {
        Log.d(TAG, "writeConfig2Dev()");
        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(characteristic != null) {
            Log.d(TAG, "get target characteristic successfully !");
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                Log.d(TAG, "writeCharacteristic ---");
                byte[] value = getConfigureByteArray1();
                mBluetoothLeService.setSendByteArr(value);
                //characteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(characteristic);

                sleep(500);

                value = getConfigureByteArray2();
                for(int i = 0; i < value.length; i++) {
                    Log.d(TAG, String.format("getConfigureByteArray2()[%d] = 0x%02x", i, value[i]));
                }
                mBluetoothLeService.setSendByteArr(value);
                mBluetoothLeService.writeCharacteristic(characteristic);

            } else {
                Log.d(TAG, "The target characteristic has no write property !");
                Toast.makeText(this, "The target characteristic has no write property !", Toast.LENGTH_SHORT).show();
            }
        }

        /*String str = mET_DiffTempThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            //value[0] = (byte)b;
            Log.d(TAG, "value[0] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            //value[1] = (byte)c;
            Log.d(TAG, "value[1] = " + c);
        }
        str = mET_LabelingThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[2] = (byte)a;
            Log.d(TAG, "value[2] = " + a);
        }
        str = mET_FNMVFrame.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[3] = (byte)a;
            Log.d(TAG, "value[3] = " + a);
        }
        str = mET_EdgeFrame.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[4] = (byte)(a >> 8);
            //value[5] = (byte)(a);
            Log.d(TAG, "value[4] + value[5] = " + a);
        }
        str = mET_CheckSumMove.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            //value[6] = (byte)b;
            Log.d(TAG, "value[6] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            //value[7] = (byte)c;
            Log.d(TAG, "value[7] = " + c);
        }
        str = mET_CheckDiffArea.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            //value[8] = (byte)b;
            Log.d(TAG, "value[8] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            //value[9] = (byte)c;
            Log.d(TAG, "value[9] = " + c);
        }
        str = mET_HumanThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[10] = (byte)a;
            Log.d(TAG, "value[10] = " + a);
        }
        str = mET_QuiltMoveThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            //value[11] = (byte)b;
            Log.d(TAG, "value[11] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            //value[12] = (byte)c;
            Log.d(TAG, "value[12] = " + c);
        }
        str = mET_QuiltAreaThresh.getText().toString();
        if(!TextUtils.isEmpty(str)){
            Float a = Float.parseFloat(str);
            // ?�數
            int b = a.intValue();
            //value[13] = (byte)b;
            Log.d(TAG, "value[13] = " + b);
            // 小數
            String numberD = String.valueOf(a);
            numberD = numberD.substring(numberD.indexOf(".")).substring(1);
            int c = Integer.parseInt(numberD);
            //value[14] = (byte)c;
            Log.d(TAG, "value[14] = " + c);
        }
        str = mET_BedLeftBoardPoint.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[15] = (byte)a;
            Log.d(TAG, "value[15] = " + a);
        }
        str = mET_BedRightBoardPoint.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[16] = (byte)a;
            Log.d(TAG, "value[16] = " + a);
        }
        str = mET_LeftX.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[17] = (byte)a;
            Log.d(TAG, "value[17] = " + a);
        }
        str = mET_RightX.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[18] = (byte)a;
            Log.d(TAG, "value[18] = " + a);
        }
        str = mET_TopY.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[19] = (byte)a;
            Log.d(TAG, "value[19] = " + a);
        }
        str = mET_BottomY.getText().toString();
        if(!TextUtils.isEmpty(str)){
            int a = Integer.parseInt(str);
            //value[20] = (byte)a;
            Log.d(TAG, "value[20] = " + a);
        }*/

    }

    private void askDevConfig(BluetoothGattCharacteristic characteristic, int requiredConfig) {
        if(characteristic != null) {
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                byte[] cmd = new byte[1];
                cmd[0] = (byte)requiredConfig;
                mBluetoothLeService.setSendByteArr(cmd);
                mBluetoothLeService.writeCharacteristic(characteristic);
                sleep(500);
            } else {
                Log.d(TAG, "The target characteristic has no write property !");
                Toast.makeText(this, "The target characteristic has no write property !", Toast.LENGTH_SHORT).show();
            }
        }
    }


    int group_id = 0;
    Handler h = new Handler();
    Runnable r = new Runnable() {
        public void run() {
            final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
            if(null != characteristic) {
                askDevConfig(characteristic, group_id);
                mBluetoothLeService.readCharacteristic(characteristic);
                group_id++;
                if(4 == group_id) group_id++;
                if(8 > group_id) h.postDelayed(r, 1000);
            }
        }
    };

    private void readCurrentConfig() {
        Log.d(TAG, "readCurrentConfig()");
        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(characteristic != null) {
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

                /* MinSMChien - ReadConfig from GAP */
                /*for(int i = 1; i <= 7; i++) {
                    askDevConfig(characteristic, i);
                    mBluetoothLeService.readCharacteristic(characteristic);
                    sleep(500);
                }*/
                group_id = 1;
                h.post(r);

            } else {
                Log.d(TAG, "The target characteristic has no read property !");
                Toast.makeText(this, "The target characteristic has no read property !", Toast.LENGTH_SHORT).show();
            }
        }
    }


    void sleep(int ms) {
        try {Thread.sleep(ms);} catch(Exception e) {Log.e("MIN", e.toString());}
    }


    private void readCurrentConfig_2() {
        Log.d(TAG, "readCurrentConfig_2()");
        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(characteristic != null) {
            Log.d(TAG, "get target characteristic successfully !");
            Log.d(TAG, "service UUID = " + characteristic.getService().getUuid());
            Log.d(TAG, "characteristic UUID = " + characteristic.getUuid());
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                Log.d(TAG, "readCharacteristic ---");
                askDevConfig(characteristic, 2);
                mBluetoothLeService.readCharacteristic(characteristic);
            } else {
                Log.d(TAG, "The target characteristic has no read property !");
                Toast.makeText(this, "The target characteristic has no read property !", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private static int getIntFromByte1(byte[] values, int offset) {
        int result = 0;
        if(0 <= offset && values.length > offset) {
            Log.d("MIN", String.format("%02x", values[offset]));
            result = (0xff & values[offset]);
        }
        return result;
    }
    private static int getIntFromByte2(byte[] values, int offset) {
        int result = 0;
        if(0 <= offset && values.length > offset + 1) {
            Log.d("MIN", String.format("%02x.%02x", values[offset], values[offset + 1]));
            result = (0xff & values[offset]) + ((0xff & values[offset + 1]) << 8);
        }
        return result;
    }
    private static String getFloatFromByte2(byte[] values, int offset) {
        String result = "0";
        if(0 <= offset && values.length > offset + 1) {
            Log.d("MIN", String.format("%02x.%02x", values[offset], values[offset + 1]));
            result = String.format("%d.%d", getIntFromByte1(values, offset), getIntFromByte1(values, offset + 1));
            //String str = String.format("%d.%d", getIntFromByte1(values, offset), getIntFromByte1(values, offset + 1));
            //result = Float.parseFloat(str);
        }
        return result;
    }

    private void displayCurrentConfig(byte[] value) {
        switch(value[0]) {
            case 1:
                mIPCameraIPAddress_1.setText(String.format("%d", getIntFromByte1(value, 1)));
                mIPCameraIPAddress_2.setText(String.format("%d", getIntFromByte1(value, 2)));
                mIPCameraIPAddress_3.setText(String.format("%d", getIntFromByte1(value, 3)));
                mIPCameraIPAddress_4.setText(String.format("%d", getIntFromByte1(value, 4)));
                mFTPIPAddress_1.setText(String.format("%d", getIntFromByte1(value, 5)));
                mFTPIPAddress_2.setText(String.format("%d", getIntFromByte1(value, 6)));
                mFTPIPAddress_3.setText(String.format("%d", getIntFromByte1(value, 7)));
                mFTPIPAddress_4.setText(String.format("%d", getIntFromByte1(value, 8)));
                mSocketServerIPAddress_1.setText(String.format("%d", getIntFromByte1(value, 9)));
                mSocketServerIPAddress_2.setText(String.format("%d", getIntFromByte1(value, 10)));
                mSocketServerIPAddress_3.setText(String.format("%d", getIntFromByte1(value, 11)));
                mSocketServerIPAddress_4.setText(String.format("%d", getIntFromByte1(value, 12)));
                break;
            case 2:
                mIPCameraEnable.setChecked(0 < value[1]);
                mIPCameraCaptureFreq.setText(String.format("%d", getIntFromByte2(value, 2)));
                mLogUploadFreq.setText(String.format("%d", getIntFromByte2(value, 4)));
                mLogUploadSize.setText(String.format("%d", getIntFromByte2(value, 6)));
                mFusionGuardEnable.setChecked(0 < getIntFromByte1(value, 8));
                break;
            case 3:
                int mode = getIntFromByte1(value, 1);
                if(0 == mode) mLeavingRadio.setChecked(true);
                else if(1 == mode) mStayRadio.setChecked(true);
                else if(2 == mode) mPatrolRadio.setChecked(true);
                break;
            case 5:
                mPatrolInterval.setText(String.format("%d", getIntFromByte2(value, 1)));
                mPatrolTempDiffThreshold.setText(getFloatFromByte2(value, 3));
                mPatrolObjectSize.setText(String.format("%d", getIntFromByte1(value, 5)));
                mPatrolCeilingHeight.setText(getFloatFromByte2(value, 6));
                break;
            case 6:
                mStayAlarmTime.setText(String.format("%d", getIntFromByte2(value, 1)));
                mStayTempDiffThreshold.setText(getFloatFromByte2(value, 3));
                mStayObjctSize.setText(String.format("%d", getIntFromByte1(value, 5)));
                mBathroomLength.setText(getFloatFromByte2(value, 6));
                mBathroomWidth.setText(getFloatFromByte2(value, 8));
                mBathroomHeight.setText(getFloatFromByte2(value, 10));
                break;
            case 7:
                mWardTempDiffThreshold.setText(getFloatFromByte2(value, 1));
                mWardObjctSize.setText(String.format("%d", getIntFromByte1(value, 3)));
                mBedLength.setText(getFloatFromByte2(value, 4));
                mBedWidth.setText(getFloatFromByte2(value, 6));
                mWardCeilingHeight.setText(getFloatFromByte2(value, 8));
                break;
        }
    }


    private void displayCurrentConfig_2(byte[] value) {
        if(null != value) {
            Log.d(TAG, "displayCurrentConfig_2");
            mIPCamAddress_1.setText(String.format("%d", value[1] & 0xff));
            mIPCamAddress_2.setText(String.format("%d", value[2] & 0xff));
            mIPCamAddress_3.setText(String.format("%d", value[3] & 0xff));
            mIPCamAddress_4.setText(String.format("%d", value[4] & 0xff));

            mFTPAddress_1.setText(String.format("%d", value[5] & 0xff));
            mFTPAddress_2.setText(String.format("%d", value[6] & 0xff));
            mFTPAddress_3.setText(String.format("%d", value[7] & 0xff));
            mFTPAddress_4.setText(String.format("%d", value[8] & 0xff));

            // mReset.setChecked(0 < (value[9] & 0xff));
            // mResetSettings.setChecked(0 < (value[10] & 0xff));
            // mDeleteLog.setChecked(0 < (value[11] & 0xff));
        }
    }

    private void displayCurrentConfig_1(byte[] value) {
        if (value != null) {
            Log.d(TAG, "displayCurrentConfig_1");

            String valueStr = String.valueOf(value[1] & 0xff) + "." + String.valueOf(value[2] & 0xff);
            mET_DiffTempThresh.setText(valueStr);
            valueStr = String.valueOf(value[3] & 0xff);
            mET_LabelingThresh.setText(valueStr);
            valueStr = String.valueOf(value[4] & 0xff);
            mET_FNMVFrame.setText(valueStr);

            int result = 0;
            result += 0xff00 & value[5] << 8;
            result += 0xff & value[6];
            valueStr = String.valueOf(result);
            mET_EdgeFrame.setText(valueStr);

            /*valueStr = String.valueOf(value[6] & 0xff) + "." + String.valueOf(value[7] & 0xff);
            mET_CheckSumMove.setText(valueStr);
            valueStr = String.valueOf(value[8] & 0xff) + "." + String.valueOf(value[9] & 0xff);
            mET_CheckDiffArea.setText(valueStr);*/
            valueStr = String.valueOf(value[7] & 0xff);
            mET_HumanThresh.setText(valueStr);
            /*valueStr = String.valueOf(value[11] & 0xff) + "." + String.valueOf(value[12] & 0xff);
            mET_QuiltMoveThresh.setText(valueStr);
            valueStr = String.valueOf(value[13] & 0xff) + "." + String.valueOf(value[14] & 0xff);
            mET_QuiltAreaThresh.setText(valueStr);*/
            valueStr = String.valueOf(value[8] & 0xff);
            mET_BedLeftBoardPoint.setText(valueStr);
            valueStr = String.valueOf(value[9] & 0xff);
            mET_BedRightBoardPoint.setText(valueStr);
            valueStr = String.valueOf(value[10] & 0xff);
            mET_LeftX.setText(valueStr);
            valueStr = String.valueOf(value[11] & 0xff);
            mET_RightX.setText(valueStr);
            valueStr = String.valueOf(value[12] & 0xff);
            mET_TopY.setText(valueStr);
            valueStr = String.valueOf(value[13] & 0xff);
            mET_BottomY.setText(valueStr);

        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        //mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public class InputFilterMinMax implements InputFilter {
        private int min, max;

        public InputFilterMinMax(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public InputFilterMinMax(String min, String max) {
            this.min = Integer.parseInt(min);
            this.max = Integer.parseInt(max);
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                int input = Integer.parseInt(dest.toString() + source.toString());
                if (isInRange(min, max, input))
                    return null;
            } catch (NumberFormatException nfe) { }
            return "";
        }

        private boolean isInRange(int a, int b, int c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
    }
}
