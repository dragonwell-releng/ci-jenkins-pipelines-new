ARG image
FROM ${image}

# Upstream supplies GCC 9.3.0 under gcc9 with a -9.3 program suffix.
# Dragonwell 11's linux.sh selects the legacy gcc-9.3.0/bin/{gcc,g++} paths.
# Keep this compatibility layer explicit; do not rebuild the upstream compiler.
RUN set -eux; \
    test "$(uname -m)" = aarch64; \
    test "$(/usr/local/gcc9/bin/gcc-9.3 -dumpfullversion)" = 9.3.0; \
    test "$(/usr/local/gcc9/bin/g++-9.3 -dumpfullversion)" = 9.3.0; \
    ln -s gcc-9.3 /usr/local/gcc9/bin/gcc; \
    ln -s g++-9.3 /usr/local/gcc9/bin/g++; \
    ln -s /usr/local/gcc9 /usr/local/gcc-9.3.0; \
    /usr/local/gcc-9.3.0/bin/gcc --version; \
    /usr/local/gcc-9.3.0/bin/g++ --version
