#!/usr/bin/env bash
# Builds every release jar: one per loader and game-version group, into
# <Desktop>/Ragdoll Deaths/<Loader>/Ragdoll Deaths v<version> - <Loader> <versions>.jar.
# A whole patch line is labelled like 26.1.x; a span across minor versions like 26.2-26.3.
#
# A group shares one jar when the mod compiles to identical bytecode against each version in it and every mixin
# target exists in each; 26.1.x and 26.2+ differ (see src/mc26_1 and src/mc26_2), so each loader gets two jars.
#
# Usage: ./build-all.sh [output folder]
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
OUT=${1:-"$(powershell.exe -NoProfile -Command "[Environment]::GetFolderPath('Desktop')" | tr -d '\r')/Ragdoll Deaths"}
mkdir -p "$OUT"
# The NeoForge and Forge builds write outside OneDrive (see their build.gradle).
EXTERNAL="$(cygpath -u "$LOCALAPPDATA")/ragdolldeaths-build"

# OneDrive sometimes locks files under build/; retry when that is the only failure.
gradle() {
	for attempt in 1 2 3 4 5; do
		if ./gradlew "$@" > build-all.log 2>&1; then
			return 0
		fi
		grep -q "clean up\|stale\|being used by another process" build-all.log || { cat build-all.log; return 1; }
		rm -rf build/classes build/resources build/test-results
	done
	cat build-all.log
	return 1
}

ship() { # ship <built jar> <loader name> <version label>
	local name="Ragdoll Deaths v$VERSION - $2 $3.jar"
	mkdir -p "$OUT/$2"
	cp "$1" "$OUT/$2/$name"
	echo "  $name"
}

fabric_like() { # fabric_like <fabric|quilt> <Loader name> <label> <mc> <compat> <fabric api> <mod menu> <range>
	rm -rf build/libs
	gradle build "-Ploader=$1" "-Pminecraft_version=$4" "-Pcompat=$5" "-Pfabric_api_version=$6" "-Pmodmenu_version=$7" \
		"-Pminecraft_range=$8" "-Pminecraft_range_name=$3"
	ship build/libs/*.jar "$2" "$3"
}

neoforge() { # neoforge <label> <mc> <compat> <neoforge> <range>
	rm -rf "$EXTERNAL/neoforge/libs"
	gradle -p neoforge build "-Pminecraft_version=$2" "-Pcompat=$3" "-Pneo_version=$4" "-Pminecraft_range=$5"
	ship "$EXTERNAL"/neoforge/libs/*.jar NeoForge "$1"
}

forge() { # forge <label> <compat> <forge> <mc range> <forge range>
	rm -rf "$EXTERNAL/forge/libs"
	gradle -p forge build "-Pcompat=$2" "-Pforge_version=$3" "-Pminecraft_range=$4" "-Pforge_range=$5"
	ship "$EXTERNAL"/forge/libs/*.jar Forge "$1"
}

echo "Building Ragdoll Deaths $VERSION into $OUT"
for loader in fabric quilt; do
	name=$([ $loader = fabric ] && echo Fabric || echo Quilt)
	fabric_like $loader $name 26.1.x 26.1 mc26_1 0.155.3+26.1.2 18.0.2 '>=26.1 <26.2'
	fabric_like $loader $name 26.2-26.3 26.3 mc26_2 0.165.0+26.3 21.0.0 '>=26.2 <26.4'
done
neoforge 26.1.x 26.1.2 mc26_1 26.1.2.126 '[26.1,26.2)'
neoforge 26.2-26.3 26.3 mc26_2 26.3.0.101 '[26.2,26.4)'
forge 26.1.x mc26_1 26.1.2-64.1.5 '[26.1,26.2)' '[62,)'
forge 26.2-26.3 mc26_2 26.3-66.1.2 '[26.2,26.4)' '[65,)'
rm -f build-all.log
echo "Done."
