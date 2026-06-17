package com.deadzone;

import com.deadzone.command.DeadzoneCommand;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.config.Messages;
import com.deadzone.core.database.Database;
import com.deadzone.core.database.SchemaManager;
import com.deadzone.core.database.dao.PlayerProfileDao;
import com.deadzone.core.database.dao.SqlPlayerProfileDao;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.entity.ZombieRadar;
import com.deadzone.core.gui.MenuListener;
import com.deadzone.core.item.ItemKeys;
import com.deadzone.core.item.ItemRegistry;
import com.deadzone.core.item.ItemUseListener;
import com.deadzone.core.profile.ProfileManager;
import com.deadzone.core.resourcepack.ResourcePackListener;
import com.deadzone.core.scheduler.TickService;
import com.deadzone.modules.classes.ClassManager;
import com.deadzone.modules.classes.command.ClassCommand;
import com.deadzone.modules.classes.command.SkillsCommand;
import com.deadzone.modules.infection.InfectionConfig;
import com.deadzone.modules.infection.InfectionManager;
import com.deadzone.modules.apocalypse.PlayerZombieListener;
import com.deadzone.modules.atmosphere.AtmosphereManager;
import com.deadzone.modules.economy.EconomyCommand;
import com.deadzone.modules.economy.EconomyDao;
import com.deadzone.modules.economy.EconomyManager;
import com.deadzone.modules.economy.VaultEconomyProvider;
import com.deadzone.core.chat.ChatFormatListener;
import com.deadzone.modules.bounty.BountyCommand;
import com.deadzone.modules.bounty.BountyListener;
import com.deadzone.modules.bounty.BountyManager;
import com.deadzone.modules.clan.ClanChatCommand;
import com.deadzone.modules.clan.ClanCombatListener;
import com.deadzone.modules.clan.ClanCommand;
import com.deadzone.modules.clan.ClanGlowService;
import com.deadzone.modules.clan.ClanManager;
import com.deadzone.modules.clan.ClanSymbolService;
import com.deadzone.modules.clan.ClanTopCommand;
import com.deadzone.modules.daily.DailyCommand;
import com.deadzone.modules.daily.DailyRewardManager;
import com.deadzone.modules.stats.StatsCommand;
import com.deadzone.modules.stats.StatsListener;
import com.deadzone.modules.shop.ShopCommand;
import com.deadzone.modules.shop.ShopManager;
import com.deadzone.modules.loot.LootCommand;
import com.deadzone.modules.loot.LootListener;
import com.deadzone.modules.loot.LootManager;
import com.deadzone.modules.events.EventsManager;
import com.deadzone.modules.firearms.FirearmManager;
import com.deadzone.modules.hud.HudService;
import com.deadzone.modules.medicine.MedicineManager;
import com.deadzone.modules.claim.ClaimManager;
import com.deadzone.modules.claim.ConfirmarBaseCommand;
import com.deadzone.modules.claim.MinhaBaseCommand;
import com.deadzone.modules.noise.NoiseManager;
import com.deadzone.modules.siege.SiegeManager;
import com.deadzone.modules.medicine.bench.BenchCommand;
import com.deadzone.modules.medicine.bench.CraftingRedirectListener;
import com.deadzone.modules.sanity.SanityManager;
import com.deadzone.modules.world.DisabledBlocksListener;
import com.deadzone.modules.world.WorldConfig;
import com.deadzone.modules.world.WorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Classe principal: bootstrap e ciclo de vida do plugin. */
public final class DeadzonePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private Messages messages;
    private Database database;
    private ProfileManager profileManager;
    private EconomyManager economyManager;
    private BountyManager bountyManager;
    private DailyRewardManager dailyRewardManager;
    private ClanManager clanManager;
    private ShopManager shopManager;
    private ClanGlowService clanGlowService;
    private ClanSymbolService clanSymbolService;
    private LootManager lootManager;
    private TickService tickService;
    private ItemRegistry itemRegistry;

    private InfectionConfig infectionConfig;
    private InfectionManager infectionManager;
    private ZombieRadar zombieRadar;

    private WorldConfig worldConfig;
    private WorldManager worldManager;

    private MedicineManager medicineManager;
    private ClassManager classManager;
    private SanityManager sanityManager;
    private EventsManager eventsManager;
    private HudService hudService;
    private AtmosphereManager atmosphereManager;
    private NoiseManager noiseManager;
    private FirearmManager firearmManager;
    private SiegeManager siegeManager;
    private ClaimManager claimManager;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        ItemKeys.init(this);
        EntityKeys.init(this);
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.messages = new Messages(this, configManager);

        try {
            this.database = new Database(this, configManager);
            this.database.connect();
            new SchemaManager(database).migrate();
        } catch (Exception e) {
            getLogger().severe("Falha ao iniciar o banco de dados: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerProfileDao dao = new SqlPlayerProfileDao(database);
        this.profileManager = new ProfileManager(this, dao, configManager);
        this.profileManager.init();

        this.economyManager = new EconomyManager(this, new EconomyDao(database));
        this.economyManager.enable();

        this.bountyManager = new BountyManager(this, configManager);
        this.bountyManager.enable();
        getServer().getPluginManager().registerEvents(new BountyListener(bountyManager), this);

        this.clanManager = new ClanManager(this, configManager);
        this.clanManager.enable();
        getServer().getPluginManager().registerEvents(new ClanCombatListener(clanManager), this);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this), this);
        this.clanGlowService = new ClanGlowService(this, clanManager);
        this.clanGlowService.enable();
        this.clanSymbolService = new ClanSymbolService(this, clanManager);
        this.clanSymbolService.enable();

        this.tickService = new TickService(this, profileManager);
        this.tickService.start();

        this.itemRegistry = new ItemRegistry();

        getServer().getPluginManager().registerEvents(new ItemUseListener(itemRegistry), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingRedirectListener(this), this);
        getServer().getPluginManager().registerEvents(new DisabledBlocksListener(), this);

        this.infectionConfig = new InfectionConfig(this, configManager);
        this.infectionManager = new InfectionManager(this, infectionConfig);
        this.infectionManager.enable(tickService);

        // Cache de "zumbis perto" compartilhado por Sanidade/Atmosfera (1 scan/player/seg).
        this.zombieRadar = new ZombieRadar(this);
        this.zombieRadar.enable();

        this.worldConfig = new WorldConfig(this, configManager);
        this.worldManager = new WorldManager(this, worldConfig);
        this.worldManager.enable();

        this.medicineManager = new MedicineManager(this, configManager);
        this.medicineManager.enable(tickService);

        this.classManager = new ClassManager(this, configManager);
        this.classManager.enable(tickService);

        this.sanityManager = new SanityManager(this, configManager);
        this.sanityManager.enable(tickService);

        this.eventsManager = new EventsManager(this, configManager);
        this.eventsManager.enable(tickService);

        this.hudService = new HudService(this, configManager);
        this.hudService.enable();

        this.atmosphereManager = new AtmosphereManager(this, configManager);
        this.atmosphereManager.enable(tickService);

        this.noiseManager = new NoiseManager(this, configManager);
        this.noiseManager.enable(tickService);

        this.firearmManager = new FirearmManager(this, configManager);
        this.firearmManager.enable(tickService);

        this.siegeManager = new SiegeManager(this, configManager);
        this.siegeManager.enable();

        this.claimManager = new ClaimManager(this, configManager);
        this.claimManager.enable();

        getServer().getPluginManager().registerEvents(new PlayerZombieListener(this), this);

        PluginCommand deadzone = getCommand("deadzone");
        if (deadzone != null) {
            DeadzoneCommand executor = new DeadzoneCommand(this);
            deadzone.setExecutor(executor);
            deadzone.setTabCompleter(executor);
        }
        PluginCommand bancada = getCommand("bancada");
        if (bancada != null) {
            bancada.setExecutor(new BenchCommand(this));
        }
        PluginCommand classe = getCommand("classe");
        if (classe != null) {
            classe.setExecutor(new ClassCommand(this));
        }
        PluginCommand skills = getCommand("skills");
        if (skills != null) {
            skills.setExecutor(new SkillsCommand(this));
        }
        PluginCommand confirmar = getCommand("confirmar");
        if (confirmar != null) {
            confirmar.setExecutor(new ConfirmarBaseCommand(this));
        }
        PluginCommand minhabase = getCommand("minhabase");
        if (minhabase != null) {
            minhabase.setExecutor(new MinhaBaseCommand(this));
        }

        EconomyCommand economyCommand = new EconomyCommand(this);
        for (String cmd : new String[]{"saldo", "pagar", "cobrar", "baltop", "eco"}) {
            PluginCommand pc = getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(economyCommand);
                pc.setTabCompleter(economyCommand);
            }
        }
        registerVaultEconomy();

        // Loot pelo mundo (busca estilo Tarkov). Depois dos itens/armas registrados (rolls usam o registry).
        this.lootManager = new LootManager(this, configManager);
        this.lootManager.enable();
        getServer().getPluginManager().registerEvents(new LootListener(lootManager), this);
        PluginCommand loot = getCommand("loot");
        if (loot != null) {
            LootCommand lootCmd = new LootCommand(lootManager);
            loot.setExecutor(lootCmd);
            loot.setTabCompleter(lootCmd);
        }

        // Recompensa diária (config lê o registry de itens só no resgate — registry já populado aqui).
        this.dailyRewardManager = new DailyRewardManager(this, configManager);
        PluginCommand diario = getCommand("diario");
        if (diario != null) {
            diario.setExecutor(new DailyCommand(this));
        }

        // Estatísticas de jogador.
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            StatsCommand statsCmd = new StatsCommand(this);
            stats.setExecutor(statsCmd);
            stats.setTabCompleter(statsCmd);
        }

        // Lojas (depois dos itens/armas registrados — as lojas resolvem itens do registry).
        this.shopManager = new ShopManager(this, configManager);
        ShopCommand shopCmd = new ShopCommand(this);
        for (String cmd : new String[]{"medico", "armeiro", "comprador"}) {
            PluginCommand pc = getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(shopCmd);
            }
        }

        // Clãs.
        PluginCommand clan = getCommand("clan");
        if (clan != null) {
            ClanCommand clanCmd = new ClanCommand(this);
            clan.setExecutor(clanCmd);
            clan.setTabCompleter(clanCmd);
        }
        PluginCommand clanChat = getCommand("c");
        if (clanChat != null) {
            clanChat.setExecutor(new ClanChatCommand(this));
        }
        PluginCommand clanTop = getCommand("clantop");
        if (clanTop != null) {
            clanTop.setExecutor(new ClanTopCommand(this));
        }

        PluginCommand bountyCmd = getCommand("bounty");
        if (bountyCmd != null) {
            BountyCommand bc = new BountyCommand(this);
            bountyCmd.setExecutor(bc);
            bountyCmd.setTabCompleter(bc);
        }

        getLogger().info("Deadzone habilitado em " + (System.currentTimeMillis() - start) + "ms.");
    }

    /** Registra os scraps como provedor de economia do Vault (se o Vault/VaultUnlocked estiver presente). */
    private void registerVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null
                && getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            getLogger().info("Vault/VaultUnlocked não encontrado — economia sem ponte Vault.");
            return;
        }
        try {
            getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class,
                    new VaultEconomyProvider(this), this, org.bukkit.plugin.ServicePriority.Highest);
            getLogger().info("Economia (scraps) registrada no Vault.");
        } catch (Throwable t) {
            getLogger().warning("Falha ao registrar economia no Vault: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (clanGlowService != null) {
            clanGlowService.disable();
        }
        if (clanSymbolService != null) {
            clanSymbolService.disable();
        }
        if (claimManager != null) {
            claimManager.disable();
        }
        if (siegeManager != null) {
            siegeManager.disable();
        }
        if (hudService != null) {
            hudService.disable();
        }
        if (eventsManager != null) {
            eventsManager.disable();
        }
        if (worldManager != null) {
            worldManager.disable();
        }
        if (tickService != null) {
            tickService.stop();
        }
        if (profileManager != null) {
            profileManager.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("Deadzone desabilitado.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public Database getDatabase() {
        return database;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public DailyRewardManager getDailyRewardManager() {
        return dailyRewardManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public TickService getTickService() {
        return tickService;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public InfectionConfig getInfectionConfig() {
        return infectionConfig;
    }

    public InfectionManager getInfectionManager() {
        return infectionManager;
    }

    public ZombieRadar getZombieRadar() {
        return zombieRadar;
    }

    public WorldConfig getWorldConfig() {
        return worldConfig;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public MedicineManager getMedicineManager() {
        return medicineManager;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public SanityManager getSanityManager() {
        return sanityManager;
    }

    public EventsManager getEventsManager() {
        return eventsManager;
    }

    public HudService getHudService() {
        return hudService;
    }

    public AtmosphereManager getAtmosphereManager() {
        return atmosphereManager;
    }

    public NoiseManager getNoiseManager() {
        return noiseManager;
    }

    public FirearmManager getFirearmManager() {
        return firearmManager;
    }

    public SiegeManager getSiegeManager() {
        return siegeManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }
}
