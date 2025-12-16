package com.getcapacitor.community.escposprinter.printers;

// https://developer.android.com/develop/connectivity/usb/host

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.getcapacitor.community.escposprinter.printers.constants.PrinterErrorCode;
import com.getcapacitor.community.escposprinter.printers.exceptions.PrinterException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * USB ESC/POS Printer implementation using Android USB Host API.
 * <p>
 * The address format is: "vendorId:productId:deviceName" where deviceName is optional
 * for disambiguation when multiple devices with same vendor/product IDs are connected.
 */
public class UsbPrinter extends BasePrinter {
    private static final String TAG = "UsbPrinter";
    private static final int BULK_TRANSFER_TIMEOUT_MS = 5000;

    private final Context context;
    private final String address;

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;
    private UsbEndpoint outEndpoint;
    private UsbEndpoint inEndpoint;

    /**
     * Creates a USB printer instance.
     *
     * @param context Application context for accessing UsbManager
     * @param address USB device identifier in format "vendorId:productId" or "vendorId:productId:deviceName"
     */
    public UsbPrinter(Context context, String address) {
        this.context = context;
        this.address = address;
    }

    @Override
    public boolean isConnected() {
        return connection != null && super.isConnected();
    }

    @Override
    public void connect() throws PrinterException {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new PrinterException(PrinterErrorCode.CONNECT, "USB service not available");
        }

        // Find the device by address
        device = findDeviceByAddress(address);
        if (device == null) {
            throw new PrinterException(PrinterErrorCode.DEVICE_NOT_FOUND, 
                "USB device not found: " + address);
        }

        // Check permission
        if (!usbManager.hasPermission(device)) {
            throw new PrinterException(PrinterErrorCode.PERMISSIONS, 
                "USB permission not granted for device: " + device.getDeviceName());
        }

        // Open connection
        connection = usbManager.openDevice(device);
        if (connection == null) {
            throw new PrinterException(PrinterErrorCode.CONNECT, 
                "Failed to open USB device connection");
        }

        try {
            // Find a suitable interface (printer class or fallback to bulk endpoints)
            usbInterface = findPrinterInterface(device);
            if (usbInterface == null) {
                throw new PrinterException(PrinterErrorCode.CONNECT, 
                    "No suitable USB interface found on device");
            }

            // Claim the interface
            if (!connection.claimInterface(usbInterface, true)) {
                throw new PrinterException(PrinterErrorCode.CONNECT, 
                    "Failed to claim USB interface");
            }

            // Find endpoints
            findEndpoints(usbInterface);
            if (outEndpoint == null) {
                throw new PrinterException(PrinterErrorCode.CONNECT, 
                    "No bulk OUT endpoint found on USB device");
            }

            // Create streams
            outputStream = new UsbOutputStream(connection, outEndpoint);
            if (inEndpoint != null) {
                inputStream = new UsbInputStream(connection, inEndpoint);
            } else {
                // Create a dummy input stream that returns no data
                inputStream = new EmptyInputStream();
            }

            Log.i(TAG, "Connected to USB printer: " + device.getDeviceName());

        } catch (PrinterException e) {
            cleanup();
            throw e;
        } catch (Exception e) {
            cleanup();
            throw new PrinterException(PrinterErrorCode.CONNECT, 
                "Error connecting to USB device: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        super.disconnect();
        cleanup();
        Log.i(TAG, "Disconnected from USB printer");
    }

    private void cleanup() {
        if (connection != null && usbInterface != null) {
            try {
                connection.releaseInterface(usbInterface);
            } catch (Exception e) {
                Log.w(TAG, "Error releasing USB interface: " + e.getMessage());
            }
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing USB connection: " + e.getMessage());
            }
            connection = null;
        }

        usbInterface = null;
        outEndpoint = null;
        inEndpoint = null;
        device = null;
    }

    /**
     * Finds a USB device by the address identifier.
     * Address format: "vendorId:productId" or "vendorId:productId:deviceName"
     */
    private UsbDevice findDeviceByAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        String[] parts = address.split(":");
        if (parts.length < 2) {
            Log.w(TAG, "Invalid address format: " + address);
            return null;
        }

        int vendorId;
        int productId;
        String deviceName = parts.length >= 3 ? parts[2] : null;

        try {
            vendorId = Integer.parseInt(parts[0]);
            productId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid vendor/product ID in address: " + address);
            return null;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                // If deviceName is specified, match it exactly
                if (deviceName != null && !deviceName.isEmpty()) {
                    if (device.getDeviceName().equals(deviceName) || 
                        device.getDeviceName().endsWith("/" + deviceName)) {
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

    /**
     * Finds a suitable USB interface for printing.
     * Prefers USB_CLASS_PRINTER interfaces, but falls back to any interface with bulk OUT endpoint.
     */
    private UsbInterface findPrinterInterface(UsbDevice device) {
        // First pass: look for printer class interface
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                Log.d(TAG, "Found printer class interface at index " + i);
                return iface;
            }
        }

        // Second pass: look for any interface with bulk OUT endpoint
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = iface.getEndpoint(j);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    Log.d(TAG, "Found interface with bulk OUT endpoint at index " + i);
                    return iface;
                }
            }
        }

        return null;
    }

    /**
     * Finds bulk IN and OUT endpoints on the given interface.
     */
    private void findEndpoints(UsbInterface iface) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = endpoint;
                    Log.d(TAG, "Found bulk OUT endpoint: " + endpoint.getEndpointNumber());
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                    Log.d(TAG, "Found bulk IN endpoint: " + endpoint.getEndpointNumber());
                }
            }
        }
    }

    /**
     * OutputStream implementation that writes to USB bulk OUT endpoint.
     */
    private class UsbOutputStream extends OutputStream {
        private final UsbDeviceConnection connection;
        private final UsbEndpoint endpoint;

        UsbOutputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
            this.connection = connection;
            this.endpoint = endpoint;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b });
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            write(buffer, 0, buffer.length);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            byte[] data;
            if (offset == 0 && length == buffer.length) {
                data = buffer;
            } else {
                data = new byte[length];
                System.arraycopy(buffer, offset, data, 0, length);
            }

            int result = connection.bulkTransfer(endpoint, data, data.length, BULK_TRANSFER_TIMEOUT_MS);
            if (result < 0) {
                throw new IOException("USB bulk transfer failed with result: " + result);
            }
            if (result != data.length) {
                Log.w(TAG, "USB bulk transfer incomplete: sent " + result + " of " + data.length + " bytes");
            }
        }

        @Override
        public void flush() throws IOException {
            // USB bulk transfers are synchronous, no buffering to flush
        }

        @Override
        public void close() throws IOException {
            // Connection is managed by UsbPrinter
        }
    }

    /**
     * InputStream implementation that reads from USB bulk IN endpoint.
     */
    private class UsbInputStream extends InputStream {
        private final UsbDeviceConnection connection;
        private final UsbEndpoint endpoint;
        private final int maxPacketSize;

        UsbInputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
            this.connection = connection;
            this.endpoint = endpoint;
            this.maxPacketSize = endpoint.getMaxPacketSize();
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int result = read(buffer, 0, 1);
            if (result <= 0) {
                return -1;
            }
            return buffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            byte[] tempBuffer = new byte[Math.min(length, maxPacketSize)];
            int result = connection.bulkTransfer(endpoint, tempBuffer, tempBuffer.length, BULK_TRANSFER_TIMEOUT_MS);
            if (result < 0) {
                // No data available or error - return 0 (no bytes read)
                return 0;
            }
            System.arraycopy(tempBuffer, 0, buffer, offset, result);
            return result;
        }

        @Override
        public int available() throws IOException {
            // USB doesn't have a reliable way to know available bytes without reading
            return 0;
        }

        @Override
        public void close() throws IOException {
            // Connection is managed by UsbPrinter
        }
    }

    /**
     * Empty InputStream for devices without bulk IN endpoint.
     */
    private static class EmptyInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            return -1;
        }

        @Override
        public int available() throws IOException {
            return 0;
        }
    }
}
