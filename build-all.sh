#!/usr/bin/env bash
# Builds every release jar: one per loader and game-version group, into
# <Desktop>/Ragdoll Deaths/Builds/<Loader>/Ragdoll Deaths v<version> - <Loader> <versions>.jar.
# A whole patch line is labelled like 26.1.x; a span across minor versions like 26.2-26.3.
#
# A group shares one jar when the mod compiles to identical bytecode against each version in it and every mixin
# target exists in each; 26.1.x and 26.2+ differ (see src/mc26_1 and src/mc26_2), and 1.21.x splits five ways
# (src/mc1_21_0, mc1_21_2, mc1_21_5, mc1_21_9, mc1_21_11), so Fabric, Quilt and NeoForge get seven jars each. Forge
# also splits where its own event bus changed (1.21.6, and its events in 1.21.9) and where it started bundling MixinExtras (1.21.10):
# eight jars.
#
# Usage: ./build-all.sh [output folder]
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
OUT=${1:-"$(powershell.exe -NoProfile -Command "[Environment]::GetFolderPath('Desktop')" | tr -d '\r')/Ragdoll Deaths/Builds"}
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

# Minecraft 1.21.x: the legacy/ build (obfuscated game, mapped while building) and Java 21. Each group is built
# against its newest version; the groups follow where the game changed (src/mc1_21_2, mc1_21_5, mc1_21_9, mc1_21_11).
legacy_fabric_like() { # legacy_fabric_like <fabric|quilt> <Loader name> <label> <mc> <compat> <fabric api> <mod menu> <range>
	rm -rf "$EXTERNAL/legacy-$1-$5/libs"
	gradle -p legacy remapJar "-Ploader=$1" "-Pminecraft_version=$4" "-Pcompat=$5" "-Pfabric_api_version=$6" "-Pmodmenu_version=$7" \
		"-Pminecraft_range=$8" "-Pminecraft_range_name=$3"
	ship "$EXTERNAL"/legacy-$1-$5/libs/*.jar "$2" "$3"
}

legacy_neoforge() { # legacy_neoforge <label> <mc> <compat> <neoforge> <range>
	rm -rf "$EXTERNAL/neoforge/libs"
	gradle -p neoforge jar "-Pminecraft_version=$2" "-Pcompat=$3" "-Pneo_version=$4" "-Pminecraft_range=$5" -Pjava_version=21
	ship "$EXTERNAL"/neoforge/libs/*.jar NeoForge "$1"
}

legacy_forge() { # legacy_forge <label> <compat> <forge> <mc range> <forge range> [-Pforge_bus=6] [-Pbundle_mixinextras]
	rm -rf "$EXTERNAL/forge/libs"
	gradle -p forge jar "-Pcompat=$2" "-Pforge_version=$3" "-Pminecraft_range=$4" "-Pforge_range=$5" -Pjava_version=21 "${@:6}"
	ship "$EXTERNAL"/forge/libs/*.jar Forge "$1"
}

echo "Building Ragdoll Deaths $VERSION into $OUT"
for loader in fabric quilt; do
	name=$([ $loader = fabric ] && echo Fabric || echo Quilt)
	fabric_like $loader $name 26.1.x 26.1 mc26_1 0.155.3+26.1.2 18.0.2 '>=26.1 <26.2'
	fabric_like $loader $name 26.2-26.3 26.3 mc26_2 0.165.0+26.3 21.0.0 '>=26.2 <26.4'
	legacy_fabric_like $loader $name 1.21-1.21.1 1.21.1 mc1_21_0 0.116.18+1.21.1 11.0.5 '>=1.21 <1.21.2'
	legacy_fabric_like $loader $name 1.21.2-1.21.4 1.21.4 mc1_21_2 0.119.4+1.21.4 13.0.0 '>=1.21.2 <1.21.5'
	legacy_fabric_like $loader $name 1.21.5-1.21.8 1.21.8 mc1_21_5 0.136.1+1.21.8 15.0.0 '>=1.21.5 <1.21.9'
	legacy_fabric_like $loader $name 1.21.9-1.21.10 1.21.10 mc1_21_9 0.138.4+1.21.10 16.0.0 '>=1.21.9 <1.21.11'
	legacy_fabric_like $loader $name 1.21.11 1.21.11 mc1_21_11 0.141.6+1.21.11 17.0.0 '>=1.21.11 <1.22'
done
neoforge 26.1.x 26.1.2 mc26_1 26.1.2.126 '[26.1,26.2)'
neoforge 26.2-26.3 26.3 mc26_2 26.3.0.101 '[26.2,26.4)'
legacy_neoforge 1.21-1.21.1 1.21.1 mc1_21_0 21.1.266 '[1.21,1.21.2)'
legacy_neoforge 1.21.2-1.21.4 1.21.4 mc1_21_2 21.4.161 '[1.21.2,1.21.5)'
legacy_neoforge 1.21.5-1.21.8 1.21.8 mc1_21_5 21.8.57 '[1.21.5,1.21.9)'
legacy_neoforge 1.21.9-1.21.10 1.21.10 mc1_21_9 21.10.64 '[1.21.9,1.21.11)'
legacy_neoforge 1.21.11 1.21.11 mc1_21_11 21.11.48 '[1.21.11,1.22)'
forge 26.1.x mc26_1 26.1.2-64.1.5 '[26.1,26.2)' '[62,)'
forge 26.2-26.3 mc26_2 26.3-66.1.2 '[26.2,26.4)' '[65,)'
# Forge bundles MixinExtras, which the mixins use, only from 1.21.10 (Forge 60): before that the jar carries its own.
# Forge 56 (1.21.6) moved to a new event bus (src/forge_bus7class), whose events became records in Forge 59
# (src/forge_bus7); before Forge 56 the jar uses the older one (src/forge_bus6).
legacy_forge 1.21-1.21.1 mc1_21_0 1.21.1-52.1.21 '[1.21,1.21.2)' '[51,53)' -Pforge_bus=6 -Pbundle_mixinextras
legacy_forge 1.21.3-1.21.4 mc1_21_2 1.21.4-54.1.22 '[1.21.3,1.21.5)' '[53,55)' -Pforge_bus=6 -Pbundle_mixinextras
legacy_forge 1.21.5 mc1_21_5 1.21.5-55.1.18 '[1.21.5,1.21.6)' '[55,56)' -Pforge_bus=6 -Pbundle_mixinextras
legacy_forge 1.21.6-1.21.8 mc1_21_5 1.21.8-58.1.26 '[1.21.6,1.21.9)' '[56,59)' -Pforge_bus=7class -Pbundle_mixinextras
legacy_forge 1.21.9-1.21.10 mc1_21_9 1.21.10-60.1.18 '[1.21.9,1.21.11)' '[59,61)' -Pbundle_mixinextras
legacy_forge 1.21.11 mc1_21_11 1.21.11-61.2.5 '[1.21.11,1.22)' '[61,62)'
rm -f build-all.log
echo "Done."
