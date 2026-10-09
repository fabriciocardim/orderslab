# Quickstart: Testes de Integração com Testcontainers

Como validar que `mvnw test` roda sem depender de nenhuma infra do projeto já provisionada.

## Pré-requisitos

- Docker Desktop (ou outro runtime Docker) rodando na máquina.
- **Importante para a validação real**: `infra/docker-compose.yml` do projeto (`postgres-api`
  etc.) **NÃO deve estar rodando** — o objetivo é provar que os testes não dependem disso.

```bash
cd infra && docker compose down
```

## Passo a passo (exemplo com order-api, mesmo padrão para os outros 2)

```bash
cd order-api && ./mvnw test
```

Esperado: `BUILD SUCCESS`, os 22 testes passando (10 Service + 9 Controller + 3 integração
HTTP) — mesmo sem `postgres-api` do Compose rodando. Nos logs, deve aparecer o Testcontainers
baixando/subindo um container `postgres:15-alpine` efêmero (algo como `Creating container for
image: postgres:15-alpine` e `Container postgres:15-alpine started`), seguido do Flyway
rodando a migração real (`Successfully applied 1 migration`) contra esse container.

## Verificação: duas execuções seguidas não interferem entre si

```bash
./mvnw test && ./mvnw test
```

Ambas devem passar de forma idêntica — cada execução sobe e descarta seu próprio container,
sem estado compartilhado entre rodadas.

## Verificação: Service/Controller continuam sem Docker

```bash
./mvnw test -Dtest=OrderServiceTest,OrderControllerTest
```

Esperado: passa instantaneamente, sem qualquer log de Testcontainers/Docker — confirma que só
os testes de integração HTTP (item 1.8) tocam em container.

## Repetir para os outros 2 serviços

```bash
cd ../payment-api && ./mvnw test
cd ../invoice-api && ./mvnw test
```
