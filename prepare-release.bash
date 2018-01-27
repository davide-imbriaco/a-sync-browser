#!/bin/bash

set -e

NEW_VERSION_NAME=$1
OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle" | awk '{print $2}' | tr -d "\"")
if [[ -z ${NEW_VERSION_NAME} ]]
then
    echo "New version name is empty. Please set a new version. Current version: $OLD_VERSION_NAME"
    exit
fi

echo "

Updating Translations
-----------------------------
"
tx push -s
# Force push/pull to make sure this is executed. Apparently tx only compares timestamps, not file
# contents. So if a file was `touch`ed, it won't be updated by default.
tx pull -a -f
git add -A "app/src/main/res/values-*/strings.xml"
if ! git diff --cached --exit-code;
then
    git commit -m "Imported translations"
fi

echo "

Updating Version
-----------------------------
"
OLD_VERSION_CODE=$(grep "versionCode" "app/build.gradle" -m 1 | awk '{print $2}')
NEW_VERSION_CODE=$(($OLD_VERSION_CODE + 1))
sed -i "s/versionCode $OLD_VERSION_CODE/versionCode $NEW_VERSION_CODE/" "app/build.gradle"
sed -i "s/versionName \"$OLD_VERSION_NAME\"/versionName \"$NEW_VERSION_NAME\"/" "app/build.gradle"

LIBRARY_NAME="com.github.Nutomic:syncthing-java"
sed -i "s/$LIBRARY_NAME:$OLD_VERSION_NAME/$LIBRARY_NAME:$NEW_VERSION_NAME/" "app/build.gradle"

git add "app/build.gradle"
git commit -m "Version $NEW_VERSION_NAME"
git tag ${NEW_VERSION_NAME}

echo "

Running Lint
-----------------------------
"
./gradlew clean lintVitalRelease

echo "
Update ready.
"
