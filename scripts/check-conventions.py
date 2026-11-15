#!/usr/bin/env python3
"""Mechanical checks that Gradle and Android lint do not cover. Run from the repo root; exits 1 on a violation."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
errors = []

# 1. A string name defined in two modules with different text: at merge one silently replaces the other.
texts = {}  # name -> {(module, locale dir): text}
for f in root.glob("*/**/src/main/res/values*/strings.xml"):
    if "build" in f.parts:
        continue
    module = str(f.relative_to(root)).split("/src/")[0]
    for name, text in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', f.read_text(), re.S):
        texts.setdefault(name, {})[(module, f.parent.name)] = text
for name, found in sorted(texts.items()):
    modules = {m for m, _ in found}
    differs = any(len({t for (m, loc), t in found.items() if loc == l}) > 1 for l in {loc for _, loc in found})
    if len(modules) > 1 and differs:
        errors.append(f'string "{name}" has different text in {", ".join(sorted(modules))}: give each a distinct name')

# 2. Intent extras are written and read only by AppRequest (core/ui).
extra = re.compile(r'"dev\.antomanc\.ameki\.[A-Z_]+"')
for f in root.glob("*/**/src/main/kotlin/**/*.kt"):
    if "build" in f.parts or f.name == "AppRequest.kt":
        continue
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if extra.search(line):
            errors.append(f"{f.relative_to(root)}:{n}: Intent extra literal; go through AppRequest")

for e in errors:
    print(e, file=sys.stderr)
sys.exit(1 if errors else 0)
