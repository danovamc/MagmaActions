package us.magmamc.magmaActions;

import java.io.File;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;


public class MagmaActions extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private FileConfiguration langConfig;
    private String prefix;
    private ActionManager actionManager;
    private CommandHandler commandHandler;
    private PlaceholderManager placeholderManager;


    public void onEnable() {
        // 1. CARGA NATIVA: Asegura que config.yml exista y carga el FileConfiguration nativo.
        this.saveDefaultConfig();

        // 2. ASIGNACIÓN CRÍTICA: Asigna la configuración nativa al campo privado 'config'.
        // ESTO DEBE HACERSE ANTES de que ActionManager o cualquier otro método llame a getConfig().
        this.config = this.getConfig();

        // 3. CARGA DE LENGUAJE
        this.createLangFile();
        this.prefix = this.langConfig.getString("prefix", "MAGMAMC");

        // --- INICIALIZACIÓN MODULAR ---
        File actionsFolder = new File(this.getDataFolder(), "actions");
        if (!actionsFolder.exists()) {
            actionsFolder.mkdirs();
            this.createDefaultActionFile(actionsFolder);
        }

        // 4. Inicializar ActionManager. Ahora es seguro llamar a loadWorldBlacklist()
        // dentro de ActionManager, ya que 'this.config' no será nulo.
        this.actionManager = new ActionManager(this, actionsFolder);

        // --- INICIALIZACIÓN DE COMPONENTES RESTANTES ---
        this.commandHandler = new CommandHandler(this);

        // --- Registro de Comandos ---
        PluginCommand magmaCommand = (PluginCommand)Objects.requireNonNull(this.getCommand("magmaactions"));
        magmaCommand.setExecutor(this.commandHandler);
        magmaCommand.setTabCompleter(this.commandHandler);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderManager = new PlaceholderManager(this);
            this.placeholderManager.register();
            this.getLogger().info("PAPI ENCONTRADO INYECTANDO MA");
        }

        // 5. Cargar las acciones
        this.actionManager.loadActions();
    }

    public void onDisable() {
        if (this.actionManager != null) {
            this.actionManager.cancelAllTasks();
        }
    }

    private void createLangFile() {
        File langFile = new File(this.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            this.saveResource("lang.yml", false);
        }
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void createDefaultActionFile(File actionsFolder) {
        File defaultActionFile = new File(actionsFolder, "actions_info.yml");
        if (!defaultActionFile.exists()) {
            this.saveResource("actions/actions_info.yml", false);
        }
    }

    public void reload() {
        this.reloadConfig();
        this.config = this.getConfig();
        File langFile = new File(this.getDataFolder(), "lang.yml");
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
        this.prefix = this.langConfig.getString("prefix", "MAGMAMC");

        if (this.actionManager != null) {
            this.actionManager.loadActions();
        }
    }

    public FileConfiguration getConfig() {
        return super.getConfig();
    }
    public FileConfiguration getLangConfig() { return this.langConfig; }
    public String getPrefix() { return this.prefix; }
    public ActionManager getActionManager() { return this.actionManager; }
}