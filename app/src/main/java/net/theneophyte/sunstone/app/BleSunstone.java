package net.theneophyte.sunstone.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class that handles the BLE connection to the Sunstone light
 *
 * Created by Matt Sutter on 7/30/15.
 */
public class BleSunstone extends BluetoothGattCallback{

//    public static final String SUNSTONE_DEVICE_NAME = "Sunstone";

    // UUIDs for Sunstone BLE service and associated characteristics.
    public static final UUID SUNSTONE_SERVICE_UUID      = UUID.fromString("1BA40001-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_SERVICE_UUID_MASK = UUID.fromString("FFFF0000-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
    public static final UUID SUNSTONE_WHITE_CHAR_UUID   = UUID.fromString("1BA40002-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_COLOR_CHAR_UUID   = UUID.fromString("1BA40003-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_WARM_CHAR_UUID    = UUID.fromString("1BA40004-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_COOL_CHAR_UUID    = UUID.fromString("1BA40005-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_RED_CHAR_UUID     = UUID.fromString("1BA40006-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_GREEN_CHAR_UUID   = UUID.fromString("1BA40007-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_BLUE_CHAR_UUID    = UUID.fromString("1BA40008-582C-46E8-9CC3-159C8D6F8BBB");

    // BLE scanning timeout
    private static final int SCAN_PERIOD = 10000;

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private String mSunstoneBleAddr = null;
    private final BluetoothGattCallback mGattCallback = this;
    private ScanCallback mLeScanCallback = new SunstoneScanCallback();
    private BluetoothGattCharacteristic
            whiteChar = null,
            colorChar = null,
            warmChar = null,
            coolChar = null,
            redChar = null,
            greenChar = null,
            blueChar = null;

    // Queues for characteristic read (synchronous)
    private Queue<BluetoothGattCharacteristic> readQueue, writeQueue;

    private final List<ScanFilter> mLeScanFilterList;
    private final ScanSettings mUartLeScanSettings;
    private final Handler mHandler;
    private final WeakReference<Callback> mCallback;
    private boolean mScanning = false, mConnected = false, mConnecting = false, mReadPending = false, mWritePending = false;

    /**
     * Callbacks for the UI
     */
    public interface Callback {

        /**
         * Called when there is a successful connection to Sunstone
         */
        void onConnected();

        /**
         * Called when the connection to Sunstone has failed
         */
        void onConnectFailed();

        /**
         * Called when Sunstone has been disconnected
         */
        void onDisconnected();

        /**
         * Called when the connection to Sunstone times out
         */
        void onConnectionTimeout();

        /**
         * Called when the white LED brightness has been changed
         * @param brightness The new 8-bit brightness value
         */
        void onWhiteBrightnessChange(final int brightness);

        /**
         * Called when the color LED brightness has been changed
         * @param brightness The new 8-bit brightness value
         */
        void onColorBrightnessChange(final int brightness);

        /**
         * Called when the warm white LED value has been changed
         * @param warmValue The new 8-bit value
         */
        void onWarmChange(final int warmValue);

        /**
         * Called when the cool white LED value has been changed
         * @param coolValue The new 8-bit value
         */
        void onCoolChange(final int coolValue);

        /**
         * Called when the red LED value has been changed
         * @param redValue The new 8-bit value
         */
        void onRedChange(final int redValue);

        /**
         * Called when the green LED value has been changed
         * @param greenValue The new 8-bit value
         */
        void onGreenChange(final int greenValue);

        /**
         * Called when the blue LED value has been changed
         * @param blueValue The new 8-bit value
         */
        void onBlueChange(final int blueValue);
    }

    /**
     * Constructor for BleSunstone.
     * @param context Application context
     * @param handler UI thread handler
     * @param callback UI callback
     */
    public BleSunstone(Context context, Handler handler, Callback callback){
        mContext = context;
        mHandler = handler;
        mCallback = new WeakReference<Callback>(callback);
        mBluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        mUartLeScanSettings = (new ScanSettings.Builder()).setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();
        mLeScanFilterList = new ArrayList<ScanFilter>();
        mLeScanFilterList.add((new ScanFilter.Builder()).setServiceUuid(
                new ParcelUuid(SUNSTONE_SERVICE_UUID),
                new ParcelUuid(SUNSTONE_SERVICE_UUID_MASK)
        ).build());

        readQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
        writeQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
    }

    /**
     * Runnable to stop the BLE scan
     */
    private final Runnable stopLeScanRunner = new Runnable() {
        @Override
        public void run() {
            Log.d("Sunstone", "Stoping scan...");
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);

            mScanning = false;

            if (!mConnecting && !mConnected) {
                notifyOnConnectionTimeout();
            }
        }
    };

    /**
     * Runnable to start a new BLE scan
     */
    public void startScan(){
        if (!mScanning) {
            Log.d("Sunstone", "Starting scan...");
            mHandler.postDelayed(stopLeScanRunner, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanFilterList, mUartLeScanSettings, mLeScanCallback);
        }
    }

    /**
     * Stops the BLE scan
     */
    public void stopScan(){
        if (mScanning) {
            mHandler.removeCallbacks(stopLeScanRunner);

            stopLeScanRunner.run();
        }
    }

    /**
     * Attempts to connect to Sunstone
     */
    public void connect(){
        if (mConnected){
            Log.d("Sunstone", "Already connected...");
            notifyOnConnected();
            return;
        }

        if (mGatt != null && mGatt.connect()){
            Log.d("Sunstone", "Attempting to reconnect to GATT Server...");
            notifyOnConnected();
            return;
        }

        if (mSunstoneBleAddr != null && mBluetoothAdapter != null) {
            Log.d("Sunstone", "Attempting to reconnect directly to the device...");
            mBluetoothAdapter.getRemoteDevice(mSunstoneBleAddr).connectGatt(mContext, false, mGattCallback);
        } else {
            startScan();
        }
    }

    /**
     * Disconnects from Sunstone
     */
    public void disconnect(){
        stopScan();

        if (mGatt == null) {
            return;
        }

        Log.d("Sunstone", "Disconnecting...");

        mGatt.disconnect();
        close();

        mGatt = null;

        notifyOnDisconnected();
    }

    /**
     * Closes the GATT connection to Sunstone
     */
    public void close(){
        if (mGatt == null) {
            return;
        }

        mGatt.close();

        clearChars();

        mConnected = false;
    }

    /**
     * Callback for the BLE scanner
     */
    private class SunstoneScanCallback extends ScanCallback{

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            final ScanRecord record = result.getScanRecord();
            if (record == null){
                return;
            }

            List<ParcelUuid> uuids = record.getServiceUuids();

            for (ParcelUuid uuid : uuids){
                if (uuid.getUuid().equals(SUNSTONE_SERVICE_UUID)) {
                    if (!mConnecting) {
                        mConnecting = true;
                        stopScan();
                        result.getDevice().connectGatt(mContext, false, mGattCallback);
                        return;
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            notifyOnConnectFailed();
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Connected to device, start discovering services.
                if (!gatt.discoverServices()) {
                    // Error starting service discovery.
                    notifyOnConnectFailed();
                }
            }
            else {
                // Error connecting to device.
                notifyOnConnectFailed();
            }
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            // Disconnected, notify callbacks of disconnection.
            clearChars();

            readQueue.clear();
            writeQueue.clear();

            mConnected = false;

            notifyOnDisconnected();
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
        super.onCharacteristicRead(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS){
            updateCharValue(characteristic);

            final BluetoothGattCharacteristic nextRead = readQueue.poll();
            if (nextRead != null){
                mGatt.readCharacteristic(nextRead);
            } else {
                mReadPending = false;
            }
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
        super.onCharacteristicWrite(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS){
            updateCharValue(characteristic);

//            final BluetoothGattCharacteristic nextWrite = writeQueue.poll();
//            if (nextWrite != null){
//                mGatt.writeCharacteristic(nextWrite);
//            } else {
//                mWritePending = false;
//            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status){
        mGatt = gatt;

        if (gatt == null){
            mConnecting = false;
            mConnected = false;

            notifyOnConnectFailed();
            return;
        }

        whiteChar   = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_WHITE_CHAR_UUID);
        colorChar   = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_COLOR_CHAR_UUID);
        warmChar    = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_WARM_CHAR_UUID);
        coolChar    = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_COOL_CHAR_UUID);
        redChar     = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_RED_CHAR_UUID);
        greenChar   = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_GREEN_CHAR_UUID);
        blueChar    = gatt.getService(SUNSTONE_SERVICE_UUID).getCharacteristic(SUNSTONE_BLUE_CHAR_UUID);

        if (       (whiteChar != null)
                && (colorChar != null)
                && (warmChar  != null)
                && (coolChar  != null)
                && (redChar   != null)
                && (greenChar != null)
                && (blueChar  != null)  ){


            mSunstoneBleAddr = mGatt.getDevice().getAddress();

            mConnecting = false;
            mConnected = true;

            notifyOnConnected();
        } else {
            mConnecting = false;
            mConnected = false;

            notifyOnConnectFailed();
        }
    }

    /**
     * Clears the Characteristic values
     */
    private void clearChars(){
        whiteChar = null;
        colorChar = null;
        warmChar = null;
        coolChar = null;
        redChar = null;
        greenChar = null;
        blueChar = null;
    }

    /**
     * Updates a Characteristic
     * @param characteristic
     */
    private void updateCharValue(BluetoothGattCharacteristic characteristic){

        final UUID char_uuid = characteristic.getUuid();
        final byte value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).byteValue();

        if (mCallback.get() == null){
            return;
        }

        // Notify the UI of the new value
        if (char_uuid.equals(SUNSTONE_WHITE_CHAR_UUID)){
            whiteChar = characteristic;
            mCallback.get().onWhiteBrightnessChange(value);
        } else if (char_uuid.equals(SUNSTONE_COLOR_CHAR_UUID)){
            colorChar = characteristic;
            mCallback.get().onColorBrightnessChange(value);
        } else if (char_uuid.equals(SUNSTONE_WARM_CHAR_UUID)){
            warmChar = characteristic;
            mCallback.get().onWarmChange(value);
        } else if (char_uuid.equals(SUNSTONE_COOL_CHAR_UUID)){
            coolChar = characteristic;
            mCallback.get().onCoolChange(value);
        } else if (char_uuid.equals(SUNSTONE_RED_CHAR_UUID)){
            redChar = characteristic;
            mCallback.get().onRedChange(value);
        } else if (char_uuid.equals(SUNSTONE_GREEN_CHAR_UUID)){
            greenChar = characteristic;
            mCallback.get().onGreenChange(value);
        } else if (char_uuid.equals(SUNSTONE_BLUE_CHAR_UUID)){
            blueChar = characteristic;
            mCallback.get().onBlueChange(value);
        }
    }

    /**
     * Request to read a Characteristic over BLE. If there are other
     * read requests pending the read will be added to the read queue.
     * @param characteristic BLE Characteristic to be read
     * @return True if the request was accepted, false otherwise
     */
    private boolean requestRead(BluetoothGattCharacteristic characteristic){
        if (mConnected){
            if (!mReadPending) {
                mReadPending = mGatt.readCharacteristic(characteristic);
            } else {
                readQueue.add(characteristic);
            }
            return mReadPending;
        } else {
            return false;
        }
    }

    /**
     * Request to write a Characteristic value.
     * @param characteristic BLE Characteristic to be written
     * @return false if there is no connection active
     */
    private boolean requestWrite(BluetoothGattCharacteristic characteristic){
        if (mConnected){
//            if (!mWritePending) {
                mGatt.writeCharacteristic(characteristic);
                mWritePending = true;
//            } else {
//                writeQueue.add(characteristic);
//            }

            return mWritePending;
        } else {
            return false;
        }
    }

    /**
     * Read the white LED brightness value
     */
    public void readWhiteBrightness(){
        requestRead(whiteChar);
    }

    /**
     * Read the color LED brightness value
     */
    public void readColorBrightness(){
        requestRead(colorChar);
    }

    /**
     * Read the warm white LED value
     */
    public void readWarmValue(){
        requestRead(warmChar);
    }

    /**
     * Read the cool white LED value
     */
    public void readCoolValue(){
        requestRead(coolChar);
    }

    /**
     * Read the red LED value
     */
    public void readRedValue(){
        requestRead(redChar);
    }

    /**
     * Read the green LED value
     */
    public void readGreenValue(){
        requestRead(greenChar);
    }

    /**
     * Read the blue LED value
     */
    public void readBlueValue(){
        requestRead(blueChar);
    }

    /**
     * Gets the white LED brightness value from the cached Characteristic
     * @return 8-bit brightness value
     */
    public int getWhiteBrightness(){
        if (whiteChar != null){
            return whiteChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the color LED brightness value from the cached Characteristic
     * @return 8-bit brightness value
     */
    public int getColorBrightness(){
        if (colorChar != null){
            return colorChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the warm white LED value from the cached Characteristic
     * @return 8-bit value
     */
    public int getWarmValue(){
        if (warmChar != null){
            return warmChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the cool white LED value from the cached Characteristic
     * @return 8-bit value
     */
    public int getCoolValue(){
        if (coolChar != null){
            return coolChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the red LED value from the cached Characteristic
     * @return 8-bit value
     */
    public int getRedValue(){
        if (redChar != null){
            return redChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the green LED value from the cached Characteristic
     * @return 8-bit value
     */
    public int getGreenValue(){
        if (greenChar != null){
            return greenChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Gets the blue LED value from the cached Characteristic
     * @return 8-bit value
     */
    public int getBlueValue(){
        if (blueChar != null){
            return blueChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    /**
     * Sets a new value for the white LED brightness
     * @param value 8-bit brightness value
     */
    public void setWhiteBrightness(int value){
        if (whiteChar != null){
            whiteChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(whiteChar);
        }
    }

    /**
     * Sets a new value for the color LED brightness
     * @param value 8-bit brightness value
     */
    public void setColorBrightness(int value){
        if (colorChar != null){
            colorChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(colorChar);
        }
    }

    /**
     * Sets a new value for the warm white LED
     * @param value 8-bit value
     */
    public void setWarmValue(int value){
        if (warmChar != null){
            warmChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(warmChar);
        }
    }

    /**
     * Sets a new value for the cool white LED
     * @param value 8-bit value
     */
    public void setCoolValue(int value){
        if (coolChar != null){
            coolChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(coolChar);
        }
    }

    /**
     * Sets a new value for the red LED
     * @param value 8-bit value
     */
    public void setRedValue(int value){
        if (redChar != null){
            redChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(redChar);
        }
    }

    /**
     * Sets a new value for the green LED
     * @param value 8-bit value
     */
    public void setGreenValue(int value){
        if (greenChar != null){
            greenChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(greenChar);
        }
    }

    /**
     * Sets a new value for the blue LED
     * @param value 8-bit value
     */
    public void setBlueValue(int value){
        if (blueChar != null){
            blueChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            requestWrite(blueChar);
        }
    }

    /**
     * Notify the UI that the connection to Sunstone has failed
     */
    private void notifyOnConnectFailed(){
        clearChars();

        if (mCallback != null) {
            mCallback.get().onConnectFailed();
        }
    }

    /**
     * Notify the UI that Sunstone is connected
     */
    private void notifyOnConnected(){
        stopScan();

        if (mCallback != null) {
            mCallback.get().onConnected();
        }
    }

    /**
     * Notify the UI that Sunstone has been disconnected
     */
    private void notifyOnDisconnected(){
        if (mCallback != null) {
            mCallback.get().onDisconnected();
        }
    }

    /**
     * Notify the UI that the Sunstone connection has timed out
     */
    private void notifyOnConnectionTimeout(){
        if (mCallback != null){
            mCallback.get().onConnectionTimeout();
        }
    }

}
