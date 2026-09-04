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
| **Serviço Pedido (order-api)** | Spring Boot / Java 21 | `8081` | `8080` |
| **Serviço Pagamento (payment-api)** | Spring Boot / Java 21 | `8082` | `8080` |
| **Serviço Nota Fiscal (invoice-api)** | Spring Boot / Java 21 | `8083` | `8080` |
| **Keycloak (IAM)** | Keycloak | `8080` | `8080` |
| **Apache Kafka** | Apache Kafka (modo KRaft, sem Zookeeper) | `9092` | `9092` / `29092` |
| **Kafbat UI** | UI de visualização/administração do Kafka | `8090` | `8090` |
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
├── frontend-react/               <-- (ainda não criado) Aplicação Frontend em React (Vite)
├── airflow/                      <-- (ainda não criado) DAGs e pipelines do Apache Airflow
└── README.md

```

---

## 📌 3. Regras e Premissas do Laboratório

1. **Independência dos Serviços:** Os microsserviços são desenvolvidos em projetos Spring Boot separados (`order-api`, `payment-api`, `invoice-api`), garantindo ciclos de vida, dependências Maven e escalabilidade totalmente isoladas.
2. **Simulação de Domínio (Sem Regras Complexas):**
* As funcionalidades de pagamento e emissão de nota fiscal **não** possuem lógica de negócio real; o sistema apenas processa e transaciona mensagens informando se o pagamento foi realizado ou não e se a nota foi emitida ou não.


3. **Ausência de Banco de Dados nos Serviços (Fase Inicial):** Os microsserviços de negócio não utilizam banco de dados em um primeiro momento (o estado é mantido em memória, ex: `ConcurrentHashMap`), focando estritamente na análise e validação da comunicação entre os serviços.
4. **Prontidão para Nuvem (Cloud-Native):** Desenvolvidos on-premisse mas arquitetados para migração futura à nuvem, contando com conteinerização via Docker (`Dockerfile` multi-stage) e orquestração via Kubernetes (`Pods` e `Deployments` dedicados).
5. **Segurança e IAM:** Camada final de autenticação e autorização centralizada utilizando o **Keycloak** integrado aos microsserviços e ao frontend.

---

## 🚀 4. Como Executar o Laboratório

### Pré-requisitos

* **Docker** e **Docker Compose** instalados.
* **Java 21+** e **Maven** — necessários apenas se for rodar algum microsserviço fora do Docker (Opção B).
* **Node.js** (para o frontend).

### Opção A: Tudo via Docker Compose (recomendado)

Builda as imagens e sobe banco de dados + microsserviços já containerizados — não precisa de Java nem Maven instalados, o build acontece dentro do container (`Dockerfile` multi-stage):

```bash
cd infra
docker compose up -d --build
```

Nesta fase, isso sobe:

| Serviço | Descrição | Porta |
| --- | --- | --- |
| `postgres-api` | Banco de dados compartilhado pelas APIs | `5432` |
| `order-api` | Microsserviço de Pedidos | `8081` |
| `payment-api` | Microsserviço de Pagamentos | `8082` |
| `invoice-api` | Microsserviço de Notas Fiscais | `8083` |
| `kafka` | Broker Kafka (modo KRaft) — `9092` para acesso externo/local, `29092` para os demais containers | `9092` |
| `kafbat-ui` | UI web para inspecionar tópicos/mensagens do Kafka | `8090` |

Verifique se subiu certo:

```bash
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8090/actuator/health
```

Acesse a UI do Kafka em http://localhost:8090 para inspecionar tópicos e mensagens do broker.

Para derrubar os containers:

```bash
docker compose down
```

> Keycloak ainda não existe/está desativado — o bloco correspondente está comentado em `infra/docker-compose.yml` e será reativado conforme as fases do laboratório avançam (ver seção 6). O Kafka já está ativo, mas nenhum dos microsserviços ainda produz/consome mensagens nele (ver seção 6, Fase 2).

### Opção B: Rodando um microsserviço localmente (fora do Docker)

Suba só o banco de dados:

```bash
cd infra
docker compose up -d postgres-api
```

Em outro terminal, rode o microsserviço desejado usando o Maven Wrapper do próprio projeto (ex.: **order-api**):

```bash
cd ../order-api
./mvnw spring-boot:run
```

*(Repita o processo nas pastas `payment-api` e `invoice-api`, ajustando as portas conforme mapeado).*

---

## ☸️ 5. Executando com Kubernetes

Além do Docker Compose (seção 4), o laboratório também roda em um cluster **Kubernetes local** (testado com o Kubernetes embutido do **Docker Desktop**). Os manifests ficam em [`infra/k8s/`](infra/k8s).

### 5.1 De onde vieram os manifests

Os arquivos de `infra/k8s/` nasceram a partir do `docker-compose.yml`, convertidos automaticamente com o [`kompose`](https://kompose.io):

```bash
kompose convert -f infra/docker-compose.yml -o infra/k8s/
```

O `kompose` traduz cada serviço do Compose em dois objetos do Kubernetes (ver seção 5.2) — mas essa tradução é só um ponto de partida. **Os manifests atuais já foram ajustados manualmente** e não devem ser regenerados às cegas com `kompose convert` de novo, porque isso reverteria os ajustes abaixo:

| Ajuste manual | Por quê |
| --- | --- |
| `imagePullPolicy: Never` nos deployments de `order-service`/`payment-service` | Essas imagens só existem no Docker local (Kubernetes puro não builda nada); sem isso o cluster tenta baixar do Docker Hub e falha com `ImagePullBackOff` |
| `type: LoadBalancer` nos Services | Expõe as portas direto em `localhost`, sem precisar de `kubectl port-forward` (ver seção 5.5) |
| Label `app` no lugar de `io.kompose.service` | Remove a dependência visual da ferramenta `kompose`; o mecanismo (label + selector) em si é padrão do Kubernetes |
| Nomes `order-service`/`payment-service` no lugar de `servico-pedido`/`servico-pagamento` | Padronização de nomenclatura em inglês, consistente com o resto do projeto |

### 5.2 Estrutura dos manifests: Deployment + Service

Cada serviço do Compose gerou **dois arquivos**, porque são dois objetos do Kubernetes com responsabilidades diferentes:

- **`*-deployment.yaml`** — descreve o *workload*: qual imagem rodar, quantas réplicas, variáveis de ambiente. Cria e gerencia os Pods.
- **`*-service.yaml`** — descreve o *acesso de rede*: um nome DNS interno fixo e estável (ex.: `postgres-api`, resolvido pelo `SPRING_DATASOURCE_URL` dos outros serviços) que faz *load balancing* entre os Pods, já que o IP de um Pod muda toda vez que ele reinicia.

Além desses pares, existe um terceiro tipo de manifest, à parte: [`namespace.yaml`](infra/k8s/namespace.yaml) (`kind: Namespace`), que cria o namespace `orderslab`. Todo Deployment e Service abaixo já declara `namespace: orderslab` no `metadata` — isso isola os recursos deste laboratório de qualquer outra coisa que você rode no mesmo cluster do Docker Desktop (ver seção 5.4).

| Serviço | Deployment | Service | Imagem |
| --- | --- | --- | --- |
| Postgres | [`postgres-api-deployment.yaml`](infra/k8s/postgres-api-deployment.yaml) | [`postgres-api-service.yaml`](infra/k8s/postgres-api-service.yaml) | `postgres:15-alpine` (pública) |
| Kafka | [`kafka-deployment.yaml`](infra/k8s/kafka-deployment.yaml) | [`kafka-service.yaml`](infra/k8s/kafka-service.yaml) | `apache/kafka:4.2.0` (pública) |
| Kafbat UI | [`kafbat-ui-deployment.yaml`](infra/k8s/kafbat-ui-deployment.yaml) | [`kafbat-ui-service.yaml`](infra/k8s/kafbat-ui-service.yaml) | `ghcr.io/kafbat/kafka-ui:v1.5.0` (pública) |
| Pedidos | [`order-service-deployment.yaml`](infra/k8s/order-service-deployment.yaml) | [`order-service-service.yaml`](infra/k8s/order-service-service.yaml) | `order-service` (local) |
| Pagamentos | [`payment-service-deployment.yaml`](infra/k8s/payment-service-deployment.yaml) | [`payment-service-service.yaml`](infra/k8s/payment-service-service.yaml) | `payment-service` (local) |
| Notas Fiscais | [`invoice-service-deployment.yaml`](infra/k8s/invoice-service-deployment.yaml) | [`invoice-service-service.yaml`](infra/k8s/invoice-service-service.yaml) | `invoice-service` (local) |

### 5.3 Build das imagens

Diferente do Compose, o Kubernetes **não builda nada** — ele só roda imagens que já existem. Antes de aplicar os manifests, gere as imagens locais com os mesmos nomes usados em `image:` nos deployments:

```bash
docker build -t order-service order-api
docker build -t payment-service payment-api
docker build -t invoice-service invoice-api
```

> Se alterar o código de um dos serviços, é preciso rebuildar a imagem **e** rodar `kubectl rollout restart deployment/order-service` (ou `payment-service`/`invoice-service`) — como `imagePullPolicy: Never`, o Kubernetes não detecta sozinho que a imagem local mudou.

### 5.4 Subindo a stack

O namespace precisa existir antes do resto, porque os manifests já declaram `namespace: orderslab` — e a ordem alfabética dos arquivos na pasta não garante que `namespace.yaml` seja aplicado primeiro:

```bash
kubectl apply -f infra/k8s/namespace.yaml
kubectl apply -f infra/k8s/
```

Opcional, mas recomendado: fixe `orderslab` como namespace padrão do seu contexto atual do `kubectl`, assim os comandos deste README (inclusive os da seção 5.7) funcionam sem precisar de `-n orderslab` toda hora:

```bash
kubectl config set-context --current --namespace=orderslab
```

> Isso é uma configuração do seu `kubectl` local (`~/.kube/config`), não do cluster — vale pra qualquer outro projeto que você rodar apontando pro mesmo contexto. Pra voltar ao padrão: `kubectl config set-context --current --namespace=default`.

```bash
kubectl get pods
```

É normal `order-service`/`payment-service` reiniciarem uma ou duas vezes logo no início — os manifests não têm um equivalente ao `depends_on` do Compose, então eles podem tentar conectar no Postgres antes dele estar pronto. O `restartPolicy: Always` se recupera sozinho em segundos.

### 5.5 Acessando os serviços (LoadBalancer)

Os seis Services são do tipo `LoadBalancer`. No Kubernetes do Docker Desktop, isso expõe a porta direto em `localhost` — de forma permanente, sem precisar manter nenhum comando rodando em segundo plano:

| Serviço | URL |
| --- | --- |
| `order-service` | http://localhost:8081 |
| `payment-service` | http://localhost:8082 |
| `invoice-service` | http://localhost:8083 |
| `postgres-api` | `localhost:5432` |
| `kafka` | `localhost:9092` |
| `kafbat-ui` | http://localhost:8090 |

```bash
kubectl get svc
```

Confirme que a coluna `EXTERNAL-IP` mostra `localhost` (pode levar alguns segundos após o `apply`).

### 5.6 Parando a stack

```bash
kubectl delete -f infra/k8s/
```

Remove o namespace `orderslab` inteiro — Deployments, Services e Pods somem junto, porque apagar o namespace já derruba tudo que existe dentro dele (também dá pra rodar direto `kubectl delete namespace orderslab`, com o mesmo efeito). As imagens Docker locais **não** são apagadas — só os objetos do cluster.

### 5.7 Comandos úteis

| Comando | Uso |
| --- | --- |
| `kubectl apply -f infra/k8s/` | Cria/atualiza tudo que está na pasta |
| `kubectl delete -f infra/k8s/` | Remove tudo que está na pasta |
| `kubectl config set-context --current --namespace=orderslab` | Fixa `orderslab` como namespace padrão do contexto atual |
| `kubectl get pods -n orderslab` | Lista os pods do namespace, sem depender do namespace padrão do contexto |
| `kubectl get pods -w` | Acompanha o status dos pods em tempo real |
| `kubectl get svc` | Lista os Services (IP interno, `EXTERNAL-IP`, portas) |
| `kubectl logs <pod>` | Mostra os logs de um pod específico |
| `kubectl describe pod <pod>` | Detalha eventos do pod — útil pra investigar `ImagePullBackOff`/`CrashLoopBackOff` |
| `kubectl rollout restart deployment/<nome>` | Recria os pods de um deployment pra pegar uma imagem local nova |
| `kubectl apply --dry-run=client -f infra/k8s/` | Valida a sintaxe dos manifests sem aplicar nada no cluster |
| `kubectl port-forward svc/<nome> <porta>:<porta>` | Túnel manual e temporário — só necessário se o Service voltar a ser `ClusterIP` |

---

## 🔄 6. CI/CD com GitHub Actions

Cada microsserviço tem um pipeline próprio e independente — a mesma regra de Independência dos Serviços (seção 3) vale pro CI/CD. Os workflows ficam em [`.github/workflows/`](.github/workflows).

### 6.1 Estrutura dos workflows

```text
.github/workflows/
├── service-ci.yml       <-- Workflow reusável: os jobs de verdade vivem aqui
├── order-api.yml        <-- Dispara o service-ci.yml com service-name: order-api
├── payment-api.yml      <-- Dispara o service-ci.yml com service-name: payment-api
└── invoice-api.yml      <-- Dispara o service-ci.yml com service-name: invoice-api
```

As três APIs usam exatamente o mesmo toolchain (Java 21, Maven Wrapper, `Dockerfile` multi-stage), então os jobs moram uma única vez em [`service-ci.yml`](.github/workflows/service-ci.yml) — cada `*-api.yml` só declara os gatilhos daquele serviço e chama o workflow reusável passando `service-name`. Editar um job (ex.: trocar a versão do JDK) é editar um arquivo só, não três.

### 6.2 Jobs

| Job | O que faz | Roda quando |
| --- | --- | --- |
| **`build-and-test`** | checkout → `setup-java` (Temurin 21, cache do `~/.m2`) → `./mvnw -B clean verify` → publica o relatório de testes (`target/surefire-reports/`) como artifact | sempre — push e pull_request |
| **`quality`** | checkout → JDK 21 → `./mvnw -B pmd:check` → publica `target/pmd.xml` como artifact, mesmo se o PMD falhar | sempre, **em paralelo** ao `build-and-test` — nenhum dos dois depende do outro |
| **`package`** | extrai a versão do nome da tag e roda `docker build` com o `Dockerfile` do serviço | só em push de tag (ver 6.4), e só depois que `build-and-test` **e** `quality` passarem |

> `package` builda a imagem só pra validar — não existe `docker push` nem login em registry ainda (ver 6.6).

Pra reproduzir os jobs localmente, dentro da pasta do serviço:

```bash
./mvnw -B clean verify   # o que o build-and-test roda
./mvnw -B pmd:check      # o que o quality roda
```

### 6.3 Gatilhos: quando cada workflow dispara

| Evento | Filtro | Roda pra |
| --- | --- | --- |
| `push` em qualquer branch | nenhum filtro de path | **os 3 serviços**, mesmo que só um tenha mudado |
| `push` de tag `<service>-vX.Y.Z` | o nome da tag já é por serviço | só o serviço daquela tag |
| `pull_request` pra `develop`/`main` | `paths` restrito à pasta do serviço | só o serviço que mudou |

O `pull_request` é escopado certinho por `paths`, mas o `push` não tem filtro de path nenhum — é proposital, não esquecimento. Combinar `paths` com `tags` no mesmo gatilho de `push` tem uma pegadinha do GitHub Actions: uma tag empurrada apontando pra um commit que já existe (o caso normal — tagueamento acontece depois do merge) gera um diff vazio pra aquele push específico, e o filtro de `paths` bloquearia o job silenciosamente — inclusive o `package` de release. Pra não arriscar isso, tirou-se o `paths` do `push` inteiro; o efeito colateral é que um push direto (fora de PR) em qualquer branch dispara os 3 workflows, não só o do serviço alterado. Como a maior parte do fluxo passa por PR (que continua escopado), o custo é só uns minutos a mais de CI em pushes diretos.

### 6.4 Convenção de tags e release

Cada serviço libera sua própria tag, prefixada pelo nome dele — não existe uma tag global pro monorepo inteiro:

```text
order-api-v1.0.0
payment-api-v1.0.2
invoice-api-v1.0.0
```

O prefixo por serviço existe pra não acoplar o versionamento dos três — bate com a regra de Independência dos Serviços (seção 3): uma tag global forçaria os três a compartilhar número de versão e ritmo de release, mesmo que só um tenha mudado.

O sufixo depois da versão (`-SNAPSHOT`, `-RELEASE`, ou nenhum) é livre — hoje é só nomenclatura, o pipeline não trata isso de forma diferente: qualquer string depois do `-v` vira literalmente a tag da imagem Docker (`order-api-v1.0.0-SNAPSHOT` → imagem `order-api:1.0.0-SNAPSHOT`). Uma diferenciação de comportamento de verdade (ex.: só RELEASE virar `latest`, ou só RELEASE ser publicada) fica pra quando houver um registry configurado.

### 6.5 Qualidade de código (PMD)

Os três `pom.xml` declaram o `maven-pmd-plugin` (versão 3.28.0) com os rulesets padrão `bestpractices` + `errorprone`. Ele **não** está amarrado a nenhuma fase do build — só roda quando chamado explicitamente (`pmd:check`), então não interfere no `mvn verify`/`build-and-test`.

```bash
cd order-api
./mvnw pmd:check
```

Relatório fica em `target/pmd.xml`; o job `quality` sobe esse arquivo como artifact mesmo quando o PMD encontra violação e derruba o build.

### 6.6 Limitações atuais

* **Nenhum registry configurado** — o `package` builda a imagem só dentro do runner efêmero do GitHub Actions; nada é publicado ou fica disponível depois que o job termina.
* **Push fora de PR dispara os 3 serviços** — trade-off da seção 6.3; dá pra restringir sem arriscar a tag separando o job de release num workflow à parte, só com gatilho de tag (sem `paths`).
* **SNAPSHOT/RELEASE é só nomenclatura** — não existe branch de comportamento no pipeline pra isso ainda (seção 6.4).

---

## 🗺️ 7. Fases de Evolução do Laboratório

* **Fase 1 (Atual):** Implementação da estrutura base, comunicação síncrona via HTTP/REST, sem persistência em banco de dados (estado em memória) e conteinerização via Docker.
* **Fase 2:** Evolução da comunicação síncrona para mensageria assíncrona utilizando **Apache Kafka** (o broker já está disponível via Docker Compose/Kubernetes — ver seção 5.2 —, mas nenhum microsserviço ainda produz ou consome mensagens nele).
* **Fase 3:** Orquestração de fluxos e rotinas com **Apache Airflow**.
* **Fase 4 (Em andamento):** Implantação e validação local utilizando **Kubernetes** (cluster local do Docker Desktop — ver seção 5), garantindo pods e serviços isolados.
* **Fase 5:** Implementação da segurança de ponta a ponta com **Keycloak (IAM)** protegendo rotas e APIs.

---

## 📝 Licença

Este projeto é de uso livre para estudos, testes e laboratórios de arquitetura de microsserviços.

```

```
