# Deadzone — Referência Completa

Plugin oficial do servidor de sobrevivência zumbi (Project Zomboid-inspired) para **Paper 1.21.1**.
Este documento reflete o **estado atual** do plugin: sistemas, comandos, permissões, configs e modelo de dados.

---

## 1. Stack & build

- **Plataforma:** Paper 1.21.1 · **Java 21** · **Maven** (maven-shade-plugin; HikariCP relocado).
- **Banco:** SQLite (arquivo) **ou** PostgreSQL — configurável em `config.yml`. Pool HikariCP, persistência assíncrona (thread FIFO única).
- **Dependências (softdepend):** ProtocolLib (pose "derrubado" + glow de clã), Vault/VaultUnlocked (economia). Integra também com LuckPerms (prefixo no chat) e PlaceholderAPI no servidor.

**Compilar:**
```
JAVA_HOME=<jdk-21>  mvn -B -ntp -q clean package
```
O jar sai em `target/Deadzone-0.1.0-SNAPSHOT.jar` → copiar para `server/plugins/`.

**Configs que NÃO mesclam valores alterados** (só adicionam chaves novas): ao mudar VALORES de `firearms.yml`, `items.yml`, `classes.yml`, `clans.yml`, `shops.yml` etc., **sobrescreva** a cópia do servidor.

---

## 2. Sistemas (módulos)

### Sobrevivência
- **Infecção** (`infection.yml`) — nível 0–100, sintomas progressivos, morte por infecção; suprimida/reduzida por itens.
- **Sanidade** (`sanity.yml`) — 0–100; sanidade baixa aplica debuffs; itens restauram.
- **Sangramento** (`bleeding.yml`) — ferimentos sangram; estancados por bandagens.
- **Perna quebrada** (`brokenleg.yml`) — quedas quebram a perna; tala acelera a recuperação.
- **Derrubado & Reanimação** — em vez de morrer, o jogador cai imóvel (pose deitada via ProtocolLib) e pode ser reanimado por um Médico (kit/desfibrilador); timer de sangrar-até-a-morte; persiste o tempo restante no relog.

### Classes & habilidades (`classes.yml`)
- Classes: **Médico, Bruto, Saqueador** (+ NONE = sem classe).
- Árvore de habilidades por classe (`/skills`), desbloqueio por XP.
- Skill universal **Armeiro** (`class: NONE`) — libera o crafting de armas/anexos para qualquer classe.
- XP por abater zumbis e por reanimar aliados.

### Armas de fogo (`firearms.yml`, `gun-skins.yml`)
- Arsenal: **g17, ump, ump_suppressed, ak47, kar98k, kar98k_scope, kar98k_scope_sup**.
- Munição: **9mm, 45acp, 762, 792**. Anexos: **silenciador, mira_8x**.
- Mecânicas: modo semi/automático, ADS/zoom de luneta, coronhada (concussão sem dano), animação de saque, queda de bala, spread/recuo acumulado, barulho do disparo (atrai zumbis).
- **Skins cosméticas:** sistema por `custom_model_data` (PDC `firearm_skin_cmd`), aplicado por `/deadzone gunskin <id|none>`; a arma base fica intacta. Skins definidas em `gun-skins.yml` (atualmente vazio — a skin "wave" foi removida).

### Itens médicos/químicos (`items.yml`) + Bancada
- bandagem, bandagem_esterilizada, alcool_desinfetante, analgesico, antidoto, seringa_adrenalina, kit_primeiros_socorros, tala, gas_mask (3D na cabeça), desfibrilador.
- **Bancada** (`/bancada`): abas Médica e Armeiro; alguns itens exigem skill.

### Ameaças & eventos (`events.yml`, `zones.yml`, `siege.yml`, `noise.yml`, `atmosphere.yml`, `world.yml`)
- Spawn de zumbis de dia, **mutantes** (Runner/Tank/Exploder), **Blood Moon**, **zonas tóxicas/radioativas** (exigem máscara de gás), **barulho** atraindo a horda, ambientação, **cerco/barricadas**, e radar de zumbis compartilhado.

### Bases / Claims (`claim.yml`)
- Reivindicar base, membros com permissões, **evolução por tiers**, **baús com senha (PIN)**, **cerco**, restrição de construção fora da base (com bypass).

### Economia (Scraps)
- Moeda **Scraps** no perfil (NÃO some na morte). Comandos `/saldo /pagar /cobrar /baltop`, admin `/eco`. Ponte **Vault**. Item **Sucata** (`scrap`) dropável.

### Loot do mundo (`loot-tables.yml`)
- Baús com **busca estilo Tarkov** (revela 1 item/seg via GUI), tabelas de **chance independente**, cooldown.

### Clãs (`clans.yml`)
- Criar/convidar/aceitar/sair/expulsar; cargos **Líder > Oficial > Membro > Recruta**.
- **Cofre** compartilhado; **progressão por tiers** paga com scraps do cofre (8 → 150 membros).
- **Friendly-fire** por clã (toggle do líder), **glow de aliado** (≤50 blocos, só membros veem; ProtocolLib) e **símbolo de aliado** acima da cabeça (TextDisplay per-viewer).
- **Chat do clã** (`/c`), **tag colorida** (perm VIP), **tier máximo** liberado a Warlord/Overlord (perm).
- **GUI completo** (`/clan`); **`/clantop`** (ranking por tier → membros → cofre).

### Bounty (`bounty.yml`)
- **Notoriedade:** cada abate de jogador soma ao seu próprio bounty (anti-farm por vítima).
- **Colocar:** `/bounty <jogador> <valor>` gasta seus scraps na cabeça do alvo.
- **Reivindicar:** matar quem tem bounty paga o valor ao matador; só zera quando reivindicado (morte por zumbi mantém).
- **`/bounty top`** — mais procurados.

### Lojas (`shops.yml`)
- **`/medico`** — compra de itens médicos (preço por raridade).
- **`/armeiro`** — seletor Armas / Modificações.
- **`/comprador`** — vende itens do jogador (sucata, carne de zumbi, loots) por scraps — gira a economia.
- GUIs emolduradas e temáticas, com preço destacado e indicador de saldo.

### Engajamento
- **Recompensa diária** (`/diario`, `daily-rewards.yml`) — streak de login com scraps + itens nos dias-marco.
- **Estatísticas** (`/stats`) — zumbis/jogadores abatidos, mortes, K/D, reanimações, maior sobrevivência.

### Núcleo
- Perfis persistentes (wipe na morte: progressão zera; scraps/stats/bounty permanecem), framework de GUI próprio, HUD, **chat formatado** (prefixo LuckPerms + tag do clã), resource pack (modelos 3D de armas/itens, máscara de gás na cabeça, reskin de armaduras).

---

## 3. Comandos

| Comando | Aliases | Permissão | Descrição |
|---|---|---|---|
| `/deadzone` | `/dz` | (subperms `deadzone.admin.*`) | Raiz admin: `info, reload, profile, giveitem, infection, skill, xp, lock, unlock, zone, bloodmoon, mutant, gunskin` |
| `/bancada` | `/medbench` | `deadzone.command.bancada` | Bancada (médica + armeiro) |
| `/classe` | `/class /classes` | `deadzone.command.classe` | Selecionar classe |
| `/skills` | `/habilidades /skilltree` | `deadzone.command.skills` | Árvore de habilidades |
| `/confirmar base` | — | `deadzone.command.base` | Reivindicar base |
| `/minhabase` | `/limitesbase` | `deadzone.command.base` | Mostrar limites da base |
| `/saldo` | — | `deadzone.command.economia` | Ver scraps |
| `/pagar` | `/pay /pix` | `deadzone.command.economia` | Transferir scraps |
| `/cobrar` | — | `deadzone.command.economia` | Cobrar (clicável + confirmação) |
| `/baltop` | — | `deadzone.command.economia` | Top 10 mais ricos |
| `/eco` | — | `deadzone.admin.eco` | Admin da economia |
| `/loot` | — | `deadzone.admin.loot` | Gerenciar baús de loot |
| `/diario` | `/daily /recompensa /recompensadiaria` | `deadzone.command.diario` | Recompensa diária |
| `/stats` | `/estatisticas /stat` | `deadzone.command.stats` | Estatísticas |
| `/clan` | `/cla /clã /clans` | `deadzone.command.clan` | Gestão de clãs (GUI) |
| `/c` | `/clanchat /cc` | `deadzone.command.clan` | Chat do clã |
| `/clantop` | `/topclans` | `deadzone.command.clan` | Ranking de clãs |
| `/bounty` | `/recompensa-cabeca /cacada` | `deadzone.command.bounty` | Ver/colocar bounty; `/bounty top` |
| `/medico` | — | `deadzone.command.lojas` | Loja do médico |
| `/armeiro` | — | `deadzone.command.lojas` | Loja do armeiro |
| `/comprador` | — | `deadzone.command.lojas` | Vender itens |

---

## 4. Permissões

**Jogador (default `true`):** `deadzone.command.bancada`, `.classe`, `.skills`, `.base`, `.economia`, `.diario`, `.stats`, `.clan`, `.bounty`, `.lojas`.

**VIP (default `false` — atribuir por grupo no LuckPerms):**
- `deadzone.clan.coloredtag` — tag de clã colorida (VIP+).
- `deadzone.clan.maxtier` — clã começa no tier máximo (Warlord/Overlord).
- `deadzone.claim.bypass` — construir fora da base.

**Admin (default `op`):** `deadzone.admin` (pai) + `deadzone.admin.{info,reload,profile,giveitem,infection,skill,xp,lock,unlock,zone,bloodmoon,mutant,gunskin,eco,loot}`.

---

## 5. Arquivos de configuração (`plugins/Deadzone/`)

| Arquivo | O que controla |
|---|---|
| `config.yml` | Banco (sqlite/postgresql), autosave, wipe na morte, debug |
| `messages.yml` | Strings (MiniMessage) |
| `world.yml` | Regras de mundo / spawn diurno |
| `infection.yml` · `sanity.yml` · `bleeding.yml` · `brokenleg.yml` | Sobrevivência |
| `atmosphere.yml` · `noise.yml` · `hud.yml` | Ambientação, barulho, HUD |
| `classes.yml` | Classes, skill tree, skill Armeiro |
| `items.yml` | Itens médicos/químicos + receitas |
| `firearms.yml` | Armas e munição (stats, sons, model-data) |
| `gun-skins.yml` | Skins cosméticas de armas |
| `events.yml` · `zones.yml` · `siege.yml` | Eventos, zonas tóxicas, cerco |
| `claim.yml` | Bases (claims, tiers, baús) |
| `loot-tables.yml` | Loot do mundo (chance independente) |
| `daily-rewards.yml` | Recompensa diária (ciclo, scraps+itens) |
| `clans.yml` | Clãs (custo, cores, tiers) |
| `bounty.yml` | Bounty (notoriedade, mínimo, anti-farm) |
| `shops.yml` | Lojas (itens e preços) |

---

## 6. Modelo de dados (tabelas SQL)

- **`players`** — `uuid` (PK), `name`, `infected`, `infection_level`, `sanity`, `player_class`, `xp`, `total_xp_earned`, `first_join`, `last_seen`, `downed_until`, `balance`, `daily_streak`, `last_daily_claim`, `stat_zombies_killed`, `stat_players_killed`, `stat_deaths`, `stat_revives`, `stat_best_survival_ms`, `life_started_at`, `bounty`.
- **`player_skills`** — `uuid`, `skill_id`, `unlocked_at` (PK uuid+skill_id).
- **`clans`** — `id` (PK), `name`, `tag`, `color`, `leader_uuid`, `created_at`, `bank`, `level` (tier), `xp`, `friendly_fire`, `glow`, `symbol`.
- **`clan_members`** — `uuid` (PK), `clan_id`, `name`, `role`, `joined_at`.
- **`schema_version`** — `version`.

Tipos portáveis: `BIGINT` (longs/timestamps em ms), `DOUBLE PRECISION` (floats). UPSERT `ON CONFLICT(uuid) DO UPDATE` funciona em SQLite e PostgreSQL. Colunas novas entram por `ALTER TABLE ... ADD COLUMN` em autocommit (idempotente).

> **PostgreSQL 15+:** o usuário do banco precisa de `GRANT ALL ON SCHEMA public TO <user>;`.

---

## 7. Operação / deploy

1. `mvn clean package` → copiar o jar para `server/plugins/`.
2. Ao mudar **valores** de configs que não mesclam (ver §1), sobrescrever a cópia do servidor.
3. **Resource pack:** re-zipar `resourcepack/` e publicar (`dist/` + cliente). Modelos custom usam `custom_model_data` em ordem ascendente.
4. **Reiniciar** o servidor aplica migrações de schema e recria configs novos a partir do jar.
