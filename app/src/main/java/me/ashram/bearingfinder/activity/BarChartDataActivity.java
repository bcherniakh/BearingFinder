package me.ashram.bearingfinder.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.ashram.bearingfinder.R;
import me.ashram.bearingfinder.activity.charttools.ScaledValueFormatter;
import me.ashram.bearingfinder.activity.charttools.ScaledYAxisValueFormatter;
import me.ashram.bearingfinder.activity.settings.SettingsActivity;
import me.ashram.bearingfinder.tools.Toaster;

public class BarChartDataActivity extends AppCompatActivity implements
        OnChartValueSelectedListener {
    //Public communication constants
    public static final String EXTRA_DEVICE_ADDRESS = "device_address_2343242";
    public static final int MESSAGE_NEW_PACKAGE = 1213124;
    public static final int MESSAGE_DEVICE_CONNECTED = 356323432;
    public static final int MESSAGE_CONNECTION_FAILED = 74234233;

    //Views
    private ActionBar mBarChartActionBar;
    private TextView mActionBarStatusValueTextView;
    private Switch mAutoScalingModeSwitch;

    //
    private static final String TAG = "finder_beacon_chart";
    private static final UUID EMB_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Handler mHandler;

    //Bluetooth objects
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;

    //Bluetooth connection and updating threads
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private ClearOldDataThread mClearOldDataThread;

    private Toaster mToaster;
    private String mDeviceAddress = "";

    //Chart members
    protected BarChart mChart;
    private Typeface mChartTypeFace;

    private List<String> mBeaconNames = new ArrayList<>();

    private Map<String, BarEntry> mChartData;
    private Map<String, Boolean> mDataFilter = new HashMap<>();
    private Map<String, Long> mDataUpdated = Collections.synchronizedMap(new HashMap<String, Long>());
    private volatile int mCorrectDelimiter = 0;
    private volatile int mCorrectBytes = 3;
    private volatile int mCorrectBearingFinderAddress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Making and enabling the custom layout for ActionBar to display current frequency and state
        mBarChartActionBar = getSupportActionBar();
        mBarChartActionBar.setDisplayShowHomeEnabled(false);
        mBarChartActionBar.setDisplayShowTitleEnabled(false);
        LayoutInflater mInflater = LayoutInflater.from(this);
        View mCustomView = mInflater.inflate(R.layout.action_br_bar_chart_data, null);
        mBarChartActionBar.setCustomView(mCustomView);
        mBarChartActionBar.setDisplayShowCustomEnabled(true);

        setContentView(R.layout.activity_bar_chart_data);

        mToaster = new Toaster(this);
        mHandler = new DataMessageHandler(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mActionBarStatusValueTextView = (TextView) findViewById(R.id.actionBarStatusValueTextView);
        mAutoScalingModeSwitch = (Switch) findViewById(R.id.actionBarAutoscalingSwitch);
        mAutoScalingModeSwitch.setChecked(true);
        mAutoScalingModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoScalingModeChanged();
            }
        });

        if (mBluetoothAdapter == null) {
            mToaster.showToast(getString(R.string.common_message_no_bluetooth));
            finish();
        }

        Intent incomingIntent = getIntent();
        mDeviceAddress = incomingIntent.getStringExtra(EXTRA_DEVICE_ADDRESS);

        //Registering receivers
        IntentFilter bluetoothStateChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        IntentFilter deviceDisconnectedFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mBluetoothDevicesReceiver, bluetoothStateChangedFilter);
        registerReceiver(mBluetoothDevicesReceiver, deviceDisconnectedFilter);

        //Creating and setiing up chart view
        mChart = (BarChart) findViewById(R.id.chart1);
        mChartTypeFace = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");
        setupBarChart();

        startBluetoothCommunication(0);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        //verridePendingTransition(R.anim.move_left_in_activity, R.anim.move_right_out_activity);
    }


    @Override
    public void onResume() {
        super.onResume();
        readPreferences();
        initDataSet();
//      startBluetoothCommunication(0);
        if (mClearOldDataThread != null) {
            mClearOldDataThread.cancel();
            mClearOldDataThread = null;
        }

        mClearOldDataThread = new ClearOldDataThread();
        mClearOldDataThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        //Stop all communication threads
        //stopBluetoothCommunication();
        if (mClearOldDataThread != null) {
            mClearOldDataThread.cancel();
            mClearOldDataThread = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast listeners
        stopBluetoothCommunication();
        unregisterReceiver(mBluetoothDevicesReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bar_chart, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.bar_chart_action_refresh:
                Iterator it = mChartData.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    BarEntry data = (BarEntry) pair.getValue();
                    data.setVal(0f);
                }
                mChart.notifyDataSetChanged();
                mChart.invalidate();
                return true;

            case R.id.bar_chart_action_reconnect:
                startBluetoothCommunication(1000);
                return true;

            case R.id.bar_chart_action_settings:
                Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private void startBluetoothCommunication(int timeout) {
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);

        if (mBluetoothDevice != null) {
            stopBluetoothCommunication();
            // Start the thread to connect with the given device
            mConnectThread = new ConnectThread(mBluetoothDevice, timeout);
            mConnectThread.start();
        } else {
            mToaster.showToast(getString(R.string.chart_data_message_no_device));
            finish();
        }
    }

    private void stopBluetoothCommunication() {
        Log.d(TAG, "Stopping all communications");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    private void setupBarChart() {
        mChart.setDrawBarShadow(false);
        mChart.setDrawValueAboveBar(true);

        mChart.setDescription("");

        //mChart.setBackgroundColor(Color.rgb(115, 125, 113));
        // if more than 60 entries are displayed in the chart, no values will be
        // drawn
        mChart.setMaxVisibleValueCount(60);

        // no scaling. No scaling. Period! It causes nothing but a hadeache.
        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mChart.setScaleEnabled(false);
        mChart.setTouchEnabled(false);
        mChart.setClickable(false);
        mChart.setHighlightPerTapEnabled(false);
        //mChart.setScaleMinima(1f, 1f);
        // mChart.setDrawYLabels(false);

        //Auto min-max is enabled by default.
        mChart.setAutoScaleMinMaxEnabled(true);
        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTypeface(mChartTypeFace);
        xAxis.setDrawGridLines(true);
        xAxis.setSpaceBetweenLabels(2);
        xAxis.setDrawLabels(true);

        YAxisValueFormatter custom = new ScaledYAxisValueFormatter();

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTypeface(mChartTypeFace);
        leftAxis.setLabelCount(8, false);
        leftAxis.setValueFormatter(custom);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setSpaceTop(5f);
        leftAxis.setStartAtZero(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setDrawGridLines(false);
        rightAxis.setTypeface(mChartTypeFace);
        rightAxis.setLabelCount(8, false);
        rightAxis.setValueFormatter(custom);
        rightAxis.setSpaceTop(5f);
        leftAxis.setStartAtZero(true);
        leftAxis.setDrawLabels(true);

        Legend l = mChart.getLegend();
        l.setPosition(Legend.LegendPosition.BELOW_CHART_LEFT);
        l.setForm(Legend.LegendForm.SQUARE);
        l.setFormSize(9f);
        l.setTextSize(12f);
        l.setXEntrySpace(4f);
    }

    private void clearChartData() {
        List<BarEntry> chartEntries = new ArrayList<BarEntry>(mChartData.values());
        for (BarEntry entry : chartEntries) {
            entry.setVal(0f);
        }
        mChart.invalidate();
        mChart.notifyDataSetChanged();
    }

    //Changing the auto min-max
    private void autoScalingModeChanged() {
        if (mAutoScalingModeSwitch.isChecked()) {
            mChart.setAutoScaleMinMaxEnabled(true);
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.resetAxisMaxValue();
            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.resetAxisMaxValue();
            mChart.invalidate();
            mChart.notifyDataSetChanged();
        } else {
            mChart.setAutoScaleMinMaxEnabled(false);
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setAxisMaxValue(270f);
            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setAxisMaxValue(270f);
            mChart.invalidate();
            mChart.notifyDataSetChanged();
        }
    }

    private void readPreferences() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String bearingFinderAddress = settings.getString(SettingsActivity.getBearingFinderAddressKey(), "");
        try {
            mCorrectBearingFinderAddress = Integer.parseInt(bearingFinderAddress, 16);
        } catch (NumberFormatException numberFormatException) {
            mToaster.showToast(getString(R.string.chart_data_message_error_invalid_bearing_address));
            finish();
        }

        String correctDelimiter = settings.getString(SettingsActivity.getCorrectDelimiterKey(), "");
        try {
            mCorrectDelimiter = Integer.parseInt(correctDelimiter, 16);
        } catch (NumberFormatException numberFormatException) {
            mToaster.showToast(getString(R.string.chart_data_message_error_invalid_delimiter));
            finish();
        }

        mBeaconNames.clear();
        mDataFilter.clear();
        mDataUpdated.clear();
        for (int i = 1; i <= 12; i++) {
            String beaconAddress = settings.getString(SettingsActivity.getBeaconAddressKey(i), "ERROR");
            if (!("ERROR".equals(beaconAddress))) {
                mBeaconNames.add(beaconAddress);
                mDataFilter.put(beaconAddress, settings.getBoolean(SettingsActivity.getUseBeaconKey(i), false));
                mDataUpdated.put(beaconAddress, System.currentTimeMillis());
            }
        }
    }

    private void initDataSet() {
        mChartData = new HashMap<>();

        int entryIndex = 0;
        for (String beaconName : mBeaconNames) {
            mChartData.put(beaconName, new BarEntry(0f, entryIndex));
            entryIndex++;
        }

        BarDataSet dataSet = new BarDataSet(new ArrayList<>(mChartData.values()), "Beacon power");
        dataSet.setColor(getResources().getColor(R.color.colorDataSet));
        dataSet.setBarSpacePercent(20f);

        ArrayList<BarDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);
        dataSet.setValueFormatter(new ScaledValueFormatter());

        BarData data = new BarData(mBeaconNames, dataSets);
        data.setValueTextSize(10f);
        data.setValueTypeface(mChartTypeFace);
        mChart.setData(data);
        mChart.notifyDataSetChanged();
        mChart.invalidate();
    }

    //@SuppressLint("NewApi")
    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {

        //if (e == null)
        //    return;

        RectF bounds = mChart.getBarBounds((BarEntry) e);
        PointF position = mChart.getPosition(e, YAxis.AxisDependency.LEFT);

        Log.d(TAG, bounds.toString());
        Log.d(TAG, position.toString());

        Log.d(TAG, "low: " + mChart.getLowestVisibleXIndex() + ", high: "
                + mChart.getHighestVisibleXIndex());
    }

    public void onNothingSelected() {
    }

    private void manageConnectedSocket(BluetoothSocket mmSocket) {
        Log.d(TAG, "Device connection succeeds");
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    private void updateViewWhenConnected() {
        mActionBarStatusValueTextView.setText(getString(R.string.action_bar_chart_status_value_connected));
        mActionBarStatusValueTextView.setTextColor(getResources().getColor(R.color.colorConnected));
    }

    private void updateViewWhenDisconnected() {
        mActionBarStatusValueTextView.setText(getString(R.string.action_bar_chart_status_value_disconnected));
        mActionBarStatusValueTextView.setTextColor(getResources().getColor(R.color.colorDisconnected));
        stopBluetoothCommunication();
        clearChartData();
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_NEW_PACKAGE:
                int address = msg.arg1;
                int rssi = msg.arg2;

                String addressString = Integer.toHexString(address).toUpperCase();
                BarEntry entry = mChartData.get(addressString);
                if (entry != null) {
                    if (mDataFilter.get(addressString)) {
                        mDataUpdated.put(addressString, System.currentTimeMillis());
                        boolean valueChanged = (float) rssi != entry.getVal();
                        if (valueChanged) {
                            //mdataSet.removeEntry(entry);
                            entry.setVal(rssi);
                            //mdataSet.addEntry(entry);
                            mChart.notifyDataSetChanged();
                            mChart.invalidate();
                        }
                    }
                }
                break;
            case MESSAGE_DEVICE_CONNECTED:
                updateViewWhenConnected();
                break;
            case MESSAGE_CONNECTION_FAILED:
                if (mConnectThread != null) {
                    mConnectThread.interrupt();
                }
                mToaster.showToast(getString(R.string.chart_data_message_error_bluetooth_connection_failed));
                break;
        }
    }

    private final BroadcastReceiver mBluetoothDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    mToaster.showToast(getString(R.string.main_message_bt_shut_down));
                    finish();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                updateViewWhenDisconnected();
            }
        }
    };

    private static class DataMessageHandler extends Handler {
        private final WeakReference<BarChartDataActivity> mCurrentActivity;

        DataMessageHandler(BarChartDataActivity currentActivity) {
            mCurrentActivity = new WeakReference<>(currentActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            BarChartDataActivity currentActivity = mCurrentActivity.get();
            if (currentActivity != null) {
                currentActivity.handleMessage(msg);
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private int startTimeout;
        private boolean successfullyConnected;

        public ConnectThread(BluetoothDevice device, int startTimeout) {
            mmDevice = device;
            this.startTimeout = startTimeout;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            if (startTimeout > 0) {
                makeTimeout(startTimeout);
            }
//The next code os the trickiest way to obtain the damned BT socket. It may be used some day
//            BluetoothSocket tmp = null;
//            try {
//                Class class1 = mmDevice.getClass();
//                Class aclass[] = new Class[1];
//                aclass[0] = Integer.TYPE;
//                Method method = class1.getMethod("createRfcommSocket", aclass);
//                Object aobj[] = new Object[1];
//                aobj[0] = Integer.valueOf(1);
//                tmp = (BluetoothSocket) method.invoke(mmDevice, aobj);
//            } catch (NoSuchMethodException e) {
//                e.printStackTrace();
//                Log.d(TAG, "createRfcommSocket() failed", e);
//            } catch (InvocationTargetException e) {
//                e.printStackTrace();
//                Log.d(TAG, "createRfcommSocket() failed", e);
//            } catch (IllegalAccessException e) {
//                e.printStackTrace();
//                Log.d(TAG, "createRfcommSocket() failed", e);
//            }
            Log.d(TAG, "Trying to establish the connection");
            mmSocket = getSocketByUUID();
            if (connectBluetoothSocket()) {
                Log.d(TAG, "First the documentation way");
                // Do work to manage the connection (in a separate thread)
                manageConnectedSocket(mmSocket);
                mHandler.obtainMessage(MESSAGE_DEVICE_CONNECTED).sendToTarget();
            } else {
                Log.d(TAG, "Normal connection failed, trying the tricky way");
                //Trying the other way
                makeTimeout(100);
                mmSocket = getSocketTricky();
                if (connectBluetoothSocket()) {
                    manageConnectedSocket(mmSocket);
                    mHandler.obtainMessage(MESSAGE_DEVICE_CONNECTED).sendToTarget();
                } else {
                    Log.d(TAG, "The tricky way failed");
                    mHandler.obtainMessage(MESSAGE_CONNECTION_FAILED).sendToTarget();
                    return;
                }
            }
        }

        private boolean connectBluetoothSocket() {
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                return true;
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.d(TAG, "An error occurred while tried to connect " + connectException.getMessage());
                try {
                    if (mmSocket != null) {
                        mmSocket.close();
                    }
                } catch (IOException closeException) {
                    Log.d(TAG, "An error occurred while tried to close connection " + connectException.getMessage());
                }
                return false;
            }
        }

        private void makeTimeout(int millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Log.e(TAG, "Something gone wrong while sleeping in connection thread", e);
            }
        }

        /* this method of getting the socket if from android documentation. Works rarely, but on some
        devices it is the only way to establish connection
        * */
        private BluetoothSocket getSocketByUUID() {
            BluetoothSocket bluetoothSocket = null;
            try {
                bluetoothSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(EMB_UUID);
            } catch (IOException e) {
                Log.d(TAG, "Exception while trying to creatr fucking socket");
            }
            return bluetoothSocket;
        }

        /* This tricky method with reflection that works on almost all the devices.
        * But not all literally.
        * */
        private BluetoothSocket getSocketTricky() {
            BluetoothSocket bluetoothSocket = null;
            try {
                Method m = mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) m.invoke(mmDevice, Integer.valueOf(1));
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "createRfcommSocket() failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "createRfcommSocket() failed", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "createRfcommSocket() failed", e);
            }
            return bluetoothSocket;
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
                Log.d(TAG, "Stopping connection process");
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs
            int delimiter;
            int size;
            int directionFinderAddress;
            int beaconAddress;
            int rssi;
            int correctDelimiter = mCorrectDelimiter;
            int correctBytes = mCorrectBytes;
            while (true) {
                try {
                    // Read from the InputStream
                    delimiter = mmInStream.read();
                    if (delimiter == correctDelimiter) {
                        size = mmInStream.read();
                        directionFinderAddress = mmInStream.read();
                        beaconAddress = mmInStream.read();
                        rssi = mmInStream.read();
                        if (size == correctBytes) {
                            mHandler.obtainMessage(MESSAGE_NEW_PACKAGE, beaconAddress & 0xFF, rssi & 0xFF).sendToTarget();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error while reading stream");
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            Log.d(TAG, "Stopping transmission process");
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private class ClearOldDataThread extends Thread {
        private volatile boolean continueRunning = true;

        @Override
        public void run() {
            Log.d(TAG, "Clearer started to operate");
            while (continueRunning) {
                Set<String> beaconNames = mDataUpdated.keySet();
                for (String beaconName : beaconNames) {
                    long updated = mDataUpdated.get(beaconName);
                    if ((System.currentTimeMillis() - updated) > 300) {
                        int address = Integer.parseInt(beaconName, 16);
                        mHandler.obtainMessage(MESSAGE_NEW_PACKAGE, address, 0).sendToTarget();
                        //Log.d(TAG, "Clearing value: " + beaconName + " address" + address);
                    }
                    timeout(100);
                }

            }
            Log.d(TAG, "Clearer stoped to operate");
        }

        private void timeout(int millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Log.e(TAG, "Something gone wrong while sleeping in connection thread", e);
            }
        }

        public void cancel() {
            continueRunning = false;
        }
    }
}