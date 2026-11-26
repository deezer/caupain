// Reads the JSON list of releases of a repository from the standard input, and prints the tag of
// the latest stable release of the major version given as argument (e.g. 1 for v1.x.y releases).
// Exits with status 2 if there is no such release.
//
// Releases are compared by version, and not by publication date, so that a patch release of an
// older minor version never takes precedence over a more recent minor version.

const major = process.argv[2];
if (!/^[0-9]+$/.test(major || "")) {
  console.error("Usage: latest-release.js <major version>");
  process.exit(1);
}
const pattern = new RegExp("^v" + major + "\\.([0-9]+)\\.([0-9]+)$");

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
});
process.stdin.on("end", () => {
  let best = null;
  for (const release of JSON.parse(input)) {
    if (release.draft || release.prerelease) continue;
    const match = pattern.exec(release.tag_name);
    if (!match) continue;
    const minor = Number(match[1]);
    const patch = Number(match[2]);
    if (
      best === null ||
      minor > best.minor ||
      (minor === best.minor && patch > best.patch)
    ) {
      best = { minor, patch, tag: release.tag_name };
    }
  }
  if (best === null) process.exit(2);
  console.log(best.tag);
});
