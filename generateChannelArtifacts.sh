#!/usr/bin/env bash

#function askProceed () {
#  read -p "This command will destroy existing channel artifacts.
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

# Generate orderer genesis block, channel configuration transaction and
# anchor peer update transactions
function generateChannelArtifacts() {
  which configtxgen
  if [ "$?" -ne 0 ]; then
    echo "configtxgen tool not found. exiting"
    exit 1
  fi

  echo "##########################################################"
  echo "#########  Generating Orderer Genesis block ##############"
  echo "##########################################################"
  # Note: For some unknown reason (at least for now) the block file can't be
  # named orderer.genesis.block or the orderer will fail to launch!
  configtxgen -profile ${GENESIS_PROFILE} -outputBlock ./channel-artifacts/${CHANNEL_NAME}/genesis.block
  if [ "$?" -ne 0 ]; then
    echo "Failed to generate orderer genesis block..."
    exit 1
  fi
  echo
  echo "#################################################################"
  echo "### Generating channel configuration transaction 'channel.tx' ###"
  echo "#################################################################"
  configtxgen -profile ${CHANNEL_PROFILE} -outputCreateChannelTx ./channel-artifacts/${CHANNEL_NAME}/channel.tx -channelID $CHANNEL_NAME
  if [ "$?" -ne 0 ]; then
    echo "Failed to generate channel configuration transaction..."
    exit 1
  fi

  echo
  echo "#################################################################"
  echo "#######    Generating anchor peer update for Org1MSP   ##########"
  echo "#################################################################"
  configtxgen -profile ${CHANNEL_PROFILE} -outputAnchorPeersUpdate ./channel-artifacts/${CHANNEL_NAME}/Org1MSPanchors.tx -channelID $CHANNEL_NAME -asOrg Org1MSP
  if [ "$?" -ne 0 ]; then
    echo "Failed to generate anchor peer update for Org1MSP..."
    exit 1
  fi

#  echo
#  echo "#################################################################"
#  echo "#######    Generating anchor peer update for Org2MSP   ##########"
#  echo "#################################################################"
#  configtxgen -profile TwoOrgsChannel -outputAnchorPeersUpdate \
#  ./channel-artifacts/Org2MSPanchors.tx -channelID $CHANNEL_NAME -asOrg Org2MSP
#  if [ "$?" -ne 0 ]; then
#    echo "Failed to generate anchor peer update for Org2MSP..."
#    exit 1
#  fi
  echo
}

CLI_TIMEOUT=10000
CHANNEL_NAME="demo-channel"
CHANNEL_PROFILE="OneOrgChannel"
GENESIS_PROFILE="OneOrgOrdererGenesis"

# Parse commandline args
while [[ $# -gt 1 ]]
do
  key="$1"
  case ${key} in
    -c|--channel-name)     CHANNEL_NAME="$2"
        shift
        ;;
    -p|--channel-profile)  CHANNEL_PROFILE="$2"
        shift
        ;;
    -g|--genesis-profile)  GENESIS_PROFILE="$2"
        shift
        ;;
  esac
  shift
done

mkdir -p "channel-artifacts/${CHANNEL_NAME}"

echo "Generating artifacts for channel '${CHANNEL_NAME}', profile '${CHANNEL_PROFILE}', genesis '${GENESIS_PROFILE}'"
#askProceed



generateChannelArtifacts

