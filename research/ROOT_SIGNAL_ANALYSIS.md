# Device registration and login repair

## Demonstrated failure

Across two initial Filtered startup captures, all six
`/service/2/device_register/` responses were HTTP 200 with `device_id = 0` and
`install_id = 0`. Registration reported `com.zhiliaoapp.musically.filtered`,
aid 1233, app name `musical_ly`, version 47.1.4 and the actual patched APK's
certificate MD5. No MSSDK security requests appeared in those captures.

A controlled experiment changed only the AppLog registration `package` field
to `com.zhiliaoapp.musically`. The APK, certificate field and rooted baseline
were otherwise the same. Registration redirected with HTTP 307 and returned
HTTP 200 with nonzero device and installation IDs. Subsequent requests carried
populated IDs and MSSDK startup traffic appeared. No account login was submitted
in that registration experiment.

This isolates a package-field registration failure. A successful HTTP status alone
had concealed the unusable application-level response.

## What the production patch changes

**Fix device registration** targets `X.08An.LIZ(JSONObject):boolean` in classes5.dex,
the verified AppLog package/version header collector. It replaces eight
`JSONObject.put(String,Object)` invocations with a static helper using the same
three argument registers. The helper changes only the exact pair
`package = com.zhiliaoapp.musically.filtered` to the original package string.
Every other key/value passes through to the original JSON operation.

Numeric version writes, actual PackageManager lookups, `real_package_name`,
certificate hashing and unrelated metadata keep their behavior. The separate
Android installation identity stays unchanged. The helper has no installed-original
lookup, account access or credential handling. It does not bypass authentication.

`X.08At.LIZ(JSONObject):boolean` computes the certificate-byte MD5 into `sig_hash`.
The controlled package-only repair succeeded without changing it, so certificate
spoofing is not part of the production fix. Earlier installed-original identity
experiments did not establish a working repair and remain outside the public bundle.

## Validation

Runtime tests cover JSON chaining, null/removal/error behavior, unrelated packages,
`real_package_name` and unchanged certificate metadata. An APK-derived fixture
verifies all eight replacement calls, register order, unchanged code sizes and
branch positions, other references and exclusion of JVM fixtures.

The repaired APK used the previous installation's signing key. All 24,167
non-DEX, non-signature ZIP entries matched the earlier Filtered APK, including
manifest, resources and native libraries. Registration correction and both filter
activation logs appeared in a stock control with no Magisk daemon or Frida hooks.
Successful authentication was then confirmed after an email/password test request.
The precise challenge flow and successful authentication traffic were not captured.

The original app remained installed during the successful test, despite the fix
requiring no lookup of it. Explicit runtime validation with the original absent
remains outstanding. The repaired update also installed on a physical device;
authentication there has not yet been confirmed. Preserve working sessions during
future validation rather than resetting them for additional comparisons.

## Root detection: evidence and limits

Six initial decrypted sessions contained 391 reconstructed requests. Four original
app startups included 16 MSSDK requests to `/ms/dyn/task`, `/ms/get_seed`,
`/sdi/get_token` and `/ri/report`, all HTTP 200. Their binary bodies remained opaque
after TLS decryption. High byte entropy alone does not identify encryption,
compression, a message schema or root flags.

No outgoing decoded JSON/form/query root indicator was found. Matched root/risk
fields were server configuration, including `/common` strategy and unrelated UI
settings. A flag may still exist in opaque reports or signing headers.

The APK contains environment checks in `X.0im5` for root packages/files,
Frida/Xposed artifacts, mapped libraries, QEMU and boot verification state.
Dispatcher `X.0CwH.LIZ` exposes `is_root`, `is_hook`, `root_packages`, `root_paths`,
`hook_framework`, `is_qemu` and `qemu_paths`. This establishes detection capability;
it does not establish execution or passport enforcement during the login attempt.

The failed rooted account capture showed `/passport/oidc/login/`,
`/passport/auth/available_ways/` and `/passport/email/send_code/`, each HTTP 200 with
`data.error_code = 7`. It did not contain `/passport/user/login/`, despite the
requested password test. Signing headers existed and timestamps were close to
capture time; their presence does not establish valid cryptographic contents.

Rooted and stock comparisons differed in restored state, elapsed time and challenge
flow. Root alone was not established as the cause. Error 7 does not prove a wrong
password, an account-wide lockout or a root blacklist. Check registration readiness
before pursuing speculative signature or root changes.

Raw traffic, secrets and identifiers are private local evidence. This document
publishes only code targets, public app identifiers and aggregate outcomes.
