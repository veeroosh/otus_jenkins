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
                sh "APPIUM_SERVER_URL=${yamlConfig['appium_server_url']} > ./.env"
                sh "WIREMOCK_URL=${yamlConfig['wiremock_url']} >> ./.env"
                sh "JDBC_URL=${yamlConfig['jdbc_url']} >> ./.env"
            }
        }

        stage('Running mobile tests via ansible') {
            def state = sh(
                    script: "ansible-playbook -i ./playbook/hosts ./playbook/tests.yaml --tags mobile_tests --extra-vars appium_server_url=${yamlConfig['appium_server_url']} --extra-vars wiremock_url=${yamlConfig['wiremock_url']} --extra-vars jdbc_url=${yamlConfig['jdbc_url']}",
                    returnStatus: true
            )
            if (state > 0) {
                currentBuild.result = 'UNSTABLE'
            }
        }

        stage('Publish allure report') {
            allure(
                    jdk: '',
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'allure-results']],
                    includeProperties: false,
                    properties: [],
                    commandline: '2.38.1'
            )
        }

        stage('Read tests stat') {
            def jsonFileContent = readJSON file: "./allure-report/widget/summary.json"
            jsonFileContent.statistic.each { k, v -> testsStat << "$k: $v" }
            currentBuild.description += ' | ' + testsStat.join(' | ')
        }

//        stage('Notification') {
//            def message = """-----------UI TESTS-----------
//            brower: ${yamlConfig['browser']}"""
//            testsStat.each { val ->
//                message += "$val\n"
//            }
//
//            withCredentials([string(credentialsId: 'telegram-token', variable: 'TELEGRAM_TOKEN')]) {
//                sh """
//                            curl -s -X POST https://api.telegram.org{TELEGRAM_TOKEN}/sendMessage \
//                            -d chat_id=latysheva_jenkins \
//                            -d text=${message}"""
//            }
//        }
    }
}