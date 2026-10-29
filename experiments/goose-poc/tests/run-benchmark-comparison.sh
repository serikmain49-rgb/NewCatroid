#!/usr/bin/env bash
set -euo pipefail
BENCH_BACKEND=false BENCH_RESULTS_DIR=benchmark-results/java bash experiments/goose-poc/tests/run-benchmark-scenes.sh
BENCH_BACKEND=true BENCH_RESULTS_DIR=benchmark-results/goose bash experiments/goose-poc/tests/run-benchmark-scenes.sh
