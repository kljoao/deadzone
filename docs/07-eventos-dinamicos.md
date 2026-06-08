# 07 — Eventos Dinâmicos & Ameaças

Três sistemas que tornam o mundo perigoso e imprevisível: **Lua de Sangue**, **Zonas Tóxicas/Irradiadas** e **Zumbis Mutantes**.

---

## 1. Lua de Sangue

Noite especial em que a horda fica muito mais perigosa.

### Disparo (configurável)
- **Por dias de jogo:** a cada X dias in-game (`worldFullTime` / 24000 ticks).
- **Por cronograma real:** ex.: toda sexta às 21h (usar agendador real). Permitir ambos.
- **Aviso prévio:** ao anoitecer do dia da Lua de Sangue, anunciar (título vermelho, som, céu/clima alterado).

### Efeitos durante o evento
- **Mais spawns:** aumentar taxa/limite de spawn de zumbis à noite (via `CreatureSpawnEvent` boost + spawns manuais ao redor dos jogadores, respeitando *spawn caps*).
- **Zumbis mais rápidos:** `SPEED` aplicado, ou ajustar atributo `GENERIC_MOVEMENT_SPEED`.
- **Mais resistentes:** mais vida (`GENERIC_MAX_HEALTH`) e/ou redução de dano recebido.
- **Quebram blocos fracos:** porta, vidro, cerca de madeira. Implementar tarefa que faz zumbis perto de jogadores "atacarem" blocos da lista (`blood-moon.breakable-blocks`) com tempo de quebra; ou usar zombies com `canBreakDoors` + lógica custom para os demais blocos.
- **Visual/atmosfera:** céu avermelhado (via packets/clientes — complexo; alternativa: efeitos de tela, partículas, trovões, música).

### Implementação
```
BloodMoonManager
  - checkSchedule() no TickService (loop raro)
  - start(): seta estado ativo, aplica buffs aos zumbis que spawnam, agenda spawns extras, anúncio
  - tick(): mantém pressão (spawns ao redor dos players), zumbis quebrando blocos
  - end(): ao amanhecer, remove estado; XP de sobrevivência aos online; anúncio de fim
```
- Marcar zumbis spawnados durante o evento com PDC `deadzone:blood_moon=true` (para remover no fim / não bagunçar a horda normal).
- **Limites de performance:** caps de spawn por jogador/chunk; não spawnar longe de players.

### `events.yml` (trecho)
```yaml
blood-moon:
  trigger: DAYS           # DAYS | REAL_SCHEDULE | BOTH
  every-x-days: 7
  real-schedule: "FRI 21:00"
  zombie:
    speed-multiplier: 1.4
    health-multiplier: 1.5
    can-break-blocks: true
    breakable-blocks: [OAK_DOOR, GLASS, OAK_FENCE, GLASS_PANE]
  spawn:
    extra-per-player: 6
    radius: 32
    cap-per-player: 30
  reward-survive-xp: 100
```

---

## 2. Zonas Tóxicas / Irradiadas

Regiões onde o jogador toma dano constante sem o equipamento certo. Abrigam bosses e o melhor loot.

### Definição de zona
- **Geometria:** caixa (cuboid) ou raio (esfera) por mundo, definida em `zones.yml`. Editável por comando (selecionar com uma "wand").
- Campos: `name`, `world`, `min`, `max` (ou center+radius), `damagePerSecond`, `requiredProtection`, efeitos de ambiente.

### Mecânica
- No `TickService` (loop de 1s), para cada jogador: checar se está dentro de alguma zona.
- Se dentro e **sem** proteção adequada (ex.: **Máscara de Gás** equipada no slot de capacete, item custom): aplicar dano por segundo + efeitos (`POISON`, `WITHER`, `NAUSEA`, partículas verdes, névoa).
- Se **com** proteção: sem dano (ou dano reduzido); talvez consumir durabilidade do filtro da máscara ao longo do tempo (item consumível → tensão econômica).
- Feedback ao entrar/sair: título/som ("Você entrou em uma zona irradiada").

### Máscara de Gás
- Item custom (doc 05), equipável no `HELMET`. Detectar via PDC do capacete.
- Pode ter "filtro" como item separado/durabilidade que esgota → loop de manutenção.

### Conteúdo de zona
- **Bosses:** zumbis especiais (ver mutantes/boss). Spawns controlados, raros, com loot garantido bom.
- **Melhor loot:** containers de loot raro (integra com Sexto Sentido do Saqueador).

### `zones.yml` (exemplo)
```yaml
zones:
  - name: "Usina Abandonada"
    world: world
    type: CUBOID
    min: { x: 1200, y: 0, z: -340 }
    max: { x: 1320, y: 120, z: -220 }
    damage-per-second: 2.0
    required-protection: gas_mask     # item_id custom equipado no capacete
    effects: [{ type: POISON, amplifier: 0 }, NAUSEA_VISUAL]
    consume-filter: true
```
Comandos: `/deadzone zone wand`, `/deadzone zone create <nome>`, `/deadzone zone remove <nome>`, `/deadzone zone list`.

---

## 3. Zumbis Mutantes

Variações do zumbi padrão, marcadas via PDC `deadzone:zombie_type`.

| Tipo | Comportamento | Stats | Mecânica especial |
|------|---------------|-------|-------------------|
| **Corredor (Runner)** | Muito rápido | Pouca vida | `SPEED` alto; visual magro (baby zombie? armadura?). **Infecta com apenas 5%** (cap fixo que ignora a Resistência Viral do Bruto — doc 04) |
| **Tanque (Tank)** | Lento | Muita vida + resistente | Causa **repulsão (knockback)** forte ao atacar; talvez área |
| **Explosivo (Exploder)** | Normal | Média | Ao **morrer**, libera **nuvem de veneno** (`AreaEffectCloud` com `POISON`); pode também aumentar chance de infecção na nuvem |

### Spawn
- **Chance de substituição:** ao spawnar um zumbi (`CreatureSpawnEvent`), com certa probabilidade transformá-lo em mutante (aplicar atributos, marcar PDC, equipar visual).
- Probabilidades por tipo/condição (noite, bioma, Lua de Sangue eleva chances) em `events.yml`.
- Bosses = mutante "elite" com nome, barra de boss e loot table própria.

### Implementação
```
MutantManager
  - onSpawn(CreatureSpawnEvent): rola tipo; se mutante → applyType(zombie, type)
  - applyType(): seta atributos (speed/health), PDC, nome custom, equipamento/efeito visual
  - onDeath(EntityDeathEvent): se Exploder → spawn AreaEffectCloud de veneno; XP por tipo
  - onAttack(EntityDamageByEntityEvent): se Tank → knockback extra na vítima
```

### `events.yml` (trecho)
```yaml
mutants:
  enabled: true
  base-chance: 0.10               # 10% de um zumbi virar mutante
  weights: { RUNNER: 0.5, TANK: 0.3, EXPLODER: 0.2 }
  blood-moon-chance-multiplier: 2.0
  runner:  { speed: 0.35, health: 8 }
  tank:    { speed: 0.15, health: 60, knockback: 1.5 }
  exploder:{ speed: 0.23, health: 20, cloud-duration: 6, cloud-radius: 3 }
```

---

## Integrações
- **Classes/XP:** matar mutantes e sobreviver à Lua de Sangue dão XP (doc 06).
- **Infecção:** o `InfectionListener` (doc 04) lê o PDC `deadzone:zombie_type` para aplicar a regra do Corredor (5% fixo); nuvem do Explosivo pode aumentar risco; Lua de Sangue = mais golpes = mais infecção/sangramento.
- **Sanidade:** estar cercado por muitos zumbis (horda/Lua de Sangue) e em zonas tóxicas derruba sanidade (doc 08).
- **Saqueador/Medicina:** loot das zonas alimenta a economia de itens.
- **Performance:** todos os spawns respeitam caps; mutantes/blood-moon mobs são marcados para limpeza controlada.
