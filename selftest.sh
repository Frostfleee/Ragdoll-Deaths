#!/usr/bin/env bash
# Runs the in-world self-test (see src/main/java/.../client/SelfTest.java) for one loader and game version and prints
# its SELFTEST lines. The game creates a world, kills a few mobs and the player, and quits by itself.
#
# Usage: ./selftest.sh fabric|quilt|neoforge|forge <gradle -P properties...>
#   e.g. ./selftest.sh fabric -Pminecraft_version=1.21.10 -Pcompat=mc1_21_9 ...    (1.21.x Fabric/Quilt use legacy/)
# EMF=1 runs it with Entity Model Features, Entity Texture Features and resource packs made for them (Fresh Animations,
# AL's packs) installed in the test game, then takes them out again (see selftest-emf.py).
set -uo pipefail
cd "$(dirname "$0")"
loader=$1; shift
case $loader in
	fabric) dir=legacy; extra=("-Ploader=fabric") ;;
	quilt) dir=legacy; extra=("-Ploader=quilt" -PquiltRun) ;;
	fabric26) dir=.; extra=("-Ploader=fabric") ;;
	quilt26) dir=.; extra=("-Ploader=quilt" -PquiltRun) ;;
	neoforge) dir=neoforge; extra=() ;;
	# Forge's development run reads the mod's resources from the build folder by its real path, which a sandbox that
	# redirects AppData can hide; the self-test builds Forge in the home folder instead.
	forge) dir=forge; extra=("-Pbuild_root=$HOME/.ragdolldeaths-selftest") ;;
esac
log=$(mktemp)
# The game stays off the screen and away from the mouse for the whole run (see test-window-guard.ps1).
guard_stop=$(mktemp -u)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$PWD/test-window-guard.ps1")" "$(cygpath -w "$guard_stop")" > /dev/null 2>&1 &
trap 'touch "$guard_stop"' EXIT
if [ -n "${EMF:-}" ]; then
	case $loader in
		fabric26|quilt26) run=run; emfloader=fabric ;;
		fabric|quilt) run=legacy/run; emfloader=fabric ;;
		neoforge) run="$(cygpath -u "$LOCALAPPDATA")/ragdolldeaths-build/neoforge/run"; emfloader=neoforge ;;
		forge) run="$HOME/.ragdolldeaths-selftest/forge/run"; emfloader=forge ;;
	esac
	game=""
	for arg in "$@"; do
		case $arg in
			-Pminecraft_version=*) game=${arg#-Pminecraft_version=} ;;
			-Pforge_version=*) [ -z "$game" ] && game=${arg#-Pforge_version=} && game=${game%%-*} ;;
		esac
	done
	cache="$HOME/.ragdolldeaths-selftest/emf-cache"
	python selftest-emf.py "$emfloader" "$game" "$(cygpath -w "$cache")" > /dev/null || { echo "EMF mode: no EMF for $emfloader $game"; exit 1; }
	mkdir -p "$run/mods" "$run/resourcepacks"
	rm -f "$run"/mods/entity_model_features-*.jar "$run"/mods/entity_texture_features-*.jar
	cp "$cache"/mods/*-"$game"-"$emfloader".jar "$run/mods/" 2>/dev/null || cp "$cache"/mods/*"$emfloader"*.jar "$run/mods/"
	cp "$cache"/packs/*.zip "$run/resourcepacks/"
	touch "$run/options.txt"
	cp "$run/options.txt" "$run/options.txt.before-emf"
	# every pack, listed both as chosen and as allowed although made for another game version
	packs=$(cd "$cache/packs" && ls *.zip | python -c "import json,sys; print(json.dumps(['file/'+l.strip() for l in sys.stdin if l.strip()]))")
	grep -v '^resourcePacks:\|^incompatibleResourcePacks:' "$run/options.txt.before-emf" > "$run/options.txt"
	echo "resourcePacks:[\"vanilla\",${packs#[}" >> "$run/options.txt"
	echo "incompatibleResourcePacks:$packs" >> "$run/options.txt"
	# The game only applies newly listed packs from its next start: the first run just registers them.
	timeout 1500 ./gradlew -p "$dir" runClient -Pselftest "${extra[@]}" "$@" > /dev/null 2>&1
	timeout 1500 ./gradlew -p "$dir" runClient -Pselftest "${extra[@]}" "$@" > "$log" 2>&1
	rc=$?
	echo "EMF mode: $(grep -a 'Reloading ResourceManager' "$log" | head -1 | tr ',' ' ' | grep -o 'file/' | wc -l) resource packs applied"
	rm -f "$run"/mods/entity_model_features-*.jar "$run"/mods/entity_texture_features-*.jar
	(cd "$cache/packs" && ls *.zip) | while read -r pack; do rm -f "$run/resourcepacks/$pack"; done
	mv "$run/options.txt.before-emf" "$run/options.txt"
else
	# OneDrive sometimes locks files under build/; try again when that is what stopped the build.
	for attempt in 1 2 3 4; do
		timeout 1500 ./gradlew -p "$dir" runClient -Pselftest "${extra[@]}" "$@" > "$log" 2>&1
		rc=$?
		grep -aqE "stale outputs|Unable to delete|being used by another process|Cannot snapshot|unreadable" "$log" || break
		rm -rf "$dir/build/classes" "$dir/build/resources" 2> /dev/null
		sleep 5
	done
fi
grep -a "SELFTEST title\|SELFTEST in world\|SELFTEST [0-9]\|Loading Minecraft .* with \(Fabric\|Quilt\)\|Mixing .* from ragdolldeaths\|Exception\|Disabling ragdolls\|Physics ragdoll failed" "$log" | grep -v "^\s*at " | sed 's/^.*\(SELFTEST\)/\1/' | cut -c1-220 | head -40
# Last and uncut: selftest-all.sh reads this line, however much came before it.
grep -a "SELFTEST RESULT" "$log" | sed 's/^.*\(SELFTEST\)/\1/'
echo "mixins applied: $(grep -ac 'from ragdolldeaths\|from #ragdolldeaths' "$log")  gradle rc=$rc  log=$log"
