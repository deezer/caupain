#!/usr/bin/env zsh

for shell in bash zsh fish; do
  ./gradlew :cli:runJvm --console=plain -q --args="--generate-completion $shell" >cli/completions/"$shell"-completion.sh
done
