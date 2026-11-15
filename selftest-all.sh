#!/usr/bin/env bash
# Runs the in-world self-test (selftest.sh) once per release jar group, on the newest game version of each group,
# and prints one RESULT line per group. The groups and versions match build-all.sh.
#
# Usage: [SKIP=<label regex>] ./selftest-all.sh fabric|quilt|neoforge|forge|all [label filter]
#   e.g. ./selftest-all.sh forge          ./selftest-all.sh all 1.21.5
cd "$(dirname "$0")"
which=${1:-all}
filter=${2:-}

run() { # run <loader> <label> <selftest.sh arguments...>
	local loader=$1 label=$2
	shift 2
	if [ "$which" != all ] && [ "$which" != "$loader" ]; then return; fi
	if [ -n "$filter" ] && [[ "$label" != *"$filter"* ]]; then return; fi
	if [ -n "${SKIP:-}" ] && [[ "$label" =~ $SKIP ]]; then return; fi
	local out
	out=$(./selftest.sh "$@" 2>&1)
	local result
	result=$(grep -a "SELFTEST RESULT" <<<"$out" | sed 's/.*SELFTEST //')
	echo "$loader $label: ${result:-NO RESULT ($(tail -1 <<<"$out"))}"
}

for loader in fabric quilt; do
	run $loader 26.1.x ${loader}26 -Pminecraft_version=26.1.2 -Pcompat=mc26_1 -Pfabric_api_version=0.155.3+26.1.2 -Pmodmenu_version=18.0.2 '-Pminecraft_range=>=26.1 <26.2' -Pminecraft_range_name=26.1.x
	run $loader 26.2-26.3 ${loader}26 -Pminecraft_version=26.3 -Pcompat=mc26_2 -Pfabric_api_version=0.165.0+26.3 -Pmodmenu_version=21.0.0 '-Pminecraft_range=>=26.2 <26.4' -Pminecraft_range_name=26.2-26.3
	run $loader 1.21-1.21.1 $loader -Pminecraft_version=1.21.1 -Pcompat=mc1_21_0 -Pfabric_api_version=0.116.18+1.21.1 -Pmodmenu_version=11.0.5 '-Pminecraft_range=>=1.21 <1.21.2' -Pminecraft_range_name=1.21-1.21.1
	run $loader 1.21.2-1.21.4 $loader -Pminecraft_version=1.21.4 -Pcompat=mc1_21_2 -Pfabric_api_version=0.119.4+1.21.4 -Pmodmenu_version=13.0.0 '-Pminecraft_range=>=1.21.2 <1.21.5' -Pminecraft_range_name=1.21.2-1.21.4
	run $loader 1.21.5-1.21.8 $loader -Pminecraft_version=1.21.8 -Pcompat=mc1_21_5 -Pfabric_api_version=0.136.1+1.21.8 -Pmodmenu_version=15.0.0 '-Pminecraft_range=>=1.21.5 <1.21.9' -Pminecraft_range_name=1.21.5-1.21.8
	run $loader 1.21.9-1.21.10 $loader -Pminecraft_version=1.21.10 -Pcompat=mc1_21_9 -Pfabric_api_version=0.138.4+1.21.10 -Pmodmenu_version=16.0.0 '-Pminecraft_range=>=1.21.9 <1.21.11' -Pminecraft_range_name=1.21.9-1.21.10
	run $loader 1.21.11 $loader -Pminecraft_version=1.21.11 -Pcompat=mc1_21_11 -Pfabric_api_version=0.141.6+1.21.11 -Pmodmenu_version=17.0.0 '-Pminecraft_range=>=1.21.11 <1.22' -Pminecraft_range_name=1.21.11
done

run neoforge 26.1.x neoforge -Pminecraft_version=26.1.2 -Pcompat=mc26_1 -Pneo_version=26.1.2.126 '-Pminecraft_range=[26.1,26.2)'
run neoforge 26.2-26.3 neoforge -Pminecraft_version=26.3 -Pcompat=mc26_2 -Pneo_version=26.3.0.101 '-Pminecraft_range=[26.2,26.4)'
run neoforge 1.21-1.21.1 neoforge -Pminecraft_version=1.21.1 -Pcompat=mc1_21_0 -Pneo_version=21.1.266 '-Pminecraft_range=[1.21,1.21.2)' -Pjava_version=21
run neoforge 1.21.2-1.21.4 neoforge -Pminecraft_version=1.21.4 -Pcompat=mc1_21_2 -Pneo_version=21.4.161 '-Pminecraft_range=[1.21.2,1.21.5)' -Pjava_version=21
run neoforge 1.21.5-1.21.8 neoforge -Pminecraft_version=1.21.8 -Pcompat=mc1_21_5 -Pneo_version=21.8.57 '-Pminecraft_range=[1.21.5,1.21.9)' -Pjava_version=21
run neoforge 1.21.9-1.21.10 neoforge -Pminecraft_version=1.21.10 -Pcompat=mc1_21_9 -Pneo_version=21.10.64 '-Pminecraft_range=[1.21.9,1.21.11)' -Pjava_version=21
run neoforge 1.21.11 neoforge -Pminecraft_version=1.21.11 -Pcompat=mc1_21_11 -Pneo_version=21.11.48 '-Pminecraft_range=[1.21.11,1.22)' -Pjava_version=21

run forge 26.1.x forge -Pcompat=mc26_1 -Pforge_version=26.1.2-64.1.5 '-Pminecraft_range=[26.1,26.2)' '-Pforge_range=[62,)'
run forge 26.2-26.3 forge -Pcompat=mc26_2 -Pforge_version=26.3-66.1.2 '-Pminecraft_range=[26.2,26.4)' '-Pforge_range=[65,)'
# Before Forge 60 (1.21.10) the jar carries MixinExtras; the event bus differs before Forge 56 and before Forge 59.
run forge 1.21-1.21.1 forge -Pcompat=mc1_21_0 -Pforge_version=1.21.1-52.1.21 '-Pminecraft_range=[1.21,1.21.2)' '-Pforge_range=[51,53)' -Pforge_bus=6 -Pbundle_mixinextras -Pjava_version=21
run forge 1.21.3-1.21.4 forge -Pcompat=mc1_21_2 -Pforge_version=1.21.4-54.1.22 '-Pminecraft_range=[1.21.3,1.21.5)' '-Pforge_range=[53,55)' -Pforge_bus=6 -Pbundle_mixinextras -Pjava_version=21
run forge 1.21.5 forge -Pcompat=mc1_21_5 -Pforge_version=1.21.5-55.1.18 '-Pminecraft_range=[1.21.5,1.21.6)' '-Pforge_range=[55,56)' -Pforge_bus=6 -Pbundle_mixinextras -Pjava_version=21
run forge 1.21.6-1.21.8 forge -Pcompat=mc1_21_5 -Pforge_version=1.21.8-58.1.26 '-Pminecraft_range=[1.21.6,1.21.9)' '-Pforge_range=[56,59)' -Pforge_bus=7class -Pbundle_mixinextras -Pjava_version=21
run forge 1.21.9-1.21.10 forge -Pcompat=mc1_21_9 -Pforge_version=1.21.10-60.1.18 '-Pminecraft_range=[1.21.9,1.21.11)' '-Pforge_range=[59,61)' -Pbundle_mixinextras -Pjava_version=21
run forge 1.21.11 forge -Pcompat=mc1_21_11 -Pforge_version=1.21.11-61.2.5 '-Pminecraft_range=[1.21.11,1.22)' '-Pforge_range=[61,62)' -Pjava_version=21

# The oldest game version of the groups whose jar is built against a newer one ("edge" runs; same jars).
if [ -n "${EDGES:-}" ]; then
	run fabric 26.2-edge fabric26 -Pminecraft_version=26.2 -Pcompat=mc26_2 -Pfabric_api_version=0.161.0+26.2 -Pmodmenu_version=20.0.3 '-Pminecraft_range=>=26.2 <26.4' -Pminecraft_range_name=26.2-26.3
	run neoforge 26.2-edge neoforge -Pminecraft_version=26.2 -Pcompat=mc26_2 -Pneo_version=26.2.0.93 '-Pminecraft_range=[26.2,26.4)'
	run fabric 1.21-edge fabric -Pminecraft_version=1.21 -Pcompat=mc1_21_0 -Pfabric_api_version=0.102.0+1.21 -Pmodmenu_version=11.0.5 '-Pminecraft_range=>=1.21 <1.21.2' -Pminecraft_range_name=1.21-1.21.1
	run neoforge 1.21-edge neoforge -Pminecraft_version=1.21 -Pcompat=mc1_21_0 -Pneo_version=21.0.167 '-Pminecraft_range=[1.21,1.21.2)' -Pjava_version=21
	run forge 1.21-edge forge -Pcompat=mc1_21_0 -Pforge_version=1.21-51.0.33 '-Pminecraft_range=[1.21,1.21.2)' '-Pforge_range=[51,53)' -Pforge_bus=6 -Pbundle_mixinextras -Pjava_version=21
	run forge 1.21.3-edge forge -Pcompat=mc1_21_2 -Pforge_version=1.21.3-53.1.16 '-Pminecraft_range=[1.21.3,1.21.5)' '-Pforge_range=[53,55)' -Pforge_bus=6 -Pbundle_mixinextras -Pjava_version=21
	run forge 1.21.9-edge forge -Pcompat=mc1_21_9 -Pforge_version=1.21.9-59.0.5 '-Pminecraft_range=[1.21.9,1.21.11)' '-Pforge_range=[59,61)' -Pbundle_mixinextras -Pjava_version=21
	run forge 1.21.6-edge forge -Pcompat=mc1_21_5 -Pforge_version=1.21.6-56.0.9 '-Pminecraft_range=[1.21.6,1.21.9)' '-Pforge_range=[56,59)' -Pforge_bus=7class -Pbundle_mixinextras -Pjava_version=21
fi
