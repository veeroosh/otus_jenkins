node('maven') {

    def WORKSPACE = pwd()
    def JOBS_DIR = "${WORKSPACE}/jobs"
    def CONFIG_FILE = "${WORKSPACE}/uploader.ini"

    stage("Checkout") {
        checkout scm
    }

    stage('Create uploader.ini') {
        withCredentials([usernamePassword(credentialsId: "uploader",
                passwordVariable: "pass", usernameVariable: "user")]) {
            sh """
        cat > $CONFIG_FILE << 'EOF'
[jenkins]
url=http://127.0.0.1/jenkins/
user=$user
password=$pass

[job_builder]
recursive=true
keep_descriptions=false
        EOF
        """
        }
    }

    stage('Upload jobs') {
        sh "jenkins-jobs --conf $CONFIG_FILE --flush-cache update $JOBS_DIR"
    }
}