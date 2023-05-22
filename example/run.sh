#!/usr/bin/bash
if ! command -v kustomize > /dev/null 2>&1; then
    echo "Missing kustomize, can't update combined.yaml"
    echo "Run 'kubectl apply -f combined.yaml' to run it without updating"
    exit 1;
fi;
kubectl delete -f combined.yaml
kustomize build . > combined.yaml
kubectl apply -f combined.yaml
