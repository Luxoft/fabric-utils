#!/usr/bin/env bash

#function askProceed () {
#  read -p "This command will destroy existing crypto artifacts.
#  Be sure you don't need them or check you saved them. Continue (y/n)? " ans
#  case "$ans" in
#    y|Y )
#      echo "proceeding ..."
#    ;;
#    n|N )
#      echo "exiting..."
#      exit 1
#    ;;
#    * )
#      echo "invalid response"
#      askProceed
#    ;;
#  esac
#}

# Generates Org certs using cryptogen tool
function generateCerts (){
  which cryptogen
  if [ "$?" -ne 0 ]; then
    echo "cryptogen tool not found. exiting"
    exit 1
  fi
#  echo
#  echo "##########################################################"
#  echo "##### Generate certificates using cryptogen tool #########"
#  echo "##########################################################"

  cryptogen generate --config=./channel/cryptogen.yaml
  if [ "$?" -ne 0 ]; then
    echo "Failed to generate certificates..."
    exit 1
  fi
  echo
}

CLI_TIMEOUT=10000

generateCerts
