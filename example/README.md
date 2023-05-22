# Usage

Run `run.sh` to start example cluster. Running the cluster requires `ghcr.io` image pulling secret to exist if image `ghcr.io/teragrep/k8s_01/app:latest` is not available locally. 

Run `stop.sh` to stop the example cluster.

The container `receiver` is the destination relp server and it will print to stdout any relp messages received. You can follow the logs with the command `kubectl logs -f receiver`
