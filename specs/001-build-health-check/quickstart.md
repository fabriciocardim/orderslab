# Quickstart: Build Health Check

Como reproduzir a verificação desta feature.

## Pré-requisitos

- Java 21 instalado (ou usar o Maven Wrapper de cada serviço, que não exige Maven global).
- Acesso ao Maven Central (para resolver dependências).

## Passos

Para cada um dos 3 serviços, a partir da raiz do repositório:

```bash
cd order-api && ./mvnw clean verify && cd ..
cd payment-api && ./mvnw clean verify && cd ..
cd invoice-api && ./mvnw clean verify && cd ..
```

## Resultado esperado

Cada comando termina com:

```text
[INFO] BUILD SUCCESS
```

E código de saída `0` (`echo $?` logo após o comando).

## Verificação opcional da árvore de dependências

Para confirmar que os artifact IDs `spring-boot-starter-webmvc`,
`spring-boot-starter-kafka` e as variantes `-test` resolvem para coordinates reais:

```bash
cd order-api && ./mvnw dependency:tree
```

Deve aparecer, entre outras linhas, `org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.1:compile`
e `org.springframework.boot:spring-boot-starter-kafka:jar:4.1.1:compile` resolvidos sem erro.

## Status confirmado nesta feature (2026-09-24)

Os 3 serviços já passam nesta verificação sem nenhuma alteração de código — ver
[research.md](./research.md) para os detalhes da investigação.
