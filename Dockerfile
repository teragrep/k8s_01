FROM rockylinux:8
COPY rpm/target/rpm/com.teragrep-k8s_01/RPMS/noarch/com.teragrep-k8s_01-*.rpm /rpm/
RUN dnf -y install jq /rpm/*.rpm && dnf clean all
VOLUME /opt/teragrep/k8s_01/var
VOLUME /opt/teragrep/k8s_01/etc
WORKDIR /opt/teragrep/k8s_01
ENTRYPOINT [ "/usr/bin/java", "-jar", "lib/k8s_01.jar" ]
