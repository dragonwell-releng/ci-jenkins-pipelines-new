ARG image

FROM $image

RUN \
    mkdir -p /opt/dragonwell; \
    wget https://cdn.azul.com/zulu/bin/zulu11.45.27-ca-jdk11.0.10-linux_musl_x64.tar.gz; \
    test $(md5sum zulu11.45.27-ca-jdk11.0.10-linux_musl_x64.tar.gz | cut -d ' ' -f1) = "f53be26033009cac7234cf9b25ff3c05" || exit 1; \
    tar -xf zulu11.45.27-ca-jdk11.0.10-linux_musl_x64.tar.gz -C /opt/dragonwell --strip-components=1

ENV JDK_BOOT_DIR="/opt/dragonwell"