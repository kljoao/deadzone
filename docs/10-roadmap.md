# 10 — Roadmap

Ordem de implementação pensada para entregar valor cedo e construir cada sistema sobre uma fundação sólida. Cada fase é "jogável/testável" ao terminar.

> Filosofia: **fundação → 1 sistema vertical completo → expandir**. Não construir tudo pela metade. Cada fase termina com algo testável no servidor.

---

## Fase 0 — Fundação (infra, sem gameplay ainda)
**Objetivo:** plugin que liga, persiste dados e tem as ferramentas core.

- [ ] Scaffolding Maven (`pom.xml`, `plugin.yml`, classe principal `DeadzonePlugin`).
- [ ] `ConfigManager` + `Messages` (carregar YAML, acesso tipado).
- [ ] `Database` (HikariCP + SQLite) + `SchemaManager` (cria tabelas, `schema_version`).
- [ ] `PlayerProfile` + `ProfileManager` (load no join, save async no quit, autosave, cache).
- [ ] **Wipe total na morte** (`PlayerDeathEvent` → `resetToDefaults`) — doc 03. Definir flags configuráveis (`reset-xp`, `keep-total-xp-stat`).
- [ ] `TickService` (loop de 1s + loop raro, fan-out de handlers).
- [ ] `ItemRegistry` + `CustomItem` base + `ItemUseListener` (framework de itens).
- [ ] Framework de GUI (`Menu`, `MenuHolder`, `MenuListener`).
- [ ] Comando raiz `/deadzone` + permissões + util de debug.

**Marco:** servidor sobe, perfis carregam/salvam, dá para criar um item custom de teste e abrir uma GUI vazia.

---

## Fase 1 — Infecção (primeiro vertical de gameplay)
**Objetivo:** o relógio central funcionando ponta a ponta.

- [ ] `infection.yml` + `InfectionManager`.
- [ ] Chance de 25% no dano de zumbi (`InfectionListener`); base reduzida pela Resistência Viral.
- [ ] Ticking 0→100 em 1h via `TickService`.
- [ ] **Agravamento +15%** (50% ao tomar dano já infectado), excluindo o tick de sangramento.
- [ ] Estágios/efeitos por faixa + feedback (sons, action bar, partículas).
- [ ] Morte por infecção (marcada, sem reanimação) → wipe total.
- [ ] Comandos admin (`infection get/set/infect`). **Sem cura no jogo.**

**Marco:** dá para ser mordido, infectar, ver progressão acelerar ao apanhar e morrer pela infecção (com wipe). Não há cura — é definitivo.

---

## Fase 2 — Itens Médicos, Química & Sangramento
**Objetivo:** itens customizados reais, gestão de ferimentos e a bancada de crafting.

- [ ] Carregar itens do `items.yml` (data-driven) + subclasses para lógica especial.
- [ ] **Sistema de sangramento** (`bleeding.yml` + `BleedingManager`): 50% por golpe de zumbi, severidade que encurta o intervalo, tick de dano.
- [ ] **Bandagem** → estanca o sangramento (sem regen/cura de vida).
- [ ] Analgésico, Antídoto comum (alivia sintomas, não cura), Seringa de Adrenalina.
- [ ] **Bancada Médica (GUI de crafting customizada)** — opção escolhida (doc 05/09), com gate de skill por receita.
- [ ] Kit de Primeiros Socorros / Desfibrilador (itens; uso de revive entra na Fase 3).

**Marco:** apanhar de zumbi pode causar sangramento; só bandagem estanca; itens são fabricados na bancada (T2/T3 dependem da skill, ativada na Fase 3). **Não há cura para infecção.**

---

## Fase 3 — Classes & Árvore de Habilidades
**Objetivo:** progressão e identidade de jogo.

- [ ] `PlayerClass`, `SkillRegistry` (data-driven via `classes.yml`), `SkillService`.
- [ ] XP: fontes (matar zumbi etc.), ganho com feedback, persistência (`player_skills`).
- [ ] GUI de seleção de classe (`/classe`) + regras de troca.
- [ ] GUI da árvore de habilidades (`/skills`) com estados visuais.
- [ ] Skills MVP:
  - [ ] Bruto: **Resistência Viral** (−15% infecção) e **Atordoamento**.
  - [ ] Médico: **Diagnóstico Rápido**, **Farmacologia Avançada** (libera receitas T2/T3 na bancada da Fase 2).
  - [ ] **Estado "Derrubado"** + **Reanimação** (Kit/Desfibrilador, exceto morte por infecção).
  - [ ] Saqueador: **Sexto Sentido** (Rádio) e **Lockpicking** (Pé de Cabra + containers trancados).

**Marco:** jogadores escolhem classe, ganham XP e desbloqueiam skills que alteram o jogo.

---

## Fase 4 — Sanidade
**Objetivo:** camada psicológica.

- [ ] `sanity.yml` + `SanityManager` (plugado no TickService).
- [ ] Fatores de queda (escuro + checagem de luz na mão, pressão de zumbis). *(Solidão removida.)*
- [ ] Fatores de recuperação (base iluminada via `BaseProvider`, companhia, dia, medicação).
- [ ] `BaseProvider` integrando com o plugin de claim (doc 11), com fallback `/base set`.
- [ ] Efeitos por faixa (redução de dano melee, slowness, visuais/sons).
- [ ] Integração: Diagnóstico Rápido mostra sanidade; itens dão boost.

**Marco:** escuridão e cerco punem; grupo e base (claim iluminado) recompensam.

---

## Fase 5 — Eventos Dinâmicos & Ameaças
**Objetivo:** mundo perigoso e variado.

- [ ] **Zumbis Mutantes** (Runner/Tank/Exploder) — spawn por chance, atributos, mecânicas especiais, XP. Corredor infecta com 5% fixo (ignora Resistência Viral).
- [ ] **Lua de Sangue** — disparo (dias/cronograma), buffs, spawns extras, quebra de blocos fracos, recompensa.
- [ ] **Zonas Tóxicas** — `zones.yml`, comandos de criação (wand), dano por segundo, Máscara de Gás, loot/boss.

**Marco:** noites de horda, regiões letais e variedade de inimigos.

---

## Fase 6 — Polimento & Balanceamento
**Objetivo:** transformar "funciona" em "divertido".

- [ ] Balanceamento de números (chances, custos de XP, dano, tempos) via playtests.
- [ ] Resource pack: texturas custom (custom model data / item models) para itens — workflow de arte/IA do doc 11.
- [ ] **ModelEngine (opcional):** modelos/animações server-side para mutantes e bosses (doc 11), atrás de `AnimationProvider`.
- [ ] **HUD lateral (Scoreboard)** persistente: Infecção/Sanidade/Classe/XP/Dia (doc 09), reduzindo o uso de BossBar. Idealmente junto da Fase 4.
- [ ] Áudio/visual de imersão (Lua de Sangue, infecção, sanidade).
- [ ] Performance: perfilamento (Spark), caps de spawn, otimização de tasks.
- [ ] `api/events` (eventos customizados) para integrações futuras.
- [ ] Migração opcional para MySQL (se virar rede de servidores).
- [ ] Estatísticas/Perfil (GUI), missões/objetivos (fonte estruturada de XP).

---

## Dependências entre fases

```
Fase 0 (Fundação + wipe na morte)
   ├─> Fase 1 (Infecção — sem cura)
   │       └─> Fase 2 (Medicina + sangramento + bancada)
   ├─> Fase 3 (Classes) ── depende de itens (Fase 2) p/ Kit/Desfibrilador e gate da bancada
   ├─> Fase 4 (Sanidade) ── usa itens (Fase 2), Diagnóstico (Fase 3) e plugin de claim (doc 11)
   └─> Fase 5 (Eventos) ── dá XP p/ classes (Fase 3), piora sanidade (Fase 4), usa itens (Fase 2)
Fase 6 (Polimento + resource pack + ModelEngine) ── contínua, intensifica no fim
```

A ordem recomendada é **0 → 1 → 2 → 3 → 4 → 5 → 6**, mas Fase 4 (Sanidade) pode rodar em paralelo à Fase 3 se houver mais de uma pessoa, pois ambas só dependem da Fase 0 (e a Sanidade só "se completa" com itens da Fase 2 e Diagnóstico da Fase 3).

## Princípios de execução
- **Cada fase entrega algo testável** no servidor de dev.
- **Config primeiro:** ao criar um sistema, definir o YAML antes de hardcodar.
- **Commitar por feature** (quando o repo git for inicializado — hoje a pasta não é um repositório).
- **Playtest cedo e sempre** — números de sobrevivência só se acertam jogando.

## Pendências de decisão (resolver antes/durante a fase relevante)
- Patch exato da 1.21 (1.21.8 recomendado) — **Fase 0**.
- Escopo do wipe: resetar XP/total mesmo? (`reset-xp`, `keep-total-xp-stat`) — **Fase 0**.
- Modo do redutor de Resistência Viral (RELATIVE vs ABSOLUTE) — **Fase 1/3**.
- Quais `DamageCause` contam para o +15% de agravamento (excluir o sangramento) — **Fase 1/2**.
- `scaling` do sangramento: INTERVAL, DAMAGE ou BOTH — **Fase 2**.
- Troca de classe: custo/cooldown/refund (lembrando que a morte já reseta tudo) — **Fase 3**.
- Qual plugin de claim adotar (GriefPrevention vs Lands) para o `BaseProvider` — **Fase 4 / doc 11**.
- Lua de Sangue: por dias de jogo, cronograma real, ou ambos — **Fase 5**.
- Adotar ModelEngine para mutantes/bosses? (custo do plugin) — **Fase 5/6 / doc 11**.

## Decisões já fechadas (nesta revisão)
- **Sem cura para infecção** — Soro Antiviral Puro removido.
- **Wipe total na morte** (classe, skills, XP, infecção, sanidade, itens).
- **+15% de infecção** (50% de chance) ao tomar dano já infectado.
- **Sangramento** (50% por golpe de zumbi), tratado **só com bandagem** (sem regen).
- **Corredor infecta 5% fixo**, ignorando Resistência Viral.
- **Crafting via Bancada Médica (GUI)**, não receitas vanilla.
- **Solidão removida** da sanidade.
- **Rádio do Saqueador:** cooldown de ~30 min reais.
- **Plugin de claim externo** para "base" da sanidade; **ModelEngine** considerado para visuais.
```
