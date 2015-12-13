package net.theneophyte.sunstone.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TimePicker;
import android.widget.Toast;


public class MainActivity extends Activity implements BleSunstone.Callback, TimePickerDialog.OnTimeSetListener {

    private final static String TAG = "Sunstone";
    private final static long WRITE_TIMEOUT_MILLIS = 200;

    private SeekBar mRedSeekBar,
            mGreenSeekBar,
            mBlueSeekBar,
            mWarmSeekBar,
            mCoolSeekBar,
            mWhiteSeekBar,
            mColorSeekBar;

    private Button mColorDemoButton, mSunriseDemoButton;
    private Handler mHandler;

    private BleSunstone mSunstone;

    private int white, color, warm, cool, red, green, blue;

    private volatile boolean colorDemo = false, redFlag = false, greenFlag = false, blueFlag = false;

    private ColorDemoTask mColorDemoTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mHandler = new Handler(Looper.getMainLooper());
        mSunstone = new BleSunstone(getApplicationContext(), mHandler, this);
        mColorDemoTask = new ColorDemoTask();

        mColorDemoButton = (Button) findViewById(R.id.color_demo_button);
        mColorDemoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!colorDemo) {
                    red = mSunstone.getRedValue();
                    green = mSunstone.getGreenValue();
                    blue = mSunstone.getBlueValue();

                    mRedSeekBar.setEnabled(false);
                    mGreenSeekBar.setEnabled(false);
                    mBlueSeekBar.setEnabled(false);

                    if (mSunriseDemoButton != null) {
                        mSunriseDemoButton.setEnabled(false);
                    }

                    colorDemo = true;

                    if (mColorDemoTask.getStatus() == AsyncTask.Status.FINISHED)
                    {
                        mColorDemoTask = new ColorDemoTask();
                    }
                    mColorDemoTask.execute();

                } else {
                    mColorDemoTask.cancel(true);

                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... params) {

                            colorDemo = false;

                            setRgb(red, green, blue, 0);

                            return null;
                        }
                    }.execute();

                    if (mSunriseDemoButton != null) {
                        mSunriseDemoButton.setEnabled(true);
                    }
                }
            }
        });

        mColorDemoButton.setEnabled(true);

        mBlueSeekBar = (SeekBar) findViewById(R.id.blue_bar);
        mBlueSeekBar.setMax(0xFF);
        mBlueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setBlue(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mBlueSeekBar.setEnabled(false);

        mRedSeekBar = (SeekBar) findViewById(R.id.red_bar);
        mRedSeekBar.setMax(0xFF);
        mRedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setRed(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mRedSeekBar.setEnabled(false);

        mGreenSeekBar = (SeekBar) findViewById(R.id.green_bar);
        mGreenSeekBar.setMax(0xFF);
        mGreenSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setGreen(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mGreenSeekBar.setEnabled(false);

        mWarmSeekBar = (SeekBar) findViewById(R.id.warm_bar);
        mWarmSeekBar.setMax(0xFF);
        mWarmSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setWarm(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mWarmSeekBar.setEnabled(false);

        mCoolSeekBar = (SeekBar) findViewById(R.id.cool_bar);
        mCoolSeekBar.setMax(0xFF);
        mCoolSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setCool(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mCoolSeekBar.setEnabled(false);

        mWhiteSeekBar = (SeekBar) findViewById(R.id.white_bar);
        mWhiteSeekBar.setMax(0xFF);
        mWhiteSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setWhiteBrightness(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mWhiteSeekBar.setEnabled(false);

        mColorSeekBar = (SeekBar) findViewById(R.id.color_bar);
        mColorSeekBar.setMax(0xFF);
        mColorSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                if (fromUser) {
                    setColorBrightness(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekbar) {

            }

            public void onStopTrackingTouch(SeekBar seekbar) {

            }
        });
        mColorSeekBar.setEnabled(false);

//        mColorSeekBar = setupSeekbar(R.id.color_bar, "setColorBrightness", this);

        mSunstone.connect();
    }

//    private SeekBar setupSeekbar(int id, final String progChangeFunc, final Object caller){
//        final Method progChangedCallback;
//
//        try {
//            progChangedCallback = caller.getClass().getMethod(progChangeFunc, Integer.class);
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//            return null;
//        }
//
//        final SeekBar seekBar = (SeekBar) findViewById(id);
//        seekBar.setMax(0xFF);
//        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
//                if (fromUser) {
//                    try {
//                        if (mSunstone != null)
//                            progChangedCallback.invoke(caller, progress);
//
//                    } catch (IllegalAccessException iaex){
//                        Log.d(TAG, "Access Denied: " + progChangedCallback.toGenericString(), iaex);
//                    } catch (InvocationTargetException itx){
//                        Log.d(TAG, "Invoked method threw an exception: " + progChangedCallback.toGenericString(), itx);
//                    }
//                }
//            }
//
//            public void onStartTrackingTouch(SeekBar seekbar) {
//
//            }
//
//            public void onStopTrackingTouch(SeekBar seekbar) {
//
//            }
//        });
//        seekBar.setEnabled(false);
//
//        return seekBar;
//    }

    private void setRed(int value){
        mSunstone.setRedValue(value);
    }

    private void setGreen(int value){
        mSunstone.setGreenValue(value);
    }

    private void setBlue(int value){
        mSunstone.setBlueValue(value);
    }

    private void setWarm(int value){
        mSunstone.setWarmValue(value);
    }

    private void setCool(int value){
        mSunstone.setCoolValue(value);
    }

    private void setColorBrightness(int value){
        mSunstone.setColorBrightness(value);
    }

    private void setWhiteBrightness(int value){
        mSunstone.setWhiteBrightness(value);
    }

    private boolean setRgb(int redValue, int greenValue, int blueValue, int writePeriod) {
        final long millis = System.currentTimeMillis();
        long write_start = System.currentTimeMillis();

        redFlag = false;
        setRed(redValue);
        while (!redFlag) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        write_start = System.currentTimeMillis();
        greenFlag = false;
        setGreen(greenValue);
        while (!greenFlag) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        write_start = System.currentTimeMillis();
        blueFlag = false;
        setBlue(blueValue);
        while (!blueFlag || System.currentTimeMillis() <= (millis + writePeriod)) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
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
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSunstone.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSunstone.disconnect();
        mColorDemoTask.cancel(true);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onConnected() {
        Log.i("Sunstone", "Device connected.");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Sunstone connected!", Toast.LENGTH_SHORT).show();
                mSunstone.readRedValue();
                mSunstone.readGreenValue();
                mSunstone.readBlueValue();
                mSunstone.readWarmValue();
                mSunstone.readCoolValue();
                mSunstone.readColorBrightness();
                mSunstone.readWhiteBrightness();
            }
        });
    }

    @Override
    public void onConnectFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Sunstone connection failed!", Toast.LENGTH_SHORT).show();

                enableView(mColorSeekBar, false);
                enableView(mWhiteSeekBar, false);
                enableView(mWarmSeekBar, false);
                enableView(mCoolSeekBar, false);
                enableView(mRedSeekBar, false);
                enableView(mGreenSeekBar, false);
                enableView(mBlueSeekBar, false);
//                enableView(mColorDemoButton, false);
//                enableView(mSunriseDemoButton, false);
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Sunstone disconnected", Toast.LENGTH_SHORT).show();

                enableView(mColorSeekBar, false);
                enableView(mWhiteSeekBar, false);
                enableView(mWarmSeekBar, false);
                enableView(mCoolSeekBar, false);
                enableView(mRedSeekBar, false);
                enableView(mGreenSeekBar, false);
                enableView(mBlueSeekBar, false);
//                enableView(mColorDemoButton, false);
//                enableView(mSunriseDemoButton, false);
            }
        });
    }

    @Override
    public void onConnectionTimeout() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "No Sunstone found", Toast.LENGTH_SHORT).show();

                enableView(mColorSeekBar, false);
                enableView(mWhiteSeekBar, false);
                enableView(mWarmSeekBar, false);
                enableView(mCoolSeekBar, false);
                enableView(mRedSeekBar, false);
                enableView(mGreenSeekBar, false);
                enableView(mBlueSeekBar, false);
//                enableView(mColorDemoButton, false);
//                enableView(mSunriseDemoButton, false);
            }
        });
    }

    private void enableView(View view, boolean enable){
        if (view != null && (view.isEnabled() != enable))
            view.setEnabled(enable);
    }

    @Override
    public void onWhiteBrightnessChange(final int brightness){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mWhiteSeekBar != null){
                    mWhiteSeekBar.setProgress(brightness & 0xFF);

                    if (!mWhiteSeekBar.isEnabled()){
                        mWhiteSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }


    @Override
    public void onColorBrightnessChange(final int brightness){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mColorSeekBar != null){
                    mColorSeekBar.setProgress(brightness & 0xFF);

                    if (!mColorSeekBar.isEnabled()){
                        mColorSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

    @Override
    public void onWarmChange(final int warmValue){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mWarmSeekBar != null){
                    mWarmSeekBar.setProgress(warmValue & 0xFF);

                    if (!mWarmSeekBar.isEnabled()){
                        mWarmSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

    @Override
    public void onCoolChange(final int coolValue){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCoolSeekBar != null){
                    mCoolSeekBar.setProgress(coolValue & 0xFF);

                    if (!mCoolSeekBar.isEnabled()){
                        mCoolSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

    @Override
    public void onRedChange(final int redValue){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRedSeekBar != null) {
                    mRedSeekBar.setProgress(redValue & 0xFF);

                    redFlag = true;
                    if (!colorDemo && !mRedSeekBar.isEnabled()) {
                        mRedSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

    @Override
    public void onGreenChange(final int greenValue){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mGreenSeekBar != null) {
                    mGreenSeekBar.setProgress(greenValue & 0xFF);

                    greenFlag = true;
                    if (!colorDemo && !mGreenSeekBar.isEnabled()) {
                        mGreenSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

    @Override
    public void onBlueChange(final int blueValue){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mBlueSeekBar != null) {
                    mBlueSeekBar.setProgress(blueValue & 0xFF);

                    blueFlag = true;
                    if (!colorDemo && !mBlueSeekBar.isEnabled()) {
                        mBlueSeekBar.setEnabled(true);
                    }
                }
            }
        });
    }

//    public static class TimePickerFragment extends DialogFragment {
//
//        private TimePickerDialog.OnTimeSetListener mListener;
//
//        public void setListener(TimePickerDialog.OnTimeSetListener listener) {
//            mListener = listener;
//        }
//
//        @Override
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            // Use the current time as the default values for the picker
//            final Calendar c = Calendar.getInstance();
//            final int hour = c.get(Calendar.HOUR_OF_DAY);
//            final int minute = c.get(Calendar.MINUTE);
//
//            // Create a new instance of TimePickerDialog and return it
//            return new TimePickerDialog(getActivity(), mListener, hour, minute, DateFormat.is24HourFormat(getActivity()));
//        }
//    }


    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {

    }

    private class ColorDemoTask extends AsyncTask<Void, Void, Void>{

        private static final int WRITE_PERIOD = 50;

        private int redValue = 0, greenValue = 0, blueValue = 0xFF;

        public Void doInBackground(Void... voids){

            while (!this.isCancelled()) {
                setRgb(redValue, greenValue, blueValue, WRITE_PERIOD);

                if (redValue != 0 && blueValue == 0) {
                    redValue--;
                    greenValue++;
                } else if (greenValue != 0 && redValue == 0) {
                    greenValue--;
                    blueValue++;
                } else {
                    blueValue--;
                    redValue++;
                }
            }
            return null;
        }
    }
}
