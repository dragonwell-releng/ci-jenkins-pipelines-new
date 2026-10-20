ARG image

FROM $image

# Keep Dragonwell-specific dependencies here instead of in an opaque base image.
# serverless-adapter uses realpath, which CentOS 6 coreutils does not provide.
# Preserve the implementation supplied by the former release_centos6_build_image.
# Dependency versions audited against its 2023-07-12 customization layer:
# sha256:75988229bf6e3fa2aa21a99a23c6c7a714a149839598d0f90c98fa771e9fc716
# Package post-install scripts regenerate ldconfig/TeX caches. Do not copy the
# old image's RPM database, yum caches/logs, or interactive shell history.
RUN set -eux; \
    yum install -y \
        TeXmacs-1.0.7.2-3.el6.x86_64 \
        Xaw3d-1.5E-15.1.el6.x86_64 \
        bc-1.06.95-1.el6.x86_64 \
        desktop-file-utils-0.15-9.el6.x86_64 \
        fig2ps-1.4.1-2.el6.noarch \
        ghostscript-8.70-24.el6_10.2.x86_64 \
        ghostscript-fonts-5.50-23.2.el6.noarch \
        guile-1.8.7-5.el6.x86_64 \
        gv-3.7.1-1.el6.x86_64 \
        kpathsea-2007-60.el6_7.x86_64 \
        lcms-libs-1.19-1.el6.x86_64 \
        libtool-ltdl-2.2.6-15.5.el6.x86_64 \
        netpbm-10.47.05-11.el6.x86_64 \
        netpbm-progs-10.47.05-11.el6.x86_64 \
        openjpeg-libs-1.3-16.el6_8.x86_64 \
        poppler-0.12.4-12.el6_10.1.x86_64 \
        poppler-data-0.4.0-1.el6.noarch \
        psutils-1.17-34.el6.x86_64 \
        tex-preview-11.85-10.el6.noarch \
        texlive-2007-60.el6_7.x86_64 \
        texlive-dvips-2007-60.el6_7.x86_64 \
        texlive-latex-2007-60.el6_7.x86_64 \
        texlive-texmf-2007-39.el6_7.noarch \
        texlive-texmf-dvips-2007-39.el6_7.noarch \
        texlive-texmf-errata-2007-7.1.el6.noarch \
        texlive-texmf-errata-dvips-2007-7.1.el6.noarch \
        texlive-texmf-errata-fonts-2007-7.1.el6.noarch \
        texlive-texmf-errata-latex-2007-7.1.el6.noarch \
        texlive-texmf-fonts-2007-39.el6_7.noarch \
        texlive-texmf-latex-2007-39.el6_7.noarch \
        texlive-utils-2007-60.el6_7.x86_64 \
        transfig-3.2.5-10.el6.x86_64 \
        urw-fonts-2.4-11.el6.noarch; \
    test -x /usr/libexec/TeXmacs/bin/realpath; \
    ln -s /usr/libexec/TeXmacs/bin/realpath /usr/bin/realpath; \
    test "$(realpath /tmp/..)" = /; \
    yum clean all

RUN \
    # Dragonwell 8 requires a Dragonwell 8 BootJDK
    mkdir -p /opt/dragonwell; \
    wget https://github.com/alibaba/dragonwell8/releases/download/dragonwell-8.4.4_jdk8u262-ga/Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz; \
    test $(sha256sum Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz | cut -d ' ' -f1) = "d54eaeb5362dfe8a94b3c9b464c99ca04c1a80aa1ad39539b44e08e4858671f6" || exit 1; \
    tar -xf Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz -C /opt/; \
    mv /opt/jdk8u262-b10 /opt/dragonwell8

ENV JDK7_BOOT_DIR="/opt/dragonwell8"
