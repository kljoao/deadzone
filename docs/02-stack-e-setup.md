# 02 — Stack & Setup

## Decisões de stack

| Item | Escolha | Notas |
|------|---------|-------|
| Plataforma | **Paper 1.21.1** | API mais rica e performática; compatível com Spigot/Bukkit. Alvo fixado em **1.21.1** (`paper-api` 1.21.1-R0.1-SNAPSHOT). |
| Java | **21 (LTS)** | Obrigatório para a linha 1.21. Compilar com `release 21`. |
| Build | **Maven** | `pom.xml` declarativo; `maven-shade-plugin` para empacotar dependências. |
| Banco | **SQLite** | Arquivo `database.db` na pasta do plugin. Acesso via JDBC + **HikariCP** (pool). DAO isola o SQL para futura migração a MySQL. |
| Texto/UI | **Adventure API** (já no Paper) | Componentes para action bar, títulos, lore, mensagens. Sem `ChatColor` legado. |
| NBT/itens | **PersistentDataContainer** | API estável do Bukkit; evita NBT reflexivo frágil. |

> ⚠️ **Versão exata:** o alvo é **1.21.1**. O plugin compila contra `paper-api:1.21.1-R0.1-SNAPSHOT` e o resource pack usa o formato antigo de `overrides` (pack_format 34). Se um dia migrar para 1.21.4+, troque o resource pack para o sistema novo (`assets/minecraft/items/`).

## `pom.xml` — esqueleto

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.deadzone</groupId>
  <artifactId>deadzone</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <id>papermc</id>
      <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
  </repositories>

  <dependencies>
    <!-- Paper API (provided: já existe no servidor) -->
    <dependency>
      <groupId>io.papermc.paper</groupId>
      <artifactId>paper-api</artifactId>
      <version>1.21.1-R0.1-SNAPSHOT</version> <!-- alvo: 1.21.1 -->
      <scope>provided</scope>
    </dependency>

    <!-- Pool de conexões (shaded para dentro do jar) -->
    <dependency>
      <groupId>com.zaxxer</groupId>
      <artifactId>HikariCP</artifactId>
      <version>5.1.0</version>
    </dependency>

    <!-- Driver SQLite (shaded) -->
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.46.x</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.x</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <relocations>
                <!-- relocar para evitar conflito com outros plugins -->
                <relocation>
                  <pattern>com.zaxxer.hikari</pattern>
                  <shadedPattern>com.deadzone.libs.hikari</shadedPattern>
                </relocation>
              </relocations>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

> **Por que relocar?** Se outro plugin também empacotar HikariCP em versão diferente, haveria conflito de classes. Relocar para `com.deadzone.libs.*` isola.

## `plugin.yml` (gerado em `src/main/resources`)

```yaml
name: Deadzone
version: ${project.version}
main: com.deadzone.DeadzonePlugin
api-version: '1.21'
authors: [SeuNome]
description: Plugin oficial do servidor de sobrevivência zumbi.

commands:
  deadzone:
    description: Comando raiz (admin/debug)
    usage: /deadzone <subcomando>
  classe:
    description: Abre o menu de seleção de classe
  skills:
    description: Abre a árvore de habilidades

permissions:
  deadzone.admin:
    description: Acesso a comandos administrativos
    default: op
```

> Em Paper moderno você pode usar `paper-plugin.yml` com bootstrap dedicado, mas `plugin.yml` clássico é mais simples e suficiente para começar.

## Estrutura de arquivos de configuração

```
plugins/Deadzone/
├── config.yml              # globais (debug, autosave, idioma)
├── messages.yml            # todas as strings exibidas
├── infection.yml           # parâmetros do sistema de infecção
├── sanity.yml              # parâmetros de sanidade
├── classes.yml             # definição de classes, skills, custos de XP
├── items.yml               # itens customizados e receitas
├── events.yml              # lua de sangue, zonas tóxicas, mutantes
├── zones.yml               # regiões tóxicas (coords) - gerado/editado por comando
└── database.db             # SQLite (criado automaticamente)
```

## Ambiente de desenvolvimento

- **IDE:** IntelliJ IDEA (melhor suporte a Minecraft/Maven) ou VS Code com extensão Java.
- **JDK:** Temurin/Adoptium 21.
- **Servidor de teste local:** baixar o `paper-1.21.x.jar`, rodar uma vez para gerar `eula.txt`, aceitar, e apontar o build para copiar o jar em `plugins/`.
- **Loop de dev sugerido:**
  1. `mvn package` gera `target/deadzone-0.1.0-SNAPSHOT.jar`.
  2. Copiar para `server/plugins/`.
  3. `reload`/`restart` do servidor (preferir restart; `/reload` é problemático).
- **Opcional (qualidade de vida):** plugin de hot-reload de dev, ou um script PowerShell que builda + copia + reinicia o servidor de teste.

## Convenções de código

- Pacote raiz `com.deadzone`.
- Nomes de classe descritivos (`InfectionManager`, não `IM`).
- Constantes de gameplay **nunca** hardcoded — sempre via `ConfigManager`.
- `NamespacedKey` centralizados em `core/item/ItemKeys.java` (ex.: `new NamespacedKey(plugin, "infection_chance")`).
- Logging com níveis (`INFO` boot, `FINE`/debug atrás de flag `debug: true`).
