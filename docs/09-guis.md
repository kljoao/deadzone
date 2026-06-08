# 09 — GUIs Interativas

Menus baseados em inventários customizados para **seleção de classe** e **árvore de habilidades**, além de futuras telas (bancada médica, estatísticas).

## Framework de GUI

Construímos um mini-framework reutilizável em `core/gui`:

```java
public abstract class Menu {
    protected Inventory inventory;
    public abstract String title();
    public abstract int size();              // múltiplo de 9
    public abstract void build(Player viewer);   // popula slots
    public abstract void onClick(InventoryClickEvent e);
    public void open(Player p) { build(p); p.openInventory(inventory); }
}

public class MenuItem {
    ItemStack icon;
    Consumer<InventoryClickEvent> action;   // callback do clique
}
```

- Um **único** `MenuListener` registra `InventoryClickEvent`/`InventoryCloseEvent` e delega ao `Menu` aberto.
- **Sempre `e.setCancelled(true)`** em menus (impede o jogador de pegar os ícones).
- Identificar "este inventário é um menu nosso": comparar o `InventoryHolder` (implementar um `MenuHolder` custom é a forma robusta) — evita conflitar com baús normais.

```java
public class MenuHolder implements InventoryHolder {
    private final Menu menu;
    // getInventory() retorna o inventário do menu
}
// no listener: if (e.getInventory().getHolder() instanceof MenuHolder h) h.menu().onClick(e);
```

- Ícones com nome/lore via Adventure; estado (ex.: skill desbloqueada vs bloqueada) muda material/encantamento brilhante/lore.

## Menu de Seleção de Classe (`/classe`)

- Abre uma GUI com um ícone por classe (Médico, Saqueador, Bruto) + descrição na lore.
- Clique seleciona a classe (respeitando regras de troca do doc 06: primeira grátis, depois custo/cooldown).
- Confirmação para trocas pagas (segundo clique ou sub-menu "Confirmar?").
- Mostra a classe atual destacada.

Layout sugerido (27 slots):
```
. . . . . . . . .
. . M . S . B . .      M=Médico  S=Saqueador  B=Bruto
. . . . i . . . .      i = info/XP atual
```

## Árvore de Habilidades (`/skills`)

- GUI maior (54 slots) mostrando os nós de skill da classe do jogador.
- Cada nó (`SkillNode`) vira um ícone posicionado pelo campo `slot`.
- **Estados visuais:**
  - **Desbloqueada:** ícone "aceso" (ex.: com brilho de encantamento), lore "✔ Desbloqueada".
  - **Disponível** (pré-requisitos ok + XP suficiente): ícone normal, lore mostra custo e "Clique para desbloquear".
  - **Bloqueada** (faltam pré-requisitos): ícone cinza (vidro/barreira), lore "Requer: <pré-requisito>".
  - **Sem XP:** disponível mas lore em vermelho "XP insuficiente (X/Y)".
- Mostrar o **XP atual** num ícone fixo (ex.: slot central inferior).
- **Linhas de conexão** entre nós: simular com painéis de vidro entre slots (estético) — opcional no MVP.
- Clique num nó disponível → tenta desbloquear (debita XP, persiste, atualiza a GUI sem fechar).

Pseudo-fluxo de clique:
```java
void onClick(InventoryClickEvent e) {
    e.setCancelled(true);
    SkillNode node = nodeAtSlot(e.getRawSlot());
    if (node == null) return;
    SkillService.UnlockResult r = skillService.tryUnlock(player, node);
    switch (r) {
        case SUCCESS    -> { sound(success); rebuild(); }
        case NO_XP      -> message("XP insuficiente");
        case LOCKED     -> message("Pré-requisitos não atendidos");
        case ALREADY    -> { /* nada */ }
        case WRONG_CLASS-> message("Sua classe não pode usar esta skill");
    }
}
```

## Boas práticas / armadilhas

- **Anti-dupe:** cancelar todo clique e nunca confiar no item do slot como fonte de verdade — a ação vem do *slot/nó*, não do ItemStack que o jogador "pegou".
- **Shift-click / number keys / drag:** cancelar também esses (cobrir `InventoryClickEvent` com todos os `ClickType` e `InventoryDragEvent`).
- **Fechar com segurança:** limpar referências no `InventoryCloseEvent` se necessário (timers de GUI vivas).
- **Rebuild vs reopen:** ao atualizar (desbloqueou skill), atualizar os itens do inventário aberto em vez de reabrir (evita flicker).
- **Itens "fantasma":** preencher slots vazios com vidro cinza sem nome para visual limpo e para impedir interação acidental.

## Bancada Médica (crafting customizado) — núcleo, não pós-MVP

É o **método oficial de crafting** dos itens médicos/químicos (doc 05, opção escolhida). GUI que:
- Lista as receitas que **aquele jogador** pode fabricar (T1 para todos; T2/T3 só para Médico com Farmacologia Avançada — receitas que ele não pode nem aparecem).
- Mostra ingredientes na lore de cada receita; destaca o que falta no inventário.
- Ao clicar: valida → consome ingredientes → entrega o item, com **som + partícula** de sucesso (ou som de falha se faltar material).
- Aberta por um item/bloco "bancada" interagível ou comando dedicado.

## Feedback sonoro e visual (aplicar em todas as GUIs)

Para dar vida aos menus, padronizar:
- **Abrir menu:** som suave (ex.: `UI_BUTTON_CLICK` / `BLOCK_CHEST_OPEN`).
- **Clique válido / seleção:** clique curto + leve.
- **Sucesso** (desbloquear skill, craftar, escolher classe): som de êxito (ex.: `ENTITY_PLAYER_LEVELUP` / `BLOCK_NOTE_BLOCK_PLING`) + partículas no jogador.
- **Falha / bloqueado:** som grave (ex.: `BLOCK_NOTE_BLOCK_BASS` / `ENTITY_VILLAGER_NO`).
- **Hover/troca de página:** clique discreto.
- Centralizar os sons num enum/config (`gui.sounds`) para ajuste fácil e consistência.

## HUD lateral (Scoreboard) — PLANEJADO

Painel fixo na **lateral direita** da tela (via `Scoreboard`/sidebar do Bukkit, sem resource pack) mostrando o estado do jogador de forma persistente:
- **Infecção** (%), **Sanidade** (%), **Classe**, **XP**, **Dia/horário**, talvez **Sangrando** (sim/nível).
- Atualizado a cada segundo (pode plugar no `TickService`).
- **Objetivo:** reduzir o uso de BossBar para status contínuo — a BossBar fica só para coisas pontuais/canalizações (bandagem, derrubado), e os números do dia a dia ficam no scoreboard.
- Módulo sugerido: `modules/hud` com `HudConfig` (quais linhas exibir) + `HudService` (monta/atualiza o scoreboard por jogador).
- Implementar idealmente junto/depois da **Fase 4 (Sanidade)**, para já incluir a linha de sanidade.

## Futuras GUIs (pós-MVP)
- **Estatísticas/Perfil:** zumbis mortos, tempo sobrevivido, melhor "run" (já que tudo reseta na morte).
- **Loja/economia** (se o servidor tiver moeda).
