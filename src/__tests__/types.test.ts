/**
 * TypeScript Compile Tests
 * 
 * This file validates that all public types are correctly exported and usable.
 * If this file compiles without errors, the public API is working correctly.
 * 
 * Run with: npx tsc --noEmit src/__tests__/types.test.ts
 */

import {
  // Plugin
  EscPosPrinter,
  
  // Enums
  PrinterConnectionType,
  PrinterErrorCode,
  
  // Printer classes
  BasePrinter,
  BluetoothPrinter,
  UsbPrinter,
  
  // Errors
  PrinterError,
} from '../index';

import type {
  // Types and interfaces
  EscPosPrinterPlugin,
  ValueResult,
  WithHashKey,
  WithAddress,
  BluetoothDevicesResult,
  UsbDevicesResult,
  UsbDeviceInfo,
  CreatePrinterOptions,
  SendToPrinterOptions,
} from '../index';

// ==========================================================================
// Type Assertions - Verify types are correctly defined
// ==========================================================================

// Verify PrinterConnectionType enum values
const _btType: PrinterConnectionType = PrinterConnectionType.Bluetooth;
const _usbType: PrinterConnectionType = PrinterConnectionType.Usb;
const _netType: PrinterConnectionType = PrinterConnectionType.Network;

// Verify enum values match string literals
const _btValue: 'bluetooth' = PrinterConnectionType.Bluetooth;
const _usbValue: 'usb' = PrinterConnectionType.Usb;
const _netValue: 'network' = PrinterConnectionType.Network;

// Verify PrinterErrorCode enum values
const _connectErr: PrinterErrorCode = PrinterErrorCode.Connect;
const _notConnErr: PrinterErrorCode = PrinterErrorCode.NotConnected;
const _sendErr: PrinterErrorCode = PrinterErrorCode.Send;
const _readErr: PrinterErrorCode = PrinterErrorCode.Read;
const _permErr: PrinterErrorCode = PrinterErrorCode.Permissions;
const _notFoundErr: PrinterErrorCode = PrinterErrorCode.DeviceNotFound;

// Verify error codes are numbers
const _errCode1: 1 = PrinterErrorCode.Connect;
const _errCode5: 5 = PrinterErrorCode.Permissions;
const _errCode6: 6 = PrinterErrorCode.DeviceNotFound;

// ==========================================================================
// Interface Shape Tests
// ==========================================================================

// Verify UsbDeviceInfo has all required fields
function checkUsbDeviceInfo(info: UsbDeviceInfo): void {
  const id: string = info.id;
  const name: string = info.name;
  const vendorId: number = info.vendorId;
  const productId: number = info.productId;
  const deviceClass: number = info.deviceClass;
  const deviceSubclass: number = info.deviceSubclass;
  const deviceName: string = info.deviceName;
  const hasPermission: boolean = info.hasPermission;
  
  // Optional field
  const manufacturerName: string | undefined = info.manufacturerName;
  
  console.log(id, name, vendorId, productId, deviceClass, deviceSubclass, deviceName, hasPermission, manufacturerName);
}

// Verify BluetoothDevicesResult structure
function checkBluetoothDevicesResult(result: BluetoothDevicesResult): void {
  for (const device of result.devices) {
    const address: string = device.address;
    const name: string = device.name;
    const bondState: number = device.bondState;
    const type: number = device.type;
    const uuids: string[] = device.uuids;
    const alias: string | undefined = device.alias;
    
    console.log(address, name, bondState, type, uuids, alias);
  }
}

// Verify UsbDevicesResult structure
function checkUsbDevicesResult(result: UsbDevicesResult): void {
  for (const device of result.devices) {
    checkUsbDeviceInfo(device);
  }
}

// ==========================================================================
// Plugin Method Signature Tests
// ==========================================================================

async function testPluginMethods(): Promise<void> {
  // Bluetooth methods
  const btEnableResult: ValueResult<boolean> = await EscPosPrinter.requestBluetoothEnable();
  const btDevices: BluetoothDevicesResult = await EscPosPrinter.getBluetoothPrinterDevices();
  
  // USB methods
  const usbDevices: UsbDevicesResult = await EscPosPrinter.getUsbPrinterDevices();
  const usbPermission: ValueResult<boolean> = await EscPosPrinter.requestUsbPermission({ address: '1234:5678' });
  
  // Printer lifecycle
  const createResult: ValueResult<string> = await EscPosPrinter.createPrinter({
    connectionType: PrinterConnectionType.Usb,
    address: '1234:5678:001',
  });
  
  const hashKey: string = createResult.value;
  
  const isConnected: ValueResult<boolean> = await EscPosPrinter.isPrinterConnected({ hashKey });
  await EscPosPrinter.connectPrinter({ hashKey });
  await EscPosPrinter.sendToPrinter({ hashKey, data: [0x1B, 0x40] });
  const readData: ValueResult<number[]> = await EscPosPrinter.readFromPrinter({ hashKey });
  await EscPosPrinter.disconnectPrinter({ hashKey });
  const disposed: ValueResult<boolean> = await EscPosPrinter.disposePrinter({ hashKey });
  
  console.log(btEnableResult, btDevices, usbDevices, usbPermission, isConnected, readData, disposed);
}

// ==========================================================================
// Printer Class Tests
// ==========================================================================

async function testPrinterClasses(): Promise<void> {
  // BluetoothPrinter
  const btPrinter = new BluetoothPrinter('00:11:22:33:44:55');
  await btPrinter.link();
  await btPrinter.connect();
  await btPrinter.send([0x1B, 0x40]);
  await btPrinter.disconnect();
  await btPrinter.dispose();
  
  // UsbPrinter
  const usbPrinter = new UsbPrinter('1234:5678:001');
  await usbPrinter.link();
  await usbPrinter.connect();
  await usbPrinter.send([0x1B, 0x40]);
  const response: number[] = await usbPrinter.read();
  await usbPrinter.disconnect();
  await usbPrinter.dispose();
  
  console.log(response);
}

// ==========================================================================
// Error Class Tests
// ==========================================================================

function testPrinterError(): void {
  const error = new PrinterError('Test error', PrinterErrorCode.Connect);
  
  const message: string = error.message;
  const code: PrinterErrorCode = error.code;
  
  // Verify it extends Error
  if (error instanceof Error) {
    const baseMessage: string = error.message;
    console.log(baseMessage);
  }
  
  console.log(message, code);
}

// ==========================================================================
// Interface usage verification
// ==========================================================================

// Verify WithHashKey interface
function useWithHashKey(opts: WithHashKey): string {
  return opts.hashKey;
}

// Verify WithAddress interface
function useWithAddress(opts: WithAddress): string {
  return opts.address;
}

// Verify CreatePrinterOptions interface
function useCreatePrinterOptions(opts: CreatePrinterOptions): void {
  const conn: PrinterConnectionType = opts.connectionType;
  const addr: string = opts.address;
  console.log(conn, addr);
}

// Verify SendToPrinterOptions interface
function useSendToPrinterOptions(opts: SendToPrinterOptions): void {
  const key: string = opts.hashKey;
  const data: number[] = opts.data;
  const wait: number | undefined = opts.waitingTime;
  console.log(key, data, wait);
}

// ==========================================================================
// Export type verification
// ==========================================================================

// Verify all exports are available
export {
  testPluginMethods,
  testPrinterClasses,
  testPrinterError,
  checkUsbDeviceInfo,
  checkBluetoothDevicesResult,
  checkUsbDevicesResult,
  useWithHashKey,
  useWithAddress,
  useCreatePrinterOptions,
  useSendToPrinterOptions,
};

// Suppress unused variable warnings for type assertions
void _btType;
void _usbType;
void _netType;
void _btValue;
void _usbValue;
void _netValue;
void _connectErr;
void _notConnErr;
void _sendErr;
void _readErr;
void _permErr;
void _notFoundErr;
void _errCode1;
void _errCode5;
void _errCode6;
