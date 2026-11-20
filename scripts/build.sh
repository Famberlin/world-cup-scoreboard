#!/usr/bin/env bash
# Runs Maven with all output in target/build.log and prints only a short summary.
# Usage: bash scripts/build.sh [maven args...]   (default: verify)
#   bash scripts/build.sh test -Dtest=ScoreboardTest#startsMatch
#   bash scripts/build.sh verify -Djava.version=21   # on an older local JDK
# Run from the repo root (the parent POM); test reports are read from every module's target/.
set -uo pipefail
shopt -s nullglob
cd "$(dirname "$0")/.."
mkdir -p target
log=target/build.log
[ $# -eq 0 ] && set -- verify

rm -rf */target/surefire-reports  # stale reports would be misread as current failures
mvn -q "$@" > "$log" 2>&1
status=$?

if [ $status -eq 0 ]; then
    # -q hides the test summary, so count from the surefire reports.
    xml=(*/target/surefire-reports/TEST-*.xml)
    if [ ${#xml[@]} -gt 0 ]; then
        tests=$(grep -ho 'tests="[0-9]*"' "${xml[@]}" | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
        echo "OK: $tests tests passed (mvn $*)"
    else
        echo "OK (mvn $*)"
    fi
    exit 0
fi

echo "FAILED (exit $status, mvn $*). Full log: $log"
txt=(*/target/surefire-reports/*.txt)
if [ ${#txt[@]} -gt 0 ] && grep -lqE '<<< (FAILURE|ERROR)!' "${txt[@]}"; then
    # Test failures: name plus the first lines of the assertion message, per failing test.
    grep -hE -A6 '<<< (FAILURE|ERROR)!' "${txt[@]}" \
        | grep -vE '^(Tests run:|--|\s*at |\s*$)' | sed -E 's/ -- Time elapsed.*<<</ <<</' | head -40
else
    # Compiler, Spotless, JaCoCo and other build errors, without the Maven help boilerplate.
    grep -E '^\[ERROR\]' "$log" \
        | grep -vE 'Re-run Maven|-e switch|-X switch|^\[ERROR\] \[Help 1\]|-rf :|For more information|full stack trace|^\[ERROR\] *$|To see the full|After correcting|resume the build|dump files' \
        | sed -e "s|$PWD/||" -e "s| -> \[Help 1\]||" | head -30
fi
exit $status
