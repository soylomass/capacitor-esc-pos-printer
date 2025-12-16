package com.getcapacitor.community.escposprinter;

import static org.junit.Assert.*;

import com.getcapacitor.community.escposprinter.printers.constants.PrinterErrorCode;

import org.junit.Test;

/**
 * Unit tests for ESC/POS Printer Plugin.
 * 
 * These tests run on the JVM (not on device) and test pure Java logic.
 */
public class PluginUnitTests {

    // ==========================================================================
    // PrinterErrorCode Tests
    // ==========================================================================

    @Test
    public void testPrinterErrorCodes_haveExpectedValues() {
        assertEquals("CONNECT should be 1", 1, PrinterErrorCode.CONNECT);
        assertEquals("NOT_CONENCTED should be 2", 2, PrinterErrorCode.NOT_CONENCTED);
        assertEquals("SEND should be 3", 3, PrinterErrorCode.SEND);
        assertEquals("READ should be 4", 4, PrinterErrorCode.READ);
        assertEquals("PERMISSIONS should be 5", 5, PrinterErrorCode.PERMISSIONS);
        assertEquals("DEVICE_NOT_FOUND should be 6", 6, PrinterErrorCode.DEVICE_NOT_FOUND);
    }

    // ==========================================================================
    // Address Parsing Logic Tests
    // ==========================================================================

    @Test
    public void testAddressParsing_validFullFormat() {
        String address = "1234:5678:001";
        String[] parts = address.split(":");
        
        assertEquals("Should have 3 parts", 3, parts.length);
        assertEquals("Vendor ID", "1234", parts[0]);
        assertEquals("Product ID", "5678", parts[1]);
        assertEquals("Device name", "001", parts[2]);
    }

    @Test
    public void testAddressParsing_validShortFormat() {
        String address = "1234:5678";
        String[] parts = address.split(":");
        
        assertEquals("Should have 2 parts", 2, parts.length);
        assertEquals("Vendor ID", "1234", parts[0]);
        assertEquals("Product ID", "5678", parts[1]);
    }

    @Test
    public void testAddressParsing_invalidFormat() {
        String address = "invalid";
        String[] parts = address.split(":");
        
        assertEquals("Should have 1 part", 1, parts.length);
        // This format should be rejected by the printer
    }

    @Test
    public void testVendorProductIdParsing_valid() {
        String vendorStr = "1234";
        String productStr = "5678";
        
        int vendorId = Integer.parseInt(vendorStr);
        int productId = Integer.parseInt(productStr);
        
        assertEquals("Vendor ID should parse correctly", 1234, vendorId);
        assertEquals("Product ID should parse correctly", 5678, productId);
    }

    @Test(expected = NumberFormatException.class)
    public void testVendorProductIdParsing_invalid() {
        String vendorStr = "invalid";
        Integer.parseInt(vendorStr);
    }

    // ==========================================================================
    // Device Key Building Tests
    // ==========================================================================

    @Test
    public void testDeviceKeyExtraction_fullPath() {
        String devicePath = "/dev/bus/usb/001/002";
        String[] parts = devicePath.split("/");
        String deviceNamePart = parts[parts.length - 1];
        
        assertEquals("Should extract last segment", "002", deviceNamePart);
    }

    @Test
    public void testDeviceKeyExtraction_simplePath() {
        String devicePath = "002";
        String deviceNamePart = devicePath;
        if (devicePath.contains("/")) {
            String[] parts = devicePath.split("/");
            deviceNamePart = parts[parts.length - 1];
        }
        
        assertEquals("Should keep simple path as-is", "002", deviceNamePart);
    }

    @Test
    public void testBuildDeviceKey() {
        int vendorId = 1234;
        int productId = 5678;
        String deviceNamePart = "002";
        
        String key = vendorId + ":" + productId + ":" + deviceNamePart;
        
        assertEquals("Device key should match expected format", "1234:5678:002", key);
    }
}

