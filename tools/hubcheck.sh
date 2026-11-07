#!/bin/bash
# Runs the dev GameTests on the hub and summarises what really happened (hubtest exit 0 can hide a crash).
# 
cd "$(dirname "$0")/.."
/c/Homework/Sessions/Japan/hubtest/hubtest gradle headjar-tests . --key headinajar "$@" -- ${GRADLE_ARGS:+--} runGameTestServer ${GRADLE_ARGS:-} > build/hub-gametest.log 2>&1
echo "hubtest exit $?"
grep -vE 'Missing translations|Ambiguity between' build/hub-gametest.log | grep -nE 'ERROR|FATAL|Exception|Caused by|error:|BUILD|required tests|optional tests|test\(s\)|failed|RESULT' | cut -c1-240 | head -${LINES_MAX:-50}
