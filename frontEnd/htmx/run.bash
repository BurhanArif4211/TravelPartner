#!/bin/bash

# Get the machine's local IP address
get_local_ip() {
    local ip
    # Try Linux (ip command)
    if ip=$(ip route get 1.2.3.4 2>/dev/null | awk '{print $7;exit}'); then
        echo "$ip"
        return
    fi
    # Try macOS (ipconfig)
    if ip=$(ipconfig getifaddr en0 2>/dev/null) || ip=$(ipconfig getifaddr en1 2>/dev/null); then
        echo "$ip"
        return
    fi
    # Fallback (hostname)
    if ip=$(hostname -I 2>/dev/null | awk '{print $1}'); then
        echo "$ip"
        return
    fi
    echo "Failed to detect IP" >&2
    exit 1
}

# Create a temporary directory
TEMP_DIR="temp_serve_$(date +%s)"
mkdir -p "$TEMP_DIR"

# Copy all files to temp directory (exclude the script itself)
find . -maxdepth 1 -mindepth 1 ! -name "$(basename "$0")" -exec cp -r {} "$TEMP_DIR" \;

# Inject IP into files
IP="$(get_local_ip):7000"
echo "Using IP: $IP"
FILES=("dashboard.html" "index.html" "dashboard.js")
for file in "${FILES[@]}"; do
    if [ -f "$TEMP_DIR/$file" ]; then
        sed -i.bak "s/localhost:7000/$IP/g" "$TEMP_DIR/$file"
        rm -f "$TEMP_DIR/$file.bak"
    fi
done

# Serve files from temp directory and cleanup on exit
cleanup() {
    echo "Cleaning up..."
    rm -rf "$TEMP_DIR"
    exit 0
}
trap cleanup SIGINT SIGTERM

cd "$TEMP_DIR" && npx serve -l tcp://0.0.0.0:3000
cleanup
