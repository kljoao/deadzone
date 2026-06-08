# 08 — Sistema de Sanidade

Camada psicológica da sobrevivência. A sanidade (`0–100`, 100 = saudável) cai sob estresse e se recupera em segurança. Sanidade baixa **penaliza** o jogador, criando pressão para não jogar isolado nem no escuro.

> **Convenção:** adotamos `sanity` onde **alto = bom**. (O texto original falava "quanto mais alta, pior" — invertemos para a convenção mais comum de "sanidade alta = saudável". Se preferir o oposto, é só inverter os sinais; mantenha a escolha consistente em todo o código e nas mensagens.)

## Fatores que **reduzem** sanidade

| Fator | Detalhe |
|-------|---------|
| **Escuridão** | Em ambiente com nível de luz baixo, **a menos que** esteja segurando uma fonte de luz na mão (tocha, lanterna) ou esteja em área iluminada. |
| **Cercado por zumbis** | Quantidade de zumbis hostis num raio acima de um limiar; quanto mais, mais rápido cai. |
| (opcional) **Infecção alta** | Medo da morte iminente acelera a queda. |
| (opcional) **Zonas tóxicas / Lua de Sangue** | Ambientes de horror intensificam a perda. |

### Detalhe da "escuridão" e da fonte de luz
- Checar nível de luz do bloco do jogador (`block.getLightLevel()` / luz combinada) abaixo de um limiar (ex.: < 7).
- **Exceção (não está no escuro)** se:
  - Segura item que emite luz na **mão principal ou secundária** (tocha, lanterna, jack o'lantern, glowstone, shroomlight… lista em `sanity.yml`), **ou**
  - está em área com luz suficiente (iluminação artificial da base).
- Isso atende ao pedido: "avaliar se ele está usando algo para iluminar na outra mão; se sim, não está no escuro".

## Fatores que **aumentam** sanidade

| Fator | Detalhe |
|-------|---------|
| **Estar na base (segura e iluminada)** | Dentro da região de base do jogador, **com iluminação adequada**, sanidade sobe. |
| **Companhia** | Perto de outros jogadores, recupera (sobreviver em grupo conforta). |
| **Medicação** | Itens (Analgésico, Seringa de Adrenalina) dão alívio/boost imediato (doc 05). |
| **Luz/dia** | Estar na luz / durante o dia desacelera a perda ou recupera devagar. |

### O que é "a base do jogador"?
**Decisão:** o servidor usará um **plugin de claim/proteção de base** (doc 11). A sanidade então integra com ele:
- "Estar na base" = estar **dentro de um claim do qual o jogador é dono/membro** (consultado via API do plugin de claim, ex.: GriefPrevention/Lands).
- Para contar como recuperação, o claim precisa ter **iluminação adequada** no entorno do jogador (checagem de luz local).

Fallback (enquanto a integração não existe ou se o plugin não for adotado): comando `/base set` que registra um ponto/raio de "lar" no profile. Mantemos a abstração `BaseProvider` para trocar a fonte (claim plugin vs comando interno) sem mexer no `SanityManager`.

## Efeitos da sanidade baixa

Quanto **menor** a sanidade, piores os efeitos (faixas configuráveis):

| Faixa | Efeito |
|-------|--------|
| 75–100 | Saudável, sem penalidade. |
| 50–75 | Leve: pequena redução de dano corpo a corpo. |
| 25–50 | Médio: `SLOWNESS` leve; redução maior de dano corpo a corpo; sons/sussurros ocasionais. |
| 0–25 | Severo: `SLOWNESS` perceptível; dano corpo a corpo bem reduzido; efeitos visuais (`NAUSEA` raro), sons assustadores, talvez alucinações (partículas/fake mobs — avançado). |

- **Redução de dano corpo a corpo:** ao desferir ataque (`EntityDamageByEntityEvent`, damager = player), multiplicar o dano por um fator derivado da sanidade (ex.: `damage *= sanityMeleeFactor(sanity)`).
- **Lentidão:** aplicar/atualizar `SLOWNESS` conforme faixa (reaplicar periodicamente; remover ao recuperar).

## Implementação

```
SanityManager (plugado no TickService, 1s)
  tick(profile):
    Player p = ...
    double delta = 0;
    delta += darkness(p);            // negativo se no escuro sem luz
    delta += zombiePressure(p);      // negativo conforme nº de zumbis perto
    delta += baseRecovery(p);        // positivo se base iluminada
    delta += companyRecovery(p);     // positivo se perto de players
    delta += daylightRecovery(p);    // pequeno positivo de dia/na luz
    profile.setSanity(clamp(profile.getSanity() + delta, 0, 100));
    applyEffects(p, profile.getSanity());   // slowness/visuais por faixa
```

- Cada fator retorna um valor por segundo (positivo/negativo), todos vindos de `sanity.yml`.
- `applyEffects` é idempotente (recalcula a cada tick a faixa atual; só reaplica efeitos quando muda de faixa, para não floodar).
- A **redução de dano melee** é consultada no listener de ataque, lendo `profile.getSanity()`.

## `sanity.yml` (exemplo)

```yaml
start: 100
min: 0
max: 100

factors:
  darkness:
    light-threshold: 7
    per-second: -0.15
    light-items: [TORCH, LANTERN, SOUL_LANTERN, JACK_O_LANTERN, GLOWSTONE, SHROOMLIGHT, SEA_LANTERN]
  zombie-pressure:
    radius: 12
    threshold: 3          # a partir de 3 zumbis começa a pesar
    per-zombie-per-second: -0.05
  infection-fear:
    enabled: true
    per-second-at-100: -0.10

recovery:
  base:
    require-light: true
    light-threshold: 8
    per-second: 0.25
  company:
    radius: 16
    per-second: 0.10
  daylight:
    per-second: 0.05

effects:
  - { below: 75, melee-multiplier: 0.90 }
  - { below: 50, melee-multiplier: 0.75, slowness: 0 }
  - { below: 25, melee-multiplier: 0.55, slowness: 1, visual: true }
```

## Comandos (admin/debug)
- `/deadzone sanity get <player>`
- `/deadzone sanity set <player> <0-100>`
- `/base set` / `/base remove` (jogador define seu lar)

## Integrações
- **Classes:** Diagnóstico Rápido do Médico mostra sanidade do aliado.
- **Medicina:** itens dão boost de sanidade.
- **Eventos:** Lua de Sangue e zonas tóxicas pioram a sanidade.
- **Infecção:** (opcional) infecção alta acelera perda.
