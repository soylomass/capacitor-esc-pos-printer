package com.getcapacitor.community.escposprinter;

import static org.junit.Assert.*;

import android.content.Context;
import android.hardware.usb.UsbManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.getcapacitor.community.escposprinter.printers.UsbPrinter;
import com.getcapacitor.community.escposprinter.printers.constants.PrinterErrorCode;
import com.getcapacitor.community.escposprinter.printers.exceptions.PrinterException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for UsbPrinter.
 * 
 * These tests validate the UsbPrinter implementation:
 * - Address parsing
 * - Connection lifecycle
 * - Error handling
 * 
 * Note: Some tests require a physical USB printer to be connected.
 * Tests that require hardware are marked with @RequiresDevice annotation comment.
 */
@RunWith(AndroidJUnit4.class)
public class UsbPrinterTests {
    private Context context;
    private UsbManager usbManager;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    // ==========================================================================
    // Address Parsing Tests
    // ==========================================================================

    @Test
    public void testUsbPrinterCreation_withValidAddress() {
        // Valid address format: vendorId:productId:deviceName
        UsbPrinter printer = new UsbPrinter(context, "1234:5678:001");
        assertNotNull("UsbPrinter should be created with valid address", printer);
        assertFalse("New printer should not be connected", printer.isConnected());
    }

    @Test
    public void testUsbPrinterCreation_withShortAddress() {
        // Short address format: vendorId:productId
        UsbPrinter printer = new UsbPrinter(context, "1234:5678");
        assertNotNull("UsbPrinter should be created with short address", printer);
    }

    @Test
    public void testUsbPrinterConnect_withInvalidAddress_throwsDeviceNotFound() {
        // Invalid device should throw DEVICE_NOT_FOUND
        UsbPrinter printer = new UsbPrinter(context, "9999:9999:999");
        
        try {
            printer.connect();
            fail("Expected PrinterException to be thrown for invalid device");
        } catch (PrinterException e) {
            assertEquals("Error code should be DEVICE_NOT_FOUND", 
                PrinterErrorCode.DEVICE_NOT_FOUND, e.getErrorCode());
        }
    }

    @Test
    public void testUsbPrinterConnect_withMalformedAddress_throwsDeviceNotFound() {
        // Malformed address should fail gracefully
        UsbPrinter printer = new UsbPrinter(context, "invalid-address");
        
        try {
            printer.connect();
            fail("Expected PrinterException to be thrown for malformed address");
        } catch (PrinterException e) {
            assertEquals("Error code should be DEVICE_NOT_FOUND", 
                PrinterErrorCode.DEVICE_NOT_FOUND, e.getErrorCode());
        }
    }

    @Test
    public void testUsbPrinterConnect_withEmptyAddress_throwsDeviceNotFound() {
        UsbPrinter printer = new UsbPrinter(context, "");
        
        try {
            printer.connect();
            fail("Expected PrinterException to be thrown for empty address");
        } catch (PrinterException e) {
            assertEquals("Error code should be DEVICE_NOT_FOUND", 
                PrinterErrorCode.DEVICE_NOT_FOUND, e.getErrorCode());
        }
    }

    @Test
    public void testUsbPrinterConnect_withNullAddress_throwsDeviceNotFound() {
        UsbPrinter printer = new UsbPrinter(context, null);
        
        try {
            printer.connect();
            fail("Expected PrinterException to be thrown for null address");
        } catch (PrinterException e) {
            assertEquals("Error code should be DEVICE_NOT_FOUND", 
                PrinterErrorCode.DEVICE_NOT_FOUND, e.getErrorCode());
        }
    }

    // ==========================================================================
    // Lifecycle Tests
    // ==========================================================================

    @Test
    public void testUsbPrinterDisconnect_whenNotConnected_doesNotThrow() {
        UsbPrinter printer = new UsbPrinter(context, "1234:5678:001");
        
        // Disconnecting a non-connected printer should not throw
        printer.disconnect();
        assertFalse("Printer should still be disconnected", printer.isConnected());
    }

    @Test
    public void testUsbPrinterDisconnect_multipleCallsSafe() {
        UsbPrinter printer = new UsbPrinter(context, "1234:5678:001");
        
        // Multiple disconnects should be safe (idempotent)
        printer.disconnect();
        printer.disconnect();
        printer.disconnect();
        
        assertFalse("Printer should remain disconnected", printer.isConnected());
    }

    // ==========================================================================
    // USB Manager Tests
    // ==========================================================================

    @Test
    public void testUsbManagerAvailable() {
        assertNotNull("UsbManager should be available on Android", usbManager);
    }

    @Test
    public void testUsbDeviceListAccessible() {
        // Should be able to get device list without error
        var deviceList = usbManager.getDeviceList();
        assertNotNull("Device list should not be null", deviceList);
    }

    // ==========================================================================
    // Hardware-Dependent Tests (Manual Testing)
    // ==========================================================================

    /**
     * @RequiresDevice: USB printer must be connected
     * 
     * Manual test: Connect a USB ESC/POS printer and run this test.
     * The test will list all connected USB devices for verification.
     */
    @Test
    public void testListConnectedUsbDevices() {
        var deviceList = usbManager.getDeviceList();
        
        System.out.println("=== Connected USB Devices ===");
        for (var entry : deviceList.entrySet()) {
            var device = entry.getValue();
            System.out.println("Device: " + device.getDeviceName());
            System.out.println("  Vendor ID: " + device.getVendorId());
            System.out.println("  Product ID: " + device.getProductId());
            System.out.println("  Device Class: " + device.getDeviceClass());
            System.out.println("  Product Name: " + device.getProductName());
            System.out.println("  Has Permission: " + usbManager.hasPermission(device));
            System.out.println();
        }
        
        // This test always passes - it's for manual verification
        assertTrue(true);
    }
}

