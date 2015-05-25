package net.theneophyte.sunstone.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;


public class MainActivity extends Activity implements BluetoothLeUart.Callback, TimePickerDialog.OnTimeSetListener {

    private Button mSyncTime;
    private Button mSetAlarm;
    private SeekBar mBrightness;

    private BluetoothLeUart mUart;
    private TimePickerFragment mAlarmPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUart = new BluetoothLeUart(getApplicationContext());

        mSyncTime = (Button) findViewById(R.id.sync_time_button);
        mSyncTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                syncTime();
            }
        });

        mSyncTime.setClickable(false);
        mSyncTime.setEnabled(false);

        mAlarmPicker = new TimePickerFragment();
        mAlarmPicker.setListener(this);

        mSetAlarm = (Button) findViewById(R.id.set_alarm_button);
        mSetAlarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mAlarmPicker.show(getFragmentManager(), "AlarmPicker");
            }
        });

        mSetAlarm.setClickable(false);
        mSetAlarm.setEnabled(false);

        mBrightness = (SeekBar) findViewById(R.id.brightness_bar);
        mBrightness.setMax(0xFF);
        mBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser){
                if (fromUser){
                    setBrightness(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar){

            }

            public void onStopTrackingTouch(SeekBar seekbar){

            }
        });

        mUart.connectFirstAvailable();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUart.registerCallback(this);
//        mUart.connectFirstAvailable();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mUart.unregisterCallback(this);
        mUart.disconnect();
    }

    @Override
    public void onConnected(BluetoothLeUart uart) {
        Log.i("BleUart", "Device connected.");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Clock connected!", Toast.LENGTH_SHORT).show();
                mSyncTime = (Button) findViewById(R.id.sync_time_button);
                mSyncTime.setClickable(true);
                mSyncTime.setEnabled(true);
                mSetAlarm = (Button) findViewById(R.id.set_alarm_button);
                mSetAlarm.setClickable(true);
                mSetAlarm.setEnabled(true);
            }
        });
    }

    @Override
    public void onConnectFailed(BluetoothLeUart uart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Clock connection failed!", Toast.LENGTH_SHORT).show();
                mSyncTime = (Button) findViewById(R.id.sync_time_button);
                mSyncTime.setClickable(false);
                mSyncTime.setEnabled(false);
                mSetAlarm = (Button) findViewById(R.id.set_alarm_button);
                mSetAlarm.setClickable(false);
                mSetAlarm.setEnabled(false);
            }
        });
    }

    @Override
    public void onDisconnected(BluetoothLeUart uart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Clock disconnected", Toast.LENGTH_SHORT).show();
                mSyncTime = (Button) findViewById(R.id.sync_time_button);
                mSyncTime.setClickable(false);
                mSyncTime.setEnabled(false);
                mSetAlarm.setClickable(false);
                mSetAlarm.setEnabled(false);
            }
        });
    }

    @Override
    public void onReceive(BluetoothLeUart uart, BluetoothGattCharacteristic rx) {

    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        Log.i("BleUart", "Device Found: " + device.getAddress());
    }

    @Override
    public void onDeviceInfoAvailable() {

    }

    private final void setBrightness(int level){
        mUart.send(String.format("B%03d", level % 0xFF));
    }

    private final void syncTime() {
        final int hours, minutes, seconds;
        final Calendar cal = Calendar.getInstance();
        hours = cal.get(Calendar.HOUR_OF_DAY);
        minutes = cal.get(Calendar.MINUTE);
        seconds = cal.get(Calendar.SECOND);
        Toast.makeText(getApplicationContext(), String.format("Time set to %02d:%02d:%02d.", hours, minutes, seconds), Toast.LENGTH_SHORT).show();
        mUart.send(String.format("S%02d%02d%02d", hours, minutes, seconds));
    }

    private final void setAlarm(int hours, int minutes) {
        Toast.makeText(getApplicationContext(), String.format("Alarm set to %02d:%02d:00.", hours, minutes), Toast.LENGTH_SHORT).show();
        mUart.send(String.format("A%02d%02d00", hours, minutes));
    }

    public static class TimePickerFragment extends DialogFragment {

        private TimePickerDialog.OnTimeSetListener mListener;

        public void setListener(TimePickerDialog.OnTimeSetListener listener) {
            mListener = listener;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();
            final int hour = c.get(Calendar.HOUR_OF_DAY);
            final int minute = c.get(Calendar.MINUTE);

            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), mListener, hour, minute, DateFormat.is24HourFormat(getActivity()));
        }
    }


    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        setAlarm(hourOfDay, minute);
    }
}
