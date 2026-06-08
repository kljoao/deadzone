# Deadzone — Resource Pack

Modelos e texturas dos itens customizados. Feito **do zero** (sem BlockBench): texturas geradas por código (`tools/TextureGen.java`) e modelos em JSON de cubos.

> **Alvo: Minecraft 1.21.1** — usa o sistema antigo de `overrides` (o formato `assets/minecraft/items/` só existe a partir do 1.21.4) e `pack_format 34`.

## Conteúdo atual
- **Pé de Cabra (crowbar)** → `CustomModelData 2002` no item base `minecraft:stick`.
  - Modelo: `assets/deadzone/models/item/pe_de_cabra.json`
  - Textura: `assets/deadzone/textures/item/pe_de_cabra.png`
  - Override: `assets/minecraft/models/item/stick.json` (predicate `custom_model_data`)

## Como testar (local, sem hospedar)
1. Empacote a pasta `resourcepack/` num `.zip` (o `pack.mcmeta` precisa ficar na **raiz** do zip).
   - Já existe um zip pronto em `dist/deadzone-resourcepack.zip` (gerado pelo build).
2. Copie o `.zip` para `.minecraft/resourcepacks/`.
3. No Minecraft: Opções → Resource Packs → ative o "Deadzone".
4. No jogo: `/deadzone giveitem pe_de_cabra` → o graveto vira a crowbar.

## Como servir automaticamente (produção)
- **Via server.properties:** hospede o `.zip` numa URL pública e defina `resource-pack=<url>` e `resource-pack-sha1=<sha1>`.
- **Via plugin (recomendado):** em `config.yml` do Deadzone, seção `resource-pack`, defina `enabled: true`, `url` e `sha1`. O plugin envia o pacote ao jogador no login.

## Notas
- `pack_format` está em **34** (1.21 / 1.21.1). Se migrar para 1.21.4+, troque para o sistema novo (`assets/minecraft/items/`) e `pack_format` 46+.
- Para adicionar mais itens (rádio = 2001, remédios = 1001–1006, etc.), crie o modelo/textura e mapeie o `CustomModelData` no override do item base correspondente.
