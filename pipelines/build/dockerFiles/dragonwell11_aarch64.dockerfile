ARG image
FROM ${image}

# Dragonwell 11 aarch64 explicitly selects this GCC installation in linux.sh.
# Rebuild the toolchain declared by joeylee97/dragonwell_centos7_gcc9_build_image
# instead of inheriting its opaque /bin/bash commit and GCC build intermediates.
# Keep the original configure options and installation prefix.
ARG GCC_BUILD_JOBS=4
RUN set -eux; \
    test "$(uname -m)" = aarch64; \
    build_dir="$(mktemp -d /tmp/dragonwell11-gcc.XXXXXX)"; \
    cd "${build_dir}"; \
    curl --fail --location --retry 3 \
        https://gcc.gnu.org/pub/gcc/releases/gcc-9.3.0/gcc-9.3.0.tar.gz \
        --output gcc-9.3.0.tar.gz; \
    echo '3e9b865354a4306b33b1dd90b58c230120d668b3e26bd4369967e84ff00adbbfb822a0ade340fdd248dbd07bcf326b262060873339bfc5b4c3773ad1be88ab33  gcc-9.3.0.tar.gz' | sha512sum -c -; \
    tar -xzf gcc-9.3.0.tar.gz; \
    cd gcc-9.3.0; \
    sed -i 's|ftp://gcc.gnu.org/|https://gcc.gnu.org/|' contrib/download_prerequisites; \
    ./contrib/download_prerequisites --verify; \
    mkdir "${build_dir}/build"; \
    cd "${build_dir}/build"; \
    ../gcc-9.3.0/configure \
        --enable-shared \
        --enable-threads=posix \
        --enable-__cxa_atexit \
        --enable-clocale=gnu \
        --disable-multilib \
        --enable-languages=all \
        --prefix=/usr/local/gcc-9.3.0/; \
    make -j"${GCC_BUILD_JOBS}"; \
    make install; \
    test "$(/usr/local/gcc-9.3.0/bin/gcc -dumpfullversion)" = 9.3.0; \
    /usr/local/gcc-9.3.0/bin/g++ --version; \
    cd /; \
    rm -rf "${build_dir}"
