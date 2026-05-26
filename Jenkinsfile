pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'truongdocker1'
        DOCKER_CREDENTIALS_ID = 'dockerhub-creds'
        IMAGE_NAME = 'bookstore-cart-service'
        TAG = "${BUILD_NUMBER}"

        K8S_DEPLOYMENT = 'cart-service-deployment'
        K8S_CONTAINER = 'cart-service'
    }

    tools {
        maven 'Maven 3.9'
        jdk 'JDK 21'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    dockerImage = docker.build(
                        "${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}",
                        "."
                    )
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry(
                        'https://index.docker.io/v1/',
                        "${DOCKER_CREDENTIALS_ID}"
                    ) {
                        dockerImage.push()
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'db-creds', usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD'),
                    string(credentialsId: 'redis-password', variable: 'SPRING_DATA_REDIS_PASSWORD')
                ]) {
                    sh '''
                export KUBECONFIG=/var/jenkins_home/.kube/config

                sed -i "s|image: .*${IMAGE_NAME}:.*|image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}|g" k8s/deployment.yaml

                kubectl apply -f k8s/configmap.yaml

                kubectl create secret generic cart-service-secret \
                  --from-literal=DB_USERNAME="$DB_USERNAME" \
                  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
                  --from-literal=SPRING_DATA_REDIS_PASSWORD="$SPRING_DATA_REDIS_PASSWORD" \
                  --dry-run=client -o yaml | kubectl apply -f -

                kubectl apply -f k8s/deployment.yaml
                kubectl apply -f k8s/service.yaml

                kubectl rollout status deployment/${K8S_DEPLOYMENT} --timeout=180s
                '''
                }
            }
        }
    }

    post {
        success {
            echo "Build & Deploy SUCCESS"
        }
        failure {
            echo "Build FAILED"
        }
    }
}
