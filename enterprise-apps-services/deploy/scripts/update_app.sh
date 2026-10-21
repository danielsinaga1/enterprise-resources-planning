#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="enterpriseapps"
INSTALL_DIR="/opt/enterpriseapps-service"
JAR_PATH="${INSTALL_DIR}/app.jar"

NEW_JAR=${1:-}
if [[ -z "${NEW_JAR}" ]]; then
  echo "Usage: $0 /path/to/new-app.jar"
  exit 1
fi

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root"
  exit 1
fi

timestamp=$(date +%Y%m%d-%H%M%S)
backup_path="${INSTALL_DIR}/app-${timestamp}.bak.jar"

echo "Stopping service..."
systemctl stop "${SERVICE_NAME}.service" || true

if [[ -f "${JAR_PATH}" ]]; then
  echo "Backing up current jar to ${backup_path}"
  cp -f "${JAR_PATH}" "${backup_path}"
fi

echo "Deploying new jar..."
cp -f "${NEW_JAR}" "${JAR_PATH}"
chown --reference="${backup_path}" "${JAR_PATH}" 2>/dev/null || true
chmod 640 "${JAR_PATH}"

echo "Starting service..."
systemctl start "${SERVICE_NAME}.service"
sleep 2
if ! systemctl is-active --quiet "${SERVICE_NAME}.service"; then
  echo "Service failed to start. Rolling back..." >&2
  if [[ -f "${backup_path}" ]]; then
    cp -f "${backup_path}" "${JAR_PATH}"
    systemctl start "${SERVICE_NAME}.service" || true
  fi
  systemctl status "${SERVICE_NAME}.service" --no-pager -l || true
  exit 1
fi

echo "Update successful."

