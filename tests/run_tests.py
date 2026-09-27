"""Run after gradlew jar. Uses the local Starsector API and requires a JDK."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
properties = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text().splitlines()
    if "=" in line and not line.lstrip().startswith("#")
)
game = Path(properties["starsectorPath"])
output = root / "build" / "regression-tests"
output.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, [
    root / "jars" / "SolRenewed.jar",
    game / "starsector-core" / "*",
    game / "mods" / "LazyLib-3.0.0" / "jars" / "internal" / "Kotlin-Runtime.jar",
]))
tests = sorted((root / "tests").glob("*Test.java"))
subprocess.run([
    "javac", "--release", "17", "-cp", classpath, "-d", str(output), *map(str, tests),
], check=True)
for test in tests:
    subprocess.run([
        "java", "-Djava.awt.headless=true", "-cp", str(output) + os.pathsep + classpath, test.stem,
    ], check=True)
