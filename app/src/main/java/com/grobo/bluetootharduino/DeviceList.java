package com.grobo.bluetootharduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class DeviceList extends AppCompatActivity {

    ListView deviceList;

    private BluetoothAdapter bluetoothAdapter = null;
    private ArrayList<BluetoothDevice> pairedDevices;
    private ArrayAdapter<BluetoothDevice> listAdapter;

    public static String EXTRA_ADDRESS = "device_address";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        deviceList = findViewById(R.id.listView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        pairedDevices = new ArrayList<>();


        listAdapter = new ArrayAdapter<BluetoothDevice>(this, 0, pairedDevices) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                BluetoothDevice device = pairedDevices.get(position);
                if (view == null)
                    view = getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                text1.setText(device.getName());
                text2.setText(device.getAddress());
                return view;
            }
        };

        deviceList.setAdapter(listAdapter);
        deviceList.setOnItemClickListener(itemClickListener);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (bluetoothAdapter == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent bluetoothOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(bluetoothOn, 1);
        }
        refresh();
    }

    void refresh() {
        pairedDevices.clear();
        if (bluetoothAdapter != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices())
                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE)
                    pairedDevices.add(device);
        }
        listAdapter.notifyDataSetChanged();
    }


    private AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            String address = ((TextView) view.findViewById(R.id.text2)).getText().toString();

            Log.e("bluetooth", address);

            PreferenceManager.getDefaultSharedPreferences(DeviceList.this).edit().putString(EXTRA_ADDRESS, address).apply();
            Intent i = new Intent(DeviceList.this, TerminalActivity.class);
            i.putExtra(EXTRA_ADDRESS, address);
            startActivity(i);
            finish();
        }
    };

}
