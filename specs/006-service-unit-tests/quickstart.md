# Quickstart: Testes Unitários de Service

Como rodar os testes desta feature.

```bash
cd order-api && ./mvnw test && cd ..
cd payment-api && ./mvnw test && cd ..
cd invoice-api && ./mvnw test && cd ..
```

## Resultado esperado

Cada comando termina com `BUILD SUCCESS` e um resumo tipo:

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

(o número exato de testes varia por serviço — `payment-api`/`invoice-api` têm 1-2 casos a
mais por terem 2 ações de transição cada, ver `research.md`).

## Rodar só a classe nova (mais rápido, sem o `contextLoads` do Spring Boot)

```bash
cd order-api && ./mvnw test -Dtest=OrderServiceTest && cd ..
cd payment-api && ./mvnw test -Dtest=PaymentServiceTest && cd ..
cd invoice-api && ./mvnw test -Dtest=InvoiceServiceTest && cd ..
```
