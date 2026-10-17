// Captures Binance BTCUSDT spot klines for BinanceGoldenTest. Public market data, no key.
// Run once: node tools/capture-binance-golden.mjs
// Writes src/test/resources/golden/binance-btcusdt-spot/<set>/<interval>.csv

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = "https://data-api.binance.vision/api/v3/klines";
const SYMBOL = "BTCUSDT";
const LIMIT = 1000;
const PAUSE_MS = 300;

const SETS = {
  minutes: {
    from: "2024-02-28T00:00:00Z",
    to: "2024-03-01T00:00:00Z",
    intervals: ["1m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d"],
  },
  hours: {
    from: "2024-01-01T00:00:00Z",
    to: "2024-03-01T00:00:00Z",
    intervals: ["1h", "4h", "6h", "8h", "12h", "1d"],
  },
  days: {
    from: "2023-01-01T00:00:00Z",
    to: "2025-01-01T00:00:00Z",
    intervals: ["1d", "3d", "1w", "1M"],
  },
};

const root = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "src/test/resources/golden/binance-btcusdt-spot",
);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let requests = 0;

// Bars opening in [from, to); Binance's endTime is inclusive on open time.
async function klines(interval, from, to) {
  const rows = [];
  let start = from;
  while (start < to) {
    const url = `${BASE}?symbol=${SYMBOL}&interval=${interval}&startTime=${start}&endTime=${to - 1}&limit=${LIMIT}`;
    const response = await fetch(url);
    requests += 1;
    if (!response.ok) {
      throw new Error(`${interval} ${start}: HTTP ${response.status} ${await response.text()}`);
    }
    const page = await response.json();
    rows.push(...page);
    if (page.length < LIMIT) {
      break;
    }
    start = page[page.length - 1][0] + 1;
    await sleep(PAUSE_MS);
  }
  return rows;
}

for (const [name, set] of Object.entries(SETS)) {
  const from = Date.parse(set.from);
  const to = Date.parse(set.to);
  for (const interval of set.intervals) {
    const rows = await klines(interval, from, to);
    const lines = ["openTime,open,high,low,close,volume,quoteVolume"];
    for (const [openTime, open, high, low, close, volume, , quoteVolume] of rows) {
      lines.push([openTime, open, high, low, close, volume, quoteVolume].join(","));
    }
    const file = join(root, name, `${interval}.csv`);
    await mkdir(dirname(file), { recursive: true });
    await writeFile(file, lines.join("\n") + "\n");
    console.log(`${name}/${interval}: ${rows.length} bars`);
    await sleep(PAUSE_MS);
  }
}
console.log(`done, ${requests} requests`);
