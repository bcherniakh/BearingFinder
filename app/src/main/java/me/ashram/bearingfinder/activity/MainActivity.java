package me.ashram.bearingfinder.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;

import me.ashram.bearingfinder.R;
import me.ashram.bearingfinder.activity.settings.SettingsActivity;
import me.ashram.bearingfinder.tools.Toaster;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "finder_main";
    private final static int REQUEST_ENABLE_BT = 1;

    private ListView mPairedDevicesListView;

    private ArrayAdapter<String> mPairedDevicesListAdapter;

    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> mPairedDevices;

    private Toaster mToaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Reading default preferences ones after new installation
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        //Setting up the list view for paired devices list
        mPairedDevicesListView = (ListView) findViewById(R.id.mainActivityPairedDevicesListView);
        mPairedDevicesListAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        mPairedDevicesListView.setAdapter(mPairedDevicesListAdapter);
        mPairedDevicesListView.setOnItemClickListener(mDeviceClickListener);

        //creating a new toaster
        mToaster = new Toaster(this);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "bluetooth not supported");
            finish();
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //Registering receivers
        IntentFilter newBluetoothDeviceFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBluetoothDevicesReceiver, newBluetoothDeviceFoundFilter);
        IntentFilter bluetoothStateChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothDevicesReceiver, bluetoothStateChangedFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPairedDevices();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }
        //Unregister receivers
        unregisterReceiver(mBluetoothDevicesReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.main_menu_action_settings:
                Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);
                return true;

            case R.id.main_menu_action_exit:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private void initPairedDevices() {
        mPairedDevices = mBluetoothAdapter.getBondedDevices();

        mPairedDevicesListAdapter.clear();
        if (mPairedDevices.size() > 0) {
            for (BluetoothDevice device : mPairedDevices) {
                mPairedDevicesListAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    }

    public void openBluetoothSettingsClicked(View v) {
        startActivityForResult(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 0);
    }

    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                initPairedDevices();
                mToaster.showToast(getString(R.string.main_message_bt_ok));
            } else {
                mToaster.showToast(getString(R.string.main_message_bt_cancelled));
                finish();
            }
        }
    }

    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBluetoothAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent startChartActivity = new Intent(MainActivity.this, BarChartDataActivity.class);
            startChartActivity.putExtra(BarChartDataActivity.EXTRA_DEVICE_ADDRESS, address);
            startActivity(startChartActivity);
        }
    };

    private final BroadcastReceiver mBluetoothDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
// Old functional for finding the devices. Don't need it anymore.
//            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                mPairedDevicesListAdapter.add(device.getName() + "\n" + device.getAddress());
//            } else
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    mToaster.showToast(getString(R.string.main_message_bt_shut_down));
                    finish();
                }
            }
        }
    };
}
