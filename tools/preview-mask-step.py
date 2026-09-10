"""Render the reimported STEP mesh and compare its profile with the printed PDF."""
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import numpy as np
import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
data = json.loads((ROOT / 'tmp/cad/mask-preview-mesh.json').read_text())
points = np.array(data['outline'])
with pdfplumber.open(ROOT / 'output/pdf/LI6800-6cm2-marker-cutout-v2.pdf') as doc:
    page = doc.pages[0]
    outlines = [c for c in page.curves if abs(c['width'] * 25.4 / 72 - 111) < .001
                and abs(c['height'] * 25.4 / 72 - 68) < .001]
    assert len(outlines) == 2
    deviations = []
    for curve in outlines:
        # pdfplumber points have a top-left page origin, matching the PDF generator.
        actual = np.array(curve['pts']) * 25.4 / 72
        actual -= actual.min(axis=0)
        expected = points - points.min(axis=0)
        distances = np.linalg.norm(actual[:, None, :] - expected[None, :, :], axis=2)
        deviation = max(distances.min(axis=0).max(), distances.min(axis=1).max())
        assert deviation < .0001, deviation
        deviations.append(float(deviation))

vertices = np.array(data['vertices'])
triangles = vertices[np.array(data['triangles'])]
image = Image.new('RGB', (1600, 760), '#f6f8f5')
draw = ImageDraw.Draw(image)
def font(size): return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)
def label(x, y, text, size=24):
    draw.text((x, y), text, font=font(size), fill='#173f32', anchor='mm', align='center')
label(800, 55, 'LI-6800 mask / 6 cm² opening', 36)
label(420, 140, 'Matching V2 print outline', 26)
label(1190, 140, '0.4 mm solid backing', 26)
def top(p): return (620 + p[0] * 6, 400 + p[1] * 6)
draw.polygon([top(p) for p in points], fill='#b5d5c8', outline='#235d49', width=2)
r = data['radius'] * 6
draw.ellipse((620-r, 400-r, 620+r, 400+r), fill='#f6f8f5', outline='#235d49', width=2)
label(620, 400, '27.6395 mm\nopening', 19)
draw.line((140, 637, 806, 637), fill='#235d49', width=2)
for x in [140, 806]: draw.line((x, 628, x, 646), fill='#235d49', width=2)
label(440, 665, '111 mm', 22)
draw.line((112, 196, 112, 604), fill='#235d49', width=2)
for y in [196, 604]: draw.line((104, y, 120, y), fill='#235d49', width=2)
label(55, 400, '68\nmm', 22)
# Orthographic render of the mesh read from the STEP, using a painter's depth sort.
a, b = np.radians(25), np.radians(53)
rz = np.array([[np.cos(a), -np.sin(a), 0], [np.sin(a), np.cos(a), 0], [0, 0, 1]])
rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
transformed = vertices @ (rx @ rz).T
xy = transformed[:, :2] * [1, -1]
scale = min(650 / np.ptp(xy[:, 0]), 450 / np.ptp(xy[:, 1]))
projected = (xy - (xy.min(axis=0) + xy.max(axis=0)) / 2) * scale + [1200, 410]
indices = np.array(data['triangles'])
order = np.argsort(transformed[indices, 2].mean(axis=1))
for index in order:
    face = triangles[index]
    normal = np.cross(face[1]-face[0], face[2]-face[0])
    normal /= max(np.linalg.norm(normal), 1e-12)
    shade = .60 + .40 * abs(normal[2])
    color = tuple(int(c * shade) for c in [153, 195, 176])
    draw.polygon([tuple(p) for p in projected[indices[index]]], fill=color)
label(800, 725, 'Flat top for the paper printout. Dimensions in mm. Preview is not a printing template.', 22)
image.save(ROOT / 'output/cad/LI6800-mask-step-v2-preview.png')
audit_path = ROOT / 'output/cad/mask-step-v2-verification.json'
audit = json.loads(audit_path.read_text())
audit['maxOutlineVertexDeviationFromPrintedPdfMm'] = deviations
audit_path.write_text(json.dumps(audit, indent=2) + '\n')
print('Both printed outlines match CAD profile; maximum vertex deviation (mm):', deviations)
