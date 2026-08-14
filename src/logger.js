import fs from 'node:fs';
import path from 'node:path';

const LOGS_DIR = process.env.LOGS_DIR || './logs';
fs.mkdirSync(LOGS_DIR, { recursive: true });

const appLogPath = path.join(LOGS_DIR, 'app.log');
const errorLogPath = path.join(LOGS_DIR, 'error.log');
export const accessLogPath = path.join(LOGS_DIR, 'access.log');

function writeLine(filePath, line) {
  fs.appendFile(filePath, line + '\n', () => {}); // best-effort, non bloquant
}

function format(level, message, meta) {
  return JSON.stringify({
    time: new Date().toISOString(),
    level,
    message,
    ...(meta ? { meta } : {}),
  });
}

export const logger = {
  info(message, meta) {
    const line = format('info', message, meta);
    writeLine(appLogPath, line);
    console.log(`[info] ${message}`, meta ?? '');
  },
  warn(message, meta) {
    const line = format('warn', message, meta);
    writeLine(appLogPath, line);
    console.warn(`[warn] ${message}`, meta ?? '');
  },
  error(message, meta) {
    const line = format('error', message, meta);
    writeLine(appLogPath, line);
    writeLine(errorLogPath, line);
    console.error(`[error] ${message}`, meta ?? '');
  },
};

// Flux d'écriture pour morgan (logs HTTP), ouvert une fois au démarrage
export const accessLogStream = fs.createWriteStream(accessLogPath, { flags: 'a' });
