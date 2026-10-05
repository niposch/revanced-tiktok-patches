# Contributing

Keep patches focused and independently selectable. Use short sentence-case names,
concrete behavior descriptions, explicit dependencies and exact tested versions.
See [the convention review](docs/PATCH_CONVENTIONS.md).

Run `./build.ps1` for production behavior checks. For bytecode changes, run the
APK-derived validation commands in README against a locally supplied original APK.
Add tests for behavioral edge cases or instruction/branch risks rather than tests
that only mirror implementation. Document the trigger, observed result and limits.

Investigation tools, experimental patches and captured evidence stay in ignored
local storage. Publish only useful reviewed findings in Markdown; preserve working
account sessions and avoid submitting raw traffic or logs.
Use synthetic examples when a reproduction needs sensitive inputs. See
[the publication checklist](docs/RELEASING.md) before staging or sharing changes.

Use semantic commits, such as `feat(tiktok): add Shop video filtering` or
`fix(tiktok): preserve registration identity after package rename`. Update
`CHANGELOG.md` and `VERSION` for releases. This is a community bundle; do not imply
official ReVanced affiliation or copy proprietary APK-derived implementation.

All contributions are licensed GPL-3.0-only under the repository's LICENSE.
