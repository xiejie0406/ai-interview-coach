#!/usr/bin/env bash

set -Eeuo pipefail

# 在选定的目标 Linux 环境中执行。脚本不安装软件、不启动容器，也不写业务数据库。
# 完整通过需要显式提供可在目标环境启动的 API 命令；命令中的账号/密钥应通过环境变量或密钥挂载注入。

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${1:-${REPO_ROOT}/target/aps-linux-verification}"
SUMMARY_FILE="${OUTPUT_DIR}/summary.tsv"
BASELINE_FILE="${OUTPUT_DIR}/runtime-baseline.txt"
STARTUP_TIMEOUT_SECONDS="${APS_STARTUP_TIMEOUT_SECONDS:-60}"
FAILURES=0
NOT_RUN=0

mkdir -p -- "${OUTPUT_DIR}"
: > "${SUMMARY_FILE}"

record_result() {
  local name="$1"
  local status="$2"
  local detail="$3"
  printf '%s\t%s\t%s\n' "${name}" "${status}" "${detail}" >> "${SUMMARY_FILE}"
  printf '[%s] %s - %s\n' "${status}" "${name}" "${detail}"
}

require_command() {
  local command_name="$1"
  if command -v "${command_name}" >/dev/null 2>&1; then
    record_result "command:${command_name}" "PASS" "$(command -v "${command_name}")"
  else
    record_result "command:${command_name}" "FAIL" "command not found"
    FAILURES=$((FAILURES + 1))
  fi
}

run_step() {
  local name="$1"
  shift
  local log_file="${OUTPUT_DIR}/${name}.log"
  if "$@" >"${log_file}" 2>&1; then
    record_result "${name}" "PASS" "log=${log_file}"
  else
    local exit_code=$?
    record_result "${name}" "FAIL" "exit=${exit_code}; log=${log_file}"
    FAILURES=$((FAILURES + 1))
  fi
}

run_process_smoke() {
  local name="$1"
  local command_text="$2"
  local ready_pattern="$3"
  local health_url="${4:-}"
  local log_file="${OUTPUT_DIR}/${name}.log"
  local pid
  local elapsed=0
  local ready=0

  # 不打印 command_text，避免把由操作者注入的凭据写进证据。
  bash -lc "${command_text}" >"${log_file}" 2>&1 &
  pid=$!

  while (( elapsed < STARTUP_TIMEOUT_SECONDS )); do
    if grep -Eq -- "${ready_pattern}" "${log_file}"; then
      if [[ -z "${health_url}" ]] || curl --fail --silent --show-error "${health_url}" >/dev/null; then
        ready=1
        break
      fi
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      break
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  if kill -0 "${pid}" 2>/dev/null; then
    kill -TERM "${pid}" 2>/dev/null || true
    wait "${pid}" 2>/dev/null || true
  else
    wait "${pid}" 2>/dev/null || true
  fi

  if (( ready == 1 )); then
    record_result "${name}" "PASS" "ready in ${elapsed}s; log=${log_file}"
  else
    record_result "${name}" "FAIL" "not ready within ${STARTUP_TIMEOUT_SECONDS}s; log=${log_file}"
    FAILURES=$((FAILURES + 1))
  fi
}

if [[ "$(uname -s)" != "Linux" ]]; then
  record_result "platform" "FAIL" "this verification must run on the selected Linux target"
  exit 2
fi

{
  printf 'captured_at_utc=%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  printf 'repository=%s\n' "${REPO_ROOT}"
  printf 'kernel=%s\n' "$(uname -srvmo)"
  printf 'architecture=%s\n' "$(uname -m)"
  if [[ -r /etc/os-release ]]; then
    printf '\n[/etc/os-release]\n'
    cat /etc/os-release
  fi
  printf '\n[glibc]\n'
  ldd --version 2>&1 | head -n 1 || true
  printf '\n[cpu]\n'
  lscpu 2>&1 || true
  printf '\n[memory]\n'
  free -h 2>&1 || true
  printf '\n[java]\n'
  java -version 2>&1 || true
  printf '\n[maven]\n'
  mvn -version 2>&1 || true
  printf '\n[node]\n'
  node --version 2>&1 || true
  printf '\n[npm]\n'
  npm --version 2>&1 || true
} > "${BASELINE_FILE}"

record_result "platform" "PASS" "Linux $(uname -m); baseline=${BASELINE_FILE}"
for command_name in java mvn node npm jar curl grep ldd lscpu free; do
  require_command "${command_name}"
done

JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 || true)"
if [[ "${JAVA_VERSION_LINE}" =~ version\ \"17\. ]]; then
  record_result "java-major" "PASS" "${JAVA_VERSION_LINE}"
else
  record_result "java-major" "FAIL" "expected Java 17; actual=${JAVA_VERSION_LINE}"
  FAILURES=$((FAILURES + 1))
fi

GLIBC_VERSION_LINE="$(ldd --version 2>&1 | head -n 1 || true)"
if grep -Eiq -- 'glibc|gnu libc' <<<"${GLIBC_VERSION_LINE}"; then
  record_result "glibc" "PASS" "${GLIBC_VERSION_LINE}"
else
  record_result "glibc" "FAIL" "GNU glibc not detected; actual=${GLIBC_VERSION_LINE}"
  FAILURES=$((FAILURES + 1))
fi

if (( FAILURES == 0 )); then
  run_step "ortools-jni-smoke" \
    mvn -f "${REPO_ROOT}/ruoyi-backend/aps/pom.xml" \
      -pl aps-solver-ortools -am \
      -Dtest=OrToolsNativeSmokeTest \
      -Dsurefire.failIfNoSpecifiedTests=false \
      test --no-transfer-progress

  run_step "backend-package" \
    mvn -f "${REPO_ROOT}/ruoyi-backend/pom.xml" \
      -pl ruoyi-admin,aps/aps-worker -am \
      -DskipTests package --no-transfer-progress

  WORKER_JAR="${REPO_ROOT}/ruoyi-backend/aps/aps-worker/target/aps-worker-3.9.2.jar"
  API_JAR="${REPO_ROOT}/ruoyi-backend/ruoyi-admin/target/ruoyi-admin.jar"

  if [[ -f "${WORKER_JAR}" ]]; then
    run_step "worker-artifact" jar tf "${WORKER_JAR}"
    run_process_smoke \
      "worker-disabled-startup" \
      "APS_ENABLED=false APS_WORKER_ENABLED=false APS_WORKER_POLLING_ENABLED=false java -jar '${WORKER_JAR}'" \
      "Started ApsWorkerApplication"
  else
    record_result "worker-artifact" "FAIL" "missing ${WORKER_JAR}"
    FAILURES=$((FAILURES + 1))
  fi

  if [[ -f "${API_JAR}" ]]; then
    run_step "api-artifact" jar tf "${API_JAR}"
  else
    record_result "api-artifact" "FAIL" "missing ${API_JAR}"
    FAILURES=$((FAILURES + 1))
  fi

  if [[ -n "${APS_API_SMOKE_COMMAND:-}" && -n "${APS_API_HEALTH_URL:-}" ]]; then
    run_process_smoke \
      "api-startup" \
      "${APS_API_SMOKE_COMMAND}" \
      "Started RuoYiApplication" \
      "${APS_API_HEALTH_URL}"
  else
    record_result "api-startup" "NOT_RUN" "set APS_API_SMOKE_COMMAND and APS_API_HEALTH_URL for the selected target"
    NOT_RUN=$((NOT_RUN + 1))
  fi

  if [[ "${APS_VERIFY_FRONTEND_BUILD:-0}" == "1" ]]; then
    if [[ -d "${REPO_ROOT}/frontend/admin-web/node_modules" ]]; then
      run_step "frontend-package" npm --prefix "${REPO_ROOT}/frontend/admin-web" run build:prod
    else
      record_result "frontend-package" "FAIL" "frontend/admin-web/node_modules is absent; provision with the approved lockfile before running"
      FAILURES=$((FAILURES + 1))
    fi
  else
    record_result "frontend-package" "NOT_RUN" "set APS_VERIFY_FRONTEND_BUILD=1 after provisioning lockfile dependencies"
    NOT_RUN=$((NOT_RUN + 1))
  fi
fi

printf '\nSummary: failures=%s, not_run=%s, evidence=%s\n' "${FAILURES}" "${NOT_RUN}" "${OUTPUT_DIR}"
if (( FAILURES > 0 || NOT_RUN > 0 )); then
  exit 1
fi
