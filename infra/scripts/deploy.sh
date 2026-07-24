#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

SCRIPT_DIR="$REPO_DIR/infra/scripts"

bash "$SCRIPT_DIR/deploy-application.sh"
bash "$SCRIPT_DIR/deploy-nginx.sh"

echo "=== All done ==="
