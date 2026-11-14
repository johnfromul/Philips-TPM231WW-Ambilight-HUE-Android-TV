#!/system/bin/sh
set -eu
disk=${1:-}
if [ -z "$disk" ]; then
    volumes=$(sm list-volumes public | awk '$2 == "mounted" && $3 != "null" {print $3}')
    set -- $volumes
    if [ "$#" -ne 1 ]; then
        echo "Expected one mounted public USB volume. Run: sm list-volumes public" >&2
        exit 1
    fi
    disk="/storage/$1"
fi
if [ ! -d "$disk" ] || [ ! -w "$disk" ]; then
    echo "ADB shell cannot write to $disk" >&2
    exit 1
fi
out="$disk/ambilight+hue"
mkdir -p "$out"
name="direct-sync-$(date +%Y%m%d-%H%M%S).log"
echo "Writing $out/$name. Press Ctrl+C to stop."
exec logcat -b main -b system -b crash -v threadtime -T 1 -f "$out/$name" -r 8192 -n 8 HueDiagnostics:I RootSyncPerf:I DirectSyncTest:I ActivityManager:I AndroidRuntime:E '*:S'
