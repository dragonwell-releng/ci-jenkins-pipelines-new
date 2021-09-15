targetConfigurations = [
        "x64Linux"    : [
                "hotspot"
        ],
        "x64Windows"  : [
                "hotspot"
        ],
        "aarch64Linux": [
                "hotspot"
        ]
]

// 03:30 Wed, Fri
triggerSchedule_nightly="TZ=UTC\n30 03 * * 3,5"
// 12:05 Sun
triggerSchedule_weekly="TZ=UTC\n05 12 * * 7"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "hotspot"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : ""
]

return this
