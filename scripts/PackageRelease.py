"""Package reviewed committed source and the production RVP; never upload anything."""
import hashlib
import io
import json
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from CheckPublishable import ROOT, git, local_tokens, review, snapshot, verify_bundle

REPOSITORY_URL = 'https://github.com/niposch/revanced-tiktok-patches'


def manager_feed(version, created_at):
    """ReVanced Manager's remote-source format; created_at is UTC without a zone suffix."""
    return {
        'download_url': f'{REPOSITORY_URL}/releases/download/v{version}/tiktok-feed-filters-{version}.rvp',
        'created_at': created_at,
        'description': 'Independent TikTok feed filters, downloads and device registration patches.',
        'version': version,
    }


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if git('status', '--porcelain', '--untracked-files=no').strip():
        raise ValueError('Commit tracked/staged changes before packaging')
    files = snapshot('HEAD')
    review(files, local_tokens())
    version = files['VERSION'].decode().strip()
    bundle = ROOT / f'dist/tiktok-feed-filters-{version}.rvp'
    verify_bundle(bundle, files)
    output = ROOT / 'dist/releases'
    output.mkdir(parents=True, exist_ok=True)
    source = output / f'tiktok-feed-filters-{version}-source.zip'
    prefix = f'tiktok-feed-filters-{version}/'
    source.write_bytes(git('archive', '--format=zip', f'--prefix={prefix}', 'HEAD'))
    with zipfile.ZipFile(io.BytesIO(source.read_bytes())) as archive:
        archived = {name[len(prefix):]: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
    if archived != files:
        raise ValueError('Source archive differs from the reviewed commit')
    public_bundle = output / bundle.name
    shutil.copyfile(bundle, public_bundle)
    created_at = datetime.fromtimestamp(int(git('show', '-s', '--format=%ct', 'HEAD')),
                                       timezone.utc).replace(tzinfo=None).isoformat(timespec='seconds')
    feed = output / 'patches.json'
    feed.write_text(json.dumps(manager_feed(version, created_at), indent=2) + '\n',
                    encoding='utf-8', newline='\n')
    checksums = output / 'SHA256SUMS.txt'
    checksum_text = ''.join(f'{sha(path)}  {path.name}\n' for path in (public_bundle, source, feed))
    checksums.write_text(checksum_text, encoding='utf-8', newline='\n')
    release = output / f'tiktok-feed-filters-{version}-release.zip'
    with zipfile.ZipFile(release, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in (public_bundle, source, feed, checksums):
            archive.write(path, path.name)
        for name, data in sorted(files.items()):
            if name == 'LICENSE' or name.endswith('.md'):
                archive.writestr(name, data)
    checksums.write_text(checksum_text + f'{sha(release)}  {release.name}\n', encoding='utf-8', newline='\n')
    print(f'Packaged release {version} from commit {git("rev-parse", "--short", "HEAD").decode().strip()}')
    for path in (public_bundle, source, release, feed, checksums):
        print(path.relative_to(ROOT).as_posix())


if __name__ == '__main__':
    try:
        main()
    except ValueError as error:
        raise SystemExit(str(error)) from None
