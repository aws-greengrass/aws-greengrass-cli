#!/usr/bin/env sh

SCRIPT_DIR="$(dirname "$(readlink -f "$0" &2>/dev/null || echo "$(pwd)/""$0")")"
if [ "$(uname)" != "Darwin" ]; then
    echo "Setting up auto-complete for Greengrass CLI."
    # Autocomplete is a feature provided by picocli, which is currently NOT supported on Mac https://github.com/remkop/picocli/issues/396

    if [ -n "$ZSH_VERSION" ]; then
        echo "Allow zsh to read bash completion specifications and functions"
        autoload -U +X compinit && compinit
        autoload -U +X bashcompinit && bashcompinit
    fi

    if [ -n "$BASH_VERSION" ]; then
        echo "Source the bash completion script"
        bash -c 'source $SCRIPT_DIR/cli_completion.sh'
    fi
fi
ln -fs $SCRIPT_DIR/bin/greengrass-cli /usr/local/bin/greengrass-cli &2>/dev/null || sudo ln -fs $SCRIPT_DIR/bin/greengrass-cli /usr/local/bin/greengrass-cli
echo "Start using greengrass-cli"
