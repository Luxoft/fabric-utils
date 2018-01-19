##Fabric configurator

Utility to create channels and deploy/update chaincodes.

###Build
    gradle shadowJar

###Run Example
    java -jar build/libs/fabric-configurator-fat.jar --type upgrade --config network/fabric-devnet.yaml

Types of operations supported by the configurator:

- **config** - create channels and deploy chaincodes using fabric.yaml
    - `%jar% --type=config --config=network/fabric-devnet.yaml`
        - *config* - fabric.yaml configuration
- **deploy** - deploy chaincode
    - `%jar% --type=deploy --name=mychaincode`
        - *name* - chaincode name
- **update** - update chaincode
    - `%jar% --type=update --name=mychaincode`
        - *name* - chaincode name
- **enroll** - enroll users to fabric 
    - `%jar% --type=enroll --ca_key=ca.luxoft.com`
        - *ca_key* - CA, in which enroll users
