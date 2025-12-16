import { PrinterConnectionType } from '../enums/printer-connection-type';

import { BasePrinter } from './base-printer';

/**
 * USB ESC/POS Printer class for Android platform.
 *
 * @example
 * ```typescript
 * import { UsbPrinter } from '@fedejm/capacitor-esc-pos-printer';
 *
 * // Create printer with USB device identifier
 * const printer = new UsbPrinter('1234:5678:002');
 *
 * // Link to native and connect
 * await printer.link();
 * await printer.connect();
 *
 * // Send data
 * await printer.send([0x1B, 0x40]); // ESC @ (initialize)
 * await printer.send([0x48, 0x65, 0x6C, 0x6C, 0x6F]); // "Hello"
 *
 * // Disconnect and dispose
 * await printer.disconnect();
 * await printer.dispose();
 * ```
 *
 * @platform Android
 */
export class UsbPrinter extends BasePrinter {
  /**
   * Creates a new USB printer instance.
   *
   * @param address USB device identifier in format "vendorId:productId" or "vendorId:productId:deviceName"
   *                This identifier can be obtained from `EscPosPrinter.getUsbPrinterDevices()`
   */
  constructor(address: string) {
    super({
      connectionType: PrinterConnectionType.Usb,
      address,
    });
  }
}

