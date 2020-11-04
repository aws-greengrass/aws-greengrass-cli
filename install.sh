#!/usr/bin/env sh

SCRIPT_DIR="$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$(pwd)/""$0")")"
ln -fs $SCRIPT_DIR/bin/greengrass-cli /usr/local/bin/greengrass-cli &2>/dev/null || sudo ln -fs $SCRIPT_DIR/bin/greengrass-cli /usr/local/bin/greengrass-cli
echo "Start using greengrass-cli"
