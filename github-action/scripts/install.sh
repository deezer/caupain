#!/usr/bin/env bash
#
# MIT License
#
# Copyright (c) 2026 Deezer
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
#

#
# Installs the bundle of the Caupain GitHub action: resolves the release matching the version the
# action is used with, downloads its bundle, verifies the checksum and extracts it.
#
# Only needs bash, curl, tar, a SHA-256 tool and node, which are available on GitHub-hosted runners.
# This stays compatible with bash 3.2, which is the one shipped with macOS.
#
# Inputs, as environment variables:
#   CAUPAIN_ACTION_REF         Ref the action is used with (github.action_ref), e.g. v1 or v1.2.3
#   CAUPAIN_ACTION_REPOSITORY  Repository of the action (github.action_repository)
#   CAUPAIN_VERSION            Explicit version, taking precedence over the ref
#   CAUPAIN_BUNDLE_PATH        Directory of an already extracted bundle, skipping the download
#   GITHUB_TOKEN               Token to query the GitHub API with, to avoid rate limits
#   RUNNER_TEMP                Directory to extract the bundle to
#   CAUPAIN_API_URL            GitHub API URL, to be overridden for tests only
#   CAUPAIN_DOWNLOAD_URL       GitHub download URL, to be overridden for tests only
#
# Output (written to GITHUB_OUTPUT): bundle-dir, the directory containing the extracted bundle.

set -euo pipefail

repository="${CAUPAIN_ACTION_REPOSITORY:-deezer/caupain}"
api_url="${CAUPAIN_API_URL:-https://api.github.com}"
download_url="${CAUPAIN_DOWNLOAD_URL:-https://github.com}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
archive_name="caupain-github-action.tar.gz"
entry_file="caupain-github-action.js"

fail() {
  echo "::error::$*"
  exit 1
}

# On Windows, bash runs in an environment that has its own path format. Native programs such as
# node need the Windows format (with forward slashes), whereas tar needs the POSIX one, as it
# would otherwise consider "C:" as a remote host.
to_native_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

to_posix_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else echo "$1"; fi
}

# Sets the tag variable to the tag of the latest release of the given major version. This does not
# print the tag, as failing from a command substitution would hide the error message.
resolve_latest_release() {
  local major="$1"
  local releases
  local -a curl_args=(-fsSL --retry 3 -H "Accept: application/vnd.github+json")
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    curl_args+=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  releases="$(curl "${curl_args[@]}" "$api_url/repos/$repository/releases?per_page=100")" \
    || fail "Unable to list the releases of $repository"
  tag="$(echo "$releases" | node "$(to_native_path "$script_dir/latest-release.js")" "$major")" \
    || fail "No release found for version v$major of $repository"
}

# Verifies the checksum of the archive in the current directory
verify_checksum() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum -c "$archive_name.sha256" > /dev/null
  else
    shasum -a 256 -c "$archive_name.sha256" > /dev/null
  fi
}

if [[ -n "${CAUPAIN_BUNDLE_PATH:-}" ]]; then
  [[ -f "$CAUPAIN_BUNDLE_PATH/$entry_file" ]] \
    || fail "No bundle found at $CAUPAIN_BUNDLE_PATH: $entry_file is missing"
  echo "bundle-dir=$(to_native_path "$CAUPAIN_BUNDLE_PATH")" >> "$output_file"
  exit 0
fi

requested="${CAUPAIN_VERSION:-${CAUPAIN_ACTION_REF:-}}"
if [[ -z "$requested" ]]; then
  fail "Unable to determine the version of Caupain to use. Set the 'version' input."
fi
spec="$requested"
if [[ "$spec" =~ ^[0-9] ]]; then spec="v$spec"; fi

tag=""
if [[ "$spec" =~ ^v([0-9]+)$ ]]; then
  resolve_latest_release "${BASH_REMATCH[1]}"
  echo "Resolved $spec to $tag"
elif [[ "$spec" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([-+].*)?$ ]]; then
  tag="$spec"
else
  fail "'$requested' is not a Caupain release version. Use the action with a version tag" \
    "(for example v1 or v1.2.3), or set the 'version' input."
fi

destination="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/caupain-action/$tag"
destination="$(to_posix_path "$destination")"

if [[ ! -f "$destination/$entry_file" ]]; then
  work_dir="$(mktemp -d)"
  trap 'rm -rf "$work_dir"' EXIT
  base_url="$download_url/$repository/releases/download/$tag"

  echo "Downloading Caupain $tag"
  (
    cd "$work_dir"
    curl -fsSL --retry 3 -o "$archive_name" "$base_url/$archive_name" \
      || fail "Unable to download $base_url/$archive_name"
    curl -fsSL --retry 3 -o "$archive_name.sha256" "$base_url/$archive_name.sha256" \
      || fail "Unable to download $base_url/$archive_name.sha256"
    verify_checksum || fail "Checksum verification failed for $archive_name"
    mkdir -p "$destination"
    tar -xzf "$archive_name" -C "$destination"
  )
fi

[[ -f "$destination/$entry_file" ]] || fail "Invalid bundle for $tag: $entry_file is missing"
echo "bundle-dir=$(to_native_path "$destination")" >> "$output_file"
