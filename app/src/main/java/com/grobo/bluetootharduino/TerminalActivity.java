package com.grobo.bluetootharduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

import static com.grobo.bluetootharduino.DeviceList.EXTRA_ADDRESS;

public class TerminalActivity extends AppCompatActivity implements SerialListener {

    private enum Connected {False, Pending, True}

    private final int REQ_CODE_SPEECH_INPUT = 100;
    public static final String EXTRA_LOG = "stored_log";
    private static final String TTS_SPEAK_ID = "SPEAK";

    private String deviceAddress;
    private String newline = "#";

    private TextView receiveText;

    private SerialSocket socket;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private SharedPreferences prefs;
    TextToSpeech tts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(EXTRA_ADDRESS)) {
            deviceAddress = getIntent().getExtras().getString(EXTRA_ADDRESS);
        } else {
            deviceAddress = prefs.getString(EXTRA_ADDRESS, "");

            if (deviceAddress.equals("")) {
                goToDeviceList();
            }
        }

        receiveText = findViewById(R.id.receive_text);
        receiveText.setTextColor(Color.parseColor("#4444cc"));
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receiveText.setText(prefs.getString(EXTRA_LOG, ""));
            }
        });

        final TextView sendText = findViewById(R.id.send_text);

        ImageButton sendBtn = findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!sendText.getText().toString().isEmpty()) {
                    send(sendText.getText().toString());
                    sendText.setText("");
                }
            }
        });

        ImageButton micBtn = findViewById(R.id.mic_btn);
        micBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {

                    int result = tts.setLanguage(new Locale("en-in"));

                    if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else if (result == TextToSpeech.LANG_MISSING_DATA) {
                        Log.e("TTS", "This Language is missing data");
                    }
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(0.8f);

                } else {
                    Log.e("TTS", "Initialization Failed!");
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
        } else if (!adapter.isEnabled()) {
            Intent bluetoothOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(bluetoothOn, 1);
        }

        if (initialStart || connected == Connected.False) {
            initialStart = false;
            connect();
        }


    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            status(deviceName + " Connecting...");
            connected = Connected.Pending;
            socket = new SerialSocket();
            socket.connect(this, this, device);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void send(final String str) {
        if (connected != Connected.True) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SpannableStringBuilder spn = new SpannableStringBuilder("YOU: " + str + '\n');
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.holo_blue_dark)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spn.setSpan(new StyleSpan(Typeface.BOLD), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    receiveText.append(spn);
                }
            });

            byte[] data = (str.toLowerCase() + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SpannableStringBuilder spn = new SpannableStringBuilder(new String(data));
                spn.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.holo_green_dark)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
                tts.speak(new String(data), TextToSpeech.QUEUE_FLUSH, null, TTS_SPEAK_ID);
            }
        });
    }

    private void status(final String str) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
                spn.setSpan(new ForegroundColorSpan(Color.parseColor("#FFDB58")), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
            }
        });
    }

    @Override
    public void onSerialConnect() {
        status("Connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("Connection failed: " + e.getMessage());
        disconnect();
        Snackbar.make(findViewById(R.id.parent_activity_terminal), "Connection failed", Snackbar.LENGTH_LONG)
                .setAction("retry", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        connect();
                    }
                })
                .show();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("Connection lost: " + e.getMessage());
        disconnect();
        connect();
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

        if (requestCode == REQ_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && null != data) {
                ArrayList<String> result = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                send(result.get(0).toLowerCase());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
        prefs.edit().putString(EXTRA_LOG, receiveText.getText().toString() + "\n").apply();
    }

    private void disconnect() {
        if (connected != Connected.False) {
            connected = Connected.False;
            socket.disconnect();
            socket = null;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    receiveText.append("Disconnected\n");
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.terminal_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_clear_log:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Clear ?");
                builder.setMessage("This will clear your logs");
                builder.setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        receiveText.setText("");
                        prefs.edit().putString(EXTRA_LOG, "").apply();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                break;

            case R.id.action_device_list:
                goToDeviceList();
                break;
        }
        return true;
    }

    private void goToDeviceList() {
        disconnect();
        startActivity(new Intent(this, DeviceList.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit ?");
        builder.setMessage("This will end your connection");
        builder.setPositiveButton("Exit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TerminalActivity.super.onBackPressed();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
