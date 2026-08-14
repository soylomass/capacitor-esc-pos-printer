export enum PrinterErrorCode {
  Connect = 1,
  NotConnected = 2,
  Send = 3,
  Read = 4,
  Permissions = 5,
  DeviceNotFound = 6,
  /** The printer answered a status request reporting a problem (paper out / offline / error). */
  Status = 7,
}
