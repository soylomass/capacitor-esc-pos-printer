/**
 * Mock ESC/POS network printer (raw TCP :9100 sink) for E2E testing of the
 * plugin's NetworkPrinter without hardware.
 *
 * Behaviour:
 *  - accepts raw byte streams and appends them to a per-connection capture
 *    file under %TEMP%/mock-network-printer/<timestamp>-<n>.bin
 *  - handles every in-band DLE EOT n=1 status request (10 04 01) according to
 *    the --dle-eot mode. The 3 probe bytes are excluded from the capture file.
 *
 * Usage: node scripts/mock-network-printer.mjs [--port=9100] [--host=0.0.0.0] [--dle-eot=ok]
 *  --host lets several instances coexist on distinct loopback addresses
 *  (127.0.0.1, 127.0.0.2, ...) sharing the standard port 9100.
 *  --dle-eot modes:
 *    ok       (default) answer 0x16 — online, paper present
 *    silent   ignore the probe (printer without DLE EOT support)
 *    paperout answer 0x36 (0x16 | 0x20) — paper-out bit set
 */
import { createServer } from 'node:net';
import { appendFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = new Map(
  process.argv.slice(2).map(a => {
    const [k, v] = a.replace(/^--/, '').split('=');
    return [k, v];
  }),
);
const PORT = Number(args.get('port') ?? 9100);
const HOST = args.get('host') ?? '0.0.0.0';
const DLE_EOT_MODE = args.get('dle-eot') ?? 'ok';
if (!['ok', 'silent', 'paperout'].includes(DLE_EOT_MODE)) {
  console.error(`[mock-9100] invalid --dle-eot mode: ${DLE_EOT_MODE} (expected ok|silent|paperout)`);
  process.exit(1);
}

const DLE_EOT_PROBE = Buffer.from([0x10, 0x04, 0x01]);
const STATUS_BY_MODE = {
  ok: Buffer.from([0x16]),
  // 0x16 base with the PAPER_OUT bit (0x20) set
  paperout: Buffer.from([0x36]),
};

const captureDir = join(tmpdir(), 'mock-network-printer');
mkdirSync(captureDir, { recursive: true });

let connSeq = 0;

/** Strip every DLE EOT probe from the stream, answering each one per mode. */
function handleChunk(socket, chunk) {
  const kept = [];
  let rest = chunk;
  for (;;) {
    const at = rest.indexOf(DLE_EOT_PROBE);
    if (at === -1) break;
    kept.push(rest.subarray(0, at));
    if (DLE_EOT_MODE !== 'silent') {
      socket.write(STATUS_BY_MODE[DLE_EOT_MODE]);
    }
    rest = rest.subarray(at + DLE_EOT_PROBE.length);
  }
  kept.push(rest);
  return Buffer.concat(kept);
}

const server = createServer(socket => {
  connSeq += 1;
  const file = join(captureDir, `${Date.now()}-${connSeq}.bin`);
  let captured = 0;
  console.log(`[mock-9100] connection #${connSeq} from ${socket.remoteAddress}`);

  socket.on('data', chunk => {
    const payload = handleChunk(socket, chunk);
    if (payload.length > 0) {
      appendFileSync(file, payload);
      captured += payload.length;
    }
  });
  socket.on('close', () => {
    console.log(
      `[mock-9100] connection #${connSeq} closed (${captured} payload bytes${captured > 0 ? ` -> ${file}` : ''})`,
    );
  });
  socket.on('error', e => {
    console.error(`[mock-9100] connection #${connSeq} error`, e.message);
  });
});

server.listen(PORT, HOST, () => {
  console.log(
    `[mock-9100] listening on ${HOST}:${PORT} (dle-eot=${DLE_EOT_MODE}), captures in ${captureDir}`,
  );
});
