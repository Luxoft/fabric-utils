cd files/artifacts
./fabric.sh restart
cd ../..
gradle shadowJar

cd files
java -jar ../build/libs/fabric-configurator-fat.jar
