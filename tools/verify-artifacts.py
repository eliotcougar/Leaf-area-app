"""Read back delivered artifacts and the final emulator export; write an audit."""
import hashlib
import io
import json
import struct
import subprocess
import zipfile
from pathlib import Path
import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
ADB = str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')
def adb(*args):
    return subprocess.run([ADB, '-s', 'emulator-5554', *args], check=True, capture_output=True).stdout
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

apk = ROOT / 'output/apk/LI6800-Area-0.1.0-debug-universal.apk'
pdf = ROOT / 'output/pdf/LI6800-6cm2-marker-cutout-v1.pdf'
assert sha(apk) == sha(ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
assert sha(pdf) == sha(ROOT / 'app/src/main/assets/LI6800-6cm2-marker-cutout-v1.pdf')
assert sha(pdf) == sha(ROOT / '.build-logs/qa/exported-cutout.pdf')
with zipfile.ZipFile(apk) as z:
    abis = sorted({n.split('/')[1] for n in z.namelist() if n.startswith('lib/')})
    assert abis == ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
    assert z.read('assets/LI6800-6cm2-marker-cutout-v1.pdf') == pdf.read_bytes()
    alignment = {}
    for name in z.namelist():
        if name.startswith('lib/arm64-v8a/') and name.endswith('.so'):
            elf = z.read(name)
            phoff = struct.unpack_from('<Q', elf, 32)[0]
            size, count = struct.unpack_from('<HH', elf, 54)
            values = [struct.unpack_from('<Q', elf, phoff + i * size + 48)[0]
                      for i in range(count) if struct.unpack_from('<I', elf, phoff + i * size)[0] == 1]
            assert all(v >= 16384 for v in values), (name, values)
            alignment[name] = values
with pdfplumber.open(pdf) as d:
    assert len(d.pages) == 1
    page = d.pages[0]
    curves = [(c['width'] * 25.4 / 72, c['height'] * 25.4 / 72) for c in page.curves]
    circles = [v for v in curves if abs(v[0] - 27.639531958) < .001 and abs(v[1] - 27.639531958) < .001]
    assert len(circles) == 2, curves
    assert any(abs(line['width'] * 25.4 / 72 - 50) < .001 for line in page.lines)
    page_mm = [page.width * 25.4 / 72, page.height * 25.4 / 72]

names = adb('shell', 'run-as', 'org.li6800.area', 'ls', 'files/measurements').decode().split()
export = None
for name in names:
    if not name.endswith('.zip'): continue
    data = adb('exec-out', 'run-as', 'org.li6800.area', 'cat', 'files/measurements/' + name)
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        meta = json.loads(z.read('measurement.json'))
        if meta['sampleId'] != 'QA-auto-half': continue
        assert abs(meta['areaMm2'] - 300) < 1
        assert 'manualCorrection' not in meta
        assert 'manual_correction' not in z.read('measurement.csv').decode()
        assert meta['source'] == 'photo'
        export = {'metadata': meta, 'entries': z.namelist()}
        (ROOT / '.build-logs/qa/QA-auto-half.zip').write_bytes(data)
assert export is not None
audit = {'apkSha256': sha(apk), 'apkBytes': apk.stat().st_size, 'abis': abis,
         'arm64LoadAlignments': alignment, 'pdfSha256': sha(pdf), 'pdfBytes': pdf.stat().st_size,
         'pageMm': page_mm, 'openingDiametersMm': circles, 'appExportMatchesPdf': True,
         'savedMeasurementReadBack': export}
(ROOT / '.build-logs/qa/artifact-audit.json').write_text(json.dumps(audit, indent=2))
print(json.dumps(audit, indent=2))
