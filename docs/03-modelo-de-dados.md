# 03 — Modelo de Dados

Tudo o que é específico do jogador vive num `PlayerProfile`, carregado no *join* e salvo de forma assíncrona. Este documento é a fonte da verdade dos campos persistidos.

## `PlayerProfile`

```java
public class PlayerProfile {
    private final UUID uuid;
    private String lastKnownName;

    // --- Infecção (doc 04) ---
    private boolean infected;          // está infectado?
    private double infectionLevel;     // 0.0 .. 100.0

    // --- Sanidade (doc 08) ---
    private double sanity;             // 0.0 .. 100.0 (100 = saudável)

    // --- Classe & progressão (doc 06) ---
    private PlayerClass playerClass;   // NONE, MEDICO, SAQUEADOR, BRUTO
    private long xp;                   // XP acumulado não gasto
    private long totalXpEarned;        // histórico (para estatística/nível)
    private Set<String> unlockedSkills; // ids das skills desbloqueadas

    // --- Estado transitório (NÃO persistir, recalculado por sessão) ---
    private transient DownedState downedState; // null se não estiver derrubado
    private transient long lastDamageByZombie;
    private transient BleedState bleedState;   // null se não estiver sangrando (doc 05)

    // metadados
    private long firstJoin;
    private long lastSeen;
    private boolean dirty;             // marca p/ autosave incremental
}
```

> **Transient vs persistido:** estado "Derrubado", timers temporários e caches ficam só em memória. Infecção, sanidade, classe, XP e skills são persistidos.

## Schema SQLite

```sql
-- Perfil principal (1 linha por jogador)
CREATE TABLE IF NOT EXISTS players (
    uuid              TEXT PRIMARY KEY,
    name              TEXT,
    infected          INTEGER NOT NULL DEFAULT 0,   -- 0/1
    infection_level   REAL    NOT NULL DEFAULT 0,
    sanity            REAL    NOT NULL DEFAULT 100,
    player_class      TEXT    NOT NULL DEFAULT 'NONE',
    xp                INTEGER NOT NULL DEFAULT 0,
    total_xp_earned   INTEGER NOT NULL DEFAULT 0,
    first_join        INTEGER,                       -- epoch millis
    last_seen         INTEGER
);

-- Skills desbloqueadas (N por jogador)
CREATE TABLE IF NOT EXISTS player_skills (
    uuid       TEXT NOT NULL,
    skill_id   TEXT NOT NULL,
    unlocked_at INTEGER,
    PRIMARY KEY (uuid, skill_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

-- (Futuro) zonas tóxicas persistidas, se não vierem só do YAML
-- (Futuro) log de eventos / estatísticas
```

> Versão do schema guardada numa tabela `schema_version` para permitir migrações incrementais no `SchemaManager`.

## Ciclo do cache (ProfileManager)

```
PlayerJoinEvent
  → async: DAO.load(uuid)            # lê banco fora da main thread
  → main: coloca PlayerProfile no cache (Map<UUID, PlayerProfile>)
  → se não existe linha: cria perfil padrão (sanity=100, classe=NONE)

durante o jogo
  → módulos leem/escrevem o profile em memória; setters marcam dirty=true

autosave (a cada N minutos, configurável)
  → async: para cada profile dirty, DAO.save(snapshot) e limpa dirty

PlayerDeathEvent (morte REAL, não "Derrubado")
  → WIPE TOTAL: reseta o profile aos padrões (ver seção abaixo)

PlayerQuitEvent
  → async: DAO.save(snapshot); remove do cache

onDisable (servidor caindo)
  → sync: DAO.saveAll() de todos os perfis em cache
```

## Reset total na morte (permadeath de progressão)

> **Decisão do projeto:** quando o jogador **morre de verdade**, *tudo* é reiniciado — classe, skills, XP, infecção, sanidade e itens. Não há "salvar progresso entre vidas". É o núcleo da proposta de sobrevivência hardcore.

```java
@EventHandler(priority = EventPriority.MONITOR)
void onDeath(PlayerDeathEvent e) {
    PlayerProfile p = profiles.get(e.getPlayer().getUniqueId());
    p.resetToDefaults();            // classe=NONE, xp=0, skills={}, infected=false,
                                    // infectionLevel=0, sanity=100, bleed limpo
    p.markDirty();
    // itens: o Minecraft já dropa/limpa o inventário na morte (keepInventory=false).
    // No respawn o jogador volta "do zero" e precisa reescolher a classe (GUI /classe).
}
```

Implicações em cada sistema:

| Sistema | O que reseta |
|---------|--------------|
| **Classe** | volta para `NONE` — reescolhe via GUI no respawn |
| **Skills** | `unlockedSkills` esvaziado; linhas em `player_skills` apagadas |
| **XP** | `xp = 0`; `totalXpEarned` **pode** ser mantido só como estatística histórica (cosmético) — configurável |
| **Infecção** | `infected=false`, `infectionLevel=0` |
| **Sanidade** | volta a `100` |
| **Sangramento** | estado transitório limpo |
| **Itens** | perdidos (inventário vanilla cai/zera na morte) |

> **Importante — interação com o estado "Derrubado" (doc 06):** o wipe só acontece na **morte real**. O estado "Derrubado" *intercepta* o dano letal antes da morte; se um Médico reviver a tempo, **não** há wipe. Isso torna o revive a única rede de proteção do jogo e o Médico extremamente valioso. **Morte por infecção pula o "Derrubado"** (doc 04) → wipe garantido.

> **Nota de balanceamento (sinalizada):** resetar a árvore de skills/XP a cada morte é *muito* punitivo e pode frustrar. Recomendo deixar o escopo do wipe **configurável** (ex.: `wipe-on-death.reset-xp: true|false`, `keep-total-xp-stat: true`) para você calibrar em playtests sem reescrever código. O padrão segue seu pedido: reseta tudo.

### Snapshot para save assíncrono

Para evitar *race conditions* (main thread alterando enquanto a thread de I/O serializa), o save recebe um **snapshot imutável**:

```java
ProfileSnapshot snap = profile.snapshot();   // cópia dos campos primitivos + set de skills
CompletableFuture.runAsync(() -> dao.save(snap), dbExecutor);
```

## DAO — contrato

```java
public interface PlayerProfileDao {
    PlayerProfile load(UUID uuid);          // null se não existir
    void save(ProfileSnapshot snapshot);    // upsert players + diff de skills
    void saveAll(Collection<ProfileSnapshot> snapshots);
    void delete(UUID uuid);                 // wipe (admin)
}
```

A implementação `SqlitePlayerProfileDao` usa `INSERT ... ON CONFLICT(uuid) DO UPDATE` (upsert) e gerencia `player_skills` por diff (inserir novas, opcionalmente remover removidas).

## Dados em itens e entidades (não no banco)

Nem tudo é "do jogador". Estado que pertence a **itens** ou **entidades** vive no `PersistentDataContainer` deles:

- **Item customizado** (bandagem, antídoto, pé de cabra…): chave `deadzone:item_id` no PDC do `ItemMeta`. Ver doc 05.
- **Zumbi mutante**: chave `deadzone:zombie_type` (RUNNER, TANK, EXPLODER) no PDC da entidade. Ver doc 07.
- **Baú trancado / baú com loot raro**: chaves no PDC do bloco/tile (ou registro em memória + persistência por chunk). Ver docs 06/07.

Isso mantém o banco enxuto (só dados de jogador) e o estado co-localizado com o objeto a que pertence.

## Por que SQLite agora, MySQL depois

- SQLite: zero infra, um arquivo, perfeito para servidor único.
- A camada **DAO + Database (pool)** isola o dialeto SQL. Migrar para MySQL = nova implementação de `Database` (URL/driver) + ajustes pontuais de SQL (ex.: `AUTOINCREMENT` vs `AUTO_INCREMENT`, upsert). A lógica de gameplay não muda.
- Recomendo manter o SQL o mais ANSI possível e concentrar particularidades no DAO.
