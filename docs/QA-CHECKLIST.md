# ✅ Deadzone — Checklist de QA

Teste manual sistema por sistema. Marque `[x]` quando passar. Anote bugs no fim.

> **Setup recomendado:** 2 contas (1 OP "admin", 1 não-OP "vítima") pra testar proteção/permissões.
> Itens: `/deadzone giveitem <id>`. Pra testar restrições como OP, **fique em sobrevivência** (o bypass é por criativo/permissão, não por OP).

---

## 0. Fundação / Persistência
- [ ] Entrar no servidor cria/carrega o perfil (sem erro no console).
- [ ] Mudanças (XP, infecção, sanidade, classe, skills) **persistem ao relogar**.
- [ ] **Morte real** → wipe total (classe→Nenhuma, XP/skills/infecção/sanidade zerados, itens dropados).
- [ ] Reiniciar o servidor mantém os perfis (autosave + flush no shutdown).

## 1. Infecção
- [ ] Apanhar de zumbi tem chance de infectar (`/deadzone infection get <jogador>` mostra %).
- [ ] `/deadzone infection set <jogador> 50` ajusta o medidor.
- [ ] Medidor sobe ao longo do tempo; estágios aplicam efeitos piores conforme sobe.
- [ ] Chegar a 100% → **morte por infecção** (pula o "Derrubado") + mensagem custom.
- [ ] Tomar dano já infectado → chance de agravar (+%) — não conta no tick de sangramento.

## 2. Sangramento & Ferida
- [ ] Golpe de zumbi pode iniciar sangramento (perde vida com o tempo, partículas vermelhas).
- [ ] **Bandagem** = canalização (imóvel ~5s, boss bar) → estanca; pode infeccionar a ferida.
- [ ] Tomar dano/trocar slot/dropar **cancela** a canalização (mas **veneno da ferida NÃO cancela**).
- [ ] **Ferida infeccionada:** título "⚠ Ferida Infeccionada" + linha no scoreboard + dano de veneno.
- [ ] **Álcool desinfetante + Bandagem** → cria **Bandagem Esterilizada** (qualquer um craft, sem ser Médico).
- [ ] Bandagem Esterilizada **cura a ferida** (usável mesmo sem estar sangrando).

## 3. Perna Quebrada
- [ ] Cair de ~8+ blocos pode quebrar a perna (lentidão); ~14+ quase garantido.
- [ ] **Tala** cura a perna quebrada.

## 4. Sanidade
- [ ] Medidor de sanidade no scoreboard (sem boss bar).
- [ ] Cai no escuro / sobe na luz e **dentro da própria base**.
- [ ] Sanidade baixa (<30) → alucinações (zumbis falsos/sons).

## 5. Estado "Derrubado" (revive)
- [ ] Dano letal (não-infecção) → cai imóvel/invulnerável com boss bar + timer.
- [ ] **Médico com skill Reanimação** revive (mira até 5 blocos).
- [ ] Timer acaba sem revive → morre de vez.
- [ ] **Relogar enquanto derrubado** → continua derrubado com o tempo restante (não escapa).
- [ ] Ficar offline até o tempo acabar → **morre ao entrar**.

## 6. Classes & Skills
- [ ] `/classe` abre o menu e troca de classe (Médico/Bruto/Saqueador).
- [ ] `/skills` abre a árvore; gastar XP libera skill.
- [ ] `/deadzone skill add/remove` e `/deadzone xp <jogador> <qtd>` funcionam.
- [ ] Skills da classe têm efeito (ex.: Bruto Resistência Viral, Saqueador Lockpicking).

## 7. Medicina & Itens (cada item faz o que promete?)
- [ ] **Bancada** (`/bancada`) abre; craft consome ingredientes; T2/T3 só Médico c/ Farmacologia.
- [ ] Analgésico, **Antídoto** (-10% infecção + suprime sintomas 90s).
- [ ] **Kit de Primeiros Socorros** (efeito esperado).
- [ ] **Seringa de Adrenalina** (conferir efeito — POSSÍVEL PLACEHOLDER).
- [ ] **Desfibrilador** (conferir revive/efeito — POSSÍVEL PLACEHOLDER).
- [ ] **Máscara de Gás** (efeito em zona tóxica).

## 8. Armas de Fogo (G17)
- [ ] Sacar tem delay (1.4s) com a arma escondida só na 1ª pessoa (visível na hotbar).
- [ ] **Botão direito atira**; munição **9mm** consome; recarrega.
- [ ] Projétil invisível com **rastro**; bullet drop a distância razoável.
- [ ] **Botão esquerdo = coronhada** → concussão no zumbi (cooldown ~8s, só 100% carregado).
- [ ] **HUD de munição** aparece; **tooltip** com stats (munição/dano/cadência/pente).
- [ ] Não quebra blocos com a arma.

## 9. Zumbis & Mundo
- [ ] Só zumbis hostis spawnam (config); **não queimam no sol**.
- [ ] DaySpawner gera zumbis de dia ao redor dos jogadores.
- [ ] Zumbis ~15% mais rápidos (config `world.yml`).
- [ ] `/deadzone mutant <tipo>` spawna mutante com efeitos próprios.

## 10. Eventos Dinâmicos
- [ ] `/deadzone bloodmoon start/stop/status` — Lua de Sangue intensifica horda.
- [ ] `/deadzone zone pos1/pos2/create/remove/list` — zona tóxica causa dano.
- [ ] Mutantes/Apocalipse escalam a dificuldade.

## 11. Atmosfera & Barulho
- [ ] Sons ambiente tocam com **intervalo** (sem spam); mais tensos perto de zumbis.
- [ ] Barulho (tiros/ações) atrai zumbis.

## 12. Cerco & Barricadas
- [ ] Zumbis **só quebram bloco se 2+** atacarem o mesmo bloco.
- [ ] Barricada reforçável; vida do bloco visível ao **agachar**.
- [ ] **O cerco funciona DENTRO da base** (proteção não bloqueia o zumbi).

## 13. Bases / Claims — núcleo do sistema
**Criação**
- [ ] `/deadzone giveitem livro_base` → segurar mostra preview 20x20 (verde/vermelho), começa a 5 blocos, segue a câmera.
- [ ] Clique direito trava (dourado) → `/confirmar base` cria a base + dá **4 baús** + consome o livro.
- [ ] Desnível calculado (base cobre o terreno).

**Núcleo (indestrutível)**
- [ ] Não quebra em **sobrevivência**, **criativo**, **TNT/creeper**, **pistão**, **fogo**.
- [ ] Clique direito (dono) abre a **GUI da base**.

**Proteção & Membros**
- [ ] Estranho **não** quebra/coloca blocos nem abre baús/portas na sua base.
- [ ] GUI → **Membros → Adicionar** (jogador online) → permissões (quebrar/baús/portas) ligam/desligam.
- [ ] Membro com permissão faz aquilo; sem permissão, é negado.

**Zona segura / integração**
- [ ] Zumbis **não spawnam** dentro da base.
- [ ] Ficar na base **recupera sanidade**; **respawn** no núcleo; **título** ao entrar/sair.

**Evolução**
- [ ] Evoluir → **Subir altura** (+4, até 4x) custa XP e aumenta o teto.
- [ ] Evoluir → **Aumentar limite de baús** (+2, entrega os baús).

**Baús com senha (PIN)**
- [ ] Colocar baú → **teclado 0–9**, digita PIN **2x** (passo 1 e 2 claros, com sons).
- [ ] Fechar sem confirmar → **baú volta** pro inventário.
- [ ] **Baú duplo** (encostar no seu) → **herda a senha**, sem teclado.
- [ ] Dono/membros abrem **direto**; estranho digita o PIN → **autorizado pra sempre**.
- [ ] Só o **dono quebra**; baú trancado **imune a explosão**.
- [ ] **Limite** respeitado (4 + upgrades) — bloqueia o excedente.

**Utilidades**
- [ ] `/minhabase` mostra as 4 extremidades por 10s (some sozinho, não dá pra quebrar).
- [ ] **Remover base** (GUI, com confirmação) → **apaga a construção** (terreno fica) + **deleta itens/baús** + devolve o **Livro de Base**.

## 14. HUD / Scoreboard
- [ ] Mostra Infecção, Sanidade, Sangramento/Ferida, Classe, XP, Dia (alinhado).
- [ ] Título mostra a **logo** (resource pack) — ou texto se `title-logo: false`.

## 15. Permissões (LuckPerms)
- [ ] Comandos de jogador abertos por padrão; dá pra **trancar** por grupo (`deadzone.command.*`).
- [ ] **Admin menor**: dar só `deadzone.admin.reload` → o jogador só usa reload (tab-complete filtra).
- [ ] `deadzone.admin` (pai) concede todos os subcomandos.
- [ ] `deadzone.claim.bypass` permite construir fora da base.

## 16. Resource Pack
- [ ] Itens custom renderizam (crowbar, G17, meds, livro, etc.).
- [ ] Logo aparece na **tab** e no **scoreboard**.
- [ ] `F3+T` recarrega o pack em dev.

---

## 🐞 Bugs encontrados
| # | Sistema | O que aconteceu | Passos pra reproduzir | Prioridade |
|---|---------|-----------------|------------------------|------------|
| 1 | | | | |
| 2 | | | | |

## 📝 Notas de balanceamento (valores a revisar no playtest)
- Chance de infecção por mordida (25%)
- Velocidade do zumbi (1.15)
- Custos de XP dos upgrades de base
- Dano/cadência da G17
- Duração do "Derrubado"
- Spawn de zumbis (intervalo/quantidade)
