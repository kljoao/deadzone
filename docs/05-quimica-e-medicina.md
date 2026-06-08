# 05 — Química & Medicina

Três partes: (a) o **framework genérico de itens customizados**; (b) o **sistema de sangramento** (ferimento que precisa ser tratado); (c) os **itens médicos/químicos** com a **bancada de crafting customizada** (GUI).

## Parte A — Framework de Itens Customizados

### Identificação via PersistentDataContainer

Cada item recebe uma chave única no PDC do `ItemMeta`:

```java
// ItemKeys.java
public static final NamespacedKey ITEM_ID = new NamespacedKey(plugin, "item_id");
```

Detectar item custom = ler `meta.getPersistentDataContainer().get(ITEM_ID, STRING)`. Robusto a renomear/lore; não depende de comparar material (frágil).

### Classe base + Registry

```java
public abstract class CustomItem {
    public abstract String id();              // "bandagem", "kit_primeiros_socorros"
    public abstract ItemStack build();        // material, nome, lore, PDC, model data
    public boolean onUse(Player player, ItemStack stack) { return false; } // clique direito
    public Tier tier() { return Tier.T1; }
    public boolean craftableByEveryone() { return true; }
    public String requiredSkillId() { return null; } // p/ a bancada (ex.: "med_farmacologia_avancada")
}
```

```java
public class ItemRegistry {
    private final Map<String, CustomItem> byId = new HashMap<>();
    public void register(CustomItem item) { byId.put(item.id(), item); }
    public Optional<CustomItem> resolve(ItemStack stack) { /* lê PDC ITEM_ID */ }
}
```

Um único `ItemUseListener` no clique direito resolve o item pelo PDC e chama `onUse`.

### Visual dos itens (resource pack — ver doc 11)

- Cada item custom usa **`CustomModelData`** (ou *item models* 1.21.4+) apontando para uma textura no resource pack.
- Reservar uma faixa de IDs por categoria em `items.yml` (ex.: 1000–1099 remédios, 2000+ armas, 3000+ munição, 4000+ misc).
- Texturas serão produzidas à parte (discussão de IA/arte em doc 11).

### Crafting: **Bancada Customizada (GUI)** — opção escolhida

Em vez de receitas vanilla, usamos uma **estação de crafting própria** baseada em GUI (doc 09):

- O jogador abre a **Bancada Médica** (item/bloco interagível, ou comando) → GUI com as receitas disponíveis.
- A GUI lista só o que **aquele jogador pode fabricar**: itens comuns para todos; itens **Tier 2/3 só aparecem para o Médico** com a skill **Farmacologia Avançada**.
- Ao clicar numa receita: valida ingredientes no inventário, consome-os e entrega o item. Som/efeito de sucesso.
- Vantagens sobre receita vanilla: controle total de quem vê/fabrica o quê, sem gambiarra de `PrepareItemCraftEvent`, e visual mais imersivo.

```java
// pseudo: ao clicar numa receita da bancada
void onRecipeClick(Player p, MedicalRecipe r) {
    if (r.requiresSkill() && !classService.hasSkill(p, r.requiredSkillId())) return; // nem aparece, mas dupla checagem
    if (!hasIngredients(p, r.ingredients())) { feedbackFail(p); return; }
    consume(p, r.ingredients());
    give(p, itemRegistry.get(r.resultId()).build());
    feedbackCraft(p);  // som + partícula
}
```

> Estrutura: `MedicalRecipe` (resultId, ingredients[], requiredSkillId) carregada de `items.yml`. A bancada é uma `Menu` (doc 09).

## Parte B — Sistema de Sangramento

Ferimento causado por zumbis. Modela "perder sangue": dano periódico que **piora quanto mais o jogador apanha** e só para com **bandagem**.

### Regras
1. Ao tomar um golpe de zumbi, **50% de chance** de iniciar (ou agravar) **sangramento**. (Independente da chance de infecção — um golpe pode causar os dois, um, ou nenhum.)
2. O sangramento tem uma **severidade** (`severity`, stacks). Cada novo golpe que "pega" (os 50%) **soma +1** à severidade (até um teto configurável).
3. O sangramento causa **dano periódico** (ex.: 1.0 = meio coração) num **intervalo que encurta conforme a severidade**:
   - severidade 1 (um tapa) → ~1 dano a cada **15s**.
   - quanto mais golpes acumulados, **menor o intervalo** (dano mais frequente) — "apanhou muito, sangra rápido".
4. **Bandagem** zera o sangramento (`severity = 0`) e **interrompe** o dano. (A bandagem **não** cura vida — só estanca; ver Parte C.)
5. **Não persiste** e é **limpo no wipe** da morte (doc 03).

### Matemática do intervalo

```
intervaloSegundos = max(minInterval, baseInterval / severity)
```
Com `baseInterval = 15s` e `minInterval = 3s`:
| Severidade | Intervalo | Sensação |
|-----------|-----------|----------|
| 1 | 15s | arranhão |
| 2 | 7,5s | ferimento |
| 3 | 5s | sério |
| 4 | 3,75s | grave |
| 5+ | 3s (piso) | hemorragia |

> Alternativa/combinável: manter o intervalo e **escalar o dano** com a severidade. Deixar `bleeding.scaling: INTERVAL | DAMAGE | BOTH` no config. Padrão: `INTERVAL`.

### Estado e tick

```java
public class BleedState {
    int severity;
    long nextDamageAt;   // epoch millis do próximo tick de dano
}

// no TickService (loop curto), por jogador com bleedState != null:
void bleedTick(PlayerProfile p) {
    BleedState b = p.getBleedState();
    if (b == null) return;
    if (now() < b.nextDamageAt) return;
    Player pl = Bukkit.getPlayer(p.getUuid());
    pl.damage(config.bleedDamage(), DamageSource.bleeding()); // causa CUSTOM — NÃO conta p/ +15% infecção
    spawnBloodParticles(pl);
    long interval = Math.max(config.minIntervalMs(), config.baseIntervalMs() / b.severity);
    b.nextDamageAt = now() + interval;
}
```

> O dano de sangramento usa uma causa marcada como **excluída** do agravamento de infecção (doc 04) para evitar o loop infecção↔sangramento.

### `bleeding.yml` (exemplo)
```yaml
chance-on-zombie-hit: 0.50
max-severity: 6
damage-per-tick: 1.0          # meio coração
base-interval-seconds: 15
min-interval-seconds: 3
scaling: INTERVAL             # INTERVAL | DAMAGE | BOTH
particles: true
```

## Parte C — Itens Médicos & Químicos

### Tabela de itens

| Item | Tier | Função | Fabricação |
|------|------|--------|-----------|
| **Bandagem** | T1 | **Estanca o sangramento** (`severity=0`). **NÃO** cura vida nem dá regeneração. | Todos (bancada) |
| **Analgésico** | T1 | Remove/alivia debuffs leves; pequeno alívio de sanidade | Todos |
| **Antídoto (comum)** | T2 | **Alivia temporariamente os sintomas da infecção** (debuffs). **NÃO** baixa o medidor nem cura. | Todos |
| **Seringa de Adrenalina** | T2 | Restaura estamina/saturação + boost de **sanidade** na hora; `SPEED` curto | **Médico** (Farmacologia Avançada) |
| **Kit de Primeiros Socorros** | T2 | Cura vida significativa; usado para **reviver** Derrubado (doc 06) | Médico fabrica; **todos usam** |
| **Desfibrilador** | T3 | Revive Derrubado com 50% de vida | Médico fabrica; **todos usam** |

> **Removido:** o *Soro Antiviral Puro* não existe mais — **não há cura para a infecção** (doc 04).
> **Regra-chave:** qualquer um **usa** itens T2/T3, mas só o Médico com a skill **fabrica** na bancada.

### Bandagem — comportamento (importante: sem regen)

```java
class Bandagem extends CustomItem {
    public boolean onUse(Player player, ItemStack stack) {
        PlayerProfile p = profiles.get(player.getUniqueId());
        if (p.getBleedState() == null) { feedbackNoEffect(player); return false; } // nada p/ estancar
        // canalização (cast) curta antes de aplicar — ver cooldown/cast
        p.setBleedState(null);              // estanca: zera severidade e dano
        consumeOne(stack);
        feedbackBandage(player);            // som de curativo + partícula
        return true;
    }
}
```
- Sem `REGENERATION`, sem cura de coração — **só** para o sangramento.
- Tem **cooldown** e **tempo de canalização** (`cast`), centralizados no config (não estancar instantâneo em combate).

### Demais efeitos (resumo)
- **Analgésico:** limpa debuffs leves; pequeno `+sanity`.
- **Antídoto comum:** suprime por X segundos os efeitos de estágio da infecção (sem tocar no medidor).
- **Seringa de Adrenalina:** `+saturation`, `SPEED` curto, `sanityManager.add(p, X)`.
- **Kit / Desfibrilador:** interagem com **jogador Derrubado** (doc 06) — clique direito mirando no caído inicia o revive (canalização → 50% de vida no Desfibrilador).

### `items.yml` (exemplo)
```yaml
items:
  bandagem:
    material: PAPER
    name: "&fBandagem"
    lore: ["&7Estanca sangramentos.", "&7Não cura vida."]
    model-data: 1001
    use: { cooldown-seconds: 8, cast-seconds: 2, stops-bleeding: true }
    recipe: { result: bandagem, ingredients: { STRING: 3 } }

  kit_primeiros_socorros:
    material: SHEARS
    name: "&cKit de Primeiros Socorros"
    lore: ["&7Cura ferimentos graves.", "&7Revive jogadores derrubados."]
    model-data: 1005
    tier: T2
    recipe:
      result: kit_primeiros_socorros
      requires-skill: med_farmacologia_avancada
      ingredients: { bandagem: 2, REDSTONE: 1 }   # placeholder
```

## Integrações
- **Infecção (doc 04):** sem cura; Antídoto só mascara sintomas; cuidado com sangramento no +15%.
- **Sangramento ↔ Bandagem:** único tratamento do sangramento.
- **Classes (doc 06):** bancada gated por Farmacologia Avançada; Kit/Desfibrilador alimentam o revive.
- **Sanidade (doc 08):** Analgésico/Seringa dão alívio/boost de sanidade.
- **Resource pack (doc 11):** texturas custom via model-data.
