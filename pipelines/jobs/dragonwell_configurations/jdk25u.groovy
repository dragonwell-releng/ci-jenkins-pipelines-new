targetConfigurations = [
        'x64Linux'    : [
                'dragonwell'
        ],
        'x64AlpineLinux' : [
                'dragonwell'
        ],
        'x64Windows'  : [
                'dragonwell'
        ],
        'aarch64Linux': [
                'dragonwell'
        ],
        'riscv64Linux': [
                'dragonwell'
        ]
]

// 23:30 Mon, Wed, Fri
//Uses releaseTrigger_21ea: triggerSchedule_nightly = 'TZ=UTC\n30 23 * * 1,3,5'
// 23:30 Sat
//Replaced by releaseTrigger_21ea: triggerSchedule_weekly = 'TZ=UTC\n30 23 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'hotspot'        : '',
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : ''
]

return this
