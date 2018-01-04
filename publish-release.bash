#!/usr/bin/env bash

set -e

version=$(git describe --tags)
regex='^[0-9]+\.[0-9]+\.[0-9]+$'
if [[ ! ${version} =~ $regex ]]
then
    echo "Current commit is not a release"
    exit;
fi

echo "

Pushing to Github
-----------------------------
"
git push
git push --tags

echo "

Push to Google Play
-----------------------------
"

read -s -p "Enter signing password: " password

SIGNING_PASSWORD=${password} ./gradlew assembleRelease

# Upload apk and listing to Google Play
SIGNING_PASSWORD=${password} ./gradlew publishRelease

echo "

Release published!
"