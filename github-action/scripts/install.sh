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
# action is used with, downloads its bundle, verifies its build provenance and extracts it.
#
# Only needs bash, the GitHub CLI (gh), tar and node, which are available on GitHub-hosted runners.
# This stays compatible with bash 3.2, which is the one shipped with macOS.
#
# Inputs, as environment variables:
#   CAUPAIN_ACTION_REF         Ref the action is used with (github.action_ref), e.g. v1 or v1.2.3
#   CAUPAIN_ACTION_REPOSITORY  Repository of the action (github.action_repository)
#   CAUPAIN_VERSION            Explicit version, taking precedence over the ref
#   CAUPAIN_BUNDLE_PATH        Directory of an already extracted bundle, skipping the download
#   GH_TOKEN / GITHUB_TOKEN    Token gh authenticates with
#   RUNNER_TEMP                Directory to extract the bundle to
#
# Output (written to GITHUB_OUTPUT): bundle-dir, the directory containing the extracted bundle.

set -euo pipefail

repository="${CAUPAIN_ACTION_REPOSITORY:-deezer/caupain}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"
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

# Makes sure gh is usable, which it is not without a token
require_gh() {
  command -v gh > /dev/null 2>&1 \
    || fail "The GitHub CLI (gh) is required to download Caupain. See https://cli.github.com"
  export GH_TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
  [[ -n "$GH_TOKEN" ]] \
    || fail "A GitHub token is required to download Caupain. Set GH_TOKEN or GITHUB_TOKEN."
}

# Sets the tag variable to the tag of the latest stable release of the given major version. This
# does not print the tag, as failing from a command substitution would hide the error message.
resolve_latest_release() {
  local major="$1"
  # gh lists releases by date, but they must be compared by version. Tags that are not versions
  # are ignored.
  local filter='[.[].tagName
    | select(test("^v'"$major"'\\.[0-9]+\\.[0-9]+$"))]
    | sort_by(ltrimstr("v") | split(".") | map(tonumber))
    | last // empty'
  tag="$(gh release list --repo "$repository" --limit 100 \
    --exclude-drafts --exclude-pre-releases --json tagName --jq "$filter")" \
    || fail "Unable to list the releases of $repository"
  [[ -n "$tag" ]] || fail "No release found for version v$major of $repository"
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
  require_gh
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
  require_gh
  work_dir="$(mktemp -d)"
  trap 'rm -rf "$work_dir"' EXIT

  echo "Downloading Caupain $tag"
  (
    # gh is a native program, so it is given relative paths, which are valid in any environment
    cd "$work_dir"
    gh release download "$tag" --repo "$repository" --pattern "$archive_name" \
      || fail "Unable to download $archive_name from release $tag of $repository"
    gh attestation verify "$archive_name" --repo "$repository" > /dev/null \
      || fail "Provenance verification failed for $archive_name of release $tag"
    mkdir -p "$destination"
    tar -xzf "$archive_name" -C "$destination"
  )
fi

[[ -f "$destination/$entry_file" ]] || fail "Invalid bundle for $tag: $entry_file is missing"
echo "bundle-dir=$(to_native_path "$destination")" >> "$output_file"
