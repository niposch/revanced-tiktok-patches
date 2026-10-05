import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from CheckPublishable import ROOT, allowed, privacy_rules, review, verify_bundle


class PublicationTest(unittest.TestCase):
    def test_private_paths_and_artifacts_are_rejected(self):
        for name in ('tools/private.md', 'dist/test.rvp', 'research/device.xml',
                     'research/App.java', 'tests/__pycache__/test.py', 'scripts/local.pem',
                     'private/investigation/scripts/CaptureTikTok.py', 'scripts/CaptureTikTok.py',
                     'extension/src/app/revanced/tiktok/LoginCompatibility.java',
                     'patches/src/app/revanced/tiktok/patches/CaptureDiagnosticsPatch.java'):
            self.assertFalse(allowed(name), name)
        self.assertTrue(allowed('research/ROOT_SIGNAL_ANALYSIS.md'))
        self.assertTrue(allowed('scripts/ValidateRegistration.java'))

    def test_identity_rules_and_safe_examples(self):
        home = 'C:' + '/' + 'Users/' + 'Example Person/file'
        email = 'person' + '@' + 'unit.test'
        address = '.'.join(('10', '9', '8', '7'))
        pem = '-' * 5 + 'BEGIN CERTIFICATE' + '-' * 5
        self.assertIn('personal-home-path', privacy_rules(home))
        self.assertIn('non-example-email', privacy_rules(email))
        self.assertIn('non-example-ipv4', privacy_rules(address))
        self.assertIn('pem-material', privacy_rules(pem))
        self.assertIn('local-identity-token', privacy_rules('device: FAKE_DEVICE', ['FAKE_DEVICE']))
        self.assertFalse(privacy_rules('placeholder@example.org 127.0.0.1 192.0.2.4'))

    def test_reviews_do_not_echo_sensitive_values(self):
        from contextlib import redirect_stdout
        value = 'person' + '@' + 'unit.test'
        output = io.StringIO()
        with redirect_stdout(output), self.assertRaises(ValueError):
            review({'README.md': value.encode()})
        self.assertNotIn(value, output.getvalue())
        self.assertIn('non-example-email', output.getvalue())

    def test_bundle_rejects_certificate_and_stale_version(self):
        version = (ROOT / 'VERSION').read_text().strip()
        bundle = ROOT / f'dist/tiktok-feed-filters-{version}.rvp'
        if not bundle.exists():
            self.skipTest('Build the production bundle first')
        files = {path.relative_to(ROOT).as_posix(): path.read_bytes()
                 for folder in ('extension', 'patches', 'stubs')
                 for path in (ROOT / folder).rglob('*.java')}
        files.update({name: (ROOT / name).read_bytes() for name in ('VERSION', 'build.ps1')})
        verify_bundle(bundle, files)
        files['VERSION'] = b'0.0.0\n'
        with self.assertRaisesRegex(ValueError, 'version or source fingerprint'):
            verify_bundle(bundle, files)
        files['VERSION'] = (ROOT / 'VERSION').read_bytes()
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as temporary:
            changed = Path(temporary) / 'changed.rvp'
            changed.write_bytes(bundle.read_bytes())
            with zipfile.ZipFile(changed, 'a') as archive:
                archive.writestr('diagnostics/capture-ca.pem', b'fake fixture certificate')
            with self.assertRaisesRegex(ValueError, 'unexpected resources'):
                verify_bundle(changed, files)


if __name__ == '__main__':
    unittest.main()
