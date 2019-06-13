#!/bin/bash

CLUSTER=$1
TAG=$2

doctl --access-token=${API_KEY} auth init
doctl kubernetes cluster kubeconfig save ${CLUSTER}
kubectl config use-context do-fra1-${CLUSTER}
kubectl --namespace=${NAMESPACE} set image deployment/${DEPLOYMENT} ${CONTAINER}=${IMAGE}:${TAG}

# Cleanup local config
rm -rf ${HOME}/.kube/
rm -rf ${HOME}/.config/doctl/
