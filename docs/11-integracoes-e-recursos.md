# 11 — Integrações Externas & Recursos

Plugins de terceiros que o servidor usará junto do Deadzone, e o plano de **recursos visuais** (resource pack / texturas).

## Princípios de integração

- **`softdepend`** no `plugin.yml`: o Deadzone funciona mesmo sem o plugin externo, mas integra se ele estiver presente.
- **Camada de adaptação:** cada integração fica atrás de uma interface (`BaseProvider`, `AnimationProvider`…) com uma implementação "vazia/fallback". Trocar de plugin externo não toca na lógica de gameplay.
- Checar presença com `Bukkit.getPluginManager().getPlugin("Nome")` antes de chamar a API.

```yaml
# plugin.yml
softdepend: [GriefPrevention, Lands, ModelEngine]
```

## 1. Proteção de base (claim)

Usado pela **sanidade** (doc 08): "estar na base" = estar num claim do jogador.

| Plugin | Modelo | Notas |
|--------|--------|-------|
| **GriefPrevention** | Claims com "pá de ouro" | Grátis, consagrado, API simples (`getClaimAt`). Ótimo padrão. |
| **Lands** | Claims com GUI rica | Pago, muito polido, API boa. Excelente UX. |
| **WorldGuard** | Regiões definidas por admin | Não é self-service de jogador; melhor para zonas (doc 07) que para "base do jogador". |
| **Towny** | Cidades/nações | Foco social/territorial; pesado para o nosso caso. |

**Recomendação:** **GriefPrevention** (grátis) para começar, ou **Lands** se quiser a melhor experiência de GUI. A integração:

```java
interface BaseProvider { boolean isInOwnBase(Player p); }
// GriefPreventionBaseProvider: claim = GriefPrevention.instance.dataStore.getClaimAt(loc, ...)
//   e claim.ownerID == p.getUniqueId() (ou é membro)
// FallbackBaseProvider: usa /base set interno (doc 08)
```

> WorldGuard pode ser usado **em paralelo** para demarcar **zonas tóxicas** (doc 07), em vez de coordenadas em `zones.yml` — também via integração opcional.

## 2. Animações / modelos de zumbi customizados

Você citou **Fresh Animations**. Importante entender a natureza disso para não ter surpresa:

- **Fresh Animations é um *resource pack* client-side.** Ele anima os mobs **vanilla**, mas depende de **CEM (Custom Entity Models)**, que o cliente só entende com **OptiFine** *ou* os mods **EMF (Entity Model Features) + ETF (Entity Texture Features)** (Fabric/Forge). Um cliente "puro" (vanilla sem mods) **não** renderiza essas animações, mesmo recebendo o pack do servidor.
  - Ou seja: dá para usar, mas **cada jogador precisa ter OptiFine/EMF+ETF**. Bom para comunidades técnicas; atrito para o público geral.
- **Se você quer modelos/animações de zumbi 100% controlados pelo servidor, que todos vejam sem instalar mod**, o padrão da indústria é o **ModelEngine (MEG)** — plugin (pago) que anexa modelos do **Blockbench** (com animações) a entidades. O jogador só precisa do **resource pack** (que o servidor envia automaticamente), **sem mod client-side**.

**Recomendação:**
- Para um servidor com mecânicas tão custom (mutantes, bosses), **ModelEngine** é o caminho mais robusto — entrega Corredor/Tanque/Explosivo/bosses com visuais e animações próprios, server-side.
- Fresh Animations pode ser **opcional/extra** para quem usa OptiFine, ou inspiração de estilo.
- Camada de adaptação: `MutantManager` aplica o tipo (PDC + atributos) independentemente do visual; um `AnimationProvider` (ModelEngine ou nenhum) cuida só da aparência. Sem ModelEngine, caímos no visual vanilla diferenciado (equipamento, baby zombie, partículas).

## 3. Resource pack (texturas dos itens)

- Itens custom usam **`CustomModelData`** (ou **item models**, 1.21.4+) apontando para texturas no resource pack.
- O servidor pode **enviar o resource pack automaticamente** (`server-resource-pack` / API de resource pack do Paper) — itens 2D não exigem mod no cliente.
- Organizar IDs por categoria (ver doc 05): remédios 1000+, armas 2000+, munição 3000+, misc 4000+.

## 4. Texturas geradas por IA (sua pergunta sobre o ChatGPT)

**Resposta curta: sim, vale a pena — mas como ponto de partida, não como saída final.** Pontos práticos:

**Onde a IA ajuda bem:**
- **Conceito/estilo** rápido (paleta, mood, ideias de design).
- **Ícones 2D de itens** (remédios, munição, miscelânea) — especialmente se você usar texturas em **resolução maior** (ex.: 32×32 ou 64×64 via custom model data), onde detalhe da IA cabe melhor que no 16×16 vanilla.

**Onde a IA atrapalha (planeje retrabalho):**
- **Pixel art 16×16:** geradores produzem imagem detalhada/ruidosa; reduzir para 16×16 costuma ficar "borrado". Precisa de limpeza manual (redesenho pixel a pixel) para ficar nítido.
- **Fundo/transparência:** a IA quase sempre adiciona fundo/sombra; você terá que **recortar o alpha** item por item.
- **Consistência de estilo:** cada geração varia iluminação/paleta. Para um set coeso (dezenas de itens), defina um **guia de estilo** (paleta fixa, direção de luz, resolução, "outline sim/não") e gere tudo com o **mesmo prompt-base**.
- **Armas em 3D:** textura sozinha não vira modelo 3D. Para armas com volume você modela no **Blockbench** (e a IA pode no máximo ajudar na *texture sheet*, com UV mapping manual).

**Workflow recomendado:**
1. Definir **guia de estilo** (resolução-alvo, paleta, luz, contorno).
2. Gerar conceitos com IA usando prompt consistente.
3. Importar no editor (Aseprite/Photoshop/GIMP): **recortar fundo**, **unificar paleta**, **redimensionar/limpar**.
4. Itens "herói" (armas principais, bosses) → considerar arte manual ou Blockbench para qualidade.
5. Montar o resource pack, mapear `CustomModelData`, testar in-game e iterar.

> Resumo: IA acelera **ideação e ícones 2D em resolução maior**; reserve tempo de **limpeza manual** e um **guia de estilo** para coesão. Para 3D (armas), Blockbench é indispensável.

## Resumo de dependências externas

| Necessidade | Solução recomendada | Tipo |
|-------------|---------------------|------|
| Proteção de base (sanidade) | GriefPrevention (ou Lands) | Plugin, softdepend |
| Zonas/regiões admin | WorldGuard (opcional) | Plugin, softdepend |
| Modelos/animações de zumbi server-side | ModelEngine (MEG) | Plugin (pago) + resource pack |
| Animações client-side (opcional) | Fresh Animations + OptiFine/EMF+ETF | Resource pack client-side |
| Texturas de itens | Resource pack próprio (envio automático) | Server resource pack |
