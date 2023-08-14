#!/bin/bash
openssl req -nodes -new -x509 -keyout ca.key -out ca.crt -batch -subj "/C=FI/CN=k8s_01";
for component in k8s_01 rlp_07; do
    keytool -genkey -storetype PKCS12 -dname "CN=k8s_01" -alias "k8s_01" -keyalg RSA -keystore "${component}-keystore.jks" -keysize 2048 -storepass "mypassword";
    keytool -noprompt -keystore "${component}-keystore.jks" -alias "k8s_01" -certreq -file "${component}-req" -keypass "mypassword" -storepass "mypassword";
    openssl x509 -req -CA "ca.crt" -CAkey "ca.key" -in "${component}-req" -out "${component}-signed" -days 99999 -CAcreateserial;
    keytool -noprompt -keystore "${component}-keystore.jks" -alias CARoot -import -trustcacerts -file "ca.crt" -keypass "mypassword" -storepass "mypassword";
    keytool -noprompt -keystore "${component}-keystore.jks" -alias "k8s_01" -import -file "${component}-signed" -keypass "mypassword" -storepass "mypassword";
    rm -f "${component}-req" "${component}-signed"
done;
