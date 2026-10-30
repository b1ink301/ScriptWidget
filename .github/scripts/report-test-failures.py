"""Prints each failed JUnit test as a GitHub error annotation (visible without opening the logs)."""
import glob
import sys
import xml.etree.ElementTree as ET

patterns = sys.argv[1:] or ["**/build/test-results/**/*.xml", "**/build/outputs/androidTest-results/connected/**/*.xml"]
failed = 0
for pattern in patterns:
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                failed += 1
                text = (problem.get("message") or problem.text or "").strip().splitlines()
                detail = " | ".join(line.strip() for line in text[:6])
                name = f"{case.get('classname', '')}.{case.get('name', '')}"
                print(f"::error title=Test failed: {name}::{detail[:900].replace('%', '%25')}")
print(f"{failed} failed test(s)")
