#!/usr/bin/env bash
if [ "$(uname)" != "Darwin" ]; then
    echo "Setting up auto-complete for Greengrass CLI."
    # Autocomplete is a feature provided by picocli, which is currently NOT supported on Mac https://github.com/remkop/picocli/issues/396

    if [ -n "$ZSH_VERSION" ]; then
        echo "Allow zsh to read bash completion specifications and functions"
        autoload -U +X compinit && compinit
        autoload -U +X bashcompinit && bashcompinit
    fi

    echo "Source the bash completion script"
    source $PWD/cli_completion.sh
fi

ln -fs $PWD/bin/greengrass-cli /usr/local/bin/greengrass-cli || sudo ln -fs $PWD/bin/greengrass-cli /usr/local/bin/greengrass-cli
echo "Start using greengrass-cli"