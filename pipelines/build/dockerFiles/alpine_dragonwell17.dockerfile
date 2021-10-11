ARG image

FROM $image

RUN \
    mkdir -p /opt/dragonwell; \
    wget https://cdn.azul.com/zulu/bin/zulu17.0.11-ea-jdk17.0.0-ea.1-linux_musl_x64.tar.gz; \
    #test $(md5sum zulu17.0.11-ea-jdk17.0.0-ea.1-linux_musl_x64.tar.gz | cut -d ' ' -f1) = "cc9626aa0f5afc70c2468505b2038ab2" || exit 1; \
    tar -xf zulu17.0.11-ea-jdk17.0.0-ea.1-linux_musl_x64.tar.gz -C /opt/dragonwell --strip-components=1

ENV JDK_BOOT_DIR="/opt/dragonwell"