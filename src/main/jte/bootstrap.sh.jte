@import java.util.Map
@import java.util.List

@param Map<String, String> envVars
@param List<String> installScripts
#!/usr/bin/env bash
set -euo pipefail

echo "[bootstrap] Starting bootstrap for $(hostname)"

# Persist environment variables to /etc/k8s-env (idempotent)
TMP_FILE="$(mktemp)"
cat >"$TMP_FILE" <<EOF
@for(Map.Entry<String, String> entry : envVars.entrySet())export ${entry.getKey()}="${entry.getValue()}"
@endfor
EOF
# Append per-VM environment if present (provided via Vagrant shell.env)
if [ -n "${"$"}{VM_NAME:-}" ]; then echo "export VM_NAME=\"$VM_NAME\"" >>"$TMP_FILE"; fi
if [ -n "${"$"}{ROLE:-}" ]; then echo "export ROLE=\"$ROLE\"" >>"$TMP_FILE"; fi
if [ -n "${"$"}{VM_IP:-}" ]; then echo "export VM_IP=\"$VM_IP\"" >>"$TMP_FILE"; fi
if [ -n "${"$"}{CONTROL_PLANE:-}" ]; then echo "export CONTROL_PLANE=\"$CONTROL_PLANE\"" >>"$TMP_FILE"; fi
if ! cmp -s "$TMP_FILE" /etc/k8s-env 2>/dev/null; then
  sudo install -m 0644 "$TMP_FILE" /etc/k8s-env
fi
rm -f "$TMP_FILE"

# Auto-source on login shells
sudo bash -lc 'cat >/etc/profile.d/50-k8s-env.sh <<\'EOS'
[ -f /etc/k8s-env ] && . /etc/k8s-env
EOS
chmod 0644 /etc/profile.d/50-k8s-env.sh'

@for(String script : installScripts)if [ -x /vagrant/scripts/${script} ]; then /vagrant/scripts/${script}; fi
@endfor

echo "[bootstrap] Completed"
