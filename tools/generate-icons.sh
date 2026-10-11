#!/bin/sh
# Regenerates the tool-window animation frames in src/main/resources/icons using Manul SVG pixel art.
set -eu
node "$(dirname "$0")/generate-icons.mjs"
