// Copies the native SDK sources into this package, so apps install one npm
// package and autolinking builds everything: no Podfile or Gradle edits.
import { cpSync, rmSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const sdk = join(root, '..');

const copies = [
    [join(sdk, 'ios/Sources/Helpium'), join(root, 'ios/vendor/Helpium')],
    [join(sdk, 'android/helpium/src/main/java/io/helpium/sdk'), join(root, 'android/src/main/java/io/helpium/sdk')],
    [join(sdk, 'android/helpium/src/main/res'), join(root, 'android/src/main/res')],
];

for (const [from, to] of copies) {
    rmSync(to, { recursive: true, force: true });
    mkdirSync(dirname(to), { recursive: true });
    cpSync(from, to, { recursive: true });
}

console.log('Helpium native sources synced.');
