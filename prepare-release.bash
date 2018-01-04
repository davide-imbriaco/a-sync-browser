#!/bin/bash

set -e

NEW_VERSION_NAME=$1
OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle" | awk '{print $2}')
if [[ -z ${NEW_VERSION_NAME} ]]
then
    echo "New version name is empty. Please set a new version. Current version: $OLD_VERSION_NAME"
    exit
fi


echo "

Running Lint
-----------------------------
"
./gradlew clean lintVitalRelease


echo "

Updating Version
-----------------------------
"
OLD_VERSION_CODE=$(grep "versionCode" "app/build.gradle" -m 1 | awk '{print $2}')
NEW_VERSION_CODE=$(($OLD_VERSION_CODE + 1))
sed -i "s/versionCode $OLD_VERSION_CODE/versionCode $NEW_VERSION_CODE/" "app/build.gradle"

OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle" | awk '{print $2}')
sed -i "s/$OLD_VERSION_NAME/\"$NEW_VERSION_NAME\"/" "app/build.gradle"
git add "app/build.gradle"
git commit -m "Version $NEW_VERSION_NAME"
git tag ${NEW_VERSION_NAME}

echo "
Update ready.
"
