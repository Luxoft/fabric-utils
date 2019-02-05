/*
Copyright IBM Corp. 2016 All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

		 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package main

import (
	"encoding/json"
	"fmt"
	"bytes"
	"strconv"
	"time"
	"strings"
	"unicode/utf8"

	"github.com/hyperledger/fabric/core/chaincode/shim"
	pb "github.com/hyperledger/fabric/protos/peer"
)

// This chaincode implements a simple map that is stored in the state.
// The following operations are available.

// Invoke operations
// put - requires two arguments, a key and value
// remove - requires a key
// get - requires one argument, a key, and returns a value
// keys - requires no arguments, returns all keys

// SimpleChaincode example simple Chaincode implementation
type SimpleChaincode struct {
}

func ensureStringIsUTF8Valid(inputString string ) string {
    outputString := inputString
    if !utf8.ValidString(inputString) {

    		fmt.Printf("inputString is not UTF-8 valid. Converting...\n")

    		v := make([]rune, 0, len(inputString))
                    for i, r := range inputString {
                        if r == utf8.RuneError {
                            _, size := utf8.DecodeRuneInString(inputString[i:])
                            if size == 1 {
                                continue
                            }
                        }
                        v = append(v, r)
                    }
                    outputString = string(v)
    }

   return outputString
}

// Init is a no-op
func (t *SimpleChaincode) Init(stub shim.ChaincodeStubInterface) pb.Response {
	creator, err := stub.GetCreator();
	creatorString := ensureStringIsUTF8Valid(string(creator[:]))
	fmt.Printf("Init creator: %s", creatorString)

	if err != nil {
		return shim.Error(fmt.Sprintf("Failed to get creator. Error: %s", err))
	}
	stub.PutState(creatorString,[]byte("['read','write','admin']"));
	state, err := stub.GetState(creatorString);
	fmt.Printf("Check state: %s", string(state[:]))
	return shim.Success(nil)
}

func checkPermission(stub shim.ChaincodeStubInterface, permission string) bool{
	creator, err := stub.GetCreator();
	creatorString := ensureStringIsUTF8Valid(string(creator[:]))
	fmt.Printf("Creator: %s", creatorString)
	if err != nil {
		fmt.Printf("Failed to get creator. Error: %s", err)
		return false;
	}
	state, err := stub.GetState(creatorString);
	fmt.Printf("State: %s", string(state[:]))
	return state != nil && strings.Contains(string(state[:]), permission)
}

// Invoke has two functions
// put - takes two arguments, a key and value, and stores them in the state
// remove - takes one argument, a key, and removes if from the state
func (t *SimpleChaincode) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	function, args := stub.GetFunctionAndParameters()
	switch function {
	case "put":
		if !checkPermission(stub, "write") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		if len(args) < 2 {
			return shim.Error("put operation must include two arguments, a key and value")
		}
		key := args[0]
		value := args[1]

		// Check current value
		currentValue, err := stub.GetState(key)
		if err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}
		fmt.Printf("Current value len: %d", len(currentValue))

		if err := stub.PutState(key, []byte(value)); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}

		indexName := "compositeKeyTest"
		compositeKeyTestIndex, err := stub.CreateCompositeKey(indexName, []string{key})
		if err != nil {
			return shim.Error(err.Error())
		}

		valueByte := []byte{0x00}
		if err := stub.PutState(compositeKeyTestIndex, valueByte); err != nil {
			fmt.Printf("Error putting state with compositeKey %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state with compositeKey: %s", err))
		}
		
		
		if err := stub.SetEvent(key, []byte("NEW STATE")); err != nil {
			return shim.Error(fmt.Sprintf("put operation failed. Error emiting state update event with compositeKey: %s", err))
		}

		return shim.Success(nil)

	case "remove":		
		if !checkPermission(stub, "write") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		if len(args) < 1 {
			return shim.Error("remove operation must include one argument, a key")
		}
		key := args[0]

		err := stub.DelState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("remove operation failed. Error updating state: %s", err))
		}
		return shim.Success(nil)

	case "get":
		if !checkPermission(stub, "read") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
	
		if len(args) < 1 {
			return shim.Error("get operation must include one argument, a key")
		}
		key := args[0]
		
		value, err := stub.GetState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("get operation failed. Error accessing state: %s", err))
		}
		return shim.Success(value)

	case "custom_history":
		if !checkPermission(stub, "read") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}

		if len(args) < 1 {
			return shim.Error("history operation must include one argument, a key")
		}

		key := args[0]

		fmt.Printf("- start getHistoryForMarble: %s\n", key)

		resultsIterator, err := stub.GetHistoryForKey(key)
		if err != nil {
			return shim.Error(err.Error())
		}
		defer resultsIterator.Close()

		var buffer bytes.Buffer
		buffer.WriteString("[")

		bArrayMemberAlreadyWritten := false
		for resultsIterator.HasNext() {
			response, err := resultsIterator.Next()
			if err != nil {
				return shim.Error(err.Error())
			}

			if bArrayMemberAlreadyWritten == true {
				buffer.WriteString(",")
			}
			buffer.WriteString("{\"TxId\":")
			buffer.WriteString("\"")
			buffer.WriteString(response.TxId)
			buffer.WriteString("\"")

			buffer.WriteString(", \"Value\":")
			// if it was a delete operation on given key, then we need to set the
			//corresponding value null. Else, we will write the response.Value
			//as-is (as the Value itself a JSON marble)
			if response.IsDelete {
				buffer.WriteString("null")
			} else {
				buffer.WriteString(string(response.Value))
			}

			buffer.WriteString(", \"Timestamp\":")
			buffer.WriteString("\"")
			buffer.WriteString(time.Unix(response.Timestamp.Seconds, int64(response.Timestamp.Nanos)).String())
			buffer.WriteString("\"")

			buffer.WriteString(", \"IsDelete\":")
			buffer.WriteString("\"")
			buffer.WriteString(strconv.FormatBool(response.IsDelete))
			buffer.WriteString("\"")

			buffer.WriteString("}")
			bArrayMemberAlreadyWritten = true
		}
		buffer.WriteString("]")

		fmt.Printf("- history returning:\n%s\n", buffer.String())

		return shim.Success(buffer.Bytes())

	case "keys":
		if !checkPermission(stub, "read") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		if len(args) < 2 {
			return shim.Error("put operation must include two arguments, a key and value")
		}
		startKey := args[0]
		endKey := args[1]

		//sleep needed to test peer's timeout behavior when using iterators
		stime := 0
		if len(args) > 2 {
			stime, _ = strconv.Atoi(args[2])
		}

		keysIter, err := stub.GetStateByRange(startKey, endKey)
		if err != nil {
			return shim.Error(fmt.Sprintf("keys operation failed. Error accessing state: %s", err))
		}
		defer keysIter.Close()

		var keys []string
		for keysIter.HasNext() {
			//if sleeptime is specied, take a nap
			if stime > 0 {
				time.Sleep(time.Duration(stime) * time.Millisecond)
			}

			response, iterErr := keysIter.Next()
			if iterErr != nil {
				return shim.Error(fmt.Sprintf("keys operation failed. Error accessing state: %s", err))
			}
			keys = append(keys, response.Key)
		}

		for key, value := range keys {
			fmt.Printf("key %d contains %s\n", key, value)
		}

		jsonKeys, err := json.Marshal(keys)
		if err != nil {
			return shim.Error(fmt.Sprintf("keys operation failed. Error marshaling JSON: %s", err))
		}

		return shim.Success(jsonKeys)
	case "query":
		if !checkPermission(stub, "read") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		query := args[0]
		keysIter, err := stub.GetQueryResult(query)
		if err != nil {
			return shim.Error(fmt.Sprintf("query operation failed. Error accessing state: %s", err))
		}
		defer keysIter.Close()

		var keys []string
		for keysIter.HasNext() {
			response, iterErr := keysIter.Next()
			if iterErr != nil {
				return shim.Error(fmt.Sprintf("query operation failed. Error accessing state: %s", err))
			}
			keys = append(keys, response.Key)
		}

		jsonKeys, err := json.Marshal(keys)
		if err != nil {
			return shim.Error(fmt.Sprintf("query operation failed. Error marshaling JSON: %s", err))
		}

		return shim.Success(jsonKeys)
	case "history":
		if !checkPermission(stub, "read") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		key := args[0]
		keysIter, err := stub.GetHistoryForKey(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("query operation failed. Error accessing state: %s", err))
		}
		defer keysIter.Close()

		var keys []string
		for keysIter.HasNext() {
			response, iterErr := keysIter.Next()
			if iterErr != nil {
				return shim.Error(fmt.Sprintf("query operation failed. Error accessing state: %s", err))
			}
			keys = append(keys, response.TxId)
		}

		for key, txID := range keys {
			fmt.Printf("key %d contains %s\n", key, txID)
		}

		jsonKeys, err := json.Marshal(keys)
		if err != nil {
			return shim.Error(fmt.Sprintf("query operation failed. Error marshaling JSON: %s", err))
		}

		return shim.Success(jsonKeys)
		
	case "permissionRequest":
	
		key := "permissionRequest"
		
		value, err := stub.GetState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("get permission request operation failed. Error accessing state: %s", err))
		}
		if value != nil {
			return shim.Error(fmt.Sprintf("permission request allready pending. Error accessing state: %s", err))
		}
		
		creator, err := stub.GetCreator();
		if err != nil {
			fmt.Printf("Failed to get creator. Error: %s", err)
			return shim.Error(fmt.Sprintf("Failed to get creator. Error: %s", err));
		}
		
		value = creator

		if err := stub.PutState(key, value); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}		

		return shim.Success(nil)
		
	case "dropLastGrantedPermission":
		if !checkPermission(stub, "admin") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
	
		key := "lastGrantedUser"
		
		userCert, err := stub.GetState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("get permission request operation failed. Error accessing state: %s", err))
		}
		if userCert == nil {
			return shim.Error(fmt.Sprintf("no rights was granted. Error accessing state: %s", err))
		}
		
		err = stub.DelState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("remove last granted record failed. Error updating state: %s", err))
		}
		
		key = string(userCert[:])
		err = stub.DelState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("remove user rights failed. Error updating state: %s", err))
		}

		return shim.Success(nil)
		
	case "addReadWritePermission":
		if !checkPermission(stub, "admin") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		key := "permissionRequest"
		userCert, err := stub.GetState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("get permission request operation failed. Error accessing state: %s", err))
		}
		
		key = string(userCert[:])
		value := []byte("['read','write']")
		
		if err := stub.PutState(key, []byte(value)); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}		
		
		if err := stub.SetEvent(key, []byte("ReadWritePermission")); err != nil {
			return shim.Error(fmt.Sprintf("put operation failed. Error emiting state update event with compositeKey: %s", err))
		}
		
		key = "lastGrantedUser"
		
		if err := stub.PutState(key, userCert); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}	
		
		key = "permissionRequest"
		err = stub.DelState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("remove operation failed. Error updating state: %s", err))
		}

		return shim.Success(nil)
		
	case "addReadPermission":
		if !checkPermission(stub, "admin") {
			fmt.Printf("Forbidden")
			return shim.Error(fmt.Sprintf("Forbidden"))
		}
		
		key := "permissionRequest"
		userCert, err := stub.GetState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("get permission request operation failed. Error accessing state: %s", err))
		}
		
		key = string(userCert[:])
		value := []byte("['read']")

		if err := stub.PutState(key, value); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}		
		
		if err := stub.SetEvent(key, []byte("ReadPermission")); err != nil {
			return shim.Error(fmt.Sprintf("put operation failed. Error emiting state update event with compositeKey: %s", err))
		}
		
		key = "lastGrantedUser"
		
		if err := stub.PutState(key, userCert); err != nil {
			fmt.Printf("Error putting state %s", err)
			return shim.Error(fmt.Sprintf("put operation failed. Error updating state: %s", err))
		}	
		
		key = "permissionRequest"
		err = stub.DelState(key)
		if err != nil {
			return shim.Error(fmt.Sprintf("remove operation failed. Error updating state: %s", err))
		}

		return shim.Success(nil)

	default:
		return shim.Success([]byte("Unsupported operation"))
	}
}

func main() {
	err := shim.Start(new(SimpleChaincode))
	if err != nil {
		fmt.Printf("Error starting chaincode: %s", err)
	}
}