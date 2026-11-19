#!/usr/bin/env bash
# The release gate. A version is finished only when this prints RELEASE CHECK PASSED and exits with 0.
#
# It checks that nothing was left out (versions, changelog, translations, mixin lists, a test run for every jar), runs the
# unit tests, the in-game self-test on every loader and game version, and the oldest version of every shared jar, and only
# then builds the release jars and checks each of them. Everything that failed is listed at the end and in
# release-check-report.txt. It never retries a failed test: find the cause, fix it and run this again (see CLAUDE.md).
#
# Usage: ./release-check.sh          everything (over an hour)
#        ./release-check.sh --quick  only the checks without the game, and the unit tests (a minute or two)
set -uo pipefail
cd "$(dirname "$0")"

quick=${1:-}
report=release-check-report.txt
work=$(mktemp -d)
started=$(date +%s)
failed=()
: > "$report"

say() { echo "$*" | tee -a "$report"; }
step() { say; say "=== $*"; }
fail() { failed+=("$1"); say "FAILED: $1"; }

unit_tests() {
	./gradlew --stop > /dev/null 2>&1
	for attempt in 1 2 3 4; do
		rm -rf build/classes build/resources build/test-results build/generated 2> /dev/null
		./gradlew test --console=plain > "$work/unit.log" 2>&1
		local rc=$?
		# OneDrive locking files under build/ says nothing about the tests: only that is tried again.
		grep -aqE "stale outputs|Unable to delete|Cannot snapshot|unreadable" "$work/unit.log" || return $rc
		sleep 8
	done
	return 1
}

step "Nothing left out"
python -I release-check.py static 2>&1 | tee -a "$report"
[ "${PIPESTATUS[0]}" = 0 ] || fail "the checks without the game (above)"

step "Unit tests"
if unit_tests; then
	say "unit tests passed"
else
	grep -aE "FAILED|What went wrong|tests completed" -A3 "$work/unit.log" | cut -c1-240 | head -40 | tee -a "$report"
	fail "unit tests"
fi

if [ "$quick" != "--quick" ]; then
	step "In-game self-test, every loader and game version"
	./selftest-all.sh all 2>&1 | sed -E 's/mobs without a body \[\], problems \[\], //' > "$work/all.txt"
	cut -c1-160 "$work/all.txt" | tee -a "$report"

	step "In-game self-test, the oldest version of every shared jar"
	EDGES=1 ./selftest-all.sh all edge 2>&1 | sed -E 's/mobs without a body \[\], problems \[\], //' > "$work/edge.txt"
	cut -c1-160 "$work/edge.txt" | tee -a "$report"

	step "Self-test results"
	python -I release-check.py results "$work/all.txt" "$work/edge.txt" 2>&1 | tee -a "$report"
	[ "${PIPESTATUS[0]}" = 0 ] || fail "the in-game self-test (above)"

	if [ ${#failed[@]} -eq 0 ]; then
		step "Release jars"
		./gradlew --stop > /dev/null 2>&1
		rm -rf build/classes build/resources build/test-results build/generated 2> /dev/null
		builds="$(cygpath -u "$(powershell.exe -NoProfile -Command "[Environment]::GetFolderPath('Desktop')" | tr -d '\r')")/Ragdoll Deaths/Builds"
		if ./build-all.sh "$builds" > "$work/build.log" 2>&1; then
			python -I release-check.py jars "$builds" "$started" 2>&1 | tee -a "$report"
			[ "${PIPESTATUS[0]}" = 0 ] || fail "the release jars (above)"
		else
			tail -30 "$work/build.log" | cut -c1-240 | tee -a "$report"
			fail "building the release jars"
		fi
	else
		say
		say "The release jars were not built, because something above failed."
	fi
fi

version=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
step "Still to check by hand (TESTING.md, New in $version)"
awk -v v="New in $version" 'index($0, "## ") == 1 { on = index($0, v) > 0 } on && /^- \[ \]/' TESTING.md | tee -a "$report"

say
if [ ${#failed[@]} -gt 0 ]; then
	say "RELEASE CHECK FAILED (${#failed[@]}):"
	for item in "${failed[@]}"; do say "  - $item"; done
	exit 1
fi
if [ "$quick" = "--quick" ]; then
	say "QUICK CHECK PASSED. This is not the release check: the game was not started and no jars were built."
else
	say "RELEASE CHECK PASSED"
fi
