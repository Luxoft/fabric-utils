##Fabric configurator

Utility to create channels and deploy/update chaincodes.

###Build
    gradle shadowJar

###Run Example
    java -jar build/libs/fabric-configurator-fat.jar --type upgrade --config network/fabric-devnet.yaml
    
TODO: finish    
###Parameters
    --type=(config|deploy|update|enroll) - configure type
    --name=mychcode - name of chaincode to deploy or update
    --config=fabric.yaml - configuration file 
    --ca_key
    --user_affiliation
