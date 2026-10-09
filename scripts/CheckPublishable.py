"""Review staged or committed source and a production bundle without printing secrets."""
import argparse
import hashlib
import ipaddress
import os
import re
import struct
import subprocess
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = {'.editorconfig', '.gitattributes', '.gitignore', 'README.md',
              'LICENSE', 'VERSION', 'build.ps1', 'CHANGELOG.md', 'CONTRIBUTING.md'}
RESEARCH_FILES = {'research/NOTES.md', 'research/ROOT_SIGNAL_ANALYSIS.md',
                  'research/EMULATOR_CAPTURE.md'}
PATCHES = {'TikTokPatches', 'AlongsidePatch', 'RegistrationIdentityPatch', 'EnableDownloadsPatch', 'ResourceRepairs'}
HELPERS = {'FeedFilter', 'RegistrationIdentity'}
PREFIX = 'app/revanced/tiktok/patches/'
PUBLIC_FILES = ROOT_FILES | RESEARCH_FILES | {
    'docs/PATCH_CONVENTIONS.md', 'docs/RELEASING.md',
    'scripts/CheckPublishable.py', 'scripts/PackageRelease.py',
    'scripts/ValidateExtension.java', 'scripts/ValidatePatches.java', 'scripts/ValidateResources.java',
    'scripts/ValidateRegistration.java', 'tests/FeedFilterTest.java',
    'tests/RegistrationIdentityTest.java', 'tests/PublicationTest.py',
    'stubs/src/android/util/Log.java', 'stubs/src/org/json/JSONObject.java',
    'stubs/src/org/json/JSONException.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/Aweme.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/FeedItemList.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/AnchorCommonStruct.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/NewLiveRoomStruct.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/LiveRoomStruct.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/RoomFeedCellStruct.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/FYPCommerceStruct.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/FeedRoomTag.java',
    'stubs/src/com/ss/android/ugc/aweme/feed/model/live/FeedRoomTagList.java',
    'stubs/src/com/ss/android/ugc/aweme/commerce/AwemeCommerceStruct.java',
} | {f'extension/src/app/revanced/tiktok/{name}.java' for name in HELPERS} | {
    f'patches/src/{PREFIX}{name}.java' for name in PATCHES
}


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, capture_output=True).stdout


def snapshot(revision=None):
    names = git('ls-tree', '-r', '--name-only', '-z', revision) if revision else git('ls-files', '--cached', '-z')
    files = {}
    for raw in names.split(b'\0'):
        if raw:
            name = raw.decode('utf-8')
            files[name] = git('show', f'{revision or ""}:{name}')
    if not files:
        raise ValueError('No staged or committed files to review')
    return files


def allowed(name):
    return name in PUBLIC_FILES


def privacy_rules(text, denied=()):
    rules = set()
    home_pattern = r'(?:[A-Za-z]:[/\\]Users[/\\]|/' + 'Users' + r'/|/' + 'home' + r'/)[^/\\\s]+'
    if re.search(home_pattern, text):
        rules.add('personal-home-path')
    for email in re.findall(r'[A-Za-z0-9_.+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})', text):
        if email.lower() not in {'example.org', 'example.com', 'example.net'} and not email.lower().endswith(('.invalid', '.example')):
            rules.add('non-example-email')
    for value in re.findall(r'(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])', text):
        try:
            addr = ipaddress.ip_address(value)
        except ValueError:
            continue
        reserved = any(addr in ipaddress.ip_network(net) for net in ('192.0.2.0/24', '198.51.100.0/24', '203.0.113.0/24'))
        if not addr.is_loopback and not addr.is_unspecified and not reserved:
            rules.add('non-example-ipv4')
    if re.search(r'^-----BEGIN (?:.*PRIVATE KEY|CERTIFICATE|OPENSSH PRIVATE KEY)-----', text, re.M):
        rules.add('pem-material')
    if re.search(r'\b(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})\b', text):
        rules.add('credential-pattern')
    for token in denied:
        if token and re.search(r'(?<!\w)' + re.escape(token) + r'(?!\w)', text, re.I):
            rules.add('local-identity-token')
    return rules


def review(files, denied=()):
    problems = []
    for name, content in files.items():
        if not allowed(name):
            problems.append((name, 'not-in-source-allowlist'))
            continue
        try:
            text = content.decode('utf-8-sig')
        except UnicodeDecodeError:
            problems.append((name, 'non-text-content'))
            continue
        for rule in privacy_rules(text, denied):
            problems.append((name, rule))
    if problems:
        for name, rule in sorted(problems):
            print(f'{name}: {rule}')
        raise ValueError(f'Publication review found {len(problems)} issue(s); sensitive values were not printed')


def source_hash(files):
    selected = ['VERSION', 'build.ps1']
    for name in files:
        path = PurePosixPath(name)
        if path.parts[0] == 'stubs' and path.suffix == '.java':
            selected.append(name)
        elif path.parts[0] == 'extension' and path.stem in HELPERS:
            selected.append(name)
        elif path.parts[0] == 'patches' and path.stem in PATCHES:
            selected.append(name)
    content = ''.join(name + '\n' + files[name].decode('utf-8-sig').replace('\r\n', '\n') + '\n'
                      for name in sorted(selected))
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def dex_classes(data):
    if data[:4] != b'dex\n' or len(data) < 112:
        raise ValueError('Invalid bundled DEX')
    def u32(offset):
        return struct.unpack_from('<I', data, offset)[0]
    string_offset, type_offset = u32(60), u32(68)
    count, offset = u32(96), u32(100)
    names = set()
    for index in range(count):
        type_index = u32(offset + index * 32)
        string_index = u32(type_offset + type_index * 4)
        pointer = u32(string_offset + string_index * 4)
        while data[pointer] & 0x80:
            pointer += 1
        pointer += 1
        names.add(data[pointer:data.index(0, pointer)].decode('utf-8'))
    return names


def verify_bundle(bundle, files):
    version = files['VERSION'].decode().strip()
    with zipfile.ZipFile(bundle) as archive:
        entries = set(archive.namelist())
        required = {'META-INF/MANIFEST.MF', 'classes.dex', 'extensions/tiktok.rve'}
        if not required <= entries or len(entries) != len(archive.namelist()):
            raise ValueError('Missing or duplicate bundle entries')
        expected_jvm = {PREFIX + name + '.class' for name in PATCHES}
        if {name for name in entries if name.endswith('.class')} != expected_jvm:
            raise ValueError('Public bundle contains unexpected JVM classes')
        directories = {'META-INF/', 'app/', 'app/revanced/', 'app/revanced/tiktok/', PREFIX, 'extensions/'}
        if entries - required - expected_jvm - directories:
            raise ValueError('Public bundle contains unexpected resources')
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n').replace('\n ', '')
        if f'Version: {version}\n' not in manifest or f'Source-SHA256: {source_hash(files)}\n' not in manifest:
            raise ValueError('Bundle version or source fingerprint differs from reviewed source')
        expected_helpers = {f'Lapp/revanced/tiktok/{name};' for name in HELPERS}
        if dex_classes(archive.read('extensions/tiktok.rve')) != expected_helpers:
            raise ValueError('Public extension contains diagnostics or model fixtures')
        for name in dex_classes(archive.read('classes.dex')):
            if not any(name == f'L{PREFIX}{patch};' or name.startswith(f'L{PREFIX}{patch}$$') for patch in PATCHES):
                raise ValueError('Public patch DEX contains unexpected classes')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--revision')
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--deny-token', action='append', default=[])
    args = parser.parse_args()
    files = snapshot(args.revision)
    review(files, local_tokens(args.deny_token))
    if args.bundle:
        verify_bundle(args.bundle, files)
    print(f'Publication review passed: {len(files)} source files' + ('; production bundle verified' if args.bundle else ''))


def local_tokens(extra=()):
    generic = {'root', 'runner', 'user', 'administrator', 'admin', 'system'}
    values = list(extra) + [os.environ.get('USERNAME', ''), os.environ.get('USER', '')]
    return [value for value in values if len(value) >= 4 and value.lower() not in generic]


if __name__ == '__main__':
    try:
        main()
    except ValueError as error:
        raise SystemExit(str(error)) from None
