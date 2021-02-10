targetConfigurations = [
        "x64Linux"    : [
                "dragonwell"
        ],
        "x64Windows"  : [
                "dragonwell"
        ],
        "aarch64Linux": [
                "dragonwell"
        ],
        "riscv64Linux": [
                "dragonwell"
        ],
        "x64AlpineLinux": [
                "dragonwell"
        ]
]

// 18:05 Tue, Thur
triggerSchedule_nightly="TZ=UTC\n05 18 * * 2,4"
// 17:05 Sat
triggerSchedule_weekly="TZ=UTC\n05 17 * * 6"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "hotspot"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : ""
]

return this
