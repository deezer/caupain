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
# Tests install.sh against a fake GitHub, served over HTTP by a local node server.
#
# Usage: test-install.sh <directory containing caupain-github-action.tar.gz and its .sha256 file>
# This is the directory created by the assembleAction Gradle task.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <directory containing the action archive and its checksum>" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dist_dir="$(cd "$1" && pwd)"
archive_name="caupain-github-action.tar.gz"
repository="test/caupain"

to_native_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

work_dir="$(mktemp -d)"
server_pid=""
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2> /dev/null || true
    wait "$server_pid" 2> /dev/null || true
  fi
  rm -rf "$work_dir"
}
trap cleanup EXIT

# --- Fake GitHub ---------------------------------------------------------------------------------

site_dir="$work_dir/site"
add_release() {
  local tag="$1"
  local dir="$site_dir/$repository/releases/download/$tag"
  mkdir -p "$dir"
  cp "$dist_dir/$archive_name" "$dist_dir/$archive_name.sha256" "$dir/"
}

for tag in v9.1.0 v9.2.0 v9.10.0; do add_release "$tag"; done

# Release with a wrong checksum
add_release v9.3.0
echo "0000000000000000000000000000000000000000000000000000000000000000  $archive_name" \
  > "$site_dir/$repository/releases/download/v9.3.0/$archive_name.sha256"

# Release list of the API: v9.2.0 comes first, to check that releases are compared by version and
# not by order. Drafts, pre-releases and other major versions must be ignored.
mkdir -p "$site_dir/repos/$repository"
cat > "$site_dir/repos/$repository/releases" << 'EOF'
[
  {"tag_name": "v9.2.0", "draft": false, "prerelease": false},
  {"tag_name": "v10.0.0", "draft": false, "prerelease": false},
  {"tag_name": "v9.12.0", "draft": true, "prerelease": false},
  {"tag_name": "v9.11.0", "draft": false, "prerelease": true},
  {"tag_name": "v9.10.0", "draft": false, "prerelease": false},
  {"tag_name": "v9.1.0", "draft": false, "prerelease": false}
]
EOF

cat > "$work_dir/server.js" << 'EOF'
const http = require("http");
const fs = require("fs");
const path = require("path");
const root = process.argv[2];
http
  .createServer((request, response) => {
    const file = path.join(root, decodeURIComponent(request.url.split("?")[0]));
    fs.readFile(file, (error, data) => {
      if (error) {
        response.statusCode = 404;
        response.end();
      } else {
        response.end(data);
      }
    });
  })
  .listen(0, "127.0.0.1", function () {
    console.log(this.address().port);
  });
EOF
node "$(to_native_path "$work_dir/server.js")" "$(to_native_path "$site_dir")" > "$work_dir/port" &
server_pid=$!
for _ in $(seq 1 50); do
  [[ -s "$work_dir/port" ]] && break
  sleep 0.1
done
[[ -s "$work_dir/port" ]] || { echo "The fake GitHub server did not start"; exit 1; }
fake_url="http://127.0.0.1:$(tr -d '\r\n' < "$work_dir/port")"

# --- Test helpers --------------------------------------------------------------------------------

counter=0
failures=0
status=0
log=""
bundle_dir=""
runner_temp="$work_dir/runner-temp"

# Runs install.sh with the given environment variables (as NAME=value arguments), and sets status,
# log (everything it printed) and bundle_dir (its output).
run_install() {
  counter=$((counter + 1))
  local output_file="$work_dir/output-$counter"
  : > "$output_file"
  status=0
  log="$(
    unset CAUPAIN_ACTION_REF CAUPAIN_VERSION CAUPAIN_BUNDLE_PATH GITHUB_TOKEN
    env \
      GITHUB_OUTPUT="$output_file" \
      RUNNER_TEMP="$runner_temp" \
      CAUPAIN_ACTION_REPOSITORY="$repository" \
      CAUPAIN_API_URL="$fake_url" \
      CAUPAIN_DOWNLOAD_URL="$fake_url" \
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

run_install CAUPAIN_ACTION_REF=v9 GITHUB_TOKEN=some-token
check "a token does not prevent releases lookup" installed_version v9.10.0

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

run_install CAUPAIN_ACTION_REF=v9.99.0
check "release without assets fails" failed
check "release without assets explains why" logged "Unable to download"

run_install CAUPAIN_ACTION_REF=v9.3.0
check "wrong checksum fails" failed
check "wrong checksum explains why" logged "Checksum verification failed"
check "wrong checksum installs nothing" not_logged "bundle-dir"

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
