package com.getcapacitor.community.escposprinter.printers;

import android.util.Log;

import com.getcapacitor.community.escposprinter.printers.constants.PrinterErrorCode;
import com.getcapacitor.community.escposprinter.printers.exceptions.PrinterException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TCP (port 9100 style) ESC/POS printer.
 *
 * Semantics mirror the desktop print bridge's TcpDriver:
 * - Connect-per-job sockets, never persistent: a write into a long-dead
 *   persistent connection "succeeds" into the kernel buffer, and most thermal
 *   printers only accept one TCP session at a time.
 * - Optional probe-gated DLE EOT status check after each send: catches
 *   paper-out/offline states that would otherwise be silent false successes.
 *   Only enabled for printers that proved DLE EOT support during an add-time
 *   probe — silent-but-healthy printers are never punished.
 *
 * Error phases map to retry safety on the JS side:
 * - connect failure  -> PrinterErrorCode.CONNECT (no bytes left: retry-safe)
 * - write failure    -> PrinterErrorCode.SEND    (bytes may have left: non-idempotent)
 * - status reported  -> PrinterErrorCode.STATUS  (job buffered on device: non-idempotent)
 */
public class NetworkPrinter extends BasePrinter {
    private static final String TAG = "NetworkPrinter";

    static final int CONNECT_TIMEOUT_MS = 4000;
    /** A printer that accepts the connection but stops reading must not wedge its queue forever. */
    static final int SEND_WATCHDOG_MS = 30000;
    static final int DLE_EOT_WINDOW_MS = 300;
    /** DLE EOT n=1: transmit printer status. */
    public static final byte[] DLE_EOT_PROBE = new byte[] { 0x10, 0x04, 0x01 };

    /**
     * Shared watchdog scheduler for all network printers. Each task captures
     * ITS OWN job's socket reference, so a late-firing watchdog can only ever
     * close an already-closed per-job socket, never another job's live one.
     */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("EscPosPrinter-net-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final String host;
    private final int port;
    private final boolean statusCheck;

    public NetworkPrinter(String host, int port, boolean statusCheck) {
        this.host = host;
        this.port = port;
        this.statusCheck = statusCheck;
    }

    /**
     * Liveness probe kept for API completeness: connect + close. The JS side
     * skips connect for network printers because send() is self-contained.
     */
    @Override
    public void connect() throws PrinterException {
        try {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            } finally {
                closeQuietly(socket);
            }
        } catch (IOException e) {
            throw new PrinterException(PrinterErrorCode.CONNECT, "Could not connect to " + host + ":" + port);
        }
    }

    /** Network printers hold no persistent connection. */
    @Override
    public boolean isConnected() {
        return false;
    }

    /** No persistent connection to tear down. */
    @Override
    public void disconnect() {
        // no-op
    }

    @Override
    public byte[] read() throws PrinterException {
        throw new PrinterException(PrinterErrorCode.READ, "Not supported for network printers");
    }

    /**
     * Fully self-contained send: connect -> write+flush -> optional DLE EOT
     * status check -> wait -> close. Does NOT reuse BasePrinter.send(): the
     * base class sleeps an extra data.length/16 ms which is meant for slow
     * serial-ish transports; TCP flushes are effectively instant.
     */
    @Override
    public void send(byte[] data, int addWaitingTime) throws PrinterException {
        final Socket socket = new Socket();
        // The watchdog closes THIS job's socket if the peer accepts the
        // connection but stops reading; closing unblocks the writer thread.
        final ScheduledFuture<?> watchdog = WATCHDOG.schedule(
                () -> closeQuietly(socket),
                SEND_WATCHDOG_MS,
                TimeUnit.MILLISECONDS
        );

        try {
            // Phase 1: connect (no bytes left yet -> retry-safe failure)
            try {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
            } catch (IOException e) {
                Log.w(TAG, "connect failed for " + host + ":" + port + ": " + e.getMessage());
                throw new PrinterException(PrinterErrorCode.CONNECT, "Could not connect to " + host + ":" + port);
            }

            // Phase 2: write (bytes may have left -> non-idempotent failure)
            try {
                OutputStream out = socket.getOutputStream();
                out.write(data);
                out.flush();
            } catch (IOException e) {
                Log.w(TAG, "send failed for " + host + ":" + port + ": " + e.getMessage());
                throw new PrinterException(PrinterErrorCode.SEND, e.getMessage());
            }

            // Phase 3: device confirmation, only when the setup probe proved
            // DLE EOT support (mirrors the bridge: probe right after the
            // write, BEFORE any wait; silence is treated as success).
            if (statusCheck) {
                checkDleEotStatus(socket);
            }

            // Phase 4: wait. Plain addWaitingTime, deliberately WITHOUT the
            // base class's data.length/16 addend.
            if (addWaitingTime > 0) {
                try {
                    Thread.sleep(addWaitingTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            watchdog.cancel(false);
            closeQuietly(socket);
        }
    }

    /**
     * Writes the DLE EOT n=1 probe and reads one status byte within a short
     * window. No reply -> success (transient silence must never fail a
     * healthy job). A reply with problem bits set -> STATUS failure.
     */
    private void checkDleEotStatus(Socket socket) throws PrinterException {
        try {
            InputStream in = socket.getInputStream();

            // Drain any pending input first: some printers emit unsolicited
            // ASB (auto status back) bytes that would be mistaken for the
            // probe's answer.
            int available = in.available();
            while (available > 0) {
                long skipped = in.skip(available);
                if (skipped <= 0) {
                    break;
                }
                available = in.available();
            }

            OutputStream out = socket.getOutputStream();
            out.write(DLE_EOT_PROBE);
            out.flush();

            socket.setSoTimeout(DLE_EOT_WINDOW_MS);
            int status;
            try {
                status = in.read();
            } catch (SocketTimeoutException e) {
                return; // silence -> success
            }
            if (status < 0) {
                return; // stream ended without an answer -> success
            }

            List<String> detail = decodeDleEotStatus(status);
            if (!detail.isEmpty()) {
                Log.w(TAG, "printer reported status problem: " + detail + " (" + host + ":" + port + ")");
                throw new PrinterException(
                        PrinterErrorCode.STATUS,
                        "La impresora reportó un problema (papel/estado)"
                );
            }
        } catch (IOException e) {
            // The job bytes were already flushed; a failed status read must
            // not fail the job (parity with the bridge's silent tolerance).
            Log.w(TAG, "DLE EOT status check failed: " + e.getMessage());
        }
    }

    /** Decode the DLE EOT n=1 status byte into human flags. */
    public static List<String> decodeDleEotStatus(int statusByte) {
        List<String> detail = new ArrayList<>();
        if ((statusByte & 0x08) != 0) {
            detail.add("OFFLINE");
        }
        if ((statusByte & 0x20) != 0) {
            detail.add("PAPER_OUT");
        }
        if ((statusByte & 0x40) != 0) {
            detail.add("ERROR");
        }
        return detail;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // ignore
        }
    }
}
