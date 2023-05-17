# k8s_01

A container that will read mounted container logfiles, then enriches them using Kubernetes API server, and forwards them with RELP to wanted target server.

# Usage

## Service account

Create simple ServiceAccount.

Example:

```json
{
  "apiVersion": "v1",
  "kind": "ServiceAccount",
  "metadata": {
    "name": "kubelogreader"
  }
}
```

## Role 
Create a Role with access to 'pods' and 'namespaces' resources, with verbs 'get', 'watch' and 'list'.

Example:

```json
{
  "apiVersion": "rbac.authorization.k8s.io/v1",
  "kind": "Role",
  "metadata": {
    "namespace": "default",
    "name": "kubelogreader"
  },
  "rules": [
    {
      "apiGroups": [
        ""
      ],
      "resources": [
        "pods",
        "namespaces"
      ],
      "verbs": [
        "get",
        "watch",
        "list"
      ]
    }
  ]
}
```

## Role binding

Create Role Binding that contains the service account

Example:

```json
{
  "apiVersion": "rbac.authorization.k8s.io/v1",
  "kind": "RoleBinding",
  "metadata": {
    "name": "kubelogreader",
    "namespace": "default"
  },
  "subjects": [
    {
      "kind": "ServiceAccount",
      "name": "kubelogreader"
    }
  ],
  "roleRef": {
    "kind": "Role",
    "name": "kubelogreader",
    "apiGroup": "rbac.authorization.k8s.io"
  }
}
```

## Service

Create a service for the service account to use

Example:

```json
{
  "apiVersion": "v1",
  "kind": "Secret",
  "metadata": {
    "name": "kubelogreader",
    "annotations": {
      "kubernetes.io/service-account.name": "kubelogreader"
    }
  },
  "type": "kubernetes.io/service-account-token"
}
```

## Volume mount

Create configMapGenerator called `app-config` using kustomize

```yaml
configMapGenerator:
  - name: app-config
    files:
      - config/config.json
      - config/log4j2.xml
```

where the configs are

#### config.json
```json
{
  "kubernetes": {
    "logdir": "/var/log/containers",
    "url": "https://127.0.0.1:8443",
    "cacheExpireInterval": 300,
    "cacheMaxEntries": 4096,
    "labels": {
      "hostname": {
        "prefix": "prefix-",
        "label": "host",
        "fallback": "fallback-hostname"
      },
      "appname": {
        "prefix": "prefix-",
        "label": "app",
        "fallback": "fallback-appname"
      }
    },
    "logfiles": [
      "first-pod_default_.*",
      "second-pod_default_.*",
      "third-pod_default_third-pod-one-.*",
      "third-pod_default_third-pod-two-.*"
    ]
  },
  "relp": {
    "target": "receiver.receiver.default",
    "port": 1601,
    "connectionTimeout": 5000,
    "readTimeout": 5000,
    "writeTimeout": 5000,
    "reconnectInterval": 5000,
    "outputThreads": 5
  }
}
```

####log4j2.xml
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Configuration monitorInterval="30" status="error">
    <Appenders>
        <Console name="STDOUT">
            <PatternLayout pattern="%d{dd.MM.yyyy HH:mm:ss.SSS} [%level] [%logger] [%thread] %msg%ex%n" />
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="com.teragrep.k8s_01" level="INFO" additivity="false">
            <AppenderRef ref="STDOUT" />
        </Logger>
        <Logger name="com.teragrep.k8s_01.KubernetesCachingAPIClient" level="DEBUG" additivity="false">
            <AppenderRef ref="STDOUT" />
        </Logger>
        <Logger name="com.teragrep.rlo_12" level="INFO" additivity="false">
            <AppenderRef ref="STDOUT" />
        </Logger>
        <Logger name="com.teragrep.rlo_13" level="INFO" additivity="false">
            <AppenderRef ref="STDOUT" />
        </Logger>
        <Root level="DEBUG">
            <AppenderRef ref="STDOUT" />
        </Root>
    </Loggers>
</Configuration>
```

## k8s_01 pod

Make the pod definition use the service account we just made.

```json
{
  <snip>
  "spec": {
    "serviceAccount": "kubelogreader",
    "serviceAccountName": "kubelogreader",
    <snip>
  }
}
```

Mount the log containing volumes for runtime processing

```json
{
  "spec": {
    <snip>
    "volumes": [
      {
        "name": "app-config",
        "configMap": {
          "name": "app-config"
        }
      },
      {
        "name": "host-var-log-containers",
        "hostPath": {
          "path": "/var/log/containers",
          "type": "Directory"
        }
      },
      {
        "name": "host-var-log-pods",
        "hostPath": {
          "path": "/var/log/pods",
          "type": "Directory"
        }
      },
      {
        "name": "host-var-lib-docker-containers",
        "hostPath": {
          "path": "/var/lib/docker/containers",
          "type": "Directory"
        }
      },
      {
        "name": "host-mnt-statefiles",
        "hostPath": {
          "path": "/mnt/statefiles",
          "type": "DirectoryOrCreate"
        }
      }
    ],
    <snip>
  }
}
```

And also mount those volumes

```json
{
  "spec": {
    <snip>
    "containers": [
      <snip>
      "volumeMounts": [
          {
            "mountPath": "/config/",
            "name": "app-config"
          },
          {
            "mountPath": "/var/log/containers",
            "name": "host-var-log-containers",
            "readOnly": true
          },
          {
            "mountPath": "/var/log/pods",
            "name": "host-var-log-pods",
            "readOnly": true
          },
          {
            "mountPath": "/var/lib/docker/containers",
            "name": "host-var-lib-docker-containers",
            "readOnly": true
          },
          {
            "mountPath": "/opt/teragrep/k8s_01/var",
            "name": "host-mnt-statefiles",
            "readOnly": false
          }
        ],
        <snip>
    ]
  }
}
```

And start the k8s_01 image with for example the following arguments. It uses `KUBERNETES_SERVICE_HOST` with `KUBERNETES_SERVICE_PORT` environment variables to find out the API server, and `RELP_SERVICE_PORT_1601_TCP_ADDR` to find the port for the RELP target server.

```json
 "command": [
  "/usr/bin/bash"
],
"args": [
  "-c",
  "jq --arg host \"${KUBERNETES_SERVICE_HOST}\" --arg port \"${KUBERNETES_SERVICE_PORT}\" --arg target \"${RELP_SERVICE_PORT_1601_TCP_ADDR}\" '.relp.target=$target | .kubernetes.url=(\"https://\" + $host + \":\" + $port)' /config/..data/config.json > /opt/teragrep/k8s_01/etc/config.json; cd /opt/teragrep/k8s_01 || exit 1; java -Dlog4j2.configurationFile=file:/config/..data/log4j2.xml -jar lib/k8s_01.jar;"
]
```

# Example test cluster usage

Read example project in `/testcluster` directory for example usage
