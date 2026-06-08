# Contribuindo

Obrigado pelo interesse em contribuir com o Deadzone!

## Ambiente

- JDK 21
- Maven
- Um servidor Paper 1.21.1 para testar

## Build

```bash
mvn clean package
```

Copie `target/Deadzone-0.1.0-SNAPSHOT.jar` para `plugins/` do seu servidor de teste.

## Estrutura

- `src/main/java/com/deadzone/core/` — infraestrutura (config, banco, perfis, scheduler, itens, GUI).
- `src/main/java/com/deadzone/modules/` — sistemas de gameplay (infection, medicine, classes, sanity, events, world).
- `src/main/resources/` — `plugin.yml` e os YAML de configuração.
- `docs/` — documentação de design de cada sistema.
- `resourcepack/` — modelos e texturas dos itens.
- `tools/` — scripts utilitários (geração/empacotamento de texturas).

## Diretrizes

- Mantenha cada sistema isolado no seu módulo; use os managers/serviços existentes.
- Nada de números de gameplay "chumbados" no código — exponha via YAML.
- Não bloqueie a main thread: I/O (banco/arquivo) sempre assíncrono.
- Garanta que `mvn clean package` passa antes de abrir o PR.

## Pull Requests

1. Crie um branch a partir do principal.
2. Faça commits pequenos e descritivos.
3. Descreva a mudança e como testou no PR.
