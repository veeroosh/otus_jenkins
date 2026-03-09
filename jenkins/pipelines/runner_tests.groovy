timeout(300) {
    node("maven") {
        def yamlConfig = readYaml

        def testsTypes = yamlConfig['TESTS_TYPES']

        def jobs = [:]

        testsTypes.each { type ->
            jobs[type] = {
                node('maven') {
                    stage('Running $type tests') {
                        return build(job: "$type", propagate: false, wait: true)
                    }
                }
            }
        }

        def results = parallel jobs

        stage('Allure report') {
            sh "mkdir -p allure-results"

            results.each { type, job ->
                def jobName = job.getProjectName()
                def jobNumber = job.getNumber()

                copyArtifacts(
                        projectName: jobName,
                        target: "allure-results",
                        selector: specific("$jobNumber"),
                        filter: "allure-results/*"
                )
            }
        }
    }
}