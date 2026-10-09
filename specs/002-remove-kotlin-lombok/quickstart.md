# Quickstart: Remover Toolchain Kotlin/Lombok Morto

Como reproduzir a verificação desta feature.

## Passos

```bash
grep -i "kotlin\|lombok" payment-api/pom.xml   # esperado: nenhum resultado
grep -i "kotlin\|lombok" invoice-api/pom.xml   # esperado: nenhum resultado

cd payment-api && ./mvnw clean verify && cd ..
cd invoice-api && ./mvnw clean verify && cd ..
```

## Resultado esperado

- Os dois `grep` não retornam nenhuma linha (exit code 1 de "nada encontrado").
- Os dois builds terminam com `BUILD SUCCESS` e código de saída `0`.
- Nenhum arquivo `.java` foi alterado (`git diff --stat` mostra só os 2 `pom.xml`).
