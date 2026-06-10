package com.deadzone;

import com.deadzone.command.DeadzoneCommand;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.config.Messages;
import com.deadzone.core.database.Database;
import com.deadzone.core.database.SchemaManager;
import com.deadzone.core.database.dao.PlayerProfileDao;
import com.deadzone.core.database.dao.SqlitePlayerProfileDao;
import com.deadzone.core.debug.TestItem;
import com.deadzone.core.entity.EntityKeys;
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
import com.deadzone.modules.events.EventsManager;
import com.deadzone.modules.firearms.FirearmManager;
import com.deadzone.modules.hud.HudService;
import com.deadzone.modules.medicine.MedicineManager;
import com.deadzone.modules.noise.NoiseManager;
import com.deadzone.modules.siege.SiegeManager;
import com.deadzone.modules.medicine.bench.BenchCommand;
import com.deadzone.modules.sanity.SanityManager;
import com.deadzone.modules.sanity.command.BaseCommand;
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
    private TickService tickService;
    private ItemRegistry itemRegistry;

    private InfectionConfig infectionConfig;
    private InfectionManager infectionManager;

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

        PlayerProfileDao dao = new SqlitePlayerProfileDao(database);
        this.profileManager = new ProfileManager(this, dao, configManager);
        this.profileManager.init();

        this.tickService = new TickService(this, profileManager);
        this.tickService.start();

        this.itemRegistry = new ItemRegistry();
        this.itemRegistry.register(new TestItem(messages));

        getServer().getPluginManager().registerEvents(new ItemUseListener(itemRegistry), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);

        this.infectionConfig = new InfectionConfig(this, configManager);
        this.infectionManager = new InfectionManager(this, infectionConfig);
        this.infectionManager.enable(tickService);

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
        PluginCommand base = getCommand("base");
        if (base != null) {
            BaseCommand baseExecutor = new BaseCommand(this);
            base.setExecutor(baseExecutor);
            base.setTabCompleter(baseExecutor);
        }

        getLogger().info("Deadzone habilitado em " + (System.currentTimeMillis() - start) + "ms.");
    }

    @Override
    public void onDisable() {
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
}
