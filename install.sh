#!/usr/bin/env zsh

./gradlew :cli:buildCurrentArchBinary
sudo mv cli/build/bin/caupain /usr/local/bin/caupain
sudo chmod +x /usr/local/bin/caupain
