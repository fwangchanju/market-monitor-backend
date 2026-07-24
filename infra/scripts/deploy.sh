#!/bin/bash
set -e

SCRIPT_DIR="$HOME/repo/market-monitor-backend/infra/scripts"

bash "$SCRIPT_DIR/deploy-application.sh"
bash "$SCRIPT_DIR/deploy-nginx.sh"

echo "=== All done ==="
