#!/usr/bin/env bash
# Runs the in-world self-test (see src/main/java/.../client/SelfTest.java) for one loader and game version and prints
# its SELFTEST lines. The game creates a world, kills a few mobs and the player, and quits by itself.
#
# Usage: ./selftest.sh fabric|quilt|neoforge|forge <gradle -P properties...>
#   e.g. ./selftest.sh fabric -Pminecraft_version=1.21.10 -Pcompat=mc1_21_9 ...    (1.21.x Fabric/Quilt use legacy/)
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
timeout 1500 ./gradlew -p "$dir" runClient -Pselftest "${extra[@]}" "$@" > "$log" 2>&1
rc=$?
grep -a "SELFTEST RESULT\|SELFTEST title\|SELFTEST in world\|SELFTEST [0-9]\|Loading Minecraft .* with \(Fabric\|Quilt\)\|Mixing .* from ragdolldeaths\|Exception\|Disabling ragdolls\|Physics ragdoll failed" "$log" | grep -v "^\s*at " | sed 's/^.*\(SELFTEST\)/\1/' | cut -c1-220 | head -40
echo "mixins applied: $(grep -ac 'from ragdolldeaths\|from #ragdolldeaths' "$log")  gradle rc=$rc  log=$log"
