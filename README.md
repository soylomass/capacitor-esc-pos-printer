# capacitor-esc-pos-printer

CapacitorJS wrapper for ESC POS (native) printers.

## Features

- **Bluetooth printing** - Connect to Bluetooth ESC/POS printers (Android, iOS)
- **USB printing** - Connect to USB ESC/POS printers via USB Host API (Android only)
- **Raw data sending** - Send raw ESC/POS commands for full control
- **Bi-directional communication** - Read responses from printer
- **Permission management** - Built-in USB permission request flow

## Install

```bash
npm install @fedejm/capacitor-esc-pos-printer
npx cap sync
```

## Platform Support

| Feature    | Android | iOS     | Web     |
|------------|---------|---------|---------|
| Bluetooth  | Yes     | Yes     | Limited |
| USB        | Yes     | No      | No      |
| Network    | Planned | Planned | Planned |

### Android Requirements

- **Bluetooth**: Requires `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions (Android 12+)
- **USB**: Requires `android.hardware.usb.host` feature (automatically declared as optional)

### iOS Requirements

- Bluetooth printing is supported via CoreBluetooth framework
- USB printing is not available on iOS

## Usage

### Bluetooth Printer

```typescript
import { EscPosPrinter, BluetoothPrinter } from '@fedejm/capacitor-esc-pos-printer';

// Request Bluetooth enable (Android)
await EscPosPrinter.requestBluetoothEnable();

// Get paired Bluetooth printers
const { devices } = await EscPosPrinter.getBluetoothPrinterDevices();
console.log('Found printers:', devices);

// Create and connect to a printer
const printer = new BluetoothPrinter(devices[0].address);
await printer.link();
await printer.connect();

// Send ESC/POS commands
await printer.send([0x1B, 0x40]); // Initialize printer
await printer.send([0x48, 0x65, 0x6C, 0x6C, 0x6F]); // "Hello"
await printer.send([0x0A]); // Line feed

// Disconnect
await printer.disconnect();
await printer.dispose();
```

### USB Printer (Android only)

```typescript
import { EscPosPrinter, UsbPrinter } from '@fedejm/capacitor-esc-pos-printer';

// Get connected USB printers
const { devices } = await EscPosPrinter.getUsbPrinterDevices();
console.log('Found USB printers:', devices);

// Find a device and check permission status
const device = devices[0];
if (!device.hasPermission) {
  // Request USB permission
  const { value: granted } = await EscPosPrinter.requestUsbPermission({ 
    address: device.id 
  });
  
  if (!granted) {
    console.log('USB permission denied for:', device.name);
    return;
  }
}

// Create and connect to a USB printer
const printer = new UsbPrinter(device.id);
await printer.link();
await printer.connect();

// Send ESC/POS commands (same as Bluetooth)
await printer.send([0x1B, 0x40]); // Initialize printer
await printer.send([0x48, 0x65, 0x6C, 0x6C, 0x6F]); // "Hello"
await printer.send([0x0A]); // Line feed

// Disconnect
await printer.disconnect();
await printer.dispose();
```

## Connection Lifecycle

The printer connection follows this lifecycle:

```
┌─────────┐     link()     ┌────────┐    connect()    ┌───────────┐
│ Created ├───────────────►│ Linked ├─────────────────►│ Connected │
└─────────┘                └────────┘                  └───────────┘
                                                            │
                                                    disconnect()
                                                            │
                                                            ▼
                           ┌─────────┐   dispose()   ┌────────────┐
                           │Disposed │◄──────────────┤Disconnected│
                           └─────────┘               └────────────┘
```

1. **Create** - Instantiate `BluetoothPrinter` or `UsbPrinter` with device address
2. **Link** - Register with native plugin (`link()`)
3. **Connect** - Open connection to physical device (`connect()`)
4. **Send/Read** - Perform I/O operations (`send()`, `read()`)
5. **Disconnect** - Close connection (`disconnect()`)
6. **Dispose** - Unregister from plugin (`dispose()`)

## API

<docgen-index>

* [`requestBluetoothEnable()`](#requestbluetoothenable)
* [`getBluetoothPrinterDevices()`](#getbluetoothprinterdevices)
* [`getUsbPrinterDevices()`](#getusbprinterdevices)
* [`requestUsbPermission(...)`](#requestusbpermission)
* [`createPrinter(...)`](#createprinter)
* [`disposePrinter(...)`](#disposeprinter)
* [`isPrinterConnected(...)`](#isprinterconnected)
* [`connectPrinter(...)`](#connectprinter)
* [`disconnectPrinter(...)`](#disconnectprinter)
* [`sendToPrinter(...)`](#sendtoprinter)
* [`readFromPrinter(...)`](#readfromprinter)
* [Interfaces](#interfaces)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### requestBluetoothEnable()

```typescript
requestBluetoothEnable() => Promise<ValueResult<boolean>>
```

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;boolean&gt;&gt;</code>

--------------------


### getBluetoothPrinterDevices()

```typescript
getBluetoothPrinterDevices() => Promise<BluetoothDevicesResult>
```

**Returns:** <code>Promise&lt;<a href="#bluetoothdevicesresult">BluetoothDevicesResult</a>&gt;</code>

--------------------


### getUsbPrinterDevices()

```typescript
getUsbPrinterDevices() => Promise<UsbDevicesResult>
```

Discovers USB devices that could be ESC/POS printers.
Returns devices with bulk OUT endpoints that may be suitable for printing.

**Returns:** <code>Promise&lt;<a href="#usbdevicesresult">UsbDevicesResult</a>&gt;</code>

--------------------


### requestUsbPermission(...)

```typescript
requestUsbPermission(options: WithAddress) => Promise<ValueResult<boolean>>
```

Requests USB permission for a specific device.
Note: May require user interaction via system UI.

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withaddress">WithAddress</a></code> |

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;boolean&gt;&gt;</code>

--------------------


### createPrinter(...)

```typescript
createPrinter(options: CreatePrinterOptions) => Promise<ValueResult<string>>
```

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#createprinteroptions">CreatePrinterOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;string&gt;&gt;</code>

--------------------


### disposePrinter(...)

```typescript
disposePrinter(options: WithHashKey) => Promise<ValueResult<boolean>>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withhashkey">WithHashKey</a></code> |

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;boolean&gt;&gt;</code>

--------------------


### isPrinterConnected(...)

```typescript
isPrinterConnected(options: WithHashKey) => Promise<ValueResult<boolean>>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withhashkey">WithHashKey</a></code> |

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;boolean&gt;&gt;</code>

--------------------


### connectPrinter(...)

```typescript
connectPrinter(options: WithHashKey) => Promise<void>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withhashkey">WithHashKey</a></code> |

--------------------


### disconnectPrinter(...)

```typescript
disconnectPrinter(options: WithHashKey) => Promise<void>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withhashkey">WithHashKey</a></code> |

--------------------


### sendToPrinter(...)

```typescript
sendToPrinter(options: SendToPrinterOptions) => Promise<void>
```

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#sendtoprinteroptions">SendToPrinterOptions</a></code> |

--------------------


### readFromPrinter(...)

```typescript
readFromPrinter(options: WithHashKey) => Promise<ValueResult<number[]>>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#withhashkey">WithHashKey</a></code> |

**Returns:** <code>Promise&lt;<a href="#valueresult">ValueResult</a>&lt;number[]&gt;&gt;</code>

--------------------


### Interfaces


#### ValueResult

| Prop        | Type           |
| ----------- | -------------- |
| **`value`** | <code>T</code> |


#### BluetoothDevicesResult

| Prop          | Type                                                                                                                |
| ------------- | ------------------------------------------------------------------------------------------------------------------- |
| **`devices`** | <code>{ address: string; alias?: string; name: string; bondState: number; type: number; uuids: string[]; }[]</code> |


#### UsbDevicesResult

Result from USB device discovery.
Contains list of USB devices that could be ESC/POS printers.

| Prop          | Type                         |
| ------------- | ---------------------------- |
| **`devices`** | <code>UsbDeviceInfo[]</code> |


#### UsbDeviceInfo

Information about a discovered USB device.

| Prop                   | Type                 | Description                                                                |
| ---------------------- | -------------------- | -------------------------------------------------------------------------- |
| **`id`**               | <code>string</code>  | Stable identifier for the device (format: "vendorId:productId:deviceName") |
| **`name`**             | <code>string</code>  | Human-readable name (product name or fallback)                             |
| **`vendorId`**         | <code>number</code>  | USB Vendor ID                                                              |
| **`productId`**        | <code>number</code>  | USB Product ID                                                             |
| **`deviceClass`**      | <code>number</code>  | USB Device Class                                                           |
| **`deviceSubclass`**   | <code>number</code>  | USB Device Subclass                                                        |
| **`deviceName`**       | <code>string</code>  | System device name/path                                                    |
| **`manufacturerName`** | <code>string</code>  | Manufacturer name if available                                             |
| **`hasPermission`**    | <code>boolean</code> | Whether the app has USB permission for this device                         |


#### WithAddress

| Prop          | Type                |
| ------------- | ------------------- |
| **`address`** | <code>string</code> |


#### CreatePrinterOptions

| Prop                 | Type                                                                    | Description                                                                                                                                                                                                    |
| -------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`connectionType`** | <code><a href="#printerconnectiontype">PrinterConnectionType</a></code> |                                                                                                                                                                                                                |
| **`address`**        | <code>string</code>                                                     | Address/identifier for the printer: - Bluetooth: MAC address (e.g., "00:11:22:33:44:55") - USB: Device identifier (e.g., "1234:5678:002") - Network: IP address and optional port (e.g., "192.168.1.100:9100") |


#### WithHashKey

| Prop          | Type                |
| ------------- | ------------------- |
| **`hashKey`** | <code>string</code> |


#### SendToPrinterOptions

| Prop              | Type                  |
| ----------------- | --------------------- |
| **`data`**        | <code>number[]</code> |
| **`waitingTime`** | <code>number</code>   |


### Enums


#### PrinterConnectionType

| Members         | Value                    |
| --------------- | ------------------------ |
| **`Bluetooth`** | <code>'bluetooth'</code> |
| **`Usb`**       | <code>'usb'</code>       |
| **`Network`**   | <code>'network'</code>   |

</docgen-api>

## Error Handling

All printer operations may throw a `PrinterError` with a specific error code:

```typescript
import { PrinterError, PrinterErrorCode } from '@fedejm/capacitor-esc-pos-printer';

try {
  await printer.connect();
} catch (error) {
  if (error instanceof PrinterError) {
    switch (error.code) {
      case PrinterErrorCode.Connect:
        console.log('Connection failed');
        break;
      case PrinterErrorCode.NotConnected:
        console.log('Printer not connected');
        break;
      case PrinterErrorCode.Send:
        console.log('Failed to send data');
        break;
      case PrinterErrorCode.Read:
        console.log('Failed to read data');
        break;
      case PrinterErrorCode.Permissions:
        console.log('Permission denied');
        break;
      case PrinterErrorCode.DeviceNotFound:
        console.log('Device not found');
        break;
    }
  }
}
```

### Error Code Reference

| Code | Name           | Value | Description                                                   |
|------|----------------|-------|---------------------------------------------------------------|
| 1    | Connect        | 1     | Failed to establish connection to the printer                 |
| 2    | NotConnected   | 2     | Attempted operation on disconnected printer                   |
| 3    | Send           | 3     | Failed to send data to the printer                            |
| 4    | Read           | 4     | Failed to read data from the printer                          |
| 5    | Permissions    | 5     | Required permission not granted (Bluetooth or USB)            |
| 6    | DeviceNotFound | 6     | Device not found or no longer available                       |

## USB Permissions (Android)

USB devices require explicit permission from the user. The plugin provides a complete permission flow:

### Checking Permission

```typescript
const { devices } = await EscPosPrinter.getUsbPrinterDevices();
const device = devices.find(d => d.vendorId === 0x0483); // Find by vendor ID

if (device && !device.hasPermission) {
  console.log('Permission required for:', device.name);
}
```

### Requesting Permission

```typescript
// Request permission - triggers system dialog
const { value: granted } = await EscPosPrinter.requestUsbPermission({
  address: device.id
});

if (granted) {
  console.log('Permission granted!');
  // Refresh device list to update permission status
  const { devices: updated } = await EscPosPrinter.getUsbPrinterDevices();
} else {
  console.log('Permission denied by user');
}
```

### Permission Flow Diagram

```
┌─────────────────────┐
│ getUsbPrinterDevices│
└──────────┬──────────┘
           │
           ▼
    ┌──────────────┐
    │hasPermission?│
    └──────┬───────┘
           │
     ┌─────┴─────┐
     │           │
    Yes         No
     │           │
     ▼           ▼
┌─────────┐  ┌────────────────────┐
│ Connect │  │requestUsbPermission│
└─────────┘  └─────────┬──────────┘
                       │
                       ▼
              ┌──────────────────┐
              │System Permission │
              │    Dialog        │
              └────────┬─────────┘
                       │
                 ┌─────┴─────┐
                 │           │
              Granted     Denied
                 │           │
                 ▼           ▼
            ┌─────────┐  ┌───────┐
            │ Connect │  │ Error │
            └─────────┘  └───────┘
```

### Notes on USB Permission

- Permission is granted per-device and persists until the app is uninstalled
- If the device is unplugged and replugged, permission may need to be re-requested
- The permission dialog is a system UI and cannot be customized
- Permission requests are idempotent - multiple calls for already-permitted devices return immediately

## Reading from Printer

Some printers support bi-directional communication. The `read()` method is best-effort:

```typescript
// Send status request command
await printer.send([0x10, 0x04, 0x01]); // DLE EOT 1 (transmit status)

// Read response
const response = await printer.read();
if (response.length > 0) {
  console.log('Printer status:', response);
} else {
  console.log('No response (printer may not support reading)');
}
```

**Note**: Many USB ESC/POS printers do not implement a read endpoint. The `read()` method will return an empty array in such cases without throwing an error.

## Future: Network Printing

Network/TCP printing is planned for a future release. The API design is already prepared:

```typescript
// Future API (not yet implemented)
const printer = new NetworkPrinter('192.168.1.100:9100');
await printer.link();
await printer.connect();
// ... same send/read API
```

The `PrinterConnectionType.Network` enum value is already defined for forward compatibility.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and contribution guidelines.

## License

MIT
