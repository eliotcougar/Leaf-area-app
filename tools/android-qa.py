"""ADB QA helper: choose controls by UI-tree labels; save XML and lossless screenshots."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = ROOT / ".build-logs/qa"
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, check=True).stdout


def snapshot(name):
    adb("shell", "rm", "-f", "/sdcard/leaf-area-ui.xml")
    status = adb("shell", "uiautomator", "dump", "/sdcard/leaf-area-ui.xml").decode(errors="replace")
    if "dumped to" not in status:
        raise RuntimeError(f"UI dump did not produce a fresh hierarchy: {status}")
    adb("pull", "/sdcard/leaf-area-ui.xml", str(OUT / f"{name}.xml"))
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    root = ET.parse(OUT / f"{name}.xml").getroot()
    for node in root.iter("node"):
        label = node.get("text") or node.get("content-desc")
        if label:
            print(label, node.get("bounds"), "enabled=" + node.get("enabled", ""))
    return root


def bounds(node):
    return list(map(int, re.findall(r"\d+", node.get("bounds", ""))))


if __name__ == "__main__":
    action, name = sys.argv[1:3]
    if action == "snapshot":
        snapshot(name)
    elif action == "tap":
        root = snapshot("before-tap")
        node = next(n for n in root.iter("node") if n.get("text") == name or n.get("content-desc") == name)
        x1, y1, x2, y2 = bounds(node)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    elif action == "scroll":
        root = snapshot("before-scroll")
        node = next(n for n in root.iter("node") if n.get("scrollable") == "true")
        x1, y1, x2, y2 = bounds(node)
        x = (x1 + x2) // 2
        start, end = int(y1 + .78 * (y2 - y1)), int(y1 + .22 * (y2 - y1))
        if name == "up": start, end = end, start
        adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "420")
