"""Package addon sources and review evidence without bundling the original mod."""
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
output = ROOT / 'build/libs/DoomedRU-2.0.0-source.zip'
files = set()
for directory in ('src', 'gradle', 'configuration'):
    files.update(p for p in (ROOT / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.suffix not in ('.class', '.jar'))
files.update(p for p in (ROOT / 'tools').iterdir() if p.is_file() and p.suffix in ('.py', '.json', '.java'))
files.add(ROOT / 'gradle/wrapper/gradle-wrapper.jar')
for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'README.md', 'LICENSE', 'CHANGES-1.0.1.md'):
    files.add(ROOT / name)
for name in ('coverage.json', 'client-result.json', 'release-verification.json', 'VALIDATION.md', 'artifact.json', 'trait-translations.json', 'rule-comments-source.json', 'backups/russian.before-1.0.1.json'):
    files.add(ROOT / 'audit' / name)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(files):
        archive.write(path, path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert 'src/main/java/local/doomed/russianfresh/RussianText.java' in archive.namelist()
    assert not any(n.startswith(('production-run/', 'audit/decompiled/', 'audit/original/')) for n in archive.namelist())
print(output)
