#!/usr/bin/env python3
"""Mechanical checks that Gradle and Android lint do not cover. Run from the repo root; exits 1 on a violation."""
import fnmatch
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

# 3. Module boundaries. Features and widgets get data from core/data, the only module that wires Sources.
ALLOWED = {
    "core/model": [],
    "core/ui": ["core/model"],
    "core/data": ["core/model", "source/*"],
    "source/api": ["core/model"],
    "source/*": ["source/api", "core/model"],
    "feature/*": ["core/*", "feature/*"],
    "widget": ["core/*"],
    "app": ["core/*", "feature/*", "widget"],
}
# Both declaration forms: the typesafe accessor (projects.source.google) and the string path (project(":source:google")).
dep = re.compile(r"\bprojects\.([a-zA-Z.]+)|\bproject\(\s*\"(:[\w:-]+)\"\s*\)")
for build in sorted(root.glob("**/*.gradle.kts")):
    module = str(build.parent.relative_to(root))
    if build.name == "settings.gradle.kts" or module == "." or module.startswith("build-logic") or "build" in build.parent.parts or ".gradle" in build.parts:
        continue
    key = module if module in ALLOWED else next((k for k in ALLOWED if fnmatch.fnmatch(module, k)), None)
    if key is None:
        errors.append(f"{module}: new module without a boundary; add it to ALLOWED in scripts/check-conventions.py")
        continue
    for n, line in enumerate(build.read_text().splitlines(), 1):
        rel = f"{module}/{build.name}"
        if module != "core/data" and "libs.room" in line:
            errors.append(f"{rel}:{n}: Room belongs to core/data; read stored data through its repositories (WidgetInstances, PlaceRepository)")
        for accessor, path in dep.findall(line):
            target = accessor.replace(".", "/") if accessor else path.strip(":").replace(":", "/")
            if target != module and not any(fnmatch.fnmatch(target, a) for a in ALLOWED[key]):
                errors.append(f"{rel}:{n}: {module} must not depend on {target}; features and widgets read weather through core/data, which owns the Sources")
            # core/data may use every Source, but api() would hand a concrete one to every module that uses core/data.
            elif module == "core/data" and target.startswith("source/") and target != "source/api" and re.search(r"\bapi\(", line):
                errors.append(f"{rel}:{n}: core/data exposes {target} with api(); use implementation() so features reach weather only through core/data")
for f in root.glob("*/**/src/main/kotlin/**/*.kt"):
    rel = str(f.relative_to(root))
    if "build" in f.parts or rel.startswith("core/data/"):
        continue
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if re.search(r"\bandroidx\.room\.", line):
            errors.append(f"{rel}:{n}: Room belongs to core/data; read stored data through its repositories (WidgetInstances, PlaceRepository)")
        if re.search(r"\bdev\.antomanc\.ameki\.core\.data\.db\b", line):
            errors.append(f"{rel}:{n}: DAOs and entities stay inside core/data; read stored data through its repositories (WidgetInstances, PlaceRepository)")

# 4. Time comes from one place in UI code, so a screen or widget can be tested at any hour.
clock = re.compile(r"\b(?:Instant|LocalDate|LocalDateTime|ZonedDateTime|OffsetDateTime)\.now\(|System\.currentTimeMillis\(|Clock\.system")
default_arg = re.compile(r":\s*[\w.?<>]+\s*=\s*(?:java\.time\.)?\w+\.now\(")
clock_owners = {
    "feature/home/src/main/kotlin/dev/antomanc/ameki/feature/home/WeatherExtras.kt",  # rememberWeatherTime()
    "widget/src/main/kotlin/dev/antomanc/ameki/widget/AmekiWidget.kt",  # Render: one now per widget update
}
for f in root.glob("*/**/src/main/kotlin/**/*.kt"):
    rel = str(f.relative_to(root))
    if "build" in f.parts or rel in clock_owners or not rel.startswith(("feature/", "widget/", "core/ui/")):
        continue
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if clock.search(line) and not default_arg.search(line):
            errors.append(f"{rel}:{n}: reads the clock; take now as a parameter, from rememberWeatherTime() in the app or from Render in widgets, so it can be tested at any hour")

for e in errors:
    print(e, file=sys.stderr)
sys.exit(1 if errors else 0)
