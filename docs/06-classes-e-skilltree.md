# 06 — Classes & Árvore de Habilidades

Sistema de progressão: o jogador escolhe uma **classe**, ganha **XP** jogando, e gasta XP para desbloquear **skills** numa árvore. Skills mudam *como* se joga, incentivando especialização e cooperação.

## Classes

| Classe | Fantasia | Eixo |
|--------|----------|------|
| **Médico** | Suporte, mantém o grupo vivo | Cura, diagnóstico, revive, farmácia avançada |
| **Saqueador** | Explorador, encontra recursos | Detecção de loot, arrombamento |
| **Bruto** | Linha de frente, tanque | Resistência viral, controle de zumbis |

- Escolha via GUI (`/classe`, doc 09). Decisão: **troca de classe é permitida?** Recomendo: primeira escolha grátis; trocas posteriores com custo (XP/itens) ou cooldown, configurável. Trocar **não** reembolsa skills automaticamente (ou reembolsa parcial — configurável).
- `playerClass = NONE` até escolher; algumas skills exigem classe específica.

## XP — fontes e progressão

Fontes de XP (todas configuráveis em `classes.yml`):

| Ação | XP (exemplo) |
|------|-------------|
| Matar zumbi comum | 5 |
| Matar zumbi mutante | 15–40 (por tipo) |
| Completar missão/objetivo | variável |
| Saquear container raro (1ª vez) | 10 |
| Reviver um aliado (Médico) | 25 |
| Sobreviver a uma Lua de Sangue | 100 |

- `xp` (gastável) e `totalXpEarned` (histórico, pode virar "nível" cosmético).
- Anti-farm: cooldown/diminishing returns por mob, ou XP só de zumbis "legítimos" (não spawners infinitos). Configurável.
- Feedback: ganho de XP via action bar discreta (`+5 XP`) e som leve.

## Árvore de Habilidades (Skill Tree)

### Modelo de dados de uma skill

```java
public record SkillNode(
    String id,                 // "med_diagnostico_rapido"
    String displayName,
    List<String> description,
    PlayerClass requiredClass, // NONE = qualquer classe
    long cost,                 // XP
    List<String> prerequisites,// ids que precisam estar desbloqueados
    int slot                   // posição no GUI
) {}
```

- Definidas em `classes.yml` (data-driven) e carregadas num `SkillRegistry`.
- Desbloqueio: checa classe, pré-requisitos e XP; debita XP; adiciona a `unlockedSkills`; persiste.
- Efeitos de skill são **passivos** (checados onde importam) ou **ativos** (habilitam um item/ação). O código consulta `classService.hasSkill(player, id)` no ponto de uso.

### Skills planejadas (MVP)

#### Médico
- `med_diagnostico_rapido` — **Diagnóstico Rápido**
- `med_reanimacao` — **Reanimação** (habilita revive de Derrubados)
- `med_farmacologia_avancada` — **Farmacologia Avançada** (habilita craft T2/T3)

#### Saqueador
- `saq_sexto_sentido` — **Sexto Sentido para Loot** (Rádio de Frequência)
- `saq_lockpicking` — **Mestre do Pé de Cabra** (abre containers trancados)

#### Bruto
- `bru_resistencia_viral` — **Resistência Viral** (−15% chance de infecção)
- `bru_atordoamento` — **Armas de Contusão** (chance de stun em zumbis)

> A árvore pode crescer com tiers (ex.: Diagnóstico Rápido II aumenta o raio; Resistência Viral II vira −25%). O modelo `prerequisites` já suporta isso.

## Especificações das skills do MVP

### Médico — Diagnóstico Rápido
- **Gatilho:** segurando *Shift* (agachado) e olhando para um jogador aliado a ≤ 2 blocos.
- **Efeito:** action bar do Médico mostra `Vida ❤ X/20 · Infecção ☣ Y% · Sanidade 🧠 Z%` do alvo, atualizado enquanto mira.
- **Implementação:** no `TickService` (ou listener de sneak), raycast/`getNearbyEntities` para achar o player na direção do olhar dentro de 2 blocos; ler o profile do alvo; enviar action bar.

### Médico — Reanimação + Estado "Derrubado"

**Estado "Derrubado" (vale para todos os jogadores, não só perto do Médico):**
- Em vez de morrer, o jogador entra em "Derrubado" por **30s** (configurável).
- Efeitos: `BLINDNESS`, `SLOWNESS` extremo, **não pode atacar** (cancelar dano de saída), talvez forçar agachado/pose. Mantém itens.
- HUD: contagem regressiva via action bar/boss bar; mensagem aos próximos pedindo ajuda.
- Se ninguém revive em 30s → **morte real** → **wipe total** (classe, skills, XP, infecção, sanidade, itens — doc 03). Por isso o revive do Médico é a *única* rede de proteção do jogo.
- **Exceção:** morte por **infecção** (doc 04) **não** ativa "Derrubado" — é morte definitiva (e wipe garantido).
- Outras causas que podem pular o Derrubado (configurável): void, `/kill`, lava prolongada? Decidir em `classes.yml` (`downed.ignored-causes`).

```java
@EventHandler(priority = EventPriority.HIGH)
void onLethalDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player player)) return;
    PlayerProfile p = profiles.get(player.getUniqueId());
    if (e.getFinalDamage() < player.getHealth()) return;        // não seria letal
    if (p.isDyingFromInfection()) return;                       // deixa morrer
    if (config.downedIgnoredCauses().contains(e.getCause())) return;
    e.setCancelled(true);                                       // impede a morte
    downedManager.enterDowned(player, p);                       // inicia estado + timer 30s
}
```

**Reanimação (revive):**
- Requer skill `med_reanimacao`.
- O Médico usa **Kit de Primeiros Socorros** ou **Desfibrilador** (doc 05) clicando no jogador Derrubado.
- Canalização de alguns segundos (barra de progresso); ao concluir, o player levanta com **50% da vida**.
- Médico ganha XP por revive. Tocar sons/partículas.

### Médico — Farmacologia Avançada
- Habilita o craft de itens T2/T3 (gate em `PrepareItemCraftEvent` ou na bancada médica). Ver doc 05.

### Saqueador — Sexto Sentido para Loot
- **Gatilho:** usar **Rádio de Frequência** (item custom, clique direito).
- **Efeito:** "ping" — baús com itens raros **não saqueados** a ≤ 10 blocos emitem trilha de partículas brilhantes, **visíveis só para o Saqueador**, por alguns segundos.
- **Como saber se tem "loot raro" e se "não foi saqueado":**
  - Marcar containers de loot via PDC/registro (gerados por evento, ou marcados manualmente/por loot tables custom). Flag `looted=false`.
  - Partículas direcionadas com `player.spawnParticle(...)` (só o Saqueador recebe).
- **Cooldown alto: ~30 minutos (tempo real).** É uma habilidade de reconhecimento estratégico, não de uso contínuo. Guardar o timestamp do último uso no profile (transitório) ou no PDC do próprio rádio.

### Saqueador — Mestre do Pé de Cabra (Lockpicking)
- **Containers trancados:** baús/barris marcados (PDC `deadzone:locked=true`).
- Jogadores comuns: não abrem (cancelar `InventoryOpenEvent`/`PlayerInteractEvent`) ou demoram muito (canalização longa + chance).
- **Saqueador com Pé de Cabra:** clique direito abre **instantaneamente** (ou canalização curta). Consome durabilidade do pé de cabra.
- Marcação de containers via comando admin (`/deadzone lock <wand>`), ou na geração do mapa.

### Bruto — Resistência Viral
- −15% na chance de infecção (doc 04, `applyViralResistance`).

### Bruto — Armas de Contusão (Atordoamento)
- Ao acertar um zumbi com **machado ou espada** (configurável quais materiais), **chance** de aplicar `SLOWNESS` pesado por alguns segundos (atordoamento).
- Implementar em `EntityDamageByEntityEvent` (damager = player com a skill, victim = zumbi). Som/partícula de impacto.

## `classes.yml` (exemplo)

```yaml
class-change:
  first-free: true
  cost-xp: 500
  cooldown-hours: 24
  refund-skills: false

xp-sources:
  zombie-kill: 5
  mutant-kill: { RUNNER: 15, TANK: 30, EXPLODER: 25 }
  revive-ally: 25
  survive-blood-moon: 100
  loot-rare-first-time: 10

downed:
  duration-seconds: 30
  revive-health-percent: 50
  ignored-causes: [VOID, CUSTOM]   # além de infecção, já tratada em código

skills:
  med_diagnostico_rapido:
    name: "Diagnóstico Rápido"
    class: MEDICO
    cost: 100
    prerequisites: []
    range: 2
  med_reanimacao:
    name: "Reanimação"
    class: MEDICO
    cost: 250
    prerequisites: [med_diagnostico_rapido]
  med_farmacologia_avancada:
    name: "Farmacologia Avançada"
    class: MEDICO
    cost: 400
    prerequisites: [med_reanimacao]
  saq_sexto_sentido:
    name: "Sexto Sentido para Loot"
    class: SAQUEADOR
    cost: 150
    radius: 10
    duration-seconds: 6
    cooldown-seconds: 1800        # 30 minutos (tempo real)
  saq_lockpicking:
    name: "Mestre do Pé de Cabra"
    class: SAQUEADOR
    cost: 250
  bru_resistencia_viral:
    name: "Resistência Viral"
    class: BRUTO
    cost: 150
  bru_atordoamento:
    name: "Armas de Contusão"
    class: BRUTO
    cost: 250
    chance: 0.20
    slowness-amplifier: 3
    duration-seconds: 3
    weapons: [IRON_AXE, DIAMOND_AXE, NETHERITE_AXE, IRON_SWORD, DIAMOND_SWORD, NETHERITE_SWORD]
```

## Integrações

- **Infecção:** Resistência Viral, e o gate de morte por infecção pulando o Derrubado.
- **Medicina:** Farmacologia Avançada (craft), Kit/Desfibrilador (revive).
- **Sanidade:** Diagnóstico Rápido mostra sanidade; possível skill futura de "Psiquiatra".
- **Eventos:** XP por sobreviver à Lua de Sangue, matar mutantes.
