def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
        options{
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            //retry(1)
        }
        parameters{
            booleanParam(name: 'deploy', defaultValue: false, description: 'Select to deploy or not')
        }
        environment {
            appVersion = '' // this will become global, we can use across pipeline
            region = 'us-east-1'
            account_id = '315069654700'
            project = configMap.get("project")
            environment = 'dev'
            component = configMap.get("component")
        }

        stages {
            stage('Read the version') {
                steps {
                    script{
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "App version: ${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    sh 'pip3.11 install -r requirements.txt'
                }
            }
            /* stage('SonarQube analysis') {
                environment {
                    SCANNER_HOME = tool 'sonar-6.0' //scanner config
                }
                steps {
                    // sonar server injection
                    withSonarQubeEnv('sonar-6.0') {
                        sh '$SCANNER_HOME/bin/sonar-scanner'
                        //generic scanner, it automatically understands the language and provide scan results
                    }
                }
            }

            stage('Quality Gate') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            } */
            stage('Docker build') {
                
                steps {
                    withAWS(region: 'us-east-1', credentials: "aws-creds-${environment}") {
                        sh """
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.us-east-1.amazonaws.com

                        docker build -t ${account_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${environment}/${component}:${appVersion} .

                        docker images

                        docker push ${account_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${environment}/${component}:${appVersion}
                        """
                    }
                }
            }
            stage('Deploy'){
                when{
                    expression {params.deploy}
                }

                steps{
                    build job: "../${component}-cd", parameters: [
                        string(name: 'version', value: "$appVersion"),
                        string(name: 'ENVIRONMENT', value: "dev"),
                    ], wait: true
                }
            }
        }

        post {
            always{
                echo "This sections runs always"
                deleteDir()
            }
            success{
                echo "This section run when pipeline success"
            }
            failure{
                echo "This section run when pipeline failure"
            }
        }
    }
}