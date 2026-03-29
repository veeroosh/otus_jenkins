import groovy.transform.Field

@Field
def testsStat = []

timeout("1200") {
    node("maven") {

        withBuildUser {
            currentBuild.description = "User: ${env.BUILD_USER}"
        }

        def configText = "${CONFIG}"
        def yamlConfig = readYaml text: configText

        sh "mkdir -p ./config"

        stage("Create env file") {
            dir("config") {
                sh "BROWSER=${yamlConfig['browser']} > ./.env"
                sh "BROWSER_VERSION=${yamlConfig['base_url']} >> ./.env"
            }
        }

        stage('Running UI tests via ansible') {
            checkout scm
            def state = sh(
                    script: "ansible-playbook -i jenkins/playbook/hosts jenkins/playbook/tests.yaml --tags ui_test", // --extra-vars browser=${yamlConfig['browser']} --extra-vars browser_version=${yamlConfig['browser_version']}
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
            def message = """-----------UI TESTS-----------
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