import type { PermissionState } from '@capacitor/core';

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

/**
 * Result from network printer discovery (TCP port sweep).
 */
export interface NetworkDevicesResult {
  devices: NetworkDeviceInfo[];
}

/**
 * Information about a discovered network printer candidate.
 */
export interface NetworkDeviceInfo {
  /** Stable identifier for the device (format: "ip:port") */
  id: string;
  /** Human-readable name */
  name: string;
  /** IPv4 address */
  ip: string;
  /** TCP port the device answered on */
  port: number;
}

/**
 * Result from probing a network printer.
 * Never rejects for unreachable devices — reachability IS the result.
 */
export interface NetworkProbeResult {
  /** Whether a TCP connection could be established */
  reachable: boolean;
  /** Whether the device answered the DLE EOT status request */
  supportsDleEot: boolean;
  /** Decoded status flags when the device answered (e.g. "PAPER_OUT", "OFFLINE", "ERROR") */
  statusDetail?: string[];
}

/**
 * Result from getCapabilities(): what the installed NATIVE build supports.
 * Native code only updates with an app-store release, so the JS side must
 * feature-detect instead of assuming the npm package version matches.
 */
export interface PrinterCapabilities {
  /** Native implementation version (kept in sync with the npm version at release time) */
  nativeVersion: string;
  /** Supported printer transports */
  transports: ('usb' | 'bluetooth' | 'network')[];
  /** Supported optional features */
  features: ('networkScan' | 'networkProbe' | 'dleEotStatusCheck')[];
}

/* Options */

export interface CreatePrinterOptions {
  connectionType: PrinterConnectionType;
  /**
   * Address/identifier for the printer:
   * - Bluetooth: MAC address (e.g., "00:11:22:33:44:55")
   * - USB: Device identifier (e.g., "1234:5678:002")
   * - Network: IP address and optional port, "host[:port]" (e.g., "192.168.1.100:9100", default port 9100)
   */
  address: string;
  /**
   * Network only: run a DLE EOT status check after each send, failing the job
   * when the printer reports paper-out/offline/error. Only enable it for
   * printers that answered the DLE EOT probe (see probeNetworkPrinter) —
   * printers that ignore it are unaffected either way, but enabling it
   * without a probe adds a pointless 300ms wait per job.
   */
  statusCheck?: boolean;
  [key: string]: unknown;
}

export interface GetNetworkPrinterDevicesOptions {
  /** TCP port to sweep (default 9100) */
  port?: number;
  /** Per-host connect timeout in ms (default 500) */
  timeoutMs?: number;
}

export interface ProbeNetworkPrinterOptions {
  /** Network printer address, "host[:port]" (default port 9100) */
  address: string;
  /** Bytes to send as the status probe (default DLE EOT n=1: [0x10, 0x04, 0x01]) */
  probeBytes?: number[];
}

export interface SendToPrinterOptions extends WithHashKey {
  data: number[];
  waitingTime?: number;
}

/**
 * Permission status for the plugin's declared permission aliases.
 */
export interface PrinterPermissionStatus {
  /** BLUETOOTH_CONNECT + BLUETOOTH_SCAN runtime permissions (Android 12+) */
  bluetooth: PermissionState;
}

/* Plugin */

export interface EscPosPrinterPlugin {
  /* Permissions (auto-generated on Android from the plugin's permission aliases) */
  /**
   * Checks the current state of the plugin's runtime permissions WITHOUT
   * prompting the user. Useful to keep passive/startup paths prompt-free.
   */
  checkPermissions(): Promise<PrinterPermissionStatus>;
  /**
   * Requests the plugin's runtime permissions, prompting the user if needed.
   */
  requestPermissions(): Promise<PrinterPermissionStatus>;

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

  /* Network methods (Android only) */
  /**
   * Discovers network printers by sweeping each local /24 subnet on the given
   * port (default 9100). Only one scan may run at a time.
   * @platform Android
   */
  getNetworkPrinterDevices(options?: GetNetworkPrinterDevicesOptions): Promise<NetworkDevicesResult>;
  /**
   * Probes a network printer: TCP connect + status request (DLE EOT by
   * default). Never rejects for unreachable devices.
   * @platform Android
   */
  probeNetworkPrinter(options: ProbeNetworkPrinterOptions): Promise<NetworkProbeResult>;

  /* Capabilities */
  /**
   * Reports what the installed NATIVE build supports, for feature detection
   * (native code only updates with an app-store release).
   */
  getCapabilities(): Promise<PrinterCapabilities>;

  /* Printer management methods */
  createPrinter(options: CreatePrinterOptions): Promise<ValueResult<string>>;
  disposePrinter(options: WithHashKey): Promise<ValueResult<boolean>>;
  isPrinterConnected(options: WithHashKey): Promise<ValueResult<boolean>>;
  connectPrinter(options: WithHashKey): Promise<void>;
  disconnectPrinter(options: WithHashKey): Promise<void>;
  sendToPrinter(options: SendToPrinterOptions): Promise<void>;
  readFromPrinter(options: WithHashKey): Promise<ValueResult<number[]>>;
}
