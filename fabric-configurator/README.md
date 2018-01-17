##Fabric configurator

Utility to configure fabric network from scratch

###Build
    gradle shadowJar

###Run Example
    java -jar build/libs/fabric-configurator-fat.jar --type upgrade --config network/fabric-devnet.yaml
    
###Parameters
    --type=(config|deploy|update) - configure type
    --name=mychcode - name of chaincode to deploy or update
    --config=fabric.yaml - configuration file 
