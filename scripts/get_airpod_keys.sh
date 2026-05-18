#!/usr/bin/env bash

set -u

SYSTEM_KEYCHAIN="/Library/Keychains/System.keychain"

usage() {
  cat <<'EOF'
Usage:
  ./get_airpod_keys.sh
  ./get_airpod_keys.sh --address AA:BB:CC:DD:EE:FF

Extracts AirPods / Beats MagicAcc keys from the macOS MobileBluetooth
Keychain item for CAPod:
  - MagicAccIRK     -> CAPod Identity key
  - MagicAccEncKey  -> CAPod Encryption key

macOS may show Keychain permission prompts. These keys are sensitive.
EOF
}

normalize_mac() {
  printf '%s\n' "$1" | tr '[:lower:]' '[:upper:]'
}

discover_bluetooth_addresses() {
  system_profiler SPBluetoothDataType -json 2>/dev/null \
    | awk -F '"' '/"device_address"[[:space:]]*:/ { print $4 }' \
    | sort -u
}

lookup_mobilebluetooth_item() {
  local address="$1"

  security find-generic-password \
    -a "$address" \
    -s MobileBluetooth \
    -g "$SYSTEM_KEYCHAIN" 2>&1
}

extract_plist_value() {
  local key="$1"

  # security(1) prints the password plist as an escaped one-line string, so
  # this intentionally parses the <key>...</key><string>...</string> pattern.
  sed -nE "s/.*<key>${key}<\\/key>[^<]*<string>([^<]+)<\\/string>.*/\\1/p"
}

print_keys_for_address() {
  local address="$1"
  local output
  local irk
  local enc_key
  local local_address

  output="$(lookup_mobilebluetooth_item "$address")"

  irk="$(printf '%s\n' "$output" | extract_plist_value "MagicAccIRK")"
  enc_key="$(printf '%s\n' "$output" | extract_plist_value "MagicAccEncKey")"
  local_address="$(printf '%s\n' "$output" | extract_plist_value "LocalAddress")"

  if [[ -z "$irk" || -z "$enc_key" ]]; then
    return 1
  fi

  printf 'Bluetooth address: %s\n' "$address"
  if [[ -n "$local_address" ]]; then
    printf 'Local Mac address: %s\n' "$local_address"
  fi
  printf 'CAPod Identity key / MagicAccIRK:\n%s\n\n' "$irk"
  printf 'CAPod Encryption key / MagicAccEncKey:\n%s\n' "$enc_key"
  printf '%s\n' '---'
  return 0
}

main() {
  local addresses=()
  local found=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --address|-a)
        if [[ $# -lt 2 ]]; then
          usage >&2
          exit 2
        fi
        addresses+=("$(normalize_mac "$2")")
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        printf 'Unknown argument: %s\n\n' "$1" >&2
        usage >&2
        exit 2
        ;;
    esac
  done

  if [[ ${#addresses[@]} -eq 0 ]]; then
    while IFS= read -r address; do
      [[ -n "$address" ]] && addresses+=("$(normalize_mac "$address")")
    done < <(discover_bluetooth_addresses)
  fi

  if [[ ${#addresses[@]} -eq 0 ]]; then
    printf 'No Bluetooth device addresses found. Try --address AA:BB:CC:DD:EE:FF\n' >&2
    exit 1
  fi

  for address in "${addresses[@]}"; do
    if print_keys_for_address "$address"; then
      found=1
    fi
  done

  if [[ "$found" -eq 0 ]]; then
    printf 'No MobileBluetooth entries with MagicAccIRK/MagicAccEncKey were found.\n' >&2
    printf 'Make sure the AirPods are paired with this Mac, then try again.\n' >&2
    exit 1
  fi
}

main "$@"
