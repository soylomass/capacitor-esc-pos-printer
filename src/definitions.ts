import type { PrinterConnectionType } from './enums/printer-connection-type';

/* Utils */

export interface WithHashKey {
  hashKey: string;
}

export interface WithAddress {
  address: string;
}

/* Results */

export interface ValueResult<T> {
  value: T;
}

export interface BluetoothDevicesResult {
  devices: {
    address: string;
    alias?: string;
    name: string;
    bondState: number;
    type: number;
    uuids: string[];
  }[];
}

/**
 * Result from USB device discovery.
 * Contains list of USB devices that could be ESC/POS printers.
 */
export interface UsbDevicesResult {
  devices: UsbDeviceInfo[];
}

/**
 * Information about a discovered USB device.
 */
export interface UsbDeviceInfo {
  /** Stable identifier for the device (format: "vendorId:productId:deviceName") */
  id: string;
  /** Human-readable name (product name or fallback) */
  name: string;
  /** USB Vendor ID */
  vendorId: number;
  /** USB Product ID */
  productId: number;
  /** USB Device Class */
  deviceClass: number;
  /** USB Device Subclass */
  deviceSubclass: number;
  /** System device name/path */
  deviceName: string;
  /** Manufacturer name if available */
  manufacturerName?: string;
  /** Whether the app has USB permission for this device */
  hasPermission: boolean;
}

/* Options */

export interface CreatePrinterOptions {
  connectionType: PrinterConnectionType;
  /**
   * Address/identifier for the printer:
   * - Bluetooth: MAC address (e.g., "00:11:22:33:44:55")
   * - USB: Device identifier (e.g., "1234:5678:002")
   * - Network: IP address and optional port (e.g., "192.168.1.100:9100")
   */
  address: string;
  [key: string]: unknown;
}

export interface SendToPrinterOptions extends WithHashKey {
  data: number[];
  waitingTime?: number;
}

/* Plugin */

export interface EscPosPrinterPlugin {
  /* Bluetooth methods */
  requestBluetoothEnable(): Promise<ValueResult<boolean>>;
  getBluetoothPrinterDevices(): Promise<BluetoothDevicesResult>;

  /* USB methods (Android only) */
  /**
   * Discovers USB devices that could be ESC/POS printers.
   * Returns devices with bulk OUT endpoints that may be suitable for printing.
   * @platform Android
   */
  getUsbPrinterDevices(): Promise<UsbDevicesResult>;
  /**
   * Requests USB permission for a specific device.
   * Note: May require user interaction via system UI.
   * @platform Android
   */
  requestUsbPermission(options: WithAddress): Promise<ValueResult<boolean>>;

  /* Printer management methods */
  createPrinter(options: CreatePrinterOptions): Promise<ValueResult<string>>;
  disposePrinter(options: WithHashKey): Promise<ValueResult<boolean>>;
  isPrinterConnected(options: WithHashKey): Promise<ValueResult<boolean>>;
  connectPrinter(options: WithHashKey): Promise<void>;
  disconnectPrinter(options: WithHashKey): Promise<void>;
  sendToPrinter(options: SendToPrinterOptions): Promise<void>;
  readFromPrinter(options: WithHashKey): Promise<ValueResult<number[]>>;
}
