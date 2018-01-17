##Fabric user enrollment

Utility tool for new users enrollment    

###Build
    gradle shadowJar

### Run
    export ca_key=ca.luxoft.com
    export user_affiliation=org1
    
    java -jar build/libs/fabric-user-enrollment-fat.jar