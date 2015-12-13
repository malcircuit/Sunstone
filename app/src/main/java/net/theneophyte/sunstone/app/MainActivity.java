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

/**
 * Main Activity class
 *
 * Created by Matt Sutter on 5/12/15
 */
public class MainActivity extends Activity implements BleSunstone.Callback, TimePickerDialog.OnTimeSetListener {

//    private final static String TAG = "Sunstone";

    // The timeout value for setting values on Sunstone
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

    private volatile boolean colorDemo = false, sunriseDemo = false, writeFlag = false;

    private ColorDemoTask mColorDemoTask;
    private SunriseDemoTask mSunriseDemoTask;

    /**
     * Called when the Application is created
     *
     * @param savedInstanceState Saved application data
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Make sure BLE is supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mHandler = new Handler(Looper.getMainLooper());
        mSunstone = new BleSunstone(getApplicationContext(), mHandler, this);
        mColorDemoTask = new ColorDemoTask();
        mSunriseDemoTask = new SunriseDemoTask();

        /*
            Set up the Color Demo button
         */
        mColorDemoButton = (Button) findViewById(R.id.color_demo_button);
        mColorDemoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!colorDemo) {
                    // Save the current color values
                    red = mSunstone.getRedValue();
                    green = mSunstone.getGreenValue();
                    blue = mSunstone.getBlueValue();

                    // Disable the color sliders and Sunrise Demo button
                    enableView(mRedSeekBar, false);
                    enableView(mGreenSeekBar, false);
                    enableView(mBlueSeekBar, false);
                    enableView(mSunriseDemoButton, false);

                    colorDemo = true;

                    /*
                        AsyncTasks can't be reused, so we have to instantiate a new one
                        if the user wants to run it again
                    */
                    if (mColorDemoTask.getStatus() == AsyncTask.Status.FINISHED)
                    {
                        mColorDemoTask = new ColorDemoTask();
                    }

                    // Start the demo
                    mColorDemoTask.execute();

                } else {
                    mColorDemoTask.cancel(true);

                    // Reset the colors to their previous values
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... params) {

                            colorDemo = false;

                            setRgbBlocking(red, green, blue, 0);

                            return null;
                        }
                    }.execute();

                    // Re-enable the Sunrise Demo Button
                    enableView(mSunriseDemoButton, true);

                }
            }
        });

        mColorDemoButton.setEnabled(true);

        /*
            Set up the Sunrise Demo button
         */
        mSunriseDemoButton = (Button) findViewById(R.id.sunrise_demo_button);
        mSunriseDemoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!sunriseDemo) {
                    // Save the current color and brightness values
                    red = mSunstone.getRedValue();
                    green = mSunstone.getGreenValue();
                    blue = mSunstone.getBlueValue();
                    warm = mSunstone.getWarmValue();
                    cool = mSunstone.getCoolValue();
                    color = mSunstone.getColorBrightness();
                    white = mSunstone.getWhiteBrightness();

                    // Disable all of the sliders and the Color Demo button
                    enableView(mColorSeekBar, false);
                    enableView(mWhiteSeekBar, false);
                    enableView(mWarmSeekBar, false);
                    enableView(mCoolSeekBar, false);
                    enableView(mRedSeekBar, false);
                    enableView(mGreenSeekBar, false);
                    enableView(mBlueSeekBar, false);
                    enableView(mColorDemoButton, false);

                    sunriseDemo = true;
                    /*
                        AsyncTasks can't be reused, so we have to instantiate a new one
                        if the user wants to run it again
                    */
                    if (mSunriseDemoTask.getStatus() == AsyncTask.Status.FINISHED)
                    {
                        mSunriseDemoTask = new SunriseDemoTask();
                    }

                    // Start the demo
                    mSunriseDemoTask.execute();

                } else {
                    mSunriseDemoTask.cancel(true);

                    // Reset the color and brightness values to their previous states
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... params) {

                            sunriseDemo = false;

                            setAllBlocking(red, green, blue, cool, warm, color, white, 0);

                            return null;
                        }
                    }.execute();

                    enableView(mColorDemoButton, true);
                }
            }
        });

        mSunriseDemoButton.setEnabled(true);

        /*
            Set up the blue color slider
         */
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

        /*
            Set up the red color slider
         */
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

        /*
            Set up the green color slider
         */
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

        /*
            Set up the warm white slider
         */
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

        /*
            Set up the cool white slider
         */
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

        /*
            Set up the white brightness slider
         */
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

        /*
            Set up the white brightness slider
         */
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

        mSunstone.connect();
    }

    /**
     * Sets the red color value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit color value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setRedBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setRedValue(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the green color value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit color value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setGreenBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setGreenValue(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the blue color value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit color value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setBlueBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setBlueValue(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the warm white value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit color value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setWarmBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setWarmValue(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the cool white value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit color value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setCoolBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setCoolValue(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the color brightness value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit brightness value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setColorBrightnessBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setColorBrightness(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the white brightness value on Sunstone, and waits for the write to
     * be verified. This function blocks execution while waiting and should
     * not be called from the UI thread! Calling this function from the main
     * UI thread will probably result in ANRs and unpredictable behavior.
     * @param value 8-bit brightness value (0-255)
     * @param wait_time System time in milliseconds to delay until returning.
     *                  Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setWhiteBrightnessBlocking(int value, long wait_time){
        final long write_start = System.currentTimeMillis();

        writeFlag = false;
        mSunstone.setWhiteBrightness(value);
        while (!writeFlag || System.currentTimeMillis() <= wait_time) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            } else if (System.currentTimeMillis() > (write_start + WRITE_TIMEOUT_MILLIS)){
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the red color value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit color value (0-255)
     */
    private void setRed(int value){
        mSunstone.setRedValue(value);
    }

    /**
     * Sets the green color value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit color value (0-255)
     */
    private void setGreen(int value){
        mSunstone.setGreenValue(value);
    }

    /**
     * Sets the blue color value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit color value (0-255)
     */
    private void setBlue(int value){
        mSunstone.setBlueValue(value);
    }

    /**
     * Sets the warm white value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit value (0-255)
     */
    private void setWarm(int value){
        mSunstone.setWarmValue(value);
    }

    /**
     * Sets the cool white value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit value (0-255)
     */
    private void setCool(int value){
        mSunstone.setCoolValue(value);
    }

    /**
     * Sets the color LED brightness value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit brightness value (0-255)
     */
    private void setColorBrightness(int value){
        mSunstone.setColorBrightness(value);
    }

    /**
     * Sets the white LED brightness value on Sunstone. The write is not
     * verified and may not occur if the BLE connection is busy.
     * @param value 8-bit brightness value (0-255)
     */
    private void setWhiteBrightness(int value){
        mSunstone.setWhiteBrightness(value);
    }

    /**
     * Sets the values for the RGB LEDs on Sunstone, and waits for
     * the writes to be verified. This function blocks execution while
     * waiting and should not be called from the UI thread! Calling this
     * function from the main UI thread will probably result in ANRs and
     * unpredictable behavior.
     * @param redValue 8-bit red value (0-255)
     * @param greenValue 8-bit green value (0-255)
     * @param blueValue 8-bit blue value (0-255)
     * @param writePeriod System time in milliseconds to delay until returning.
     *                    Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setRgbBlocking(int redValue, int greenValue, int blueValue, int writePeriod) {
        final long millis = System.currentTimeMillis();
        boolean result = true;

        result &= setRedBlocking(redValue, 0);
        result &= setGreenBlocking(greenValue, 0);
        result &= setBlueBlocking(blueValue, millis + writePeriod);
        return result;
    }

    /**
     * Sets the values for all of the values on Sunstone, and waits for
     * the writes to be verified. This function blocks execution while waiting
     * and should not be called from the UI thread! Calling this function from
     * the main UI thread will probably result in ANRs and unpredictable behavior.
     * @param red 8-bit red value (0-255)
     * @param green 8-bit green value (0-255)
     * @param blue 8-bit blue value (0-255)
     * @param cool 8-bit cool value (0-255)
     * @param warm 8-bit warm value (0-255)
     * @param color 8-bit brightness value (0-255)
     * @param white 8-bit brightness value (0-255)
     * @param writePeriod System time in milliseconds to delay until returning.
     *                    Useful for writes that are done at a fixed frequency.
     * @return false if the write failed (either because the thread was
     * interrupted or the write timed out), true if successful
     */
    private boolean setAllBlocking(int red, int green, int blue, int cool, int warm, int color, int white, int writePeriod) {
        final long millis = System.currentTimeMillis();
        boolean result = true;

        result &= setRedBlocking(red, 0);
        result &= setGreenBlocking(green, 0);
        result &= setBlueBlocking(blue, 0);
        result &= setCoolBlocking(cool, 0);
        result &= setWarmBlocking(warm, 0);
        result &= setColorBrightnessBlocking(color, 0);
        result &= setWhiteBrightnessBlocking(white, millis + writePeriod);
        return result;
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
                enableView(mColorDemoButton, false);
                enableView(mSunriseDemoButton, false);
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
                enableView(mColorDemoButton, false);
                enableView(mSunriseDemoButton, false);
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
                enableView(mColorDemoButton, false);
                enableView(mSunriseDemoButton, false);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!sunriseDemo){
                        enableView(mWhiteSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!sunriseDemo){
                        enableView(mColorSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!sunriseDemo){
                        enableView(mWarmSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!sunriseDemo){
                        enableView(mCoolSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!colorDemo && !sunriseDemo) {
                        enableView(mRedSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!colorDemo && !sunriseDemo) {
                        enableView(mGreenSeekBar, true);
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

                    writeFlag = true;

                    // If we're not currently in a demo mode, re-enable the slider
                    if (!colorDemo && !sunriseDemo) {
                        enableView(mBlueSeekBar, true);
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

    /**
     * Color Demo Mode AsyncTask Class
     *
     * Cycles through the RGB spectrum on Sunstone
     */
    private class ColorDemoTask extends AsyncTask<Void, Void, Void>{

        private static final int WRITE_PERIOD = 50;

        private int redValue = 0, greenValue = 0, blueValue = 0xFF;

        @Override
        public Void doInBackground(Void... voids){

            while (!this.isCancelled()) {
                setRgbBlocking(redValue, greenValue, blueValue, WRITE_PERIOD);

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

    /**
     * Sunrise Demo Mode AsyncTask Class
     *
     * Simulates a sunrise on Sunstone. Starts out as a dim red-orange and
     * brightens until to a bright sunlight hue.
     */
    private class SunriseDemoTask extends AsyncTask<Void, Void, Void>{

        private static final int GREEN_MAX = 0x50;
        private static final int GREEN_MIN = 0x10;
        private static final float GREEN_STEP = (float)(GREEN_MAX - GREEN_MIN) / 0xFF;
        private static final int WRITE_PERIOD = 40;

        private int redValue = 0xFF, blueValue = 0, warmValue = 0xFF, coolValue = 0;
        private int colorBrightness = 0, whiteBrightness = 0;
        private float greenValue = 0x10;

        @Override
        public Void doInBackground(Void... voids){

            setAllBlocking(redValue, (int) greenValue, blueValue, coolValue, warmValue, colorBrightness, whiteBrightness, WRITE_PERIOD);
            long millis;
            do {
                millis = System.currentTimeMillis();
                setColorBrightnessBlocking(colorBrightness, 0);
                setGreenBlocking((int)greenValue, millis + WRITE_PERIOD);

                colorBrightness++;
                greenValue += GREEN_STEP;
            } while (!this.isCancelled() && colorBrightness < 0xFF);

            do {
                millis = System.currentTimeMillis();
                setWhiteBrightnessBlocking(whiteBrightness, millis + WRITE_PERIOD);
                whiteBrightness++;
            } while (!this.isCancelled() && whiteBrightness < 0xFF);

            do {
                millis = System.currentTimeMillis();
                setCoolBlocking(coolValue, millis + WRITE_PERIOD);
                coolValue++;
            } while (!this.isCancelled() && coolValue < 0xFF);

            return null;
        }
    }
}
