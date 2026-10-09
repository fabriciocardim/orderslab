# Research: Build Health Check e Correção de Dependências

## Investigação: artifact IDs suspeitos nos pom.xml

**Pergunta original**: `spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e as
variantes `-test` (`spring-boot-starter-webmvc-test`, `spring-boot-starter-kafka-test`,
`spring-boot-starter-actuator-test`) são coordinates reais que resolvem no BOM do Spring
Boot 4.1.1, ou nomes incorretos?

**Método**: executado `./mvnw clean verify` e `./mvnw dependency:tree` em cada um dos 3
serviços (`order-api`, `payment-api`, `invoice-api`), a partir do diretório de cada módulo.

**Resultado**: os 3 serviços buildam com `BUILD SUCCESS` (exit code 0), todos os testes
passam, e a árvore de dependências resolve 100% das declarações sem nenhum erro ou conflito.

**Decision**: os artifact IDs em questão são válidos e não precisam de correção.

**Rationale**: o projeto usa Spring Boot **4.1.1** (não 3.x). A partir do Spring Boot 4, a
Spring reestruturou os starters:
- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`** (para diferenciar
  explicitamente de `spring-boot-starter-webflux`).
- Passou a existir um starter oficial **`spring-boot-starter-kafka`** (mapeado para
  `spring-kafka` internamente), em vez de declarar `spring-kafka` diretamente como nas
  versões 3.x.
- Cada starter de funcionalidade ganhou seu próprio companion de teste granular
  (`spring-boot-starter-webmvc-test`, `spring-boot-starter-kafka-test`,
  `spring-boot-starter-actuator-test`), em vez de um único `spring-boot-starter-test`
  genérico cobrindo tudo. Cada `-test` já traz exatamente as ferramentas de teste relevantes
  para aquele starter (ex.: `webmvc-test` traz `MockMvc`/`WebTestClient`;
  `actuator-test` traz JUnit/Mockito/AssertJ como base transitiva).

**Alternatives considered**:
- *Substituir por `spring-boot-starter-web`/`spring-kafka` (coordinates do Boot 3.x)*:
  rejeitado — quebraria o build, pois essas dependências específicas de Boot 3 não existem
  no BOM do Boot 4.1.1 do jeito que o projeto está montado hoje.
- *Consolidar os 3 `-test` em um único `spring-boot-starter-test` genérico*: rejeitado —
  iria contra o padrão idiomático que o próprio Spring Boot 4 adotou (granularidade por
  starter). Não há redundância real a resolver: cada `-test` traz ferramentas específicas do
  seu starter correspondente, não uma cópia do mesmo conteúdo.

## Conclusão

O item 1.1 do ROADMAP já está satisfeito no estado atual do repositório — nenhuma mudança de
código é necessária. A suspeita original (registrada no ROADMAP.md e na spec desta feature)
partiu de uma comparação implícita com convenções do Spring Boot 3.x, que não se aplicam à
versão 4.1.1 usada pelo projeto. O trabalho desta feature se torna: documentar a
confirmação (spec.md, ROADMAP.md) para que a suspeita não seja reaberta por engano no
futuro.
