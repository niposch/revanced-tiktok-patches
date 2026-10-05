# Traffic analysis takeaways

The investigation compared original TikTok 47.1.4 with the separate filtered package.
Local instrumentation, capture scripts and captured evidence are private. These notes
retain findings useful for future patch development without publishing that tooling
or any account/device data.

## Capture methods and controls

Java TLS and Cronet-option hooks did not reliably reach the active networking path
in the ARM-translated emulator app. Native callbacks in x86 BoringSSL libraries
captured TLS session keys for both original and Filtered startup traffic. The native
hook preserved any existing callback and restored it on clean shutdown; normal
capture did not change certificate verification or request identity.

A fresh app process allows collecting handshake secrets that attaching later can
miss. Preserve app data and working authentication sessions when planning controls.
Instrumentation can affect app behavior, so confirm a repair separately on a stock
control. Booting previously rooted data with a stock image is a weaker comparison
than restoring an untouched pre-root data image. Disk backing chains must be
preserved with their snapshots; copying only a QCOW layer may not preserve state.

## Decryption and reconstruction

TLS decryption reveals transport plaintext, not application-level encrypted messages.
Binary security reports remained opaque after HTTPS decryption. High entropy alone
does not identify their cipher, schema, compression or any root-detection flag.

Wireshark HTTP/2 exports can contain complete reassembled wire bytes plus a nested
decompressed copy. Concatenating both duplicates the payload and breaks gzip parsing.
Prefer the complete wire body; otherwise assemble only direct DATA fragments. Keep
connections, stream IDs and request/response directions distinct when reconstructing
exchanges. Validate the assembler on synthetic fragmented and compressed examples.

Export a minimal reviewed summary: public app metadata, operation labels, numeric
error codes and identifier-presence booleans. Raw URLs, headers, bodies, cookies,
TLS secrets and actual account/device identifiers remain private. An automated
allowlist does not prove that arbitrary captured traffic is safe to publish.

## Login investigation lessons

HTTP 200 is not enough: the failed registration responses returned zero IDs.
Check the response schema and registration readiness before attributing a login
error to a wrong password, a locked account or root detection. An operation label
records the requested test; it does not prove which endpoint the app called.

Changing only the verified AppLog registration package field restored usable IDs
and security SDK startup, without changing the certificate field. Successful
Filtered authentication was then confirmed on a stock control. See the
[registration findings](ROOT_SIGNAL_ANALYSIS.md) for scope, bytecode targets and
validation limits.

## Primary references

- [Frida Android setup](https://frida.re/docs/android/)
- [Frida instrumentation API](https://frida.re/docs/javascript-api/)
- [Wireshark TLS decryption](https://wiki.wireshark.org/TLS)
- [Magisk source](https://github.com/topjohnwu/Magisk)
