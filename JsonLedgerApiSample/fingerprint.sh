#!/usr/bin/env bash

compute_canton_hash() {
  # The hash purpose integer must be prefixed to the content to be hashed as a 4 bytes big endian
  (printf "\\x00\\x00\\x00\\x$(printf '%02X' "$1")"; cat - <(cat)) | \
  # Then hash with sha256
  openssl dgst -sha256 -binary | \
  # And finally prefix with 0x12 (The multicodec code for SHA256 https://github.com/multiformats/multicodec/blob/master/table.csv#L9)
  # and 0x20, the length of the hash (32 bytes)
  ( printf '\x12\x20'; cat - )
}

# Compute a Canton public key fingerprint.
# Reads input from stdin and outputs a hash prefixed with multi-hash encoding.
compute_fingerprint() {
  # 12 is the hash purpose for public key fingerprint
  # https://github.com/digital-asset/canton/blob/main/community/base/src/main/scala/com/digitalasset/canton/crypto/HashPurpose.scala#L63
  compute_canton_hash 12
}

compute_fingerprint_from_base64() {
  local public_key_base64=$1
  echo "$public_key_base64" | openssl base64 -d  | compute_fingerprint | xxd -p -c 256
}

echo "Computing fingerprint of: $1"
compute_fingerprint_from_base64 $1
