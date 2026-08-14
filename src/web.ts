import { WebPlugin } from '@capacitor/core';

import type {
  CreatePrinterOptions,
  EscPosPrinterPlugin,
  GetNetworkPrinterDevicesOptions,
  NetworkDevicesResult,
  NetworkProbeResult,
  PrinterCapabilities,
  PrinterPermissionStatus,
  ProbeNetworkPrinterOptions,
  ValueResult,
  WithHashKey,
  WithAddress,
  SendToPrinterOptions,
  BluetoothDevicesResult,
  UsbDevicesResult,
} from './definitions';

export class EscPosPrinterWeb extends WebPlugin implements EscPosPrinterPlugin {
  async checkPermissions(): Promise<PrinterPermissionStatus> {
    // Web has no Bluetooth printing: report "prompt" so passive discovery
    // paths (which only proceed on "granted") stay quiet.
    return { bluetooth: 'prompt' };
  }

  async requestPermissions(): Promise<PrinterPermissionStatus> {
    return { bluetooth: 'denied' };
  }

  async requestBluetoothEnable(): Promise<ValueResult<boolean>> {
    return { value: false };
  }

  async getBluetoothPrinterDevices(): Promise<BluetoothDevicesResult> {
    const devices = await navigator.bluetooth.getDevices();
    console.log('getBluetoothPrinterDevices', devices);
    return { devices: [] };
  }

  async getUsbPrinterDevices(): Promise<UsbDevicesResult> {
    // USB printing is not supported on web platform
    console.log('getUsbPrinterDevices: USB not supported on web');
    return { devices: [] };
  }

  async requestUsbPermission(_options: WithAddress): Promise<ValueResult<boolean>> {
    // USB printing is not supported on web platform
    console.log('requestUsbPermission: USB not supported on web');
    return { value: false };
  }

  async getNetworkPrinterDevices(_options?: GetNetworkPrinterDevicesOptions): Promise<NetworkDevicesResult> {
    // Raw TCP sockets are not available on web platform
    console.log('getNetworkPrinterDevices: network printing not supported on web');
    return { devices: [] };
  }

  async probeNetworkPrinter(_options: ProbeNetworkPrinterOptions): Promise<NetworkProbeResult> {
    // Raw TCP sockets are not available on web platform
    console.log('probeNetworkPrinter: network printing not supported on web');
    return { reachable: false, supportsDleEot: false, statusDetail: [] };
  }

  async getCapabilities(): Promise<PrinterCapabilities> {
    return { nativeVersion: '0.0.0-web', transports: [], features: [] };
  }

  async createPrinter(options: CreatePrinterOptions): Promise<ValueResult<string>> {
    console.log('createPrinter', JSON.stringify(options));
    return { value: '' };
  }

  async disposePrinter(options: WithHashKey): Promise<ValueResult<boolean>> {
    console.log('disposePrinter', JSON.stringify(options));
    return { value: false };
  }

  async isPrinterConnected(options: WithHashKey): Promise<ValueResult<boolean>> {
    console.log('isPrinterConnected', JSON.stringify(options));
    return { value: false };
  }

  async connectPrinter(options: WithHashKey): Promise<void> {
    console.log('connectPrinter', JSON.stringify(options));
  }

  async disconnectPrinter(options: WithHashKey): Promise<void> {
    console.log('disconnectPrinter', JSON.stringify(options));
  }

  async sendToPrinter(options: SendToPrinterOptions): Promise<void> {
    console.log('sendToPrinter', JSON.stringify(options));
  }

  async readFromPrinter(options: WithHashKey): Promise<ValueResult<number[]>> {
    console.log('readFromPrinter', JSON.stringify(options));
    return { value: [] };
  }
}
