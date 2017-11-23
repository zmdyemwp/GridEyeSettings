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
import android.widget.SimpleExpandableListAdapter;
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
import java.lang.Runnable;

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
                    if(0x01 == value[0]) {
                        displayCurrentConfig_1(value);
                    } else if(0x02 == value[0]) {
                        displayCurrentConfig_2(value);
                    }
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
            // 整數
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
            // 整數
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
            // 整數
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
            // 整數
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
            // 整數
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
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_configuration);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
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

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

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
                mConnectionState.setText(resourceId);
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

                try {Thread.sleep(500);} catch(Exception e) {Log.e("MIN", e.toString());}

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
            // 整數
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
            // 整數
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
            // 整數
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
            // 整數
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
            // 整數
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
                if(1 == requiredConfig) {
                    cmd[0] = 0x01;
                } else if(2 == requiredConfig) {
                    cmd[0] = 0x02;
                } else {
                    return;
                }
                mBluetoothLeService.setSendByteArr(cmd);
                mBluetoothLeService.writeCharacteristic(characteristic);
            } else {
                Log.d(TAG, "The target characteristic has no write property !");
                Toast.makeText(this, "The target characteristic has no write property !", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readCurrentConfig() {
        Log.d(TAG, "readCurrentConfig()");
        final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
        if(characteristic != null) {
            Log.d(TAG, "get target characteristic successfully !");
            Log.d(TAG, "service UUID = " + characteristic.getService().getUuid());
            Log.d(TAG, "characteristic UUID = " + characteristic.getUuid());
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                Log.d(TAG, "readCharacteristic ---");
                askDevConfig(characteristic, 1);
                try {Thread.sleep(500);} catch(Exception e) {Log.e("MIN", e.toString());}
                mBluetoothLeService.readCharacteristic(characteristic);

                try {Thread.sleep(1000);} catch(Exception e) {Log.e("MIN", e.toString());}

                askDevConfig(characteristic, 2);
                try {Thread.sleep(500);} catch(Exception e) {Log.e("MIN", e.toString());}
                mBluetoothLeService.readCharacteristic(characteristic);
                // h.postDelayed(r, 500);
            } else {
                Log.d(TAG, "The target characteristic has no read property !");
                Toast.makeText(this, "The target characteristic has no read property !", Toast.LENGTH_SHORT).show();
            }
        }
    }

    Handler h = new Handler();
    Runnable r = new Runnable() {
        public void run() {
            final BluetoothGattCharacteristic characteristic = mBluetoothLeService.getDeviceCharacteristic();
            if(characteristic != null) {
                mBluetoothLeService.readCharacteristic(characteristic);
            }
        }
    };


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
