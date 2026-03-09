import groovy.transform.Field

@Field
def JOBS_DIR = "${WORKSPACE}/jobs"

@Field
def CONFIG_FILE = "${WORKSPACE}/uploader.ini"


node('maven') {
    stage("Checkout") {
        scm checkout
    }

    stage("Create ini conf") {
        withCredentials([usernamePassword(credentialsId: "jenkins",
                passwordVariable: "pass", usernameVariable: "user")]) {
            sh """
        cat > $CONFIG_FILE << 'EOF'
[jenkins]
url=http://127.0.0.1/jenkins/
user=$user
password=$pass

[job_builder]
recursive=True
keep_descriptions=false
        EOF
        """
        }
    }

    stage('Upload jobs') {
        sh "jenkins-jobs --conf $CONFIG_FILE -- flush-cache update $JOBS_DIR"
    }
}