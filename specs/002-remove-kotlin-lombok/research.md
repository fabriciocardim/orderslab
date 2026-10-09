# Research: Remover Toolchain Kotlin/Lombok Morto

## Confirmação: nenhum uso real de Kotlin ou Lombok

**Método**: `find . -name "*.kt" -not -path "*/target/*"` (repo inteiro) e
`grep -rln "lombok" --include="*.java" .` (repo inteiro).

**Resultado**: zero arquivos `.kt`; zero arquivos `.java` referenciando `lombok`.

**Decision**: seguro remover o toolchain Kotlin/Lombok de `payment-api` e `invoice-api` sem
tocar em nenhum arquivo `.java`.

## O que remover exatamente (mapeado do `pom.xml` atual, idêntico nos 2 serviços)

- Propriedade `<kotlin.version>2.3.20</kotlin.version>`.
- Dependências: `org.jetbrains.kotlin:kotlin-stdlib`, `org.jetbrains.kotlin:kotlin-test`
  (scope test), `org.projectlombok:lombok` (optional).
- Plugin `org.jetbrains.kotlin:kotlin-maven-plugin` inteiro, incluindo as 3 sub-dependências
  de compiler plugin (`kotlin-maven-noarg`, `kotlin-maven-lombok`, `kotlin-maven-allopen`) e
  a configuração `<compilerPlugins>` (`jpa`, `lombok`, `spring`).
- No `maven-compiler-plugin`: os dois blocos `<annotationProcessorPaths>` (em
  `default-compile` e `default-testCompile`) que apontam para `org.projectlombok:lombok` —
  sem a dependência Lombok, esses processors não têm efeito e só adicionam ruído.

**Rationale**: cada um desses blocos só existe para suportar Kotlin ou Lombok, e nenhum dos
dois é usado. `order-api/pom.xml` (mesmo Spring Boot 4.1.1, mesmas funcionalidades reais —
web, Kafka, actuator) não tem nenhum desses blocos e builda normalmente — prova de que não
são necessários para o que o projeto realmente faz hoje.

**Alternatives considered**:
- *Manter "por via das dúvidas" caso o time queira Kotlin depois*: rejeitado — a constitution
  (Princípio II) trata débito técnico não intencional como algo a resolver, não a preservar;
  se Kotlin for adotado formalmente no futuro, isso deve ser uma decisão explícita e uma
  feature própria, reintroduzindo o toolchain com um motivo documentado (e provavelmente
  algum arquivo `.kt` real, já que não faz sentido reintroduzir o suporte sem uso).
- *Remover só as dependências, manter o `kotlin-maven-plugin` configurado mas inerte*:
  rejeitado — um plugin sem propósito ainda executa nas fases `compile`/`test-compile`
  (mesmo sem fontes `.kt` para compilar), adicionando tempo de build e superfície de
  configuração sem benefício.

## Conclusão

Remoção segura e mecânica: editar os 2 `pom.xml` (idênticos exceto `artifactId`), sem tocar
em nenhum arquivo `.java`, e revalidar com `./mvnw clean verify`.
