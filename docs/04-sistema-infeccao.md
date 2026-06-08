# 04 — Sistema de Infecção

O medidor de vírus é o relógio central do jogo. **Neste mundo não há cura**: uma vez infectado, é só questão de tempo até a morte (1 hora de tempo real do 0 ao 100%). E como a morte reinicia *tudo* (doc 03), ser infectado é uma sentença de perda total — a tensão máxima da proposta.

## Regras

1. **Dano de zumbi** causa perda de vida normal **+ chance de infecção** (e, em paralelo, chance de sangramento — ver doc 05).
2. **Chance base de infectar: 25%** por golpe de zumbi (configurável). Modificadores:
   - *Bruto* com skill **Resistência Viral**: −15% (ver nota de modo).
   - **Zumbi Corredor (Runner):** chance fixa de **apenas 5%**, e essa chance **ignora** a Resistência Viral do Bruto (cap absoluto — vale "até para o Bruto"). Ver doc 07.
3. **Não há item de cura.** Uma vez `infected=true`, não existe como reverter. (O antigo "Soro Antiviral Puro" foi **removido** do jogo.)
4. **Agravamento por dano:** se o jogador **já está infectado** e **sofre mais dano**, há **50% de chance** de o medidor **saltar +15%** (`infectionLevel += 15`, limitado a 100). Isso acelera a morte de quem continua apanhando.
5. Enquanto infectado, `infectionLevel` sobe de forma contínua de 0 → 100 ao longo de **3600 segundos** (1h real), **além** dos saltos do item 4.
6. Ao atingir **100%**, o jogador morre. Essa é uma **morte por infecção**: **não** ativa o estado "Derrubado" (doc 06) e, como toda morte real, dispara o **wipe total** (doc 03).

> **Nota sobre o −15% do Bruto:** definir no config se é relativo (`25% * (1-0.15) = 21,25%`) ou absoluto (`25% - 15% = 10%`). Recomendo **relativo**. Lembrando: isso só afeta a chance **base** de 25% — **não** afeta o 5% do Corredor.

## Agravamento por dano (+15%)

```java
// chamado quando um jogador JÁ infectado sofre qualquer dano relevante
void onDamageWhileInfected(PlayerProfile p, EntityDamageEvent e) {
    if (!p.isInfected()) return;
    if (isExcludedCause(e.getCause())) return;   // ver nota do loop de feedback abaixo
    if (random(p) < config.aggravateChance()) {  // 0.50
        double now = Math.min(100.0, p.getInfectionLevel() + config.aggravateAmount()); // +15
        p.setInfectionLevel(now);
        feedbackAggravate(p);                     // som/tela: "a infecção avança..."
        if (now >= 100.0) killByInfection(p);
    }
}
```

> ⚠️ **Evitar espiral infinita:** o **dano do próprio sangramento** (doc 05) é um `DamageCause` que NÃO deve contar para o +15% — senão infecção + sangramento entram em loop de morte instantânea. Excluir `CUSTOM`/dano de DoT interno em `isExcludedCause`. Deixar configurável quais causas contam (`aggravate.count-causes`). Padrão sugerido: contar golpes de entidade (zumbis/combate) e excluir o tick de sangramento.

## Matemática do ticking contínuo

- Reaching 100% em 3600s, tick de 1s (via `TickService`): incremento por segundo = `100 / 3600 ≈ 0.02778`.
- Generalizado: `increment = 100.0 / config.timeToDeathSeconds`.
- Só incrementa se `infected == true`.

```java
void tick(PlayerProfile p) {            // TickService, 1x/segundo, por jogador online
    if (!p.isInfected()) return;
    double inc = 100.0 / config.timeToDeathSeconds();
    double now = Math.min(100.0, p.getInfectionLevel() + inc);
    p.setInfectionLevel(now);
    feedbackByThreshold(p, now);        // sons/efeitos conforme estágio
    if (now >= 100.0) killByInfection(p);
}
```

## Aplicação da chance de infectar (no dano)

```java
@EventHandler
void onZombieHit(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player player)) return;
    if (!isZombieLike(e.getDamager())) return;            // zumbi vanilla ou mutante
    PlayerProfile p = profiles.get(player.getUniqueId());
    p.markLastZombieDamage();

    // sangramento (doc 05) é avaliado em paralelo aqui também

    if (p.isInfected()) {                                  // já infectado: só agravamento
        onDamageWhileInfected(p, e);
        return;
    }

    double chance;
    if (isRunner(e.getDamager())) {
        chance = config.runnerInfectionChance();           // 0.05 fixo, sem Resistência Viral
    } else {
        chance = config.baseInfectionChance();             // 0.25
        chance = classService.applyViralResistance(player, chance); // Bruto −15% (só na base)
    }
    if (random(player) < chance) {
        infectionManager.infect(p);                        // infected=true, level=0
        feedbackInfected(player);                          // som sinistro, mensagem, partícula
    }
}
```

## Feedback ao jogador (imersão sem HUD poluído)

- **Ao infectar:** som grave/sinistro, mensagem curta em vermelho (action bar/título), partículas breves. Mensagem deixa claro que **não há cura** — é contagem regressiva.
- **Estágios** (configuráveis), efeitos crescentes para dar tensão:
  | Faixa | Efeito sugerido |
  |-------|-----------------|
  | 0–25% | nenhum efeito; som ocasional |
  | 25–50% | `NAUSEA` curto e raro |
  | 50–75% | `WEAKNESS` leve; `NAUSEA` mais frequente |
  | 75–99% | `WEAKNESS`/`SLOWNESS` perceptível; visão esverdeada; batimento cardíaco |
  | 100% | morte por infecção → wipe total |
- **Onde ver o número:** o próprio jogador vê sob demanda (item/comando); o **Médico** vê o valor exato de aliados via *Diagnóstico Rápido* (doc 06).
- **Antídoto comum (doc 05):** apenas **alivia temporariamente os sintomas/efeitos** (debuffs) — **não** mexe no medidor nem na sentença de morte.

## Morte por infecção

```java
void killByInfection(PlayerProfile p) {
    Player player = Bukkit.getPlayer(p.getUuid());
    if (player == null) return;
    p.setInfectionFlagForDeath(true);    // marca p/ o ClassManager NÃO ativar "Derrubado"
    player.setHealth(0.0);               // dispara PlayerDeathEvent → wipe total (doc 03)
    // mensagem de morte customizada: "<player> sucumbiu à infecção."
}
```

- A morte por infecção **ignora** o sistema de reanimação do Médico (sem segunda chance).
- O reset (infecção, classe, skills, XP, sanidade, itens) acontece no `PlayerDeathEvent` (doc 03).

## `infection.yml` (exemplo)

```yaml
base-infection-chance: 0.25        # 25% (golpe de zumbi comum)
runner-infection-chance: 0.05      # 5% fixo do Corredor, ignora Resistência Viral
time-to-death-seconds: 3600        # 1 hora real do 0 ao 100%

aggravate:
  chance: 0.50                     # 50% ao tomar dano já infectado
  amount: 15                       # +15% no medidor
  count-causes: [ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE]  # NÃO inclui o tick de sangramento

viral-resistance:
  mode: RELATIVE                   # RELATIVE | ABSOLUTE
  amount: 0.15                     # Bruto: -15% (só na chance base, não no Corredor)

stages:
  - { from: 25, effects: [{ type: NAUSEA, amplifier: 0, frequency: 200 }] }
  - { from: 50, effects: [{ type: WEAKNESS, amplifier: 0 }] }
  - { from: 75, effects: [{ type: SLOWNESS, amplifier: 0 }, { type: WEAKNESS, amplifier: 1 }] }
```

## Comandos (admin/debug)

- `/deadzone infection get <player>`
- `/deadzone infection set <player> <0-100>`
- `/deadzone infection infect <player>`
- *(não há `cure` — não existe cura no jogo; um `set 0` admin/debug serve para testes)*

## Integrações

- **Classes:** chance base reduzida pelo Bruto (não vale para o Corredor); morte por infecção pula o "Derrubado".
- **Morte/Wipe (doc 03):** morte por infecção = wipe total garantido.
- **Sangramento (doc 05):** mesmo golpe de zumbi pode iniciar sangramento; cuidado com o loop no +15%.
- **Sanidade:** opcional — infecção alta pode acelerar perda de sanidade (medo da morte). Configurável.
- **Evento `PlayerInfectedEvent`** (futuro, em `api/events`) para estatística.
