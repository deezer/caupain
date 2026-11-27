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
# Tests install.sh against a fake GitHub: a stub gh, put first in the PATH, serves the releases of
# a directory, and runs the real jq filter of install.sh on a release list.
#
# Requires jq, which is available on GitHub-hosted runners.
#
# Usage: test-install.sh <directory containing caupain-github-action.tar.gz>
# This is the directory created by the assembleAction Gradle task.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <directory containing the action archive>" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dist_dir="$(cd "$1" && pwd)"
archive_name="caupain-github-action.tar.gz"
repository="test/caupain"

to_native_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

# --- Fake GitHub ---------------------------------------------------------------------------------

site_dir="$work_dir/site"
mkdir -p "$site_dir" "$work_dir/bin"

add_release() {
  local dir="$site_dir/releases/$1"
  mkdir -p "$dir"
  cp "$dist_dir/$archive_name" "$dir/"
}

for tag in v9.1.0 v9.2.0 v9.10.0 snapshot; do add_release "$tag"; done

# The only digest that has an attestation is the one of the real archive
sha256_of "$dist_dir/$archive_name" > "$site_dir/attested-digests"

# Release whose archive was not built by the release workflow, so it has no attestation
add_release v9.3.0
echo "tampered" >> "$site_dir/releases/v9.3.0/$archive_name"

# Release list of the API: v9.2.0 comes first, to check that releases are compared by version and
# not by order. Drafts, pre-releases, other major versions and other tags must be ignored.
cat > "$site_dir/releases.json" << 'EOF'
[
  {"tag_name": "v9.2.0", "draft": false, "prerelease": false},
  {"tag_name": "v10.0.0", "draft": false, "prerelease": false},
  {"tag_name": "v9.12.0", "draft": true, "prerelease": false},
  {"tag_name": "v9.11.0", "draft": false, "prerelease": true},
  {"tag_name": "v9.10.0", "draft": false, "prerelease": false},
  {"tag_name": "v9.1.0", "draft": false, "prerelease": false},
  {"tag_name": "nightly", "draft": false, "prerelease": false},
  {"tag_name": "v9.9.0-beta.1", "draft": false, "prerelease": false}
]
EOF

# Stub of the GitHub CLI, implementing only what install.sh uses. Like the real one, it needs a
# token.
cat > "$work_dir/bin/gh" << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "gh: no authentication token" >&2
  exit 4
fi

command="$1 $2"
case "$1" in
  release)
    case "$2" in
      list) ;;
      download) ;;
      *) exit 1 ;;
    esac
    if [[ "$2" == "list" ]]; then
      shift 2
      filter="."
      select_all="."
      repo_ok=false
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --jq) filter="$2"; shift 2 ;;
          --repo) [[ "$2" == "$FAKE_REPOSITORY" ]] && repo_ok=true; shift 2 ;;
          --exclude-drafts) select_all="$select_all | select(.draft | not)"; shift ;;
          --exclude-pre-releases) select_all="$select_all | select(.prerelease | not)"; shift ;;
          --json) [[ "$2" == "tagName" ]] || exit 1; shift 2 ;;
          *) shift ;;
        esac
      done
      $repo_ok || exit 1
      jq -c "[.[] | $select_all | {tagName: .tag_name}]" "$FAKE_SITE/releases.json" \
        | jq -r "$filter" | tr -d '\r'
      exit 0
    fi
    tag="$3"
    shift 3
    pattern=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --pattern) pattern="$2"; shift 2 ;;
        --repo) [[ "$2" == "$FAKE_REPOSITORY" ]] || exit 1; shift 2 ;;
        *) shift ;;
      esac
    done
    cp "$FAKE_SITE/releases/$tag/$pattern" .
    ;;
  attestation)
    [[ "$2" == "verify" ]] || exit 1
    digest="$(sha256_of "$3")"
    grep -q "$digest" "$FAKE_SITE/attested-digests" || { echo "no matching attestation" >&2; exit 1; }
    ;;
  *)
    echo "unsupported gh command: $command" >&2
    exit 1
    ;;
esac
EOF
chmod +x "$work_dir/bin/gh"

# --- Test helpers --------------------------------------------------------------------------------

counter=0
failures=0
status=0
log=""
bundle_dir=""
runner_temp="$work_dir/runner-temp"

# Runs install.sh with the given environment variables (as NAME=value arguments), and sets status,
# log (everything it printed) and bundle_dir (its output). It runs with a token, unless
# overridden.
run_install() {
  counter=$((counter + 1))
  local output_file="$work_dir/output-$counter"
  : > "$output_file"
  status=0
  log="$(
    unset CAUPAIN_ACTION_REF CAUPAIN_VERSION CAUPAIN_BUNDLE_PATH GH_TOKEN GITHUB_TOKEN
    env \
      PATH="$work_dir/bin:$PATH" \
      FAKE_SITE="$(to_native_path "$site_dir")" \
      FAKE_REPOSITORY="$repository" \
      GITHUB_OUTPUT="$output_file" \
      RUNNER_TEMP="$runner_temp" \
      CAUPAIN_ACTION_REPOSITORY="$repository" \
      GITHUB_TOKEN=some-token \
      "$@" \
      bash "$script_dir/install.sh" 2>&1
  )" || status=$?
  bundle_dir="$(sed -n 's/^bundle-dir=//p' "$output_file" | tr -d '\r')"
}

check() {
  local description="$1"
  shift
  if "$@"; then
    echo "ok   - $description"
  else
    echo "FAIL - $description"
    echo "       status=$status bundle_dir=$bundle_dir"
    echo "       | ${log//$'\n'/$'\n'       | }"
    failures=$((failures + 1))
  fi
}

succeeded() { [[ $status -eq 0 ]]; }
failed() { [[ $status -ne 0 ]]; }
logged() { [[ "$log" == *"$1"* ]]; }
not_logged() { [[ "$log" != *"$1"* ]]; }
installed_version() { [[ "$bundle_dir" == */"$1" && -f "$bundle_dir/caupain-github-action.js" ]]; }

# --- Tests ---------------------------------------------------------------------------------------

run_install CAUPAIN_ACTION_REF=v9.1.0
check "exact version tag is installed" succeeded
check "exact version tag installs that version" installed_version v9.1.0

run_install CAUPAIN_ACTION_REF=v9
check "major version tag succeeds" succeeded
check "major version tag resolves to the highest stable release by version" installed_version v9.10.0
check "major version tag logs the resolution" logged "Resolved v9 to v9.10.0"

run_install CAUPAIN_ACTION_REF=main CAUPAIN_VERSION=9.2.0
check "version input takes precedence over the ref and accepts no v prefix" installed_version v9.2.0

run_install CAUPAIN_ACTION_REF=v9 GITHUB_TOKEN= GH_TOKEN=other-token
check "GH_TOKEN is accepted instead of GITHUB_TOKEN" installed_version v9.10.0

run_install CAUPAIN_ACTION_REF=v9.1.0 GITHUB_TOKEN= RUNNER_TEMP="$work_dir/empty-runner-temp"
check "a token is required to download" failed
check "a missing token explains how to fix it" logged "token is required"

run_install CAUPAIN_ACTION_REF=v8
check "major version without release fails" failed
check "major version without release explains why" logged "No release found for version v8"

run_install CAUPAIN_ACTION_REF=main
check "branch ref fails" failed
check "branch ref explains how to fix it" logged "is not a Caupain release version"

run_install CAUPAIN_ACTION_REF=3c9ab81f
check "commit ref fails" failed
check "commit ref explains how to fix it" logged "'version' input"

run_install
check "missing version fails" failed
check "missing version explains how to fix it" logged "Set the 'version' input"

run_install CAUPAIN_ACTION_REF=snapshot
check "snapshot ref is installed" succeeded
check "snapshot ref installs the snapshot release" installed_version snapshot
run_install CAUPAIN_ACTION_REF=main CAUPAIN_VERSION=snapshot
check "snapshot version input is installed" installed_version snapshot
run_install CAUPAIN_ACTION_REF=snapshot
check "snapshot is downloaded again, as it changes" logged "Downloading Caupain snapshot"

run_install CAUPAIN_ACTION_REF=v9.99.0
check "release without assets fails" failed
check "release without assets explains why" logged "Unable to download"

run_install CAUPAIN_ACTION_REF=v9.3.0
check "archive without attestation fails" failed
check "archive without attestation explains why" logged "Provenance verification failed"
check "archive without attestation installs nothing" not_logged "bundle-dir"

run_install CAUPAIN_ACTION_REF=v9.1.0
check "already installed version succeeds" succeeded
check "already installed version is not downloaded again" not_logged "Downloading"

run_install CAUPAIN_BUNDLE_PATH="$bundle_dir"
check "local bundle succeeds" succeeded
check "local bundle is used as is" installed_version v9.1.0
check "local bundle is not downloaded" not_logged "Downloading"

run_install CAUPAIN_BUNDLE_PATH="$work_dir/missing"
check "missing local bundle fails" failed
check "missing local bundle explains why" logged "caupain-github-action.js is missing"

# The extracted bundle must start. With nothing to check it fails with a clean error message,
# which does not need network access.
run_install CAUPAIN_ACTION_REF=v9.1.0
empty_dir="$work_dir/empty"
mkdir -p "$empty_dir"
bundle_status=0
bundle_log="$(cd "$empty_dir" && node "$bundle_dir/caupain-github-action.js" 2>&1)" || bundle_status=$?
status=$bundle_status
log="$bundle_log"
check "extracted bundle runs and fails cleanly without a version catalog" failed
check "extracted bundle reports the missing version catalog" logged "::error::No version catalog found"

echo
if [[ $failures -eq 0 ]]; then
  echo "All install.sh tests passed"
else
  echo "$failures install.sh check(s) failed"
  exit 1
fi
