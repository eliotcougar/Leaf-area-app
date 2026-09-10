"""Create the V2 print backing with the installed FreeCAD Python interpreter.

On this PC:
  & 'C:\Program Files\FreeCAD 1.1\bin\python.exe' tools/generate-mask-step.py
Geometry is in mm. The outline function is read directly from the PDF generator
without importing or rerunning its PDF/image generation code.
"""
import ast
import hashlib
import json
import math
from pathlib import Path

import numpy as np
import FreeCAD as App
import Part

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'output/cad'
OUTPUT.mkdir(parents=True, exist_ok=True)
THICKNESS = 0.4
generator = ROOT / 'tools/generate-template.py'
tree = ast.parse(generator.read_text(encoding='utf-8'))
definition = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'outline')
namespace = {'math': math, 'np': np}
exec(compile(ast.Module(body=[definition], type_ignores=[]), str(generator), 'exec'), namespace)
points = namespace['outline']()
template = json.loads((ROOT / 'app/src/main/assets/template-v2.json').read_text())
radius = template['apertureRadiusMm']
assert abs(math.pi * radius ** 2 - 600) < 1e-9

# The PDF uses y-down coordinates. Symmetry means the same x,y values describe
# the identical physical profile in CAD's XY plane; the base rests on z=0.
vertices = [App.Vector(float(x), float(y), 0) for x, y in points]
outline = Part.makePolygon(vertices + [vertices[0]])
outer_face = Part.Face(outline)
circle = Part.Wire([Part.makeCircle(radius, App.Vector(0, 0, 0))])
hole_face = Part.Face(circle)
profile = outer_face.cut(hole_face)
solid = profile.extrude(App.Vector(0, 0, THICKNESS))
assert solid.isValid() and solid.isClosed() and len(solid.Solids) == 1
assert len(profile.Faces) == 1 and len(profile.Faces[0].Wires) == 2
assert abs(outer_face.Area - profile.Area - 600) < 1e-8

doc = App.newDocument('LI6800MaskV2')
obj = doc.addObject('PartDesign::Feature', 'PrintBacking')
obj.Label = 'LI6800-6-V2 mask backing - 0.4 mm'
obj.Shape = solid
obj.addProperty('App::PropertyLength', 'Thickness', 'Dimensions').Thickness = THICKNESS
obj.addProperty('App::PropertyLength', 'OpeningDiameter', 'Dimensions').OpeningDiameter = 2 * radius
doc.recompute()
path = OUTPUT / 'LI6800-6cm2-mask-v2-0.4mm.step'
Part.export([obj], str(path))

# Validate the actual neutral-format output, including its one-piece solid and
# all imported geometry, rather than relying only on the in-memory CAD object.
reimported = Part.Shape()
reimported.read(str(path))
assert reimported.isValid() and reimported.isClosed() and len(reimported.Solids) == 1
box = reimported.BoundBox
for actual, expected in [(box.XLength, 111), (box.YLength, 68), (box.ZLength, THICKNESS)]:
    assert abs(actual - expected) < 1e-7, (actual, expected)
expected_volume = (outer_face.Area - 600) * THICKNESS
assert abs(reimported.Volume - expected_volume) < 1e-7
assert solid.cut(reimported).Volume < 1e-7
assert reimported.cut(solid).Volume < 1e-7
cylinders = [f for f in reimported.Faces if isinstance(f.Surface, Part.Cylinder)]
assert len(cylinders) == 1
assert abs(cylinders[0].Surface.Radius - radius) < 1e-10

audit = {
    'file': path.name, 'units': 'mm', 'templateId': template['id'],
    'boundingBoxMm': [box.XLength, box.YLength, box.ZLength],
    'openingDiameterMm': 2 * cylinders[0].Surface.Radius,
    'openingAreaMm2': 600.0, 'openingCenterMm': [0, 0],
    'boundsMm': {'min': [box.XMin, box.YMin, box.ZMin], 'max': [box.XMax, box.YMax, box.ZMax]},
    'profileAreaMm2': profile.Area, 'volumeMm3': reimported.Volume,
    'valid': reimported.isValid(), 'closed': reimported.isClosed(), 'solids': len(reimported.Solids),
    'outerOutlineVertices': len(points), 'outlineSource': 'tools/generate-template.py:outline',
    'outlineSourceSha256': hashlib.sha256(generator.read_bytes()).hexdigest(),
    'stepSha256': hashlib.sha256(path.read_bytes()).hexdigest(),
    'freecadVersion': App.Version(),
    'notes': ['Nominal dimensions; no print shrinkage or adhesive allowance.',
              'Front outline preserves the PDF generator\'s 64 short arc segments.',
              'Opening is an exact CAD circle; the PDF represents circles using Bezier curves.',
              'Markers and labels are supplied by the paper printout; both solid faces are flat.'],
}
(OUTPUT / 'mask-step-v2-verification.json').write_text(json.dumps(audit, indent=2) + '\n')
mesh_vertices, triangles = reimported.tessellate(0.01)
scratch = ROOT / 'tmp/cad'
scratch.mkdir(parents=True, exist_ok=True)
(scratch / 'mask-preview-mesh.json').write_text(json.dumps({
    'vertices': [[p.x, p.y, p.z] for p in mesh_vertices], 'triangles': triangles,
    'outline': points, 'radius': radius,
}))
print(json.dumps(audit, indent=2))
App.closeDocument(doc.Name)
