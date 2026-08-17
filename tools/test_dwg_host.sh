#!/usr/bin/env bash
# اختبار قراءة DWG المحلي على مضيف Linux. يترجم جسر JNI نفسه مع LibreDWG ثم
# يختبر كيانات حقيقية من حزمة LibreDWG، بما فيها مسار الخطأ للملف غير الصالح.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$(mktemp -d /tmp/corewall-libredwg-host.XXXXXX)"
HOST_BIN="${BUILD_DIR}/corewall-dwg-host-test"
BAD_FILE="${BUILD_DIR}/malformed.dwg"
trap 'rm -rf "${BUILD_DIR}"' EXIT

cmake -G Ninja -S "${ROOT}/third_party/libredwg" -B "${BUILD_DIR}/libredwg" \
  -DBUILD_SHARED_LIBS=OFF \
  -DLIBREDWG_LIBONLY=ON \
  -DLIBREDWG_DISABLE_WRITE=ON \
  -DLIBREDWG_DISABLE_JSON=ON \
  -DBUILD_TESTING=OFF \
  -DENABLE_LTO=OFF \
  -DDISABLE_WERROR=ON >/dev/null
cmake --build "${BUILD_DIR}/libredwg" --parallel 2 >/dev/null

JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
c++ -std=c++20 -DCOREWALL_DWG_HOST_TEST \
  -I"${JAVA_HOME}/include" \
  -I"${JAVA_HOME}/include/linux" \
  -I"${ROOT}/third_party/libredwg/include" \
  -I"${ROOT}/third_party/libredwg/src" \
  -I"${BUILD_DIR}/libredwg/src" \
  "${ROOT}/app/src/main/cpp/dwg_jni.cpp" \
  "${BUILD_DIR}/libredwg/libredwg.a" -lm -o "${HOST_BIN}"

assert_entity() {
  local fixture="$1" expected="$2" payload
  payload="$("${HOST_BIN}" "${ROOT}/third_party/libredwg/test/test-data/${fixture}")"
  grep -q '"ok":true' <<<"${payload}"
  grep -q "\"type\":\"${expected}\"" <<<"${payload}"
}

assert_entity "2018/Line.dwg" "LINE"
assert_entity "2018/Polyline.dwg" "POLYLINE"
assert_entity "2018/Ellipse.dwg" "ELLIPSE"
assert_entity "2018/Text.dwg" "TEXT"
assert_entity "2018/Point.dwg" "POINT"

printf 'AC1027\nintentionally incomplete fixture\n' > "${BAD_FILE}"
if "${HOST_BIN}" "${BAD_FILE}" > "${BUILD_DIR}/bad.json"; then
  echo "Malformed DWG unexpectedly parsed successfully" >&2
  exit 1
fi
grep -q '"ok":false' "${BUILD_DIR}/bad.json"

echo "DWG host geometry fixtures passed"
