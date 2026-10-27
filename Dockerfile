FROM rockylinux/rockylinux:9
RUN --mount=type=bind,source=./rpm/,target=/rpm/ dnf --assumeyes install /rpm/*.rpm && dnf upgrade --assumeyes && dnf clean all
VOLUME /opt/teragrep/k8s_01/var
VOLUME /opt/teragrep/k8s_01/etc
WORKDIR /opt/teragrep/k8s_01
ENTRYPOINT [ "/usr/bin/java", "-jar", "lib/k8s_01.jar" ]
