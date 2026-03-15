import groovy.transform.Field

@Field
def testsStat = []

timeout("1200") {
    node("maven") {
        withBuildUser {
            currentBuild.description = "User: ${env.BUILD_USER}"
        }

        def yamlConfig = readYaml text: "{$CONFIG}"
        sh "mkdir -p ./config"

        stage("Create env file") {
            dir("config") {
                sh "BROWSER=${yamlConfig['browser']} > ./.env"
                sh "BROWSER_VERSION=${yamlConfig['browser_version']} >> ./.env"
            }
        }

        stage('Running mobile tests via ansible') {
            def state = sh(
                    script: "ansible-playbook -i ./playbook/hosts ./playbook/playbook.yaml --tags mobile_tests",
                    returnStatus: true
            )
            if (state > 0) {
                currentBuild.result = 'UNSTABLE'
            }
        }

        stage('Publish allure report') {
            allure([
                    results          : [{ path: 'allure-results' }],
                    includeProperties: false,
                    jdk              : '',
                    properties       : [],
                    reportBuildPolicy: 'ALWAYS'
            ])
        }

        stage('Read tests stat') {
            def jsonFileContent = readJSON file: "./allure-report/widget/summary.json"
            jsonFileContent.statistic.each { k, v -> testsStat << "$k: $v" }
            currentBuild.description += ' | ' + testsStat.join(' | ')
        }

        stage('Notification') {
            def message = """-----------MOBILE TESTS-----------
            brower: ${yamlConfig['browser']}"""
            testsStat.each { val ->
                message += "$val\n"
            }

            withCredentials([string(credentialsId: 'telegram-token', variable: 'TELEGRAM_TOKEN')]) {
                sh """
                            curl -s -X POST https://api.telegram.org{TELEGRAM_TOKEN}/sendMessage \
                            -d chat_id=latysheva_jenkins \
                            -d text=${message}"""
            }
        }
    }
}