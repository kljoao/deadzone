# 01 — Arquitetura

## Princípios

- **Modular por sistema**: cada mecânica (infecção, sanidade, classes…) é um módulo independente com seu próprio *manager*, *listeners* e *commands*. Adicionar/remover um sistema não deve quebrar os outros.
- **Service Locator leve**: a classe principal expõe os managers; módulos pedem o que precisam. Sem framework de DI pesado no começo (podemos adotar Guice depois se crescer).
- **Dados em memória, persistência assíncrona**: o estado do jogador vive num cache em RAM durante a sessão; escrita no banco é assíncrona e em lote. Nunca bloquear a *main thread* com I/O.
- **Tudo configurável**: nenhum número mágico no código. Constantes de gameplay vêm de YAML.
- **Thread-safety consciente**: a API do Bukkit é single-thread (main thread). Só usamos async para I/O (banco, arquivos) e cálculos puros; qualquer mudança no mundo/entidades volta para a main thread via scheduler.

## Estrutura de pacotes

```
com.deadzone
├── DeadzonePlugin.java          # onEnable/onDisable, bootstrap, registro de managers
│
├── core/                        # Infraestrutura compartilhada
│   ├── config/                  # Carregamento e acesso tipado de YAML
│   │   ├── ConfigManager.java
│   │   └── Messages.java        # Strings/i18n (Adventure components)
│   ├── database/
│   │   ├── Database.java        # Pool HikariCP + conexão SQLite
│   │   ├── SchemaManager.java   # Criação/migração de tabelas
│   │   └── dao/                 # Data Access Objects (1 por agregado)
│   ├── profile/
│   │   ├── PlayerProfile.java   # Modelo de dados do jogador (ver doc 03)
│   │   ├── ProfileManager.java  # Cache, load no join, save no quit/autosave
│   │   └── ProfileService.java  # API de alto nível p/ outros módulos
│   ├── scheduler/
│   │   └── TickService.java     # Loops centrais (a cada segundo/tick) com fan-out
│   ├── item/                    # Framework de itens customizados (ver doc 05)
│   │   ├── CustomItem.java      # Classe base abstrata
│   │   ├── ItemRegistry.java
│   │   └── ItemKeys.java        # NamespacedKeys do PDC
│   ├── gui/                     # Framework de menus (ver doc 09)
│   │   ├── Menu.java
│   │   ├── MenuItem.java
│   │   └── MenuListener.java
│   └── util/                    # Helpers (location, particles, text, math)
│
├── modules/                     # Sistemas de gameplay
│   ├── infection/               # Doc 04
│   ├── medicine/                # Doc 05
│   ├── classes/                 # Doc 06 (classes, XP, skill tree, downed/revive)
│   ├── events/                  # Doc 07 (lua de sangue, zonas tóxicas, mutantes)
│   └── sanity/                  # Doc 08
│
└── api/                         # (futuro) eventos customizados expostos a outros plugins
    └── events/                  # ex.: PlayerInfectedEvent, BloodMoonStartEvent
```

Cada módulo segue o mesmo formato interno:

```
modules/infection/
├── InfectionManager.java        # Lógica + estado, registrado no plugin principal
├── InfectionListener.java       # Eventos do Bukkit relevantes
├── InfectionTask.java           # (se precisar) tick próprio ou plugado no TickService
└── InfectionCommand.java        # Comandos admin/debug (/infection set ...)
```

## Ciclo de vida do plugin

```
onEnable()
  1. ConfigManager.load()          # lê config.yml + arquivos de cada módulo
  2. Database.connect()            # abre pool, roda SchemaManager.migrate()
  3. ProfileManager.init()         # registra listeners de join/quit, agenda autosave
  4. ItemRegistry.registerAll()    # registra itens + receitas customizadas
  5. Para cada módulo: manager.enable()  # registra listeners, tasks, comandos
  6. TickService.start()           # inicia loops centrais
  7. Log de boot com versão e módulos ativos

onDisable()
  1. TickService.stop()
  2. Para cada módulo: manager.disable()
  3. ProfileManager.saveAll(sync)  # flush síncrono de todos os perfis (servidor caindo)
  4. Database.close()
```

> **Importante:** no `onDisable` o save é **síncrono** porque o scheduler já não roda de forma confiável durante o shutdown. Durante o jogo normal, saves são **assíncronos**.

## TickService — o coração temporal

Vários sistemas precisam "rodar a cada segundo" (infecção subindo, sanidade variando, checagem de zonas). Em vez de cada módulo criar seu próprio `BukkitRunnable`, centralizamos:

- Um único loop a **cada 20 ticks (1s)** que itera os jogadores online uma vez e chama os *handlers* registrados (`InfectionTick`, `SanityTick`, `ZoneTick`…).
- Um loop mais raro (ex.: a cada 5s ou 1 min) para coisas caras (varredura de baús, eventos).
- Vantagem: uma só iteração de jogadores, ordem previsível, fácil de perfilar e pausar.

```java
tickService.registerSecondHandler(profile -> infectionManager.tick(profile));
tickService.registerSecondHandler(profile -> sanityManager.tick(profile));
```

## Padrões adotados

| Padrão | Onde | Por quê |
|--------|------|---------|
| Manager/Service | Todos os módulos | Encapsula estado e expõe API limpa |
| DAO | `core/database/dao` | Isola SQL; troca SQLite→MySQL sem tocar na lógica |
| Registry | Itens, GUIs, eventos | Registro centralizado e descoberta |
| PersistentDataContainer (PDC) | Itens, entidades | Marcar itens/zumbis customizados sem NBT manual frágil |
| Event-driven | Integração entre módulos | Baixo acoplamento (ex.: morte por infecção dispara evento) |

## Integração entre módulos (exemplos)

- **Infecção ↔ Classes**: o `InfectionManager`, ao calcular a chance de 25%, consulta `ClassService` para aplicar o redutor de 15% do *Bruto* (Resistência Viral).
- **Classes ↔ Medicina**: receitas de Tier 2/3 checam se o jogador tem a skill *Farmacologia Avançada* antes de permitir o craft.
- **Infecção ↔ Downed/Revive**: ao morrer, o `ClassManager` checa se a causa foi infecção; se sim, **não** entra no estado "Derrubado" (morte definitiva).
- **Sanidade ↔ Eventos**: estar perto de muitos zumbis (lua de sangue) acelera a perda de sanidade.

Essas integrações são feitas via **chamadas diretas a Services** (cedo) e podem migrar para **eventos customizados** (`api/events`) conforme o acoplamento incomodar.

## Concorrência — regras de ouro

1. Leitura/escrita de entidades, blocos, inventários, efeitos → **sempre main thread**.
2. I/O de banco e arquivo → **sempre async**.
3. O cache de `PlayerProfile` é acessado pela main thread; o save async recebe uma **cópia imutável** (snapshot) para serializar, evitando *race conditions*.
4. Nunca chamar API do Bukkit dentro de uma task async — voltar com `Bukkit.getScheduler().runTask(...)`.
