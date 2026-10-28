#!/usr/bin/env bash
set -euo pipefail
: "${GOOSE:?Set GOOSE to the pinned Goose compiler executable}"
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
out="catroid/src/main/cpp/goose"
"$GOOSE" --standalone --header "$out/movement_generated.h" -o "$out/movement_generated.c" experiments/goose-poc/math.goose
sed -i -e 's/\bgs_i32\b/int32_t/g' -e 's/\bgs_f32\b/float/g' "$out/movement_generated.h"
# Keep generated files free of trailing blank lines.
python3 - "$out/movement_generated.c" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
path.write_text(path.read_text().rstrip() + '\n')
PY
