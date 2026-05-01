#!/usr/bin/env bats
# Unit tests for bump.sh. Run with: bats tools/release/bump.bats

setup() {
  BUMP_SH="${BATS_TEST_DIRNAME}/bump.sh"
  TMP_REPO="$(mktemp -d)"
  cat > "$TMP_REPO/version.properties" <<'EOF'
### Updated by release.sh ###
project.versioning.major=5
project.versioning.minor=1
project.versioning.patch=1
project.versioning.build=0
project.versioning.type=rc
#############################
EOF
  echo "5.1.1-rc0 50101000" > "$TMP_REPO/VERSION"
}

teardown() {
  rm -rf "$TMP_REPO"
}

bump() {
  "$BUMP_SH" --repo-root="$TMP_REPO" "$@"
}

# ----- check mode ----------------------------------------------------------

@test "check: passes on consistent state" {
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=5.1.1-rc0"* ]]
  [[ "$output" == *"current_code=50101000"* ]]
}

@test "check: fails on VERSION/version.properties drift (name)" {
  echo "5.1.0-rc0 50100000" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on VERSION/version.properties drift (code)" {
  echo "5.1.1-rc0 99999999" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on duplicate key" {
  echo "project.versioning.major=6" >> "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"major"* ]]
  [[ "$output" == *"found 2"* ]]
}

@test "check: fails on missing key" {
  sed -i '/project\.versioning\.type=/d' "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"type"* ]]
  [[ "$output" == *"found 0"* ]]
}

@test "check: fails on bad type" {
  sed -i 's/^project\.versioning\.type=.*/project.versioning.type=foo/' "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
}

@test "check: fails on malformed VERSION" {
  echo "garbage" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"VERSION file does not match"* ]]
}

# ----- plan: bump kinds ----------------------------------------------------

@test "plan: build bump increments build" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.1.1-rc1"* ]]
  [[ "$output" == *"new_code=50101010"* ]]
}

@test "plan: patch bump zeros build" {
  run bump --mode=plan --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.1.2-rc0"* ]]
  [[ "$output" == *"new_code=50102000"* ]]
}

@test "plan: minor bump zeros patch and build" {
  run bump --mode=plan --bump-kind=minor --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.2.0-rc0"* ]]
  [[ "$output" == *"new_code=50200000"* ]]
}

@test "plan: major bump zeros minor, patch, build" {
  run bump --mode=plan --bump-kind=major --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=6.0.0-rc0"* ]]
  [[ "$output" == *"new_code=60000000"* ]]
}

@test "plan: version-type beta switches type" {
  run bump --mode=plan --bump-kind=build --version-type=beta
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.1.1-beta1"* ]]
}

@test "plan: keep-current preserves type" {
  # change current type to beta in fixture
  sed -i 's/^project\.versioning\.type=.*/project.versioning.type=beta/' "$TMP_REPO/version.properties"
  echo "5.1.1-beta0 50101000" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.1.1-beta1"* ]]
}

# ----- plan: override ------------------------------------------------------

@test "plan: version-override accepts valid version" {
  run bump --mode=plan --version-override=5.2.0-rc0
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=5.2.0-rc0"* ]]
  [[ "$output" == *"new_code=50200000"* ]]
}

@test "plan: version-override rejects bad regex (build=100)" {
  run bump --mode=plan --version-override=5.1.2-rc100
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "plan: version-override rejects bad type" {
  run bump --mode=plan --version-override=5.1.2-alpha0
  [ "$status" -ne 0 ]
}

@test "plan: version-override rejects leading zero" {
  run bump --mode=plan --version-override=5.01.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"leading zero"* ]]
}

@test "plan: version-override rejects no-op identity" {
  run bump --mode=plan --version-override=5.1.1-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"no-op"* ]]
}

@test "plan: version-override rejects monotonicity break" {
  run bump --mode=plan --version-override=5.1.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"monotonicity"* ]]
}

# ----- plan: bounds --------------------------------------------------------

@test "plan: rejects build overflow when bumping past 99" {
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=99/' "$TMP_REPO/version.properties"
  echo "5.1.1-rc99 50101990" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

@test "plan: rejects patch overflow when bumping past 99" {
  sed -i 's/^project\.versioning\.patch=.*/project.versioning.patch=99/' "$TMP_REPO/version.properties"
  echo "5.1.99-rc0 50199000" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=patch --version-type=keep-current
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

# ----- plan: expected-current ----------------------------------------------

@test "plan: expected-current matches passes" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current --expected-current=5.1.1-rc0
  [ "$status" -eq 0 ]
}

@test "plan: expected-current mismatch fails" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current --expected-current=5.0.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"expected-current"* ]]
}

# ----- write mode ----------------------------------------------------------

@test "write: rewrites both files correctly" {
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.build=1$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "5.1.1-rc1 50101010" ]
}

@test "write: preserves comment block" {
  run bump --mode=write --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties"
  grep -q '^#############################$' "$TMP_REPO/version.properties"
}

@test "write: preserves key order" {
  run bump --mode=write --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -nE '^project\.versioning\.' "$TMP_REPO/version.properties" > "$TMP_REPO/order.txt"
  # Five keys, in order: major, minor, patch, build, type.
  run cat "$TMP_REPO/order.txt"
  [[ "${lines[0]}" == *"major="* ]]
  [[ "${lines[1]}" == *"minor="* ]]
  [[ "${lines[2]}" == *"patch="* ]]
  [[ "${lines[3]}" == *"build="* ]]
  [[ "${lines[4]}" == *"type="* ]]
}

@test "write: type switch persists" {
  run bump --mode=write --bump-kind=build --version-type=beta
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.type=beta$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "5.1.1-beta1 50101010" ]
}

@test "write: idempotent re-check passes" {
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=5.1.1-rc1"* ]]
}

@test "write: header refresh idempotent if already updated" {
  sed -i 's|^### Updated by release\.sh ###$|### Updated by tools/release/bump.sh ###|' "$TMP_REPO/version.properties"
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [ "$(grep -c '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties")" -eq 1 ]
}

# ----- mode parsing --------------------------------------------------------

@test "rejects missing --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO"
  [ "$status" -ne 0 ]
}

@test "rejects invalid --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=foo
  [ "$status" -ne 0 ]
}

@test "rejects unknown flag" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=check --frobnicate=yes
  [ "$status" -ne 0 ]
}
