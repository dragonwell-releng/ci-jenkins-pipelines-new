ARG image

FROM $image

RUN \
    # Dragonewell 8 requires a dragonwell 8 BootJDK
    mkdir -p /opt/dragonwell8; \
    wget  -q https://dragonwell.oss-cn-shanghai.aliyuncs.com/8/8.5.5-FP1/Alibaba_Dragonwell_8.5.5-FP1_Linux_aarch64.tar.gz; \
    tar -xf Alibaba_Dragonwell_8.5.5-FP1_Linux_aarch64.tar.gz -C /opt/dragonwell8 --strip-components=1


ENV JDK7_BOOT_DIR="/opt/dragonwell8"
