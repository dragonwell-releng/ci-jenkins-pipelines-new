import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

String nodeLabel = (params.NODE_LABEL) ?: ''

node(nodeLabel) {
    timestamps {
        try {
            Integer JOB_TIMEOUT = 1
            timeout(time: JOB_TIMEOUT, unit: 'HOURS') {
                String jobName = params.UPSTREAM_JOB_NAME ? params.UPSTREAM_JOB_NAME : ''
                String jobNumber = params.UPSTREAM_JOB_NUMBER ? params.UPSTREAM_JOB_NUMBER : ''
                String upstreamDir = params.UPSTREAM_DIR ? params.UPSTREAM_DIR : ''
                String jdkFileFilter = params.JDK_FILE_FILTER ? params.JDK_FILE_FILTER : '**/*.tar.gz'

                println '[INFO] PARAMS:'
                println "UPSTREAM_JOB_NAME = ${jobName}"
                println "UPSTREAM_JOB_NUMBER = ${jobNumber}"
                println "UPSTREAM_DIR = ${upstreamDir}"
                println "JDK_FILE_FILTER = ${jdkFileFilter}"

                // Verify any previous binaries and versions have been cleaned out
                if (fileExists('OpenJDKBinary')) {
                    dir('OpenJDKBinary') {
                        deleteDir()
                    }
                }

                dir('OpenJDKBinary') {
                    // Retrieve built JDK & unzip
                    println "[INFO] Retrieving build artifact from ${jobName}/${jobNumber} matching filter ${jdkFileFilter}"
                    copyArtifacts(
                        projectName: "${jobName}",
                        selector: specific("${jobNumber}"),
                        filter: "workspace/target/${jdkFileFilter}",
                        fingerprintArtifacts: true,
                        flatten: true
                    )

                    String files = sh(
                        script: 'ls',
                        returnStdout: true,
                        returnStatus: false
                    ).trim()

                    for (file in files.split("\n")) {
                        println "[INFO] Start sign ${file}..."
                        sh "cat ~/.password | gpg --pinentry-mode loopback --passphrase-fd 0 -u 'Alibaba Dragonwell' -b ${file}"
                        println "[INFO] Verify sign file ${file}.sig"
                        def verify = sh(returnStdout: true, script: "gpg --verify ${file}.sig 2>&1").trim()
                        if (verify.contains("Good signature")) {
                            println "[INFO] Good signature"
                        } else {
                            throw new Exception("[ERROR] Bad signature for ${file}.sig")
                        }
                    }

                    println "[INFO] Archiving to artifactory..."
                    archiveArtifacts artifacts: "*.sig"
                    println "[SUCCESS] All archived! Cleaning up..."
                }
            }
        } catch (FlowInterruptedException e) {
            throw new Exception("[ERROR] Job timeout (${JOB_TIMEOUT} HOURS) has been reached. Exiting...")
        } finally {
            // Clean up and return to upstream job
            cleanWs notFailBuild: true, deleteDirs: true
        }
    }
}
