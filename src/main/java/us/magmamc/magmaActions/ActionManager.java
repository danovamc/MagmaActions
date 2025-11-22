package us.magmamc.magmaActions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import org.bukkit.configuration.file.FileConfiguration;

public class ActionManager {
    private final MagmaActions plugin;
    private final File actionsFolder;

    private final Map<String, List<String>> actionsMap = new HashMap<>();
    private final Map<String, ConfigurationSection> actionConfigs = new HashMap<>();

    private final Map<String, TimerTask> scheduledTasks = new HashMap();
    private final Map<String, Long> nextExecutionTimes = new HashMap();
    private final Map<String, Map<UUID, Long>> playerTaskTimes = new HashMap();

    private final Map<String, Integer> activeBukkitTasks = new HashMap<>();
    private final Map<String, Timer> activeTimers = new HashMap<>();

    private final ActionExecutor actionExecutor;
    private Timer timer;
    private Set<String> worldBlacklist = new HashSet();

    public ActionManager(MagmaActions plugin, File actionsFolder) {
        this.plugin = plugin;
        this.actionsFolder = actionsFolder;
        this.actionExecutor = new ActionExecutor(plugin);
        this.timer = new Timer();
    }

    public void loadActions() {
        this.cancelAllTasks();
        this.nextExecutionTimes.clear();
        this.playerTaskTimes.clear();

        this.actionsMap.clear();
        this.actionConfigs.clear();

        this.timer = new Timer();
        this.loadWorldBlacklist(); // Ahora es seguro llamar a este método

        File[] actionFiles = this.actionsFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (actionFiles != null) {
            for (File file : actionFiles) {
                if (file.isFile()) {
                    try {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        this.processConfigSection(config);
                        this.plugin.getLogger().info("Loaded actions from: " + file.getName());
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Could not load actions from " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private void processConfigSection(ConfigurationSection config) {
        for(String key : config.getKeys(false)) {
            if (config.isConfigurationSection(key)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    this.actionConfigs.put(key, section);

                    String type = section.getString("type");
                    if (type != null) {
                        List<String> actionsList = section.getStringList("actions");
                        this.actionsMap.put(key, actionsList);

                        switch (type.toUpperCase()) {
                            case "SCHEDULE":
                                String[] times = section.getString("time", "").split(";");
                                String daysStr = section.getString("days", "MONDAY;TUESDAY;WEDNESDAY;THURSDAY;FRIDAY;SATURDAY;SUNDAY");
                                Set<Integer> allowedDays = this.parseDays(daysStr);

                                for(String time : times) {
                                    this.scheduleTimeAction(key, time.trim(), actionsList, allowedDays);
                                }
                                break;
                            case "TASK":
                                String interval = section.getString("interval", "1m");
                                this.scheduleIntervalAction(key, interval, actionsList);
                                break;
                            case "TASK_PER_PLAYER":
                                String playerInterval = section.getString("interval", "1m");
                                this.schedulePlayerIntervalAction(key, playerInterval, actionsList);
                            case "COMMAND":
                                break;
                        }
                    }
                }
            }
        }
    }

    private void loadWorldBlacklist() {
        this.worldBlacklist.clear();

        // ACCESO RÁPIDO Y SEGURO A LA CONFIGURACIÓN DEL PLUGIN PRINCIPAL
        FileConfiguration configSource = this.plugin.getConfig();

        // Si la configuración es NULL (no debería pasar si onEnable está bien), salimos.
        if (configSource == null) {
            this.plugin.getLogger().severe("Error: La configuración principal (config.yml) es NULL al leer la World Blacklist.");
            return;
        }

        String blacklistStr = configSource.getString("world-blacklist", "");

        if (!blacklistStr.isEmpty()) {
            if (blacklistStr.startsWith("*")) {
                blacklistStr = blacklistStr.substring(1);
            }

            if (blacklistStr.endsWith("*")) {
                blacklistStr = blacklistStr.substring(0, blacklistStr.length() - 1);
            }

            String[] worlds = blacklistStr.split("/");

            for(String world : worlds) {
                if (!world.trim().isEmpty()) {
                    this.worldBlacklist.add(world.trim());
                }
            }
        }

        this.plugin.getLogger().info("World blacklist loaded: " + this.worldBlacklist.toString());
    }

    private boolean isWorldBlacklisted(String worldName) {
        return this.worldBlacklist.contains(worldName);
    }

    private Set<Integer> parseDays(String daysStr) {
        Set<Integer> days = new HashSet();
        String[] dayArray = daysStr.split(";");

        for(String day : dayArray) {
            switch (day.trim().toUpperCase()) {
                case "MONDAY":
                    days.add(2);
                    break;
                case "TUESDAY":
                    days.add(3);
                    break;
                case "WEDNESDAY":
                    days.add(4);
                    break;
                case "THURSDAY":
                    days.add(5);
                    break;
                case "FRIDAY":
                    days.add(6);
                    break;
                case "SATURDAY":
                    days.add(7);
                    break;
                case "SUNDAY":
                    days.add(1);
            }
        }

        if (days.isEmpty()) {
            days.add(2);
            days.add(3);
            days.add(4);
            days.add(5);
            days.add(6);
            days.add(7);
            days.add(1);
        }

        return days;
    }

    private void scheduleTimeAction(final String key, String timeStr, final List<String> actions, final Set<Integer> allowedDays) {
        try {
            String[] timeParts = timeStr.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                this.plugin.getLogger().warning("Invalid time format for " + key + ": " + timeStr);
                return;
            }

            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT-6"));
            calendar.set(11, hour);
            calendar.set(12, minute);
            calendar.set(13, 0);

            while(!allowedDays.contains(calendar.get(7)) || calendar.getTimeInMillis() < System.currentTimeMillis()) {
                calendar.add(5, 1);
            }

            final String taskId = key + "_" + timeStr;
            this.nextExecutionTimes.put(taskId, calendar.getTimeInMillis());
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        ActionManager.this.actionExecutor.executeActions(actions, (Player)null);
                        Calendar nextRun = Calendar.getInstance(TimeZone.getTimeZone("GMT-6"));
                        nextRun.setTimeInMillis(calendar.getTimeInMillis());
                        nextRun.add(5, 1);

                        while(!allowedDays.contains(nextRun.get(7))) {
                            nextRun.add(5, 1);
                        }

                        ActionManager.this.nextExecutionTimes.put(taskId, nextRun.getTimeInMillis());
                        TimerTask nextTask = new TimerTask() {
                            public void run() {
                                ActionManager.this.actionExecutor.executeActions(actions, (Player)null);
                            }
                        };
                        ActionManager.this.timer.schedule(nextTask, nextRun.getTime());
                        ActionManager.this.scheduledTasks.put(taskId, nextTask);
                    } catch (Exception e) {
                        Logger var10000 = ActionManager.this.plugin.getLogger();
                        String var10001 = key;
                        var10000.warning("Error executing scheduled task for " + var10001 + ": " + e.getMessage());
                    }

                }
            };
            this.timer.schedule(task, calendar.getTime());
            this.scheduledTasks.put(taskId, task);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT-6"));
            this.plugin.getLogger().info("Scheduled time action for " + key + " at " + sdf.format(calendar.getTime()));
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to schedule time action for " + key + ": " + e.getMessage());
        }

    }

    private void scheduleIntervalAction(final String key, String intervalStr, final List<String> actions) {
        try {
            final long intervalMs = parseTimeInterval(intervalStr);
            final long intervalTicks = msToTicks(intervalMs);
            this.nextExecutionTimes.put(key, System.currentTimeMillis() + intervalMs);

            // 1. CANCELAR TAREA ANTERIOR del Bukkit Scheduler
            if (this.activeBukkitTasks.containsKey(key)) {
                Bukkit.getScheduler().cancelTask(this.activeBukkitTasks.get(key));
            }

            // 2. CREAR NUEVA TAREA ASÍNCRONA (TASK)
            int taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        ActionManager.this.actionExecutor.executeActions(actions, (Player)null);
                        ActionManager.this.nextExecutionTimes.put(key, System.currentTimeMillis() + intervalMs);
                    } catch (Exception e) {
                        ActionManager.this.plugin.getLogger().warning("Error executing interval task for " + key + ": " + e.getMessage());
                    }
                }
            }, 0L, intervalTicks).getTaskId(); // INICIO INMEDIATO (0L)

            // 3. GUARDAR ID de la tarea
            this.activeBukkitTasks.put(key, taskId);

            this.plugin.getLogger().info("Scheduled Bukkit TASK action for " + key + " every " + intervalStr);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to schedule interval action for " + key + ": " + e.getMessage());
        }
    }

    private void schedulePlayerIntervalAction(final String key, String intervalStr, final List<String> actions) {
        try {
            final long intervalMs = parseTimeInterval(intervalStr);
            final long intervalTicks = msToTicks(5000L); // Chequea cada 5 segundos

            this.playerTaskTimes.put(key, new HashMap());

            // 1. CANCELAR TAREA ANTERIOR del Bukkit Scheduler
            if (this.activeBukkitTasks.containsKey(key)) {
                Bukkit.getScheduler().cancelTask(this.activeBukkitTasks.get(key));
            }

            // 2. CREAR NUEVA TAREA ASÍNCRONA (TASK_PER_PLAYER)
            int taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
                        Map<UUID, Long> taskTimes = ActionManager.this.playerTaskTimes.get(key);
                        long currentTime = System.currentTimeMillis();

                        // LECTURA CORREGIDA para obtener el permiso
                        ConfigurationSection section = ActionManager.this.getActionConfigSection(key);
                        String permission = section != null ? section.getString("permission", (String)null) : null;

                        for(Player player : onlinePlayers) {
                            try {
                                if (player != null && player.isOnline() &&
                                        !ActionManager.this.isWorldBlacklisted(player.getWorld().getName()) &&
                                        (permission == null || permission.isEmpty() || player.hasPermission(permission)))
                                {
                                    UUID playerId = player.getUniqueId();
                                    Long lastExecution = taskTimes.get(playerId);
                                    if (lastExecution == null || currentTime - lastExecution >= intervalMs) {

                                        // EJECUTAR COMANDOS EN EL HILO PRINCIPAL (NECESARIO PARA BUKKIT API)
                                        Bukkit.getScheduler().runTask(ActionManager.this.plugin, () -> {
                                            try {
                                                if (player.isOnline()) {
                                                    ActionManager.this.actionExecutor.executeActionsForPlayer(actions, player);
                                                }
                                            } catch (Exception e) {
                                                ActionManager.this.plugin.getLogger().warning("Error executing action for player " + player.getName() + " in task " + key + ": " + e.getMessage());
                                                e.printStackTrace();
                                            }
                                        });
                                        taskTimes.put(playerId, currentTime);
                                    }
                                }
                            } catch (Exception e) {
                                ActionManager.this.plugin.getLogger().warning("Error processing player in task " + key + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        // ... (lógica de limpieza)
                        taskTimes.entrySet().removeIf((entry) -> {
                            try {
                                Player player = Bukkit.getPlayer((UUID)entry.getKey());
                                return player == null || !player.isOnline();
                            } catch (Exception var2) {
                                return true;
                            }
                        });

                    } catch (Exception e) {
                        ActionManager.this.plugin.getLogger().warning("Error executing player interval task for " + key + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, 0L, intervalTicks).getTaskId(); // INICIO INMEDIATO (0L)

            // 3. GUARDAR ID de la tarea
            this.activeBukkitTasks.put(key, taskId);

            this.plugin.getLogger().info("Scheduled Bukkit TASK_PER_PLAYER action for " + key + " every " + intervalStr + " per player");
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to schedule player interval action for " + key + ": " + e.getMessage());
            e.printStackTrace();
        }

    }

    public void cancelAllTasks() {
        this.plugin.getLogger().info("Iniciando cancelación de todas las tareas activas de MagmaActions...");

        // 1. CANCELAR TAREAS REPETITIVAS (TASK/TASK_PER_PLAYER) DEL BUKKIT SCHEDULER
        for (String key : this.activeBukkitTasks.keySet()) {
            int taskId = this.activeBukkitTasks.get(key);
            Bukkit.getScheduler().cancelTask(taskId);
        }
        this.activeBukkitTasks.clear();
        this.plugin.getLogger().info("Tareas TASK/TASK_PER_PLAYER del Bukkit Scheduler canceladas.");

        // 2. CANCELAR TODAS LAS SECUENCIAS DE [DELAY] Y TAREAS FUTURAS PENDIENTES
        // ESTA ES LA CLAVE PARA DETENER LA SECUENCIA DE RUPÍAS INTERNA.
        Bukkit.getScheduler().cancelTasks(this.plugin);
        this.plugin.getLogger().info("Secuencias internas de [DELAY] canceladas.");

        // 2. Cancelar TimerTask de SCHEDULE (usan el timer global)
        for(TimerTask task : this.scheduledTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        this.scheduledTasks.clear();

        // 3. Cancelar Timers individuales de activeTimers (solo por seguridad, aunque ya no deberían usarse para repetición)
        for (Timer timer : this.activeTimers.values()) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
        }
        this.activeTimers.clear();


        // 4. Cancelar el Timer global (usado para SCHEDULE)
        if (this.timer != null) {
            this.timer.cancel();
            this.timer.purge();
            this.plugin.getLogger().info("Timer global SCHEDULE cancelado.");
        }
    }

    public void executeAction(String actionKey, Player targetPlayer) {
        // CORREGIDO: Busca la acción en el mapa, NO en el config.yml
        List<String> actions = this.getActions(actionKey);

        if (actions != null) {
            this.actionExecutor.executeActions(actions, targetPlayer);
        } else {
            // Mejoramos el mensaje de error si no se encuentra la acción
            this.plugin.getLogger().warning("Action key '" + actionKey + "' not found in loaded actions.");
        }
    }

    public static long parseTimeInterval(String interval) {
        Pattern pattern = Pattern.compile("(\\d+)([smh])");
        Matcher matcher = pattern.matcher(interval);
        if (matcher.matches()) {
            int amount = Integer.parseInt(matcher.group(1));
            switch (matcher.group(2)) {
                case "s" -> {
                    return TimeUnit.SECONDS.toMillis((long)amount);
                }
                case "m" -> {
                    return TimeUnit.MINUTES.toMillis((long)amount);
                }
                case "h" -> {
                    return TimeUnit.HOURS.toMillis((long)amount);
                }
                default -> {
                    return 60000L;
                }
            }
        } else {
            return 60000L;
        }
    }

    // Nuevo método auxiliar para convertir milisegundos a Ticks de Bukkit
    private long msToTicks(long ms) {
        return ms / 50;
    }

    public String getTimeRemaining(String taskId) {
        Long nextExecution = (Long)this.nextExecutionTimes.get(taskId);
        if (nextExecution == null) {
            return "Unknown";
        } else {
            long remainingMs = nextExecution - System.currentTimeMillis();
            return remainingMs <= 0L ? "Soon" : this.formatTimeRemaining(remainingMs);
        }
    }

    public String getPlayerTimeRemaining(String taskId, UUID playerId) {
        Map<UUID, Long> taskTimes = (Map)this.playerTaskTimes.get(taskId);
        if (taskTimes == null) {
            return "Unknown";
        } else {
            Long lastExecution = (Long)taskTimes.get(playerId);
            if (lastExecution == null) {
                return "Ready";
            } else {
                // CORREGIDO: Debe usar getActionConfigSection para la configuración modular.
                ConfigurationSection section = this.getActionConfigSection(taskId);

                if (section == null) {
                    return "Unknown";
                } else {
                    String intervalStr = section.getString("interval", "1m");
                    long intervalMs = parseTimeInterval(intervalStr);
                    long timeSinceLastExecution = System.currentTimeMillis() - lastExecution;
                    long remainingMs = intervalMs - timeSinceLastExecution;
                    return remainingMs <= 0L ? "Ready" : this.formatTimeRemaining(remainingMs);
                }
            }
        }
    }

    public String formatTimeRemaining(long milliseconds) {
        if (milliseconds < 0L) {
            return "Now";
        } else {
            long seconds = milliseconds / 1000L;
            long minutes = seconds / 60L;
            long hours = minutes / 60L;
            seconds %= 60L;
            minutes %= 60L;
            StringBuilder result = new StringBuilder();
            if (hours > 0L) {
                result.append(hours).append("h ");
                result.append(minutes).append("m");
            } else if (minutes > 0L) {
                result.append(minutes).append("m ");
                result.append(seconds).append("s");
            } else {
                result.append(seconds).append("s");
            }

            return result.toString();
        }
    }

    public Set<String> getTaskIds() {
        return this.nextExecutionTimes.keySet();
    }

    /**
     * Devuelve las claves de las tareas por jugador.
     */
    public Set<String> getPlayerTaskIds() {
        return this.playerTaskTimes.keySet();
    }

    public Set<String> getWorldBlacklist() {
        return new HashSet(this.worldBlacklist);
    }

    // MÉTODOS DE ACCESO PARA OTRAS CLASES (CommandHandler y PlaceholderManager)

    /**
     * Devuelve todas las claves de acción cargadas (SCHEDULE, TASK, COMMAND, etc.).
     * Usado para /ma test y /ma list.
     */
    public Set<String> getActionsKeys() {
        return this.actionsMap.keySet();
    }

    /**
     * Devuelve las claves de las acciones cargadas como una Lista (conveniente para Listas/TabCompleter).
     */
    public List<String> getLoadedActionNames() {
        return new ArrayList<>(this.actionsMap.keySet());
    }

    /**
     * Obtiene la lista de comandos (actions List<String>) para ejecutar.
     * Usado por ActionExecutor.
     */
    public List<String> getActions(String actionKey) {
        return this.actionsMap.get(actionKey);
    }

    /**
     * Obtiene la sección de configuración completa de una acción.
     * Usado por PlaceholderManager para calcular SCHEDULE y por tareas TASK_PER_PLAYER.
     */
    public ConfigurationSection getActionConfigSection(String key) {
        return this.actionConfigs.get(key);
    }

    /**
     * Comprueba si existe una tarea repetitiva activa (TASK o TASK_PER_PLAYER)
     * en el Bukkit Scheduler.
     * * NOTA: Este método es nuevo y podría ser útil para depuración.
     */
    public boolean isBukkitTaskActive(String key) {
        return this.activeBukkitTasks.containsKey(key);
    }
}