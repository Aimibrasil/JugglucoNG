#!/usr/bin/env bash
#
# Architecture debt metrics and the "must not worsen" ratchet (plan task T0.3).
#
# Every metric here counts something that grew while nobody was looking: global
# static coupling, swallowed errors, duplicated classes across the mobile/wear
# source sets. None of them is a bug on its own; the point is that they may go
# down and must not go up, so improvements stick and regressions are visible.
#
# Usage:
#   scripts/arch-metrics.sh --print                  # JSON to stdout
#   scripts/arch-metrics.sh --check                  # compare with the baseline
#   scripts/arch-metrics.sh --print --root DIR       # measure another tree
#   scripts/arch-metrics.sh --check --root DIR --baseline FILE
#
# --check exits 0 when no metric grew, 1 when one did (printing which), so CI can
# gate on it. A metric going down is fine — update the baseline in the same PR to
# lock the improvement in.

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE="$ROOT/docs/architecture/metrics-baseline.json"
MODE=""

while [ $# -gt 0 ]; do
    case "$1" in
        --print|--check) MODE="${1#--}"; shift ;;
        --root) ROOT="$2"; shift 2 ;;
        --baseline) BASELINE="$2"; shift 2 ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ -z "$MODE" ]; then
    echo "usage: $0 --print|--check [--root DIR] [--baseline FILE]" >&2
    exit 2
fi

SRC="$ROOT/Common/src/main/java $ROOT/Common/src/mobile/java $ROOT/Common/src/wear/java"
NATIVES="$ROOT/Common/src/main/java/tk/glucodata/Natives.java"

count_lines() { # read stdin, print the number of lines
    grep -c '' || true
}

metric_files_touching_Applic_static()  { grep -rl "Applic\."  $SRC 2>/dev/null | count_lines; }
metric_files_touching_Natives_static() { grep -rl "Natives\." $SRC 2>/dev/null | count_lines; }

metric_kotlin_object_singletons() {
    grep -rhE '^[[:space:]]*(internal |private |public )*object [A-Za-z]' $SRC 2>/dev/null | count_lines
}

metric_volatile_fields() {
    grep -rhE '@Volatile|(^|[[:space:]])volatile[[:space:]]' $SRC 2>/dev/null | count_lines
}

metric_ad_hoc_coroutine_scopes() { grep -rh 'CoroutineScope(' $SRC 2>/dev/null | count_lines; }
metric_raw_threads()             { grep -rh 'Thread('        $SRC 2>/dev/null | count_lines; }
metric_raw_handlers()            { grep -rh 'Handler('       $SRC 2>/dev/null | count_lines; }

# A runCatching whose failure path is getOrNull/getOrElse/getOrDefault: the
# error becomes "no value" and nothing else. Plan invariant I6.
metric_runcatching_swallowed() {
    grep -rh 'runCatching' $SRC 2>/dev/null | grep -cE 'getOrNull|getOrDefault|getOrElse' || true
}

metric_catch_throwable() { grep -rh 'catch (Throwable' $SRC 2>/dev/null | count_lines; }

metric_shared_prefs_files() {
    grep -rhoE '(PREFS|PREFS_FILE|PREFS_NAME|PREF_FILE|prefsName|TELEMETRY_PREFS|CGM_READINESS_PREFS)[[:space:]]*=[[:space:]]*"[^"]+"' \
        $SRC 2>/dev/null | grep -oE '"[^"]+"' | sort -u | count_lines
}

# package.class present in both mobile and wear, extension ignored: the twin
# classes T5.1 replaces, and the mechanism behind "the watch silently loses a
# feature". NoDuplicateFqnTest guards the same thing for the build; here it is a
# number the ratchet can watch.
fqn_list() {
    local dir="$1"
    [ -d "$dir" ] || return 0
    find "$dir" -type f \( -name '*.kt' -o -name '*.java' \) -print0 |
        while IFS= read -r -d '' file; do
            local pkg
            pkg="$(sed -n 's/^[[:space:]]*package[[:space:]]\{1,\}\([A-Za-z0-9_.]*\).*/\1/p' "$file" | head -n1)"
            [ -n "$pkg" ] && printf '%s.%s\n' "$pkg" "$(basename "$file" | sed 's/\.[^.]*$//')"
        done | sort -u
}

metric_duplicate_fqn_pairs() {
    comm -12 <(fqn_list "$ROOT/Common/src/mobile/java") <(fqn_list "$ROOT/Common/src/wear/java") | count_lines
}

metric_composables_calling_jni() {
    grep -rl '@Composable' $SRC 2>/dev/null |
        while IFS= read -r file; do grep -q 'Natives\.' "$file" && echo "$file"; done | count_lines
}

# Declared in Natives.java but never referenced as Natives.<name> anywhere.
metric_dead_native_declarations() {
    local declared used
    declared="$(mktemp)"; used="$(mktemp)"
    grep -oE '[[:space:]]native[[:space:]]+[^;()]+[[:space:]]+[A-Za-z0-9_]+[[:space:]]*\(' "$NATIVES" 2>/dev/null |
        sed -E 's/.*[[:space:]]([A-Za-z0-9_]+)[[:space:]]*\(.*/\1/' | sort -u > "$declared"
    grep -rhoE 'Natives\.[A-Za-z_0-9]+' $SRC 2>/dev/null | sed 's/Natives\.//' | sort -u > "$used"
    comm -23 "$declared" "$used" | count_lines
    rm -f "$declared" "$used"
}

root_package_files() {
    # Only the shared main source set: the flavour copies are the twins, counted
    # by duplicate_fqn_pairs instead.
    find "$ROOT/Common/src/main/java/tk/glucodata" -maxdepth 1 -type f \
        \( -name '*.kt' -o -name '*.java' \) 2>/dev/null
}

metric_root_package_files() { root_package_files | count_lines; }

metric_root_package_loc() {
    [ -n "$(root_package_files)" ] && root_package_files | xargs wc -l | tail -n1 | awk '{print $1}' || echo 0
}

METRICS="
files_touching_Applic_static
files_touching_Natives_static
kotlin_object_singletons
volatile_fields
ad_hoc_coroutine_scopes
raw_threads
raw_handlers
runcatching_swallowed
catch_throwable
shared_prefs_files
duplicate_fqn_pairs
composables_calling_jni
dead_native_declarations
root_package_files
root_package_loc
"

print_json() {
    local first=1
    echo "{"
    for name in $METRICS; do
        [ $first -eq 0 ] && echo ","
        first=0
        printf '  "%s": %s' "$name" "$(metric_$name)"
    done
    echo
    echo "}"
}

check_against_baseline() {
    CURRENT_JSON="$(print_json)" BASELINE_FILE="$BASELINE" python3 -c '
import json, os, sys
current = json.loads(os.environ["CURRENT_JSON"])
try:
    baseline = json.load(open(os.environ["BASELINE_FILE"]))
except FileNotFoundError:
    sys.exit("baseline not found: " + os.environ["BASELINE_FILE"])

grew = []
for name, value in baseline.items():
    now = current.get(name)
    if now is None:
        grew.append(f"{name}: baseline has it, metrics script does not")
    elif isinstance(value, int) and now > value:
        grew.append(f"{name}: {value} -> {now}")

if grew:
    print("architecture debt grew:", file=sys.stderr)
    for line in grew:
        print(f"  {line}", file=sys.stderr)
    print("\nIf the growth is deliberate, justify it and raise the baseline in the same PR.", file=sys.stderr)
    sys.exit(1)

better = {n: (baseline[n], current[n]) for n in baseline
          if isinstance(baseline[n], int) and n in current and current[n] < baseline[n]}
if better:
    print("improved (lower the baseline to lock it in):", file=sys.stderr)
    for name, (was, now) in better.items():
        print(f"  {name}: {was} -> {now}", file=sys.stderr)
print("architecture metrics: no metric grew")
'
}

case "$MODE" in
    print) print_json ;;
    check) check_against_baseline ;;
esac
