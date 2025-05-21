//자주 사용되는 필요한 변수를 전역으로 선언하는 것도 가능
def ecrLoginHelper = "docker-credential-ecr-login"

// 젠킨스의 선언형 파이프라인 정의부 시작(Groovy 언어)
pipeline{
    agent any // 어느 젠킨스 서버에서나 실행이 가능
    environment{
        //환경변수 선언 하는 곳.
                SERVICE_DIRS = "config-service,discovery-service,gateway-service,user-service,ordering-service,product-service"
                ECR_URL = "940791490007.dkr.ecr.ap-northeast-2.amazonaws.com/say4team-image"
                REGION = "ap-northeast-2"
    }
    stages{
        // 각 작업당위를 스테이지로 나누어서 작성가능.
        stage('Pull Codes from Github'){ // 스테이지 제목 (맘대로 써도됌)
            steps{
                checkout scm //젠킨스와 연결된 소스컨트롤 매니저 (git 등)에서 코드를 가져오는 명령어
            }
        }
        stage('Detect Changes'){
            steps{
                script{
                    // rev-list : 특정 브랜치나 커밋을 기준으로 모든 이전 커밋 목록을 나열
                    // --count : 목록 출력 말고 커밋 개수만 숫자로 변환
                    def commitCount = sh(script "git rev-list --count HEAD", returnStdout:  true)
                                        .trim()
                                        .toInteger()
                    def changedServices =[]
                    def serviceDirs = env.SERVICE_DIRS.split(",")

                    if(commitCount == 1){
                        // 최초 커밋이라면 모든 서비스를 빌드 해라
                        echo "Intial commit detected, All service will be built"
                        changedServices = serviceDirs // 변경된 서비스는 모든 서비스이다.

                    } else{
                        // 변경된 파일 감지
                        def changedFiles = sh(script: "git diff --name-only  HEAD~1 HEAD", returnStdout: true)
                                            .trim()
                                            .split('\n') //변경된 파일을 줄 단위로 분리

                        // 변경된 파일 출력
                        // [user-service/src/main/resources/application.yml,
                        // user-service/src/main/java/com/playdata/userservice/controller/UserController.java,
                        // ordering-service/src/main/resources/application.yml]
                        echo "Changed files: ${changedFiles}"

                        def changedServices =[]
                        def serviceDirs = env.SERVICE_DIRS.split(",")

                        serviceDirs.each{ service ->
                            //changedFiles라는 리스트를 조회해서 service 변수에 들어온 서비스이름과
                            //하나라도 일치하는 이름이 있다면 true, 하나라도 존재하지 않으면 false
                            // service: user-service
                            if(changedFiles.any {it.startsWith(service+"/")}){
                                changedServices.add(service)
                            }

                           //변경된 서비스 이름을 모아놓은 리스트를 다른 스테이지에서도 사용하기위 환경변수로 선언
                           // join() -> 지정한 문자열을 구분자로 하여 리스트 요소를 하나의 문자열로 리턴. 중복 제거
                           env.CHANGED_SERVICES  = changedServices.join(",")
                           if(env.CHANGED_SERVICES == ""){
                                echo "No changes detected in service directories. Skipping build and deployment"
                                // 성공상태로 파이프라인을 종료
                                currentBuild.result = 'SUCCESS'
                           }
                        }
                    }
                }
            }
        }
        stage('Build Codes by Gradle'){
            // 이 스테이지는 빌드되어야 할 서비스가 존재한다면 실행되는 스테이지
            // 이전 스테이지에서 세팅한 CHANGED_SERVICES라는 환경변수가 비어있지 않아야만 실행
            when{
                expression{env.CHANGED_SERVICES != "" }
            }
            steps{
                script{
                    def changedServices = env.SERVICE_DIRS.split(",")
                    changedServices.each{ service ->
                        sh """
                         echo "Building ${service}"
                         cd ${service}
                         ./gradlew clean build -x test
                         ls -al ./build/libs
                         cd ..
                        """
                    }
                }
            }
        }
        stage('Build Docker Image & Push to AWS ECR'){
            when{
                expression{env.CHANGED_SERVICES != "" }
            }
            steps{
                script{
                    // jenkins에 저장된 credentials를 사용하여 AWS 자격 증명을 설정.
                    withAWS(region:"${REGION}", credentials: "aws-key")
                    def changedServices = env.CHANGED_SERVICES.split(",")
                    changedServices.each{ service ->
                        sh """
                        # ECR에 이미지를 push하기 위해 인증정보를 대신 검증해 주는 도구 다운로드
                        # /usr/local/bin/ 경로에 해당 파일을 이동
                        curl -O https://amazon-ecr-credential-helper-releases.s3.us-east-2.amazonaws.com/0.4.0/linux-amd64/${ecrLoginHelper}
                        chmod +x ${ecrLoginHelper}
                        mv ${ecrLoginHelper} /usr/local/bin/


                        # Docker 에게 push 명령을 내리면 지정된 URL로 push 할 수 있게 설정.
                        # 자동으로 로그인 도구를 쓰게 설정
                        echo '{"credHelpers": {"${ECR_URL}": "ecr-login"}}' > ~/.docker/config.json

                        docker build -t ${service}:latest ${service}
                        docker tag ${service}:latest ${ECR_URL}/${service}:latest
                        docker push ${ECR_URL}/${service}:latest
                        """
                    }
                }
            }
        }
    }
}