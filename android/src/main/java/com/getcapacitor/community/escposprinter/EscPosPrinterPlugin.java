package com.getcapacitor.community.escposprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.community.escposprinter.printers.BasePrinter;
import com.getcapacitor.community.escposprinter.printers.BluetoothPrinter;
import com.getcapacitor.community.escposprinter.printers.UsbPrinter;
import com.getcapacitor.community.escposprinter.printers.exceptions.PrinterException;

import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

@SuppressWarnings("unused")
@CapacitorPlugin(
        name = "EscPosPrinter",
        permissions = {
                @Permission(
                        alias = "bluetooth",
                        strings = {
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }
                )
        }
)
public class EscPosPrinterPlugin extends Plugin {
    private static final String TAG = "EscPosPrinterPlugin";
    private static final String ACTION_USB_PERMISSION = "com.getcapacitor.community.escposprinter.USB_PERMISSION";

    private BluetoothAdapter bluetoothAdapter;
    private final Map<String, BasePrinter> printersMap = new ConcurrentHashMap<>();

    /**
     * Per-printer single-thread executors.
     *
     * Goals:
     * - Allow concurrent printing to different printers (e.g. ticket + labels).
     * - Preserve ordering and prevent interleaving for the SAME printer.
     *
     * Keyed by the Capacitor printer hashKey (created by createPrinter()).
     */
    private final Map<String, ExecutorService> printerExecutors = new ConcurrentHashMap<>();
    
    // USB permission request tracking
    private final Map<String, PluginCall> pendingUsbPermissionCalls = new ConcurrentHashMap<>();
    private BroadcastReceiver usbPermissionReceiver;
    private boolean receiverRegistered = false;

    private ExecutorService getOrCreatePrinterExecutor(String hashKey) {
        return printerExecutors.computeIfAbsent(hashKey, (key) -> {
            final String suffix = key.length() > 8 ? key.substring(0, 8) : key;
            ThreadFactory tf = r -> {
                Thread t = new Thread(r);
                t.setName("EscPosPrinter-" + suffix);
                t.setDaemon(true);
                return t;
            };
            return Executors.newSingleThreadExecutor(tf);
        });
    }

    // ==========================================================================
    // Plugin Lifecycle
    // ==========================================================================

    @Override
    public void load() {
        super.load();
        registerUsbPermissionReceiver();
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        unregisterUsbPermissionReceiver();

        // Stop all per-printer executors (best-effort). Any pending JS calls are moot during teardown.
        for (ExecutorService executor : printerExecutors.values()) {
            try {
                executor.shutdownNow();
            } catch (Exception ignored) {
                // ignore
            }
        }
        printerExecutors.clear();
        
        // Clear any pending permission calls
        for (PluginCall call : pendingUsbPermissionCalls.values()) {
            call.reject("Plugin destroyed before USB permission response");
        }
        pendingUsbPermissionCalls.clear();
    }

    // ==========================================================================
    // USB Permission Receiver
    // ==========================================================================

    private void registerUsbPermissionReceiver() {
        if (receiverRegistered) {
            return;
        }

        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    handleUsbPermissionResult(intent);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(usbPermissionReceiver, filter);
        }
        
        receiverRegistered = true;
        Log.d(TAG, "USB permission receiver registered");
    }

    private void unregisterUsbPermissionReceiver() {
        if (receiverRegistered && usbPermissionReceiver != null) {
            try {
                getContext().unregisterReceiver(usbPermissionReceiver);
                Log.d(TAG, "USB permission receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver already unregistered: " + e.getMessage());
            }
            receiverRegistered = false;
        }
    }

    private void handleUsbPermissionResult(Intent intent) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

        if (device == null) {
            Log.w(TAG, "USB permission result received but device is null");
            return;
        }

        // Build the device key to find the pending call
        String deviceKey = buildDeviceKey(device);
        PluginCall pendingCall = pendingUsbPermissionCalls.remove(deviceKey);

        if (pendingCall == null) {
            // Try alternate key formats (in case of matching variations)
            String altKey1 = device.getVendorId() + ":" + device.getProductId();
            pendingCall = pendingUsbPermissionCalls.remove(altKey1);
        }

        if (pendingCall != null) {
            JSObject data = new JSObject();
            data.put("value", granted);
            pendingCall.resolve(data);
            
            Log.d(TAG, "USB permission " + (granted ? "granted" : "denied") + 
                " for device: " + device.getDeviceName());
        } else {
            Log.w(TAG, "No pending call found for USB permission result: " + device.getDeviceName());
        }
    }

    private String buildDeviceKey(UsbDevice device) {
        String deviceNamePart = device.getDeviceName();
        if (deviceNamePart.contains("/")) {
            String[] parts = deviceNamePart.split("/");
            deviceNamePart = parts[parts.length - 1];
        }
        return device.getVendorId() + ":" + device.getProductId() + ":" + deviceNamePart;
    }

    // ==========================================================================
    // Bluetooth Methods
    // ==========================================================================

    @SuppressWarnings("unused")
    @PluginMethod
    public void requestBluetoothEnable(PluginCall call) {
        if (!assertBluetoothAdapter(call)) {
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            var data = new JSObject();
            data.put("value", true);
            call.resolve(data);
        } else {
            if (!assertBluetoothPermission(call)) {
                return;
            }
            var enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(call, enableBluetoothIntent, "requestBluetoothEnableResultCallback");
        }
    }

    @ActivityCallback
    private void requestBluetoothEnableResultCallback(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        var resultCode = result.getResultCode();
        var data = new JSObject();
        data.put("value", resultCode == Activity.RESULT_OK);
        call.resolve(data);
    }

    @SuppressWarnings("unused")
    @SuppressLint("MissingPermission")
    @PluginMethod
    public void getBluetoothPrinterDevices(PluginCall call) {
        if (!assertBluetoothAdapter(call) || !assertBluetoothEnabled(call) || !assertBluetoothPermission(call)) {
            return;
        }

        var devicesArray = new JSArray();
        var bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices != null) {
            for (var device : bondedDevices) {
                var bluetoothClass = device.getBluetoothClass();
                var majorClassType = bluetoothClass.getMajorDeviceClass();
                var deviceType = bluetoothClass.getDeviceClass();

                // From https://inthehand.github.io/html/T_InTheHand_Net_Bluetooth_DeviceClass.htm
                // 1664 - Imaging printer
                var isPrinterDevice = majorClassType == BluetoothClass.Device.Major.IMAGING && 
                    (deviceType == 1664 || deviceType == BluetoothClass.Device.Major.IMAGING);
                if (!isPrinterDevice) {
                    continue;
                }

                var uuidsArray = new JSArray();
                var servicesUuids = device.getUuids();
                if (servicesUuids != null) {
                    for (var uuid : servicesUuids) {
                        uuidsArray.put(uuid.toString());
                    }
                }

                var deviceObject = new JSObject();
                devicesArray.put(deviceObject);

                deviceObject.put("address", device.getAddress());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    deviceObject.put("alias", device.getAlias());
                }
                deviceObject.put("name", device.getName());
                deviceObject.put("bondState", device.getBondState());
                deviceObject.put("type", device.getType());
                deviceObject.put("uuids", uuidsArray);
            }
        }

        var data = new JSObject();
        data.put("devices", devicesArray);
        call.resolve(data);
    }

    // ==========================================================================
    // USB Methods
    // ==========================================================================

    @SuppressWarnings("unused")
    @PluginMethod
    public void getUsbPrinterDevices(PluginCall call) {
        var usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            call.reject("USB service not available");
            return;
        }

        var devicesArray = new JSArray();
        var deviceList = usbManager.getDeviceList();

        for (var device : deviceList.values()) {
            // Check if device could be a printer
            if (!isPotentialPrinter(device)) {
                continue;
            }

            var deviceObject = new JSObject();

            // Create stable identifier: vendorId:productId:deviceNamePart
            String deviceKey = buildDeviceKey(device);
            deviceObject.put("id", deviceKey);

            // Name: use product name if available, otherwise device name
            var name = device.getProductName();
            if (name == null || name.isEmpty()) {
                name = "USB Device " + device.getVendorId() + ":" + device.getProductId();
            }
            deviceObject.put("name", name);

            deviceObject.put("vendorId", device.getVendorId());
            deviceObject.put("productId", device.getProductId());
            deviceObject.put("deviceClass", device.getDeviceClass());
            deviceObject.put("deviceSubclass", device.getDeviceSubclass());
            deviceObject.put("deviceName", device.getDeviceName());

            // Optional manufacturer name
            var manufacturerName = device.getManufacturerName();
            if (manufacturerName != null && !manufacturerName.isEmpty()) {
                deviceObject.put("manufacturerName", manufacturerName);
            }

            // Check if we have permission
            deviceObject.put("hasPermission", usbManager.hasPermission(device));

            devicesArray.put(deviceObject);

            Log.d(TAG, "Found USB device: " + name + 
                " (VID:" + device.getVendorId() + " PID:" + device.getProductId() + 
                " Class:" + device.getDeviceClass() + " Permission:" + usbManager.hasPermission(device) + ")");
        }

        var data = new JSObject();
        data.put("devices", devicesArray);
        call.resolve(data);
    }

    /**
     * Checks if a USB device could potentially be a printer.
     * Returns true for:
     * - Devices with USB_CLASS_PRINTER
     * - Devices with interfaces that have bulk OUT endpoints (common for ESC/POS printers)
     */
    private boolean isPotentialPrinter(UsbDevice device) {
        // Check device class
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_PRINTER) {
            return true;
        }

        // Check each interface
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            var iface = device.getInterface(i);
            
            // Check interface class
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                return true;
            }

            // Check for bulk OUT endpoint (many ESC/POS printers use this)
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                var endpoint = iface.getEndpoint(j);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Request USB permission for a specific device.
     * 
     * Behavior:
     * - If permission already granted: resolves immediately with { value: true }
     * - If not granted: triggers OS permission dialog, then resolves with { value: true/false }
     * - If device not found: rejects with error
     * 
     * This method is idempotent - safe to call multiple times.
     */
    @SuppressWarnings("unused")
    @PluginMethod
    public void requestUsbPermission(PluginCall call) {
        var address = call.getString("address");
        if (address == null || address.isEmpty()) {
            call.reject("Address is required");
            return;
        }

        var usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            call.reject("USB service not available");
            return;
        }

        // Find device by address
        var device = findUsbDeviceByAddress(usbManager, address);
        if (device == null) {
            call.reject("USB device not found: " + address);
            return;
        }

        // Check if permission already granted
        if (usbManager.hasPermission(device)) {
            var data = new JSObject();
            data.put("value", true);
            call.resolve(data);
            Log.d(TAG, "USB permission already granted for: " + address);
            return;
        }

        // Ensure receiver is registered
        registerUsbPermissionReceiver();

        // Store the pending call keyed by device identifier
        String deviceKey = buildDeviceKey(device);
        
        // Check if there's already a pending request for this device
        PluginCall previousCall = pendingUsbPermissionCalls.put(deviceKey, call);
        if (previousCall != null) {
            // Reject the existing call and replace with new one
            previousCall.reject("Superseded by new permission request");
        }

        // Create PendingIntent for the permission request
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(getContext().getPackageName());
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            getContext(),
            device.getDeviceId(),
            intent,
            flags
        );

        // Request permission - the result will be delivered via the BroadcastReceiver
        Log.d(TAG, "Requesting USB permission for: " + address);
        usbManager.requestPermission(device, pendingIntent);
    }

    /**
     * Finds a USB device by address string.
     * Address format: "vendorId:productId" or "vendorId:productId:deviceNamePart"
     */
    private UsbDevice findUsbDeviceByAddress(UsbManager usbManager, String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        var parts = address.split(":");
        if (parts.length < 2) {
            return null;
        }

        int vendorId;
        int productId;
        String deviceNamePart = parts.length >= 3 ? parts[2] : null;

        try {
            vendorId = Integer.parseInt(parts[0]);
            productId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        var deviceList = usbManager.getDeviceList();
        for (var device : deviceList.values()) {
            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                if (deviceNamePart != null && !deviceNamePart.isEmpty()) {
                    if (device.getDeviceName().endsWith("/" + deviceNamePart) ||
                        device.getDeviceName().equals(deviceNamePart)) {
                        return device;
                    }
                } else {
                    // No device name specified, return first match
                    return device;
                }
            }
        }

        return null;
    }

    // ==========================================================================
    // Printer Management Methods
    // ==========================================================================

    @SuppressWarnings("unused")
    @PluginMethod
    public void createPrinter(PluginCall call) {
        var hashKey = UUID.randomUUID().toString();
        var address = call.getString("address");
        var connectionType = call.getString("connectionType", "bluetooth");

        BasePrinter printer;

        switch (connectionType) {
            case "bluetooth": {
                if (!assertBluetoothAdapter(call)) {
                    return;
                }
                printer = new BluetoothPrinter(bluetoothAdapter, address);
                break;
            }
            case "usb": {
                if (address == null || address.isEmpty()) {
                    call.reject("Address is required for USB connection");
                    return;
                }
                printer = new UsbPrinter(getContext(), address);
                break;
            }
            default: {
                call.reject("Connection type not known: " + connectionType);
                return;
            }
        }

        printersMap.put(hashKey, printer);

        var data = new JSObject();
        data.put("value", hashKey);
        call.resolve(data);
    }

    @SuppressWarnings("unused")
    @PluginMethod
    public void disposePrinter(PluginCall call) {
        var hashKey = call.getString("hashKey");
        if (hashKey == null) {
            call.reject("hashKey is required.");
            return;
        }

        // Prevent new operations from being enqueued for this printer.
        var printer = printersMap.remove(hashKey);
        final boolean hasPrinter = printer != null;

        // Serialize dispose after any in-flight sends for this same printer.
        final BasePrinter finalPrinter = printer;
        final ExecutorService executor = getOrCreatePrinterExecutor(hashKey);

        try {
            executor.execute(() -> {
                try {
                    if (finalPrinter != null) {
                        finalPrinter.disconnect();
                    }
                } finally {
                    try {
                        executor.shutdown();
                    } catch (Exception ignored) {
                        // ignore
                    }
                    try {
                        printerExecutors.remove(hashKey);
                    } catch (Exception ignored) {
                        // ignore
                    }

                    var data = new JSObject();
                    data.put("value", hasPrinter);
                    call.resolve(data);
                }
            });
        } catch (Exception e) {
            // Executor rejected (already shutting down): fallback to best-effort direct cleanup.
            try {
                if (finalPrinter != null) {
                    finalPrinter.disconnect();
                }
            } catch (Exception ignored) {
                // ignore
            }
            try {
                executor.shutdown();
            } catch (Exception ignored) {
                // ignore
            }
            try {
                printerExecutors.remove(hashKey);
            } catch (Exception ignored) {
                // ignore
            }

            var data = new JSObject();
            data.put("value", hasPrinter);
            call.resolve(data);
        }
    }

    @SuppressWarnings("unused")
    @PluginMethod
    public void isPrinterConnected(PluginCall call) {
        var printer = getGuardedPrinterByHash(call);
        if (printer == null) {
            return;
        }

        var data = new JSObject();
        data.put("value", printer.isConnected());
        call.resolve(data);
    }

    @SuppressWarnings("unused")
    @PluginMethod
    public void connectPrinter(PluginCall call) {
        var printer = getGuardedPrinterByHash(call);
        if (printer == null) {
            return;
        }

        try {
            // Only check Bluetooth permissions for BluetoothPrinter
            if (printer instanceof BluetoothPrinter) {
                if (!assertBluetoothEnabled(call) || !assertBluetoothPermission(call)) {
                    return;
                }
            }

            printer.connect();

            call.resolve();
        } catch (PrinterException e) {
            rejectWithPrinterException(call, e);
        }
    }

    @SuppressWarnings("unused")
    @PluginMethod
    public void disconnectPrinter(PluginCall call) {
        var hashKey = call.getString("hashKey");
        if (hashKey == null) {
            call.reject("hashKey is required.");
            return;
        }

        var printer = printersMap.get(hashKey);
        if (printer == null) {
            call.reject("Printer with hash " + hashKey + " not found.");
            return;
        }

        // Serialize disconnect after any in-flight sends for this same printer.
        final BasePrinter finalPrinter = printer;
        final ExecutorService executor = getOrCreatePrinterExecutor(hashKey);

        try {
            executor.execute(() -> {
                try {
                    finalPrinter.disconnect();
                    call.resolve();
                } catch (Exception e) {
                    call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            });
        } catch (Exception e) {
            // Best-effort fallback
            try {
                finalPrinter.disconnect();
            } catch (Exception ignored) {
                // ignore
            }
            call.resolve();
        }
    }

    @SuppressWarnings("unused")
    @PluginMethod
    public void sendToPrinter(PluginCall call) {
        var hashKey = call.getString("hashKey");
        if (hashKey == null) {
            call.reject("hashKey is required.");
            return;
        }

        var printer = printersMap.get(hashKey);
        if (printer == null) {
            call.reject("Printer with hash " + hashKey + " not found.");
            return;
        }

        var waitingTime = call.getInt("waitingTime", 0);
        var data = call.getArray("data");
        if (data == null) {
            call.reject("data is required.");
            return;
        }

        byte[] bytesArray = new byte[data.length()];
        for (var i = 0; i < bytesArray.length; i++) {
            bytesArray[i] = (byte)data.optInt(i);
        }

        // Serialize per-printer sends, but allow concurrency across printers.
        // This prevents long ticket prints from blocking label printers.
        final int finalWaitingTime = waitingTime != null ? waitingTime : 0;
        final byte[] finalBytes = bytesArray;

        var executor = getOrCreatePrinterExecutor(hashKey);

        try {
            executor.execute(() -> {
                try {
                    printer.send(finalBytes, finalWaitingTime);
                    call.resolve();
                } catch (PrinterException e) {
                    rejectWithPrinterException(call, e);
                } catch (Exception e) {
                    call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            });
        } catch (RejectedExecutionException e) {
            call.reject("Printer executor is shutting down.");
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    /**
     * Read available data from the printer.
     * 
     * Note: For USB printers, this is best-effort and may return empty arrays
     * if no data is available. Not all USB printers support read operations.
     */
    @SuppressWarnings("unused")
    @PluginMethod
    public void readFromPrinter(PluginCall call) {
        var printer = getGuardedPrinterByHash(call);
        if (printer == null) {
            return;
        }

        try {
            var bytes = printer.read();
            var bytesArray = new JSArray();
            for (var i = 0; i < bytes.length; i++) {
                try {
                    bytesArray.put(i, bytes[i]);
                } catch (JSONException e) {
                    // Ignore JSON errors for individual bytes
                }
            }

            var data = new JSObject();
            data.put("value", bytesArray);
            call.resolve(data);
        } catch (PrinterException e) {
            rejectWithPrinterException(call, e);
        }
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private boolean assertBluetoothAdapter(PluginCall call) {
        if (bluetoothAdapter == null) {
            var bluetoothAvailable = getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
            if (bluetoothAvailable) {
                var bluetoothManager = getContext().getSystemService(BluetoothManager.class);
                bluetoothAdapter = bluetoothManager.getAdapter();
            }
            if (bluetoothAdapter == null) {
                call.reject("Bluetooth is not available.");
                return false;
            }
        }
        return true;
    }

    private boolean assertBluetoothEnabled(PluginCall call) {
        if (!bluetoothAdapter.isEnabled()) {
            call.reject("Bluetooth is not enabled.");
            return false;
        }
        return true;
    }

    private boolean assertBluetoothPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getPermissionState("bluetooth") != PermissionState.GRANTED) {
            requestPermissionForAlias("bluetooth", call, "bluetoothPermissionCallback");
            return false;
        }
        return true;
    }

    @PermissionCallback
    private void bluetoothPermissionCallback(PluginCall call) {
        if (call == null) {
            return;
        }
        if (getPermissionState("bluetooth") == PermissionState.GRANTED) {
            var methodName = call.getMethodName();
            try {
                var method = this.getClass().getMethod(methodName, PluginCall.class);
                method.invoke(this, call);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                call.reject("Failed to invoke method after permission grant: " + e.getMessage());
            }
        } else {
            call.reject("Bluetooth permission not granted.");
        }
    }

    private BasePrinter getGuardedPrinterByHash(PluginCall call) {
        var hashKey = call.getString("hashKey");
        if (hashKey == null) {
            call.reject("hashKey is required.");
            return null;
        }
        var printer = printersMap.get(hashKey);
        if (printer == null) {
            call.reject("Printer with hash " + hashKey + " not found.");
            return null;
        }
        return printer;
    }

    private void rejectWithPrinterException(PluginCall call, PrinterException e) {
        var data = new JSObject();
        data.put("code", e.getErrorCode());
        call.reject(e.getMessage(), data);
    }
}
