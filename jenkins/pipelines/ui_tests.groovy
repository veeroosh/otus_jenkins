import groovy.json.JsonSlurperClassic
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

        stage("Running UI tests via docker") { // либо
            def state = sh(
                    script: "docker run --name=ui_tests --network=host --en-file ./config/.env -t localhost:5005/ui_tests:1.0.0",
                    returnStatus: true
            )
            if (state > 0) {
                currentBuild.result = 'UNSTABLE'
            }
        }

        stage('Running UI tests via ansible') { // либо
            def state = sh(
                    script: "ansible-playbook -i ./playbook/hosts ./playbook/playbook.yaml --extra-vars host=dev browser=${yamlConfig['browser']} --extra-vars browser_version=${yamlConfig['browser_version']} --tags ui_test",
                    returnStatus: true
            )
            if (state > 0) {
                currentBuild.result = 'UNSTABLE'
            }
        }

        stage('Publish allure report') {
            allure([
                    results: [{path: 'target/allure'}],
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS'
            ])
        }

        stage('Read tests stat') {
            def jsonFileContent = readFile text: "./allure-report/widget/summary.json"
            def slurper = new JsonSlurperClassic().parseText(jsonFileContent)

            slurper.each { key, value ->
                testsStat += "$key: $value"
            }
            currentBuild.description += ' | '.join(testsStat)
        }

        stage('Notification') {
            def message = """-----------UI TESTS-----------
            brower: ${yamlConfig['browser']}"""
            testsStat.each { val ->
                message += "$val\n"
            }

            withCredentials(secret({credentialsId: 'telegram_token', 'vars': 'TOKEN'}))
            sh "curl -X POST -H 'Content-ype: application/json -d '{\"chant_id\": \"\", \"message\": \"\"}' url_for_telegram"
        }
    }
    finally { // если не через ansible
        sh "docker rm -force ui_tests"
    }
}