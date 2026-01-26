
# DeviceLife Worker Service 🛠

**DeviceLife** 서비스의 핵심인 디바이스 평가(Evaluation)를 백그라운드에서 비동기로 처리하는 **Worker 서비스**입니다.

## 📌 1. 프로젝트 개요

사용자가 디바이스 평가를 요청했을 때, 본사(Backend) 서버의 부하를 줄이고 응답 속도를 높이기 위해 **AWS SQS**를 활용한 비동기 아키텍처를 채택했습니다.
이 워커는 큐(Queue)에 쌓인 작업을 하나씩 꺼내어 평가 알고리즘을 수행하고, 결과를 다시 본사 서버로 전송합니다.

###  전체 워크플로우

1. **Backend:** 사용자 요청 수신 → SQS에 메시지 발행 (Publish)
2. **Worker:** SQS 메시지 감지 (Polling) → `JobConsumer` 작동
3. **Worker:** Backend API 호출 (`/payload`) → 평가 데이터 획득
4. **Worker:** 알고리즘 수행 (`Evaluators`) → 점수 계산
5. **Worker:** 결과 전송 (`/result`) → Backend DB 저장

---

## 2. 핵심 기술 및 특징

### 🚀 비동기 처리 (Asynchronous Processing)

* **Spring Cloud AWS SQS 3.x**를 사용하여 메시지를 **Polling** 방식으로 수신합니다.
* 사용자에게 즉각적인 대기 시간을 주지 않고, 백그라운드에서 복잡한 연산을 처리합니다.
* 처리 실패 시 **Retry(재시도)** 및 **DLQ(Dead Letter Queue)** 정책을 통해 데이터 유실을 방지합니다.

### 🐳 Docker & Multi-stage Build (운영 최적화)

본 프로젝트는 **Docker**를 통해 배포되며, **Multi-stage Build** 전략을 사용하여 이미지를 최적화했습니다.

* **가벼운 이미지:** 빌드 단계에는 `JDK`(개발 도구)를 사용하지만, 최종 실행 이미지는 `JRE`(실행 환경)만 포함하여 이미지 크기를 획기적으로 줄였습니다. (약 500MB → 100MB 수준)
* **환경 일치:** 로컬(Mac/Windows) 개발 환경과 운영(Rocky Linux) 환경을 100% 일치시켜 "내 컴퓨터에선 되는데?" 문제를 원천 차단했습니다.
* **보안:** 소스 코드나 빌드 도구가 최종 이미지에 남지 않아 보안성이 뛰어납니다.

### 🔒 Tailscale 내부망 통신

* 워커는 **Tailscale VPN**을 통해 구축된 사설 네트워크(`100.x.x.x`) 안에서만 본사 서버와 통신합니다.
* 외부(Public Internet)에 포트를 개방하지 않아도 되어 보안성이 매우 높습니다.

---

## 📂 3. 프로젝트 구조 (Project Structure)

```
src/main/java
└─ com.devicelife.devicelife_worker
    ├── client      # Backend 서버와 통신하는 RestClient (Payload 요청, Result 전송)
    ├── config      # 설정 파일 (Security, RestClient, SQS 설정)
    ├── consumer    # SQS 메시지를 수신하는 리스너 (Main Entry Point)
    ├── dto         # 데이터 전송 객체 (Message, Payload, Result)
    └── service     # 핵심 비즈니스 로직 (평가 알고리즘 구현체)

```

### 주요 컴포넌트 역할

* **JobConsumer (`consumer`):** SQS 메시지를 항시 대기하며, 메시지 수신 시 전체 로직을 조율합니다.
* **BackendClient (`client`):** 본사 서버의 내부 API(`/internal/**`)를 호출합니다. 보안 토큰을 헤더에 실어 보냅니다.
* **EvaluationService (`service`):** 실제 기기 스펙과 사용자 취향을 매칭하여 점수를 계산하는 알고리즘이 위치합니다.

---

## 4. 실행 방법 (Getting Started)

이 프로젝트는 **Docker Compose**를 통해 원클릭으로 실행되도록 설계되었습니다.

### 1) 환경 변수 설정 (.env)


### 2) 실행 (Docker Compose)

코드 변경 사항을 반영하여 빌드하고 실행합니다.

```bash
# 최신 코드 가져오기
git pull origin main

# 빌드 및 백그라운드 실행
sudo docker compose up -d --build

```

### 3) 로그 확인

워커가 정상적으로 SQS를 바라보고 있는지 확인합니다.

```bash
sudo docker logs -f worker-app

```

---

## 5. 개발자 가이드

* **Stateless:** 워커는 DB에 직접 접속하지 않습니다. 모든 데이터는 API를 통해 주고받습니다.
* **확장성 (Scalability):** 작업량이 많아질 경우, Docker 컨테이너 개수만 늘리면(`docker compose up --scale worker=3`) 병렬 처리가 가능합니다.
그대로 복사해서 프로젝트 최상단 `README.md` 파일에 붙여넣으세요!

---

# DeviceLife Worker Service 🛠️

**DeviceLife** 서비스의 핵심인 **디바이스 평가(Evaluation)**를 백그라운드에서 비동기로 처리하는 **Worker 서비스**입니다.

## 📌 1. 프로젝트 개요

사용자가 디바이스 평가를 요청했을 때, 본사(Backend) 서버의 부하를 줄이고 응답 속도를 높이기 위해 **AWS SQS**를 활용한 비동기 아키텍처를 채택했습니다.
이 워커는 큐(Queue)에 쌓인 작업을 하나씩 꺼내어 평가 알고리즘을 수행하고, 결과를 다시 본사 서버로 전송합니다.

### 🔄 전체 워크플로우

1. **Backend:** 사용자 요청 수신 → SQS에 메시지 발행 (Publish)
2. **Worker:** SQS 메시지 감지 (Polling) → `JobConsumer` 작동
3. **Worker:** Backend API 호출 (`/payload`) → 평가 데이터 획득
4. **Worker:** 알고리즘 수행 (`Evaluators`) → 점수 계산
5. **Worker:** 결과 전송 (`/result`) → Backend DB 저장

---

## ⚡ 2. 핵심 기술 및 특징

### 🚀 비동기 처리 (Asynchronous Processing)

* **Spring Cloud AWS SQS 3.x**를 사용하여 메시지를 **Polling** 방식으로 수신합니다.
* 사용자에게 즉각적인 대기 시간을 주지 않고, 백그라운드에서 복잡한 연산을 처리합니다.
* 처리 실패 시 **Retry(재시도)** 및 **DLQ(Dead Letter Queue)** 정책을 통해 데이터 유실을 방지합니다.

### 🐳 Docker & Multi-stage Build (운영 최적화)

본 프로젝트는 **Docker**를 통해 배포되며, **Multi-stage Build** 전략을 사용하여 이미지를 최적화했습니다.

* **가벼운 이미지:** 빌드 단계에는 `JDK`(개발 도구)를 사용하지만, 최종 실행 이미지는 `JRE`(실행 환경)만 포함하여 이미지 크기를 획기적으로 줄였습니다. (약 500MB → 100MB 수준)
* **환경 일치:** 로컬(Mac/Windows) 개발 환경과 운영(Rocky Linux) 환경을 100% 일치시켜 "내 컴퓨터에선 되는데?" 문제를 원천 차단했습니다.
* **보안:** 소스 코드나 빌드 도구가 최종 이미지에 남지 않아 보안성이 뛰어납니다.

### 🔒 Tailscale 내부망 통신

* 워커는 **Tailscale VPN**을 통해 구축된 사설 네트워크(`100.x.x.x`) 안에서만 본사 서버와 통신합니다.
* 외부(Public Internet)에 포트를 개방하지 않아도 되어 보안성이 매우 높습니다.

---

## 📂 3. 프로젝트 구조 (Project Structure)

```
src/main/java/com/devicelife/worker
├── client      # 📡 Backend 서버와 통신하는 RestClient (Payload 요청, Result 전송)
├── config      # ⚙️ 설정 파일 (Security, RestClient, SQS 설정)
├── consumer    # 📬 SQS 메시지를 수신하는 리스너 (Main Entry Point)
├── dto         # 📦 데이터 전송 객체 (Message, Payload, Result)
└── service     # 🧠 핵심 비즈니스 로직 (평가 알고리즘 구현체)

```

### 주요 컴포넌트 역할

* **JobConsumer (`consumer`):** SQS 메시지를 항시 대기하며, 메시지 수신 시 전체 로직을 조율합니다.
* **BackendClient (`client`):** 본사 서버의 내부 API(`/internal/**`)를 호출합니다. 보안 토큰을 헤더에 실어 보냅니다.
* **EvaluationService (`service`):** 실제 기기 스펙과 사용자 취향을 매칭하여 점수를 계산하는 알고리즘이 위치합니다.

---

## 🛠️ 4. 실행 방법 (Getting Started)

이 프로젝트는 **Docker Compose**를 통해 원클릭으로 실행되도록 설계되었습니다.

### 1) 환경 변수 설정 (.env)

### 2) 실행 (Docker Compose)

코드 변경 사항을 반영하여 빌드하고 실행합니다.

```bash
# 최신 코드 가져오기
git pull origin main

# 빌드 및 백그라운드 실행
sudo docker compose up -d --build

```

### 3) 로그 확인

워커가 정상적으로 SQS를 바라보고 있는지 확인합니다.

```bash
sudo docker logs -f worker-app

```

---

## 📝 5. 개발자 가이드

* **상태 없음 (Stateless):** 워커는 DB에 직접 접속하지 않습니다. 모든 데이터는 API를 통해 주고받습니다.
* **확장성 (Scalability):** 작업량이 많아질 경우, Docker 컨테이너 개수만 늘리면(`docker compose up --scale worker=3`) 병렬 처리가 가능합니다.