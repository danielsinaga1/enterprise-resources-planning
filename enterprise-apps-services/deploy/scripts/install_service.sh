#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="enterpriseapps"
SERVICE_USER="enterpriseapps"
SERVICE_GROUP="enterpriseapps"
INSTALL_DIR="/opt/enterpriseapps-service"
ENV_FILE="/etc/sysconfig/enterpriseapps-service"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

JAR_SOURCE_PATH=${1:-}
if [[ -z "${JAR_SOURCE_PATH}" ]]; then
  echo "Usage: $0 /path/to/app.jar"
  exit 1
fi

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root"
  exit 1
fi

echo "Creating service user/group if missing..."
if ! id -u "${SERVICE_USER}" >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /sbin/nologin "${SERVICE_USER}"
fi

mkdir -p "${INSTALL_DIR}"
cp -f "${JAR_SOURCE_PATH}" "${INSTALL_DIR}/app.jar"
chown -R "${SERVICE_USER}:${SERVICE_GROUP}" "${INSTALL_DIR}"
chmod 750 "${INSTALL_DIR}" "${INSTALL_DIR}/app.jar"

echo "Installing systemd unit..."
cat > "${UNIT_FILE}" <<'UNIT'
[Unit]
Description=EnterpriseApps Spring Boot Service
After=network.target

[Service]
Type=simple
User=enterpriseapps
Group=enterpriseapps
WorkingDirectory=/opt/enterpriseapps-service
Environment=SPRING_PROFILES_ACTIVE=prod
EnvironmentFile=-/etc/sysconfig/enterpriseapps-service
ExecStart=/usr/bin/java -Xms256m -Xmx1024m -Djava.security.egd=file:/dev/./urandom -jar /opt/enterpriseapps-service/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
LimitNOFILE=65536
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true

[Install]
WantedBy=multi-user.target
UNIT

echo "Creating env file if missing..."
if [[ ! -f "${ENV_FILE}" ]]; then
  install -m 0640 -o root -g "${SERVICE_GROUP}" /dev/null "${ENV_FILE}"
  echo "APP_PORT=8080" >> "${ENV_FILE}"
fi

echo "Reloading and starting service..."
systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}.service"
systemctl status "${SERVICE_NAME}.service" --no-pager -l || true

echo "Done."

