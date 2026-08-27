# 🧪 Lab: Microsserviços de Gestão de Pedidos (orderslab)
**(Spring Boot + Java 21 + Maven + Kafka + Airflow + React + Keycloak + PostgreSQL + Kubernetes)**

Laboratório prático de arquitetura de microsserviços voltado ao estudo de comunicação síncrona (REST) e assíncrona (Kafka), orquestração de containers (Kubernetes), pipelines (Airflow) e segurança corporativa com Identity Provider (Keycloak).

---

## 🏗️ 1. Arquitetura do Sistema e Domínio

O domínio do laboratório é a **Gestão de Pedidos**, estruturado em um modelo de **Monorepo** onde cada microsserviço é um projeto Spring Boot independente, subindo em portas e pods isolados.

```text
 [ React (Frontend) ] ---> [ Gateway / Keycloak (Auth) ]
                                    |
            +-----------------------+-----------------------+
            | (HTTP / REST)                                 |
            v                                               v
    [ 1. Serviço Pedido (order-api) ] ---> (Kafka / Opcional) ---> [ 2. Pagamento (payment-api) ]
            |                                               |
            +-------------> [ 3. Nota Fiscal (invoice-api)] <------------+

```

### 📋 Mapeamento de Portas e Infraestrutura Local (On-Premisse)

| Componente | Tecnologia | Porta Local | Porta no Pod (K8s) |
| --- | --- | --- | --- |
| **Serviço Pedido (order-api)** | Spring Boot / Java 17 | `8081` | `8080` |
| **Serviço Pagamento (payment-api)** | Spring Boot / Java 17 | `8082` | `8080` |
| **Serviço Nota Fiscal (invoice-api)** | Spring Boot / Java 17 | `8083` | `8080` |
| **Keycloak (IAM)** | Keycloak | `8080` | `8080` |
| **Apache Kafka** | Confluent / Zookeeper | `9092` | `9092` |
| **Frontend** | React (Vite) | `3000` | `80` |

---

## 📂 2. Estrutura do Repositório (Monorepo)

```text
orderslab/
├── infra/
│   ├── k8s/                      <-- Manifestos do Kubernetes (Deployments e Services)
│   └── docker-compose.yml        <-- Infraestrutura base (Kafka, Keycloak, PostgreSQL)
├── order-api/                    <-- Microsserviço 1: Domínio de Pedidos (Spring Boot / Maven / Dockerfile)
├── payment-api/                  <-- Microsserviço 2: Domínio de Pagamentos (Spring Boot / Maven / Dockerfile)
├── invoice-api/                  <-- Microsserviço 3: Emissão de Notas Fiscais (Spring Boot / Maven / Dockerfile)
├── frontend-react/               <-- Aplicação Frontend em React (Vite)
├── airflow/                      <-- DAGs e pipelines do Apache Airflow
└── README.md

```

---

## 📌 3. Regras e Premissas do Laboratório

1. **Independência dos Serviços:** Os microsserviços são desenvolvidos em projetos Spring Boot separados (`order-api`, `payment-api`, `invoice-api`), garantindo ciclos de vida, dependências Maven e escalabilidade totalmente isoladas.
2. **Simulação de Domínio (Sem Regras Complexas):**
* As funcionalidades de pagamento e emissão de nota fiscal **não** possuem lógica de negócio real; o sistema apenas processa e transaciona mensagens informando se o pagamento foi realizado ou não e se a nota foi emitida ou não.


3. **Ausência de Banco de Nos Serviços (Fase Inicial):** Os microsserviços de negócio não utilizam banco de dados em um primeiro momento (o estado é mantido em memória, ex: `ConcurrentHashMap`), focando estritamente na análise e validação da comunicação entre os serviços.
4. **Prontidão para Nuvem (Cloud-Native):** Desenvolvidos on-premisse mas arquitetados para migração futura à nuvem, contando com conteinerização via Docker (`Dockerfile` multi-stage) e orquestração via Kubernetes (`Pods` e `Deployments` dedicados).
5. **Segurança e IAM:** Camada final de autenticação e autorização centralizada utilizando o **Keycloak** integrado aos microsserviços e ao frontend.

---

## 🚀 4. Como Executar o Laboratório

### Pré-requisitos

* **Docker** e **Docker Compose** instalados.
* **Java 21+** e **Maven**.
* **Node.js** (para o frontend).

### Passo 1: Subir a Infraestrutura Base

Acesse a pasta de infraestrutura e suba os containers essenciais (Kafka, Zookeeper, PostgreSQL e Keycloak):

```bash
cd infra
docker compose up -d

```

### Passo 2: Executar os Microsserviços

Cada microsserviço reside em sua própria pasta. Para rodar o **Serviço de Pedidos**, por exemplo:

```bash
cd ../servico-pedido
mvn spring-boot:run

```

*(Repita o processo nas pastas `servico-pagamento` e `servico-nota-fiscal` em seus respectivos terminais, ajustando as portas conforme mapeado).*

---

## 🗺️ 5. Fases de Evolução do Laboratório

* **Fase 1 (Atual):** Implementação da estrutura base, comunicação síncrona via HTTP/REST, sem persistência em banco de dados (estado em memória) e conteinerização via Docker.
* **Fase 2:** Evolução da comunicação síncrona para mensageria assíncrona utilizando **Apache Kafka**.
* **Fase 3:** Orquestração de fluxos e rotinas com **Apache Airflow**.
* **Fase 4:** Implantação e validação local utilizando **Kubernetes** (Minikube/Kind), garantindo pods e serviços isolados.
* **Fase 5:** Implementação da segurança de ponta a ponta com **Keycloak (IAM)** protegendo rotas e APIs.

---

## 📝 Licença

Este projeto é de uso livre para estudos, testes e laboratórios de arquitetura de microsserviços.

```

```
