@import java.util.Map

@param Map<String, String> envVars
#!/usr/bin/env bash
set -euo pipefail

echo "[bootstrap] Starting bootstrap for $(hostname)"

# Persist environment variables to /etc/k8s-env (idempotent)
TMP_FILE="$(mktemp)"
cat >"$TMP_FILE" <<EOF
@for(Map.Entry<String, String> entry : envVars.entrySet())export ${entry.getKey()}="${entry.getValue()}"
@endfor
EOF
if ! cmp -s "$TMP_FILE" /etc/k8s-env 2>/dev/null; then
  sudo install -m 0644 "$TMP_FILE" /etc/k8s-env
fi
rm -f "$TMP_FILE"

# Auto-source on login shells
sudo bash -lc 'cat >/etc/profile.d/50-k8s-env.sh <<\'EOS'
[ -f /etc/k8s-env ] && . /etc/k8s-env
EOS
chmod 0644 /etc/profile.d/50-k8s-env.sh'

# Install minimal toolchain for kind/minikube path
chmod +x scripts/install_*.sh || true
if [ -x scripts/install_docker.sh ]; then scripts/install_docker.sh; fi
if [ -x scripts/install_kubectl.sh ]; then scripts/install_kubectl.sh; fi
if [ -x scripts/install_kind.sh ]; then scripts/install_kind.sh; fi

echo "[bootstrap] Completed"
