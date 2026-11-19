"""The checks behind release-check.sh that need no game: everything that can be left out by accident.

Usage:
  python release-check.py static
  python release-check.py results <all results file> <oldest versions results file>
  python release-check.py jars <Builds folder> <unix time the build started>

Prints one line per problem and exits with 1 if there is any.
"""
import json
import re
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PACKAGE = "com/frostfleee/ragdolldeaths"
LOADERS = {"fabric": "Fabric", "quilt": "Quilt", "neoforge": "NeoForge", "forge": "Forge"}
BANDS = ["mc1_21_0", "mc1_21_2", "mc1_21_5", "mc1_21_9", "mc1_21_11", "mc26_1", "mc26_2"]
# 1.21 and 1.21.1 swap these entries of the mixin list while building (see the processResources filters).
SWAPPED_ON_1_21_0 = {"EntityRendererMixin": ["ModelBakeMixin", "ModelRootMixin", "AgeableListModelAccessor", "CubeAccessor", "SlimeSquishMixin"],
                     "LivingEntityRendererAccessor": ["LivingEntityRendererFlipAccessor"], "EntityRenderStateMixin": []}

problems = []


def problem(text):
    problems.append(text)
    print("PROBLEM:", text)


def read(name):
    return (ROOT / name).read_bytes().decode("utf-8").replace("\r\n", "\n")


def properties(name):
    return dict(line.split("=", 1) for line in read(name).splitlines() if "=" in line and not line.startswith("#"))


def version():
    return properties("gradle.properties")["mod_version"]


def build_groups():
    """Every jar build-all.sh makes, as (loader, label)."""
    groups = set()
    for line in read("build-all.sh").splitlines():
        line = line.strip()
        m = re.match(r"(?:legacy_)?fabric_like \$loader \$name (\S+) ", line)
        if m:
            groups |= {("fabric", m[1]), ("quilt", m[1])}
        m = re.match(r"(?:legacy_)?(neoforge|forge) (\S+) ", line)
        if m:
            groups.add((m[1], m[2]))
    return groups


def test_groups():
    """Every run selftest-all.sh makes: (normal runs, runs on the oldest version of a group)."""
    normal, edges = set(), set()
    for line in read("selftest-all.sh").splitlines():
        m = re.match(r"\s*run (\$loader|fabric|quilt|neoforge|forge) (\S+) ", line)
        if not m:
            continue
        loaders = ["fabric", "quilt"] if m[1] == "$loader" else [m[1]]
        (edges if m[2].endswith("-edge") else normal).update((loader, m[2]) for loader in loaders)
    return normal, edges


def mixins():
    return json.loads(read("src/main/resources/ragdolldeaths.mixins.json"))["client"]


def mixins_for(band):
    names = []
    for name in mixins():
        if band == "mc1_21_0" and name in SWAPPED_ON_1_21_0:
            names += SWAPPED_ON_1_21_0[name]
        else:
            names.append(name)
    return names


def static():
    v = version()
    for name in ("legacy/gradle.properties", "neoforge/gradle.properties", "forge/gradle.properties"):
        if properties(name).get("mod_version") != v:
            problem(f"{name} does not say version {v}")
    if f"## {v}\n" not in read("CHANGELOG.md"):
        problem(f"CHANGELOG.md has no section for {v}")
    if f"Ragdoll Deaths v{v}" not in read("README.md"):
        problem(f"README.md does not name the {v} jars")
    for name in ("README.md",):
        for old in set(re.findall(r"Ragdoll Deaths v(\d+\.\d+\.\d+)", read(name))) - {v}:
            problem(f"{name} still names version {old}")
    modrinth = ROOT / "MODRINTH.md"
    if modrinth.exists():
        for old in set(re.findall(r"Ragdoll Deaths (\d+\.\d+\.\d+)", read("MODRINTH.md"))) - {v}:
            problem(f"MODRINTH.md still names version {old}")
    if f"New in {v}" not in read("TESTING.md"):
        problem(f"TESTING.md has no section for what is new in {v}")

    # Every jar has a test run, and every test run a jar.
    builds = build_groups()
    normal, edges = test_groups()
    for group in sorted(builds - normal):
        problem(f"no self-test run for the jar {group}")
    for group in sorted(normal - builds):
        problem(f"self-test run {group} has no jar")
    if len(builds) < 29:
        problem(f"only {len(builds)} jars in build-all.sh")
    if not edges:
        problem("no oldest-version runs in selftest-all.sh")

    # Every mixin in the list exists for every game version, and none is forgotten in the list.
    main = ROOT / "src/main/java" / PACKAGE / "mixin"
    for band in BANDS:
        own = ROOT / "src" / band / "java" / PACKAGE / "mixin"
        listed = mixins_for(band)
        for name in listed:
            if not (main / f"{name}.java").exists() and not (own / f"{name}.java").exists():
                problem(f"{band}: the mixin list names {name}, which does not exist")
        for file in own.glob("*.java"):
            if file.stem not in listed:
                problem(f"{band}: {file.stem} is not in the mixin list")

    # Every setting has its name and tooltip, in every language.
    lang = ROOT / "src/main/resources/assets/ragdolldeaths/lang"
    tables = {}
    for file in lang.glob("*.json"):
        try:
            tables[file.stem] = json.loads(file.read_bytes().decode("utf-8"))
        except ValueError as error:
            problem(f"{file.name} is not valid JSON: {error}")
    english = tables.get("en_us", {})
    for name, table in tables.items():
        for key in sorted(set(english) - set(table)):
            problem(f"{name}.json lacks {key}")
        for key in sorted(set(table) - set(english)):
            problem(f"{name}.json has {key}, which en_us.json does not")
    screen = read(f"src/main/java/{PACKAGE}/client/config/RagdollConfigScreen.java")
    fields = read(f"src/main/java/{PACKAGE}/core/RagdollConfig.java")
    for setting in set(re.findall(r'(?:toggle|slider|percent)\("(\w+)"', screen)):
        for key in (f"ragdolldeaths.config.{setting}", f"ragdolldeaths.config.{setting}.tooltip"):
            if key not in english:
                problem(f"en_us.json lacks {key}")
        if not re.search(rf"\b{setting}\s*=", fields):
            problem(f"the settings screen shows {setting}, which RagdollConfig does not have")


def results(all_file, edge_file):
    normal, edges = test_groups()
    for path, wanted in ((all_file, normal), (edge_file, edges)):
        seen = {}
        for line in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
            m = re.match(r"(\w+) (\S+): (.*)", line)
            if m:
                seen[(m[1], m[2])] = m[3]
        for group in sorted(wanted):
            result = seen.get(group)
            if result is None:
                problem(f"{group[0]} {group[1]}: did not run")
            elif not result.startswith("RESULT PASS"):
                problem(f"{group[0]} {group[1]}: {result[:300]}")


def jars(folder, started):
    v = version()
    folder = Path(folder)
    for loader, label in sorted(build_groups()):
        name = LOADERS[loader]
        path = folder / name / v / f"Ragdoll Deaths v{v} - {name} {label}.jar"
        if not path.exists():
            problem(f"missing jar: {path.name}")
            continue
        if path.stat().st_mtime < started:
            problem(f"not rebuilt by this check: {path.name}")
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            band = "mc1_21_0" if label.startswith("1.21-") else "other"
            for mixin in mixins_for(band):
                if f"{PACKAGE}/mixin/{mixin}.class" not in names:
                    problem(f"{path.name} lacks the mixin {mixin}")
            meta = [n for n in ("fabric.mod.json", "quilt.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml") if n in names]
            if not meta or not any(v.encode() in jar.read(n) for n in meta):
                problem(f"{path.name} does not say version {v} in its mod information")
    for name in LOADERS.values():
        expected = sum(1 for loader, _ in build_groups() if LOADERS[loader] == name)
        found = len(list((folder / name / v).glob("*.jar")))
        if found != expected:
            problem(f"{name}: {found} jars in the {v} folder, expected {expected}")


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "static":
        static()
    elif command == "results":
        results(sys.argv[2], sys.argv[3])
    elif command == "jars":
        jars(sys.argv[2], float(sys.argv[3]))
    else:
        raise SystemExit(__doc__)
    print(f"{command}: {'OK' if not problems else str(len(problems)) + ' problem(s)'} ({time.strftime('%H:%M:%S')})")
    sys.exit(1 if problems else 0)
