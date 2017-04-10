
package com.blescent.library;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service{
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private static int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.demo.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.demo.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.demo.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.demo.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.demo.bluetooth.le.EXTRA_DATA";

    private static final UUID kOPhoneServiceUUID = UUID.fromString("B8E06067-62AD-41BA-9231-206AE80AB550");
    private static final UUID kOPhoneTXCharacteristicUUID = UUID.fromString("BF45E40A-DE2A-4BC8-BBA0-E5D6065F1B4B");
    private static final UUID kOPhoneRXCharacteristicUUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final UUID bluetoothDeviceInformationServiceUUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID oPhoneFirmwareRevisionStringCharacteristicUUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    private static final int oPBTPeripheralMaxStoredTrackSize = 256;
    private static final int VPBTResponseStatusIncomplete = 0;
    private static final int VPBTResponseStatusInvalid = 1;
    private static final int VPBTResponseStatusValid = 2;
    private static byte firmwareRevision = 0;
    private static final byte VPHeartbeatCharacter = 'W';
    private static boolean stillProcessing = false;
    private static ArrayList<Byte> responseArrayList = new ArrayList<>();
    private static Queue<QueueData> queueData = new LinkedList<QueueData>();
    private static boolean processing=false;
    private static boolean waitForResponse = false;
    private static int VPWriteOperationTimeout = 3000;
    private static int VPMaxNumberOfRetries = 3;
    private static long commandStartTime;
    private static int retryCount;

    public enum notification {
        OPBTPeripheralConnectedNotification,
        OPBTPeripheralDisconnectedNotification,
        OPBTPeripheralDataQueueSendingNotification,
        OPBTPeripheralDataQueueConnectionDiedNotification,
        OPBTPeripheralDataQueueAcknowledgedNotification,
        OPBTPeripheralHeartbeatNotification,
        OPBTPeripheralDataReceivedNotification,
        OPBTPeripheralDeviceStatusNotification,
        OPBTPeripheralStoredTrackReadNotification,
        OPBTPeripheralWroteTrackNotification,
        OPBTPeripheralRFIDReadNotification,
        OPBTPeripheralOfflineAnalyticsReadNotification,
        OPBTPeripheralCommunicationErrorNotification,
        OPBTPeripheralBluetoothDataLogNotification,
        OPBTPeripheralFirmwareRevisionNotification
    }

    public enum Key {
        OPBTPeripheralWasSuspectKey,
        OPBTPeripheralDataQueueOperationKey,
        OPBTPeripheralDataQueueDataKey,
        OPBTPeripheralReceivedDataKey,
        OPBTPeripheralBatteryLevelKey,
        OPBTPeripheralHasOfflineAnalyticsKey,
        OPBTPeripheralStoredTrackKey,
        OPBTPeripheralRFIDFamilyKey,
        OPBTPeripheralRFIDIdentifierKey,
        OPBTPeripheralRFIDTrackKey,
        OPBTPeripheralOfflineAnalyticsKey,
        OPBTPeripheralCommunicationErrorMessageKey,
        OPBTPeripheralCommunicationBluetoothDataLogMessageKey
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.e(TAG, "inside statechange method----" + newState);
            if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.d(TAG, "Disconnecting from server----");
            }
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "newState is connecting-----");
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                queueData.clear();
              //  broadcastUpdate(notification.OPBTPeripheralConnectedNotification.name());
                broadcastUpdate(ACTION_GATT_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                processing = false;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
               // broadcastUpdate(notification.OPBTPeripheralDisconnectedNotification.name());
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                  broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                if (gatt.getService(kOPhoneServiceUUID).getCharacteristic(kOPhoneRXCharacteristicUUID) != null) {
                    Log.d(TAG, "receiver found");
                    setCharacteristicNotification(gatt.getService(kOPhoneServiceUUID).getCharacteristic(kOPhoneRXCharacteristicUUID), true);
                }
                if (gatt.getService(bluetoothDeviceInformationServiceUUID) != null && gatt.getService(bluetoothDeviceInformationServiceUUID).getCharacteristic(oPhoneFirmwareRevisionStringCharacteristicUUID) != null) {
                    Log.d(TAG, "firmware found");
                    BluetoothGattCharacteristic firmwareRevisionCharacteristics = gatt.getService(bluetoothDeviceInformationServiceUUID).getCharacteristic(oPhoneFirmwareRevisionStringCharacteristicUUID);
                    setCharacteristicNotification(firmwareRevisionCharacteristics, true);
                    readCharacteristic(firmwareRevisionCharacteristics);
                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                 broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                if (oPhoneFirmwareRevisionStringCharacteristicUUID.toString().equals(characteristic.getUuid().toString())) {
                    Log.d(TAG, "onCharacteristicRead firmRevision");
                    String stringFromData = new String(characteristic.getValue());
                    setFirmwareRevision(stringFromData);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            Log.d(TAG, "onCharacteristicWrite: status:" + status);
            if(!waitForResponse){
                Log.d(TAG,"Not waitForResponse");
                processing = false;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                  broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            if (oPhoneFirmwareRevisionStringCharacteristicUUID.toString().equals(characteristic.getUuid().toString())) {
                Log.d(TAG, "onCharacteristicChanged firmRevision");
                String stringFromData = new String(characteristic.getValue());
                setFirmwareRevision(stringFromData);
            }

            if (kOPhoneRXCharacteristicUUID.toString().equals(characteristic.getUuid().toString())) {
               // Log.d(TAG, "onCharacteristicChanged Receiver:");
                byte[] bytes = characteristic.getValue();
                int firstNonHeartbeatByte = 0;
                for (byte singleByte : bytes) {
                    if (singleByte != VPHeartbeatCharacter) {
                        break;
                    }
                    firstNonHeartbeatByte++;
                }
                if (firstNonHeartbeatByte > 0) {
                    broadcastUpdate(notification.OPBTPeripheralHeartbeatNotification.name());
                }
                byte[] dataWithoutHeartbeats = Arrays.copyOfRange(bytes, firstNonHeartbeatByte, bytes.length);

                if (dataWithoutHeartbeats.length > 0 && waitForResponse) {
                    didUpdateValueWithData(dataWithoutHeartbeats);
                }
            }
        }

    };

    private String byteArrayToString(byte[] bytes) {
        String dataToString="";
        if (bytes != null && bytes.length > 0) {
            for (byte byteChar : bytes) {
                dataToString = dataToString + String.format("%02X ", byteChar);
            }
        }
        return dataToString;
    }

    boolean didUpdateValueWithData(byte[] data) {
        Log.d(TAG,"didUpdateValueWithData:"+byteArrayToString(data));
        responseArrayList.addAll(toByteList(data));
        Intent intent = new Intent(notification.OPBTPeripheralDataReceivedNotification.name());
        intent.putExtra(Key.OPBTPeripheralReceivedDataKey.name(), data);
        sendBroadcast(intent);
        int status = parseResponse();
        if (status != VPBTResponseStatusIncomplete) {
            responseArrayList.clear();
            processing = false;
            if (status == VPBTResponseStatusValid) {
                return true;
            } else {
                // There was a with the response; resend the command
                Log.d(TAG, "!!! The response was invalid; sending again after timeout.");
            }
        }
        return false;
    }

    private int parseResponse() {
        byte[] response = toPrimitives(responseArrayList);
        int position = 0;
        byte headerByte1;
        if (read8BitValue(response.length, position)) {
            headerByte1 = response[position];
            position++;
        } else {
            return VPBTResponseStatusIncomplete;
        }
        if (headerByte1 != 'V') {
            return VPBTResponseStatusInvalid;
        }

        byte headerByte2;
        if (read8BitValue(response.length, position)) {
            headerByte2 = response[position];
            position++;
        } else {
            return VPBTResponseStatusIncomplete;
        }
        if (headerByte2 != 'C') {
            return VPBTResponseStatusInvalid;
        }

        byte firmwareRevision;
        if (read8BitValue(response.length, position)) {
            firmwareRevision = response[position];
            position++;
        } else {
            return VPBTResponseStatusIncomplete;
        }

        byte responseStatus;
        if (read8BitValue(response.length, position)) {
            responseStatus = response[position];
            position++;
        } else {
            return VPBTResponseStatusIncomplete;
        }

        byte opcode;
        if (read8BitValue(response.length, position)) {
            opcode = response[position];
            position++;
        } else {
            return VPBTResponseStatusIncomplete;
        }

        short payloadLength;
        if (read16BitValue(response.length, position)) {
            payloadLength = (short) ((response[position + 1] << 8) + (response[position] & 0xFF));
            position = position + 2;
        } else {
            return VPBTResponseStatusIncomplete;
        }

        int numBytesLeft = response.length - position;
        if (numBytesLeft < payloadLength) {
            return VPBTResponseStatusIncomplete;
        }
        byte[] payload = Arrays.copyOfRange(response, position, position + payloadLength);
        position = position + payloadLength;
        byte checksum;
        if (read8BitValue(response.length, position)) {
            checksum = response[position];
        } else {
            return VPBTResponseStatusIncomplete;
        }

        if (!verifyCheckSum(response)) {
            broadcastUpdate(notification.OPBTPeripheralCommunicationErrorNotification.name(), "Invalid checksum.");
            return VPBTResponseStatusInvalid;
        }

        Log.d(TAG, "parseResponse" + headerByte1 + " " + headerByte2 + " " + firmwareRevision + " " + responseStatus + " " + opcode + " " + payloadLength);
        if (opcode == 2) {
            int value = ((int) payload[0]) & 0xff;
            byte state = payload[1];
            byte hasOfflineAnalytics = payload[2];
            int fValue = value, fFromStart = 0xAA, fFromEnd = 0xC8, fToStart = 10, fToEnd = 100;
            int percentage = fToStart + ((fToEnd - fToStart) * (fValue - fFromStart) / (fFromEnd - fFromStart));
            percentage = percentage > 2 ? percentage : 2;
            percentage = percentage < 100 ? percentage : 100;
            Intent intent = new Intent(notification.OPBTPeripheralDeviceStatusNotification.name());
            intent.putExtra(Key.OPBTPeripheralBatteryLevelKey.name(), percentage);
            intent.putExtra(Key.OPBTPeripheralHasOfflineAnalyticsKey.name(), hasOfflineAnalytics);
            sendBroadcast(intent);
            Log.d(TAG, "batteryPercentage" + percentage);
        } else if (opcode == 3) {
            broadcastUpdate(notification.OPBTPeripheralWroteTrackNotification.name());
        } else if (opcode == 4) {
            Intent intent = new Intent(notification.OPBTPeripheralStoredTrackReadNotification.name());
            intent.putExtra(Key.OPBTPeripheralStoredTrackKey.name(),payload);
            sendBroadcast(intent);
        } else if (opcode == 5) {
            position = 0;
            short familyCode;
            if (read16BitValue(payload.length, position)) {
                familyCode = (short) ((payload[position + 1] << 8) + (response[position] & 0xFF));
                position = position + 2;
            } else {
                return VPBTResponseStatusInvalid;
            }
            short identifierLength;
            if (read16BitValue(payload.length, position)) {
                identifierLength = (short) ((payload[position + 1] << 8) + (response[position] & 0xFF));
                position = position + 2;
            } else {
                return VPBTResponseStatusInvalid;
            }
            byte[] identifierData = Arrays.copyOfRange(payload, position, position + identifierLength);
            position += identifierLength;
            String identifierString = new String(identifierData, StandardCharsets.UTF_8);
            short trackLength;
            if (read16BitValue(payload.length, position)) {
                trackLength = (short) ((payload[position + 1] << 8) + (response[position] & 0xFF));
                position = position + 2;
            } else {
                return VPBTResponseStatusInvalid;
            }
            byte[] trackData = Arrays.copyOfRange(payload, position, position + trackLength);
            position += trackLength;
            Intent intent = new Intent(notification.OPBTPeripheralRFIDReadNotification.name());
            intent.putExtra(Key.OPBTPeripheralRFIDFamilyKey.name(), familyCode);
            intent.putExtra(Key.OPBTPeripheralRFIDIdentifierKey.name(), identifierData);
            intent.putExtra(Key.OPBTPeripheralRFIDTrackKey.name(), trackData);
            Log.d(TAG,">>>>> RFIDRead:" + familyCode+", "+byteArrayToString(identifierData)+", "+byteArrayToString(trackData));
            sendBroadcast(intent);

        }else if(opcode == 8){
            Intent intent = new Intent(notification.OPBTPeripheralOfflineAnalyticsReadNotification.name());
            intent.putExtra(Key.OPBTPeripheralOfflineAnalyticsKey.name(),payload);
            sendBroadcast(intent);
        }
        Intent intent = new Intent("PARSE_RESPONSE");
        intent.putExtra("RESPONSE",response);
        sendBroadcast(intent);
        return VPBTResponseStatusValid;
    }

    private boolean verifyCheckSum(byte[] response) {
        byte sum = 0;
        for (byte aResponse : response) {
            sum += aResponse;
        }
        return sum == 0;
    }

    private boolean read8BitValue(int length, int position) {
        if (length > position)
            return true;
        return false;
    }

    private boolean read16BitValue(int length, int position) {
        if (length - 1 > position)
            return true;
        return false;
    }

    private byte[] toPrimitives(ArrayList<Byte> oBytes) {

        byte[] bytes = new byte[oBytes.size()];
        for (int i = 0; i < oBytes.size(); i++) {
            bytes[i] = oBytes.get(i);
        }
        return bytes;

    }

    ArrayList<Byte> toByteList(byte[] bytesPrim) {
        ArrayList<Byte> bytes = new ArrayList<>();
        for (byte b : bytesPrim) bytes.add(b); //Autoboxing
        return bytes;
    }


    private void setFirmwareRevision(String stringFromData) {
        if ("Firmware Revision".equals(stringFromData)) {
            firmwareRevision = 0x15;
        } else if (stringFromData.contains("Revision")) {
            String version = stringFromData.replace("Revision ", "");
            String components[] = version.split(".");
            if (components.length == 2) {
                String majorVersionString = components[0];
                String minorVersionString = components[1];
                byte majorVersion = Byte.valueOf(majorVersionString);
                byte minorVersion = Byte.valueOf(minorVersionString);
                firmwareRevision = (byte) ((majorVersion << 4) | minorVersion);
            } else {
                Log.d(TAG, "Couldn't parse firmware version.");
                firmwareRevision = 0x23;
            }

            // If we have "Revision 0.0" then we've encountered a problem
            // where we the BT shield wasn't setting the firmware revision
            // characteristic, so use our query to determine the version
            if (firmwareRevision == 0) {
                Log.d(TAG, "!!  Invalid firmware revision, reading it via querying.");
                disconnect();
            }
        } else {
            // oPhones end up here
            firmwareRevision = 0x15;
        }
        broadcastUpdate(notification.OPBTPeripheralFirmwareRevisionNotification.name());
        Log.e(TAG, "firmwareRevision=" + firmwareRevision);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, String message) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, message);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();
        broadcastUpdate(action, byteArrayToString(data));
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, com.blescent.library.R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] a) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        characteristic.setValue(a);
        Log.d(TAG, "writeCharacteristic");
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);


       /* if( kOPhoneTXCharacteristicUUID.equals(characteristic.getUuid())){
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }*/
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


    public void playScent(int duration, int intensity, String scentCode) {
        int intensityLength = 1;
        int durationLength = 2;
        int numberScentCodeLength = 1;
        byte code[] = scentCode.getBytes();
        if (firmwareRevision < 0x20) {
            byte[] payload = new byte[4 + code.length + 1];
            byte speedDur[] = {
                    '*',
                    OVERFLOW_MAX_TO_BYTE(intensity),
                    '@',
                    OVERFLOW_MAX_TO_BYTE(duration)
            };
            System.arraycopy(speedDur, 0, payload, 0, speedDur.length);
            System.arraycopy(code, 0, payload, speedDur.length, code.length);
            payload[payload.length - 1] = 'Z';
            enqueueData(payload);
        } else {
            byte payload[] = new byte[intensityLength + durationLength + numberScentCodeLength + code.length];
            payload[0] = OVERFLOW_MAX_TO_BYTE(intensity);
            payload[1] = (byte) (duration & 0xff);
            payload[2] = (byte) ((duration >> 8) & 0xff);
            payload[3] = (byte) (code.length);
            System.arraycopy(code, 0, payload, 4, code.length);
            byte[] finalBytes = packCommand((byte) 0, payload);
            enqueueData(finalBytes,true);
        }
    }

    private byte OVERFLOW_MAX_TO_BYTE(int X) {
        return (X > 0xff) ? (byte) 0xff : MASK_TO_BYTE(X);
    }

    private byte MASK_TO_BYTE(int X) {
        return (byte) (X & 0xff);
    }


    public void stopScent() {
        Log.d(TAG, "stopScent");
        if (firmwareRevision < 0x20) {
            byte[] data = new byte[1];
            data[0] = '!';
            enqueueData(data);
        } else {
            transmitDataWithoutPayloadToCommand((byte) 1);
        }

    }

    public void queryForStatus() {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't query.");
        } else
            transmitDataWithoutPayloadToCommand((byte) 2);
    }

    public void writeTrackPayload(byte[] payload) {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't write track.");
        } else {
            if (payload.length <= oPBTPeripheralMaxStoredTrackSize) {
                byte[] finalBytes = packCommand((byte) 3, payload);
                enqueueData(finalBytes,true);
            }
        }
    }

    public void readTrackPayload() {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't write track.");
        } else {
            transmitDataWithoutPayloadToCommand((byte) 4);
        }
    }

    public void queryForRFID() {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't query.");
        } else {
            transmitDataWithoutPayloadToCommand((byte) 5);
        }
    }

    public void queryForOfflineAnalytics() {
        if (firmwareRevision < 0x20) {

        } else {
            transmitDataWithoutPayloadToCommand((byte) 8);
        }
    }

    public void clearOfflineAnalytics() {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't write track.");
        } else {
            transmitDataWithoutPayloadToCommand((byte) 9);
        }
    }

    public void enableTimeout(boolean isEnable) {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't enable timeout on device.");
        } else {
            byte[] payload = new byte[1];
            if (isEnable) {
                payload[0] = 0x01;
            } else {
                payload[0] = 0x00;
            }
            byte[] finalBytes = packCommand((byte) 6, payload);
            enqueueData(finalBytes,true);
        }
    }

    public void writeSettingsWithFanSpeed(int fanSpeedPercentage, boolean isTimeoutOn, int timeoutMinutes) {
        if (firmwareRevision < 0x20) {
            Log.d(TAG, "Legacy code can't enable timeout on device.");
        } else {
            byte[] payload = new byte[4];
            byte timeoutOn = (byte) (isTimeoutOn ? 0x01 : 0x00);
            payload[0] = (byte) fanSpeedPercentage;
            payload[1] = timeoutOn;
            payload[2] = (byte) (timeoutMinutes & 0xff);
            payload[3] = (byte) ((timeoutMinutes >> 8) & 0xff);
            byte[] finalBytes = packCommand((byte) 7, payload);
            enqueueData(finalBytes,true);
        }
    }

    private void transmitDataWithoutPayloadToCommand(byte command) {
        byte[] payload = new byte[0];
        byte[] finalBytes = packCommand(command, payload);
        enqueueData(finalBytes,true);
    }
    private void enqueueData(byte[] finalBytes){
        enqueueData(finalBytes,false);
    }
    private void enqueueData(byte[] finalBytes,boolean waitForResponse) {
        try {
            BluetoothGattCharacteristic mNotifyCharacteristic = mBluetoothGatt.getService(kOPhoneServiceUUID)
                    .getCharacteristic(kOPhoneTXCharacteristicUUID);
            queueData.add(new QueueData(finalBytes,waitForResponse,mNotifyCharacteristic));
            //Log.d(TAG,"here>>>");
           // writeCharacteristic(queueData.element().getCharacteristic(), queueData.element().getPayload());
            runQueueIfNecessary();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    private void runQueueIfNecessary(){
        if(mConnectionState== STATE_CONNECTED && !queueData.isEmpty()){
           if(!processing){
               processing = true;
               waitForResponse = queueData.element().isWaitForResponse();
               actualMain();
           }
        }else{
            queueData.clear();
        }
    }


    private void actualMain(){
        retryCount = 0;
        send();
        Log.d(TAG,"actual main");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(processing) {
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - commandStartTime;
                  //  Log.d(TAG,"elapseTime: "+elapsedTime+",commandStartTime: "+commandStartTime+", currentTime: "+currentTime);
                    if (elapsedTime > VPWriteOperationTimeout) {
                        retryCount++;
                        if (retryCount > VPMaxNumberOfRetries) {
                            Log.d(TAG, "!! Timeout waiting for response. Giving up.");
                            processing = false;
                        } else {
                            Log.d(TAG, "!! Timeout waiting for response; sending again.");
                            send();
                        }
                    }
                    handler.postDelayed(this,100);
                }else{
                    queueData.remove();
                    Log.d(TAG,"work completed");
                    handler.removeCallbacks(this);
                    runQueueIfNecessary();
                }
            }

        },100);



    }
    private void send(){
            commandStartTime =System.currentTimeMillis();
            QueueData queueD = queueData.element();
            waitForResponse = queueD.isWaitForResponse();
            processing = true;
            writeCharacteristic(queueD.getCharacteristic(), queueD.getPayload());
    }

    private byte[] packCommand(byte command, byte[] payload) {
        byte programID = 1, revision = 1;
        int commonLength = 5;
        int payloadLengthNotifierLength = 2;
        int payloadLength = payload.length;
        int checkSumLength = 1;
        byte[] finalBytes = new byte[commonLength + payloadLengthNotifierLength + payloadLength + checkSumLength];
        byte[] commandWithCommon = {'V', 'C', programID, revision, command};
        finalBytes[commonLength] = (byte) (payloadLength & 0xff);
        finalBytes[commonLength + 1] = (byte) ((payloadLength >> 8) & 0xff);
        System.arraycopy(commandWithCommon, 0, finalBytes, 0, commandWithCommon.length);
        System.arraycopy(payload, 0, finalBytes, commonLength + 2, payloadLength);
        finalBytes[finalBytes.length - 1] = calculateCheckSum(finalBytes);
        return finalBytes;
    }

    private byte calculateCheckSum(byte[] bytes) {
        byte checkSum = 0, i = 0;
        for (i = 0; i < bytes.length; i++) {
            checkSum += bytes[i];
        }
        return (byte) (~checkSum + 1);
    }

    public boolean isCharacteristicsPresent() {
        if(mBluetoothGatt!=null){
            BluetoothGattService localService = mBluetoothGatt.getService(kOPhoneServiceUUID);
            if(localService!=null){
                return localService.getCharacteristic(kOPhoneTXCharacteristicUUID)!=null;
            }
        }
        return false;
    }


    public boolean scanForDevices(BluetoothAdapter.LeScanCallback mLeScanCallback) {
        if (mBluetoothAdapter != null) {
            UUID services[] = {kOPhoneServiceUUID};
            return mBluetoothAdapter.startLeScan(services, mLeScanCallback);
        } else {
            return false;
        }
    }

    public boolean stopScanning(BluetoothAdapter.LeScanCallback mLeScanCallback) {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            return false;
        } else {
            return false;
        }
    }

    public boolean isBluetoothEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public static int getmConnectionState() {
        return mConnectionState;
    }

    public static void setmConnectionState(int mConnectionState) {
        BluetoothLeService.mConnectionState = mConnectionState;
    }
}
