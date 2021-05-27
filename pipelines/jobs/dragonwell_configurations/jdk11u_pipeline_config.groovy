class Config11 {
    final Map<String, Map<String, ?>> buildConfigurations = [
            x64Mac        : [
                    os                  : 'mac',
                    arch                : 'x64',
                    additionalNodeLabels: 'macos10.14',
                    test                : 'default',
                    configureArgs       : [
                            "openj9" : '--enable-dtrace=auto --with-cmake',
                            "hotspot": '--enable-dtrace=auto'
                    ]
            ],

            x64MacXL      : [
                    os                   : 'mac',
                    arch                 : 'x64',
                    additionalNodeLabels : 'macos10.14',
                    test                 : 'default',
                    additionalFileNameTag: "macosXL",
                    configureArgs        : '--with-noncompressedrefs --enable-dtrace=auto --with-cmake'
            ],

            x64Linux      : [
                    os           : 'linux',
                    arch         : 'x64',
                    dockerImage  : 'adoptopenjdk/centos6_build_image',
                    dockerFile   : [
                            openj9: 'pipelines/build/dockerFiles/cuda.dockerfile'
                    ],
                    test                : [
                            nightly: ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external', 'special.openjdk'],
                            release: ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external', 'special.openjdk']
                    ],
                    configureArgs: [
                            "openj9"    : '--enable-jitserver --enable-dtrace=auto',
                            "hotspot"   : '--enable-dtrace=auto',
                            "corretto"  : '--enable-dtrace=auto',
                            "SapMachine": '--enable-dtrace=auto',
                            "dragonwell": '--enable-dtrace=auto --enable-unlimited-crypto --with-jvm-variants=server --with-zlib=system --with-jvm-features=zgc'
                    ]
            ],

            x64Windows    : [
                    os                  : 'windows',
                    arch                : 'x64',
                    additionalNodeLabels: [
                            hotspot   : 'win2012',
                            openj9    : 'win2012&&vs2017',
                            dragonwell: 'win2012&&vs2017'
                    ],
                    buildArgs           : [
                            hotspot: '--jvm-variant client,server',
                            dragonwell : '--jdk-boot-dir  /cygdrive/c/Jenkins/jdk11/'
                    ],
                    test                : 'default'
            ],

            x64WindowsXL  : [
                    os                   : 'windows',
                    arch                 : 'x64',
                    additionalNodeLabels : 'win2012&&vs2017',
                    test                 : 'default',
                    additionalFileNameTag: "windowsXL",
                    configureArgs        : '--with-noncompressedrefs'
            ],

            x32Windows    : [
                    os                  : 'windows',
                    arch                : 'x86-32',
                    additionalNodeLabels: [
                            hotspot   : 'win2012',
                            openj9    : 'win2012&&mingw-standalone',
                            dragonwell: 'vs2017'
                    ],
                    buildArgs           : [
                            hotspot   : '--jvm-variant client,server',
                            dragonwell: '--jdk-boot-dir  /cygdrive/c/Jenkins/jdk11/'
                    ],
                    test                : 'default'
            ],

            ppc64Aix      : [
                    os                      : 'aix',
                    arch                    : 'ppc64',
                    additionalNodeLabels    : [
                            hotspot: 'xlc13&&aix710',
                            openj9 : 'xlc13&&aix715'
                    ],
                    test                    : 'default',
                    cleanWorkspaceAfterBuild: true
            ],

            s390xLinux    : [
                    os           : 'linux',
                    arch         : 's390x',
                    test         : 'default',
                    configureArgs: '--enable-dtrace=auto'
            ],

            sparcv9Solaris: [
                    os           : 'solaris',
                    arch         : 'sparcv9',
                    test         : false,
                    configureArgs: '--enable-dtrace=auto'
            ],

            ppc64leLinux  : [
                    os                  : 'linux',
                    arch                : 'ppc64le',
                    additionalNodeLabels: 'centos7',
                    test                : 'default',
                    configureArgs       : [
                            "hotspot": '--enable-dtrace=auto',
                            "openj9" : '--enable-dtrace=auto --enable-jitserver'
                    ]

            ],

            arm32Linux    : [
                    os           : 'linux',
                    arch         : 'arm',
                    test         : 'default',
                    configureArgs: '--enable-dtrace=auto'
            ],

            aarch64Linux  : [
                    os           : 'linux',
                    arch         : 'aarch64',
                    dockerImage  : 'joeylee97/dragonwell_centos7_gcc9_build_image',
                    test                 : [
                            // TODO: enable tests
                            nightly: ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external', 'special.openjdk'],
                            release: ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external', 'special.openjdk']
                    ],
                    configureArgs: [
                            "hotspot"   : '--enable-dtrace=auto',
                            "openj9"    : '--enable-dtrace=auto',
                            "corretto"  : '--enable-dtrace=auto',
                            "dragonwell": "--enable-dtrace=auto --with-extra-cflags=\"-march=armv8.2-a+crypto\" --with-extra-cxxflags=\"-march=armv8.2-a+crypto\""
                    ]
            ],

            x64LinuxXL    : [
                    os                   : 'linux',
                    dockerImage          : 'adoptopenjdk/centos6_build_image',
                    dockerFile           : [
                            openj9: 'pipelines/build/dockerFiles/cuda.dockerfile'
                    ],
                    arch                 : 'x64',
                    test                 : "default",
                    additionalFileNameTag: "linuxXL",
                    configureArgs        : '--with-noncompressedrefs --enable-jitserver --enable-dtrace=auto'
            ],
            s390xLinuxXL  : [
                    os                   : 'linux',
                    arch                 : 's390x',
                    test                 : 'default',
                    additionalFileNameTag: "linuxXL",
                    configureArgs        : '--with-noncompressedrefs --enable-dtrace=auto'
            ],
            ppc64leLinuxXL: [
                    os                   : 'linux',
                    arch                 : 'ppc64le',
                    additionalNodeLabels : 'centos7',
                    test                 : 'default',
                    additionalFileNameTag: "linuxXL",
                    configureArgs        : '--with-noncompressedrefs --enable-dtrace=auto --enable-jitserver'
            ],
            aarch64LinuxXL: [
                    os                   : 'linux',
                    dockerImage          : 'adoptopenjdk/centos7_build_image',
                    arch                 : 'aarch64',
                    test                 : 'default',
                    additionalFileNameTag: "linuxXL",
                    configureArgs        : '--with-noncompressedrefs --enable-dtrace=auto'
            ],
            riscv64Linux  : [
                    os           : 'linux',
                    dockerImage  : 'adoptopenjdk/centos6_build_image',
                    arch         : 'riscv64',
                    crossCompile : 'x64',
                    buildArgs    : '--cross-compile',
                    configureArgs: '--disable-ddr --openjdk-target=riscv64-unknown-linux-gnu --with-sysroot=/opt/fedora28_riscv_root'
            ],
            x64AlpineLinux: [
                    os         : 'alpine-linux',
                    arch       : 'x64',
                    dockerImage: 'adoptopenjdk/alpine3_build_image',
                    dockerFile : [
                            dragonwell: 'pipelines/build/dockerFiles/alpine_dragonwell.dockerfile'
                    ],
                    test       : [
                            // TODO: enable tests
                            nightly: [],
                            // release: ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external', 'special.functional']
                            release: []
                    ]
            ],
    ]

}

Config11 config = new Config11()
return config.buildConfigurations
