#!/usr/bin/env bash
# GGym toolchain environment. Source this, or rely on the block this script
# appends to ~/.bashrc (see bootstrap-toolchain.sh).
export JAVA_HOME="$HOME/tools/jdk-21"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
