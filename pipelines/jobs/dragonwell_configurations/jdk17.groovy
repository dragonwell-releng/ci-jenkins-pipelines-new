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
        ]
]

// 03:30 Wed, Fri
triggerSchedule_nightly="TZ=UTC\n30 03 * * 3,5"
// 12:05 Sun
triggerSchedule_weekly="TZ=UTC\n05 12 * * 7"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "dragonwell"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : ""
]

return this
