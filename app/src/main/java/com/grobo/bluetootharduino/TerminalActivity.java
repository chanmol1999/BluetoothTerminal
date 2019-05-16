package com.grobo.bluetootharduino;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

import static com.grobo.bluetootharduino.DeviceList.EXTRA_ADDRESS;

public class TerminalActivity extends AppCompatActivity implements  SerialListener {

    private enum Connected { False, Pending, True }
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private String deviceAddress;
    private String newline = "\r\n";

    private TextView receiveText;

    private SerialSocket socket;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (getIntent().getExtras() != null) {
            deviceAddress = getIntent().getExtras().getString(EXTRA_ADDRESS);
        } else {
            deviceAddress = prefs.getString(EXTRA_ADDRESS, "");

            if (deviceAddress.equals("")) {
                goToDeviceList();
            }
        }

        receiveText = findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(Color.parseColor("#4444cc")); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        final TextView sendText = findViewById(R.id.send_text);

        ImageButton sendBtn = findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(sendText.getText().toString());
                sendText.setText("");
            }
        });

        ImageButton micBtn = findViewById(R.id.mic_btn);
        micBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        ImageButton devices = findViewById(R.id.device_list_btn);
        devices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToDeviceList();
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart) {
            initialStart = false;
            connect();
        }
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            status("connecting...");
            connected = Connected.Pending;
            socket = new SerialSocket();
            socket.connect(this, this, device);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder("YOU: " + str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.holo_blue_dark)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            byte[] data = (str + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder(new String(data));
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.holo_green_dark)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(Color.parseColor("#FFDB58")), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }



    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Something");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), "Speech not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    checkSpeechInput(result.get(0));
                }
                break;
            }
        }
    }

    private void checkSpeechInput(String input) {
        SpannableStringBuilder spn = new SpannableStringBuilder("YOU: " + input + "\n");
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.holo_blue_dark)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);

        if (input.toLowerCase().equals("turn on")){
            send("1");
        } else if (input.toLowerCase().equals("turn off")){
            send("0");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    private void disconnect() {
        if (connected != Connected.False) {
            connected = Connected.False;
            socket.disconnect();
            socket = null;
        }
    }

    private void goToDeviceList() {
        disconnect();
        startActivity(new Intent(this, DeviceList.class));
        finish();
    }
}
