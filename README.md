# Deadzone

Plugin de **sobrevivência zumbi realista** para servidores Minecraft (Paper), inspirado em *Project Zomboid*. O jogador não enfrenta só a fome e a noite: lida com **infecção viral sem cura**, **sangramento**, **sanidade**, **eventos dinâmicos** e uma **árvore de habilidades por classe** — e, ao morrer, **perde tudo**.

> **Stack:** Paper 1.21.1 · Java 21 · Maven · SQLite (HikariCP)

## Funcionalidades

- **Infecção** — golpes de zumbi podem infectar (25%, sem cura). O medidor sobe ao longo de 1h real até a morte; tomar dano infectado pode agravar. Ao chegar a 100%, o jogador colapsa e morre.
- **Sangramento** — ferimentos causam dano periódico que piora com os golpes e só é estancado com **bandagem** (canalização imóvel).
- **Sanidade** — cai no escuro e cercado de zumbis; recupera com luz, base, companhia e remédios. Sanidade baixa reduz o dano corpo a corpo e dá lentidão.
- **Classes & árvore de habilidades** — Médico, Bruto e Saqueador, cada um com skills próprias compradas com XP.
- **Estado "Derrubado" & revive** — em vez de morrer, o jogador cai derrubado por 30s; um Médico pode revivê-lo. Sem revive (ou morte por infecção) → **wipe total** (classe, skills, XP, itens).
- **Eventos dinâmicos** — Lua de Sangue, zonas tóxicas (com Máscara de Gás) e zumbis mutantes (Corredor, Tanque, Explosivo).
- **Itens customizados** — fabricados numa Bancada Médica própria; suporte a resource pack (modelos/texturas).

## Requisitos

- Servidor **Paper 1.21.1**
- **Java 21**

## Build

Requer JDK 21 e Maven.

```bash
mvn clean package
```

O jar final fica em `target/Deadzone-0.1.0-SNAPSHOT.jar`. As dependências (HikariCP, driver SQLite) já vão embutidas.

## Instalação

1. Copie o jar para a pasta `plugins/` do servidor.
2. Inicie o servidor — os arquivos de configuração são gerados em `plugins/Deadzone/`.
3. (Opcional) Resource pack com os modelos/texturas dos itens: veja [`resourcepack/`](resourcepack/).

## Comandos

| Comando | Descrição |
|---------|-----------|
| `/classe` | Menu de seleção de classe |
| `/skills` | Árvore de habilidades |
| `/bancada` | Bancada Médica (crafting) |
| `/base set\|remove\|info` | Define seu "lar" (recuperação de sanidade) |
| `/deadzone ...` | Comandos administrativos (requer `deadzone.admin`) |

Subcomandos de `/deadzone`: `info`, `reload`, `profile`, `xp`, `skill`, `giveitem`, `infection`, `zone`, `bloodmoon`, `mutant`, `lock`, `unlock`.

## Itens

`bandagem`, `analgesico`, `antidoto`, `seringa_adrenalina`, `kit_primeiros_socorros`, `desfibrilador`, `gas_mask`, `radio_frequencia`, `pe_de_cabra`.

Para obter durante testes: `/deadzone giveitem <id>`.

## Configuração

Tudo é ajustável por YAML em `plugins/Deadzone/`:

| Arquivo | Conteúdo |
|---------|----------|
| `config.yml` | Globais (banco, autosave, wipe na morte, resource pack) |
| `infection.yml` | Infecção e colapso |
| `bleeding.yml` | Sangramento |
| `items.yml` | Itens e receitas da bancada |
| `classes.yml` | Classes, XP, skills, derrubado |
| `sanity.yml` | Sanidade |
| `world.yml` | Regras de spawn (só zumbis, anti-baby, spawn diurno) |
| `events.yml` | Mutantes e Lua de Sangue |
| `zones.yml` | Zonas tóxicas |
| `messages.yml` | Textos |

Após editar, use `/deadzone reload`.

## Documentação

O design completo de cada sistema está em [`docs/`](docs/).

## Contribuindo

Veja [CONTRIBUTING.md](CONTRIBUTING.md).

## Licença

[MIT](LICENSE).
