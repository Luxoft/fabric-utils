#!/usr/bin/env bash

cd files/artifacts

echo '### Restarting fabric'
./fabric.sh restart

echo '### Building configurator'
cd ../..
gradle shadowJar

echo '### Running configurator'
cd files
java -jar ../build/libs/fabric-configurator-fat.jar
