#Fabric utils
Project contains convenient client and utility tools for Hyperledger Fabric.

All tools uses `fabric.yaml` descriptor to get an overview of the Fabric network structure.

##fabric.yaml structure
###Client
To work with already configured channel you will need to provide following data
- admins – list of users
    - key-name – key value that can be used for referring user in this config file
        - name – user name
        - cert – path to user certificate pem file
        - privateKey – path to user private key pem file
        - mspID – id of organization(predefined in fabric network) that user belongs to
- eventhubs – list of event hubs
    - key-name – key value that can be used for referring eventhubs in this config file
        - url – url for access
        - pemFile – certificate of CA that was issuing eventhub's certificate
        - sslProvider – ssl provider to use, openSSL or JDK
        - negotationType – encryption protocol to use, TLS or plainText
        - hostnameOverride – host name that eventshub's  cert should correspond to
- peers – list of peers
    - key-name – key value that can be used for referring peer in this config file
        - url – url for access
        - pemFile – certificate of CA that was issuing peer's certificate
        - sslProvider – ssl provider to use, openSSL or JDK
        - negotationType – encryption protocol to use, TLS or plainText
        - hostnameOverride – host name that peer cert should correspond to
- orderers – list of orderers
    - key-name – key value that can be used for referring orderer in this config file
        - url – url for access
        - pemFile – certificate of CA that was issuing orderer's certificate
        - sslProvider – ssl provider to use, openSSL or JDK
        - negotationType – encryption protocol to use, TLS or plainText
        - hostnameOverride – host name that peer cert should correspond to
        - waitTime: set request`s timeout in ms
- channels – list of channels
    - key-name – key value that can be used for referring channel in application
        - admin – user that will be used for working with this channel
        - orderers – list of orderer key-names that serve this channel
        - peers – list of peer key-names that serve this channel
        - eventhubs – list of eventhub key-names that serve this channel
- cas – list of CAs
    - key-name – key value that can be used for referring CA in application
        - url – url for access
        - pemFile – TLS certificate of the relevant CA
        - allowAllHostNames – true/false, override certificates CN Host matching
        - adminLogin – login of CA admin user
        - adminSecret – secret of CA admin user
        - mspID – MSP identifier of organisation

###Configurator
To configure network from scratch you will need to provide Network client data in addition to following:
- chaincodes – list of chaincodes
    - key-name – key value that can be used for referring chaincode in this config file
        - id – id of chaincode to be referenced by application
        - sourceLocation – path to chaincode, final path consist of ./{sourceLocationPrefix }/src/{sourceLocation}
        - sourceLocationPrefix – path prefix to chaincode
        - version – version number for this chaincode, should consist of v{number}
        - type – chaincode`s format\language
        - initArguments – list  of arguments to for initialization function as JSON strings
- channels – list of channels
    - key-name – key value that can be used for referring channel in application
        - txFile – transaction file used for channel creation
        - chaincodes – list of chaincode key-names that should be deployed to this channel
        