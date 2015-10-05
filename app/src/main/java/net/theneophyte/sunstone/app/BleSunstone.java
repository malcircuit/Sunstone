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
 * Created by matt on 7/30/15.
 */
public class BleSunstone extends BluetoothGattCallback{

//    public static final String SUNSTONE_DEVICE_NAME = "Sunstone";

    // UUIDs for Sunstone service and associated characteristics.
    public static final UUID SUNSTONE_SERVICE_UUID      = UUID.fromString("1BA40001-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_SERVICE_UUID_MASK = UUID.fromString("FFFF0000-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
    public static final UUID SUNSTONE_WHITE_CHAR_UUID   = UUID.fromString("1BA40002-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_COLOR_CHAR_UUID   = UUID.fromString("1BA40003-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_WARM_CHAR_UUID    = UUID.fromString("1BA40004-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_COOL_CHAR_UUID    = UUID.fromString("1BA40005-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_RED_CHAR_UUID     = UUID.fromString("1BA40006-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_GREEN_CHAR_UUID   = UUID.fromString("1BA40007-582C-46E8-9CC3-159C8D6F8BBB");
    public static final UUID SUNSTONE_BLUE_CHAR_UUID    = UUID.fromString("1BA40008-582C-46E8-9CC3-159C8D6F8BBB");

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
    private Queue<BluetoothGattCharacteristic> readQueue;

    private final List<ScanFilter> mLeScanFilterList;
    private final ScanSettings mUartLeScanSettings;
    private final Handler mHandler;
    private final WeakReference<Callback> mCallback;
    private boolean mScanning = false, mConnected = false, mConnecting = false, mReadPending = false;

    public interface Callback {
        void onConnected();
        void onConnectFailed();
        void onDisconnected();
        void onConnectionTimeout();

        void onWhiteBrightnessChange(final int brightness);
        void onColorBrightnessChange(final int brightness);
        void onWarmChange(final int warmValue);
        void onCoolChange(final int coolValue);
        void onRedChange(final int redValue);
        void onGreenChange(final int greenValue);
        void onBlueChange(final int blueValue);
    }

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
    }

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

    public void startScan(){
        if (!mScanning) {
            Log.d("Sunstone", "Starting scan...");
            mHandler.postDelayed(stopLeScanRunner, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanFilterList, mUartLeScanSettings, mLeScanCallback);
        }
    }

    public void stopScan(){
        if (mScanning) {
            mHandler.removeCallbacks(stopLeScanRunner);

            stopLeScanRunner.run();
        }
    }

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

    public void close(){
        if (mGatt == null) {
            return;
        }

        mGatt.close();

        clearChars();

        mConnected = false;
    }

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

    private void updateCharValue(BluetoothGattCharacteristic characteristic){

        final UUID char_uuid = characteristic.getUuid();
        final byte value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).byteValue();

        if (mCallback.get() == null){
            return;
        }

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

    private void requestRead(BluetoothGattCharacteristic characteristic){
        if (mConnected){
            if (!mReadPending) {
                mGatt.readCharacteristic(characteristic);

                mReadPending = true;
            } else {
                readQueue.add(characteristic);
            }
        }
    }

    public void readWhiteBrightness(){
        requestRead(whiteChar);
    }

    public void readColorBrightness(){
        requestRead(colorChar);
    }

    public void readWarmValue(){
        requestRead(warmChar);
    }

    public void readCoolValue(){
        requestRead(coolChar);
    }

    public void readRedValue(){
        requestRead(redChar);
    }

    public void readGreenValue(){
        requestRead(greenChar);
    }
    public void readBlueValue(){
        requestRead(blueChar);
    }

    public int getWhiteBrightness(){
        if (whiteChar != null){
            return whiteChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getColorBrightness(){
        if (colorChar != null){
            return colorChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getWarmValue(){
        if (warmChar != null){
            return warmChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getCoolValue(){
        if (coolChar != null){
            return coolChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getRedValue(){
        if (redChar != null){
            return redChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getGreenValue(){
        if (greenChar != null){
            return greenChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public int getBlueValue(){
        if (blueChar != null){
            return blueChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return 0;
    }

    public void setWhiteBrightness(int value){
        if (mConnected){
            whiteChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(whiteChar);
        }
    }

    public void setColorBrightness(int value){
        if (mConnected){
            colorChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(colorChar);
        }
    }

    public void setWarmValue(int value){
        if (mConnected){
            warmChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(warmChar);
        }
    }

    public void setCoolValue(int value){
        if (mConnected){
            coolChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(coolChar);
        }
    }

    public void setRedValue(int value){
        if (mConnected){
            redChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(redChar);
        }
    }

    public void setGreenValue(int value){
        if (mConnected){
            greenChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(greenChar);
        }
    }
    public void setBlueValue(int value){
        if (mConnected){
            blueChar.setValue(value & 0xFF, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mGatt.writeCharacteristic(blueChar);
        }
    }

    private void notifyOnConnectFailed(){
        clearChars();

        if (mCallback != null) {
            mCallback.get().onConnectFailed();
        }
    }

    private void notifyOnConnected(){
        stopScan();

        if (mCallback != null) {
            mCallback.get().onConnected();
        }
    }

    private void notifyOnDisconnected(){
        if (mCallback != null) {
            mCallback.get().onDisconnected();
        }
    }

    private void notifyOnConnectionTimeout(){
        if (mCallback != null){
            mCallback.get().onConnectionTimeout();
        }
    }

}
