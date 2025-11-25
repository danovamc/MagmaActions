package us.magmamc.magmaActions;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class ActionExecutor {
    private final MagmaActions plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Random random = new Random();

    public ActionExecutor(MagmaActions plugin) {
        this.plugin = plugin;
    }

    // Sobrecarga para compatibilidad o uso simple
    public void executeActions(List<String> actions, Player specificPlayer) {
        executeActions(actions, specificPlayer, null);
    }

    // MÉTODO PRINCIPAL MODIFICADO: Acepta actionKey para el rastreo
    public void executeActions(List<String> actions, Player specificPlayer, String actionKey) {
        int delayMs = 0;

        for(int i = 0; i < actions.size(); ++i) {
            String action = (String)actions.get(i);
            final String finalAction = action;

            if (action.startsWith("[DELAY] ")) {
                String delayStr = action.substring(8).trim();
                delayMs += (int)ActionManager.parseTimeInterval(delayStr);
            } else if (delayMs > 0) {
                // Usamos BukkitRunnable para poder acceder al ID de la tarea dentro de run()
                BukkitRunnable task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            processSingleAction(finalAction, specificPlayer);
                        } finally {
                            // LIMPIEZA: Si se proporcionó una key, eliminamos esta tarea de la lista de pendientes
                            // porque ya se ejecutó.
                            if (actionKey != null) {
                                plugin.getActionManager().removeActiveTask(actionKey, this.getTaskId());
                            }
                        }
                    }
                };

                // Programar la tarea
                BukkitTask bukkitTask = task.runTaskLater(this.plugin, (long)delayMs / 50L);

                // REGISTRO: Si se proporcionó una key, guardamos el ID para poder cancelarlo luego
                if (actionKey != null) {
                    plugin.getActionManager().addActiveTask(actionKey, bukkitTask.getTaskId());
                }

            } else {
                // Acciones inmediatas (delay 0)
                Bukkit.getScheduler().runTask(this.plugin, () -> this.processSingleAction(finalAction, specificPlayer));
            }
        }
    }

    // Sobrecarga para compatibilidad
    public void executeActionsForPlayer(List<String> actions, Player player) {
        executeActionsForPlayer(actions, player, null);
    }

    // MÉTODO PARA PLAYER MODIFICADO: Acepta actionKey
    public void executeActionsForPlayer(List<String> actions, Player player, String actionKey) {
        int delayMs = 0;

        for(int i = 0; i < actions.size(); ++i) {
            String action = (String)actions.get(i);
            final String finalAction = action;

            if (action.startsWith("[DELAY] ")) {
                String delayStr = action.substring(8).trim();
                delayMs += (int)ActionManager.parseTimeInterval(delayStr);
            } else if (delayMs > 0) {

                BukkitRunnable task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            processSingleAction(finalAction, player);
                        } finally {
                            if (actionKey != null) {
                                plugin.getActionManager().removeActiveTask(actionKey, this.getTaskId());
                            }
                        }
                    }
                };

                BukkitTask bukkitTask = task.runTaskLater(this.plugin, (long)delayMs / 50L);

                if (actionKey != null) {
                    plugin.getActionManager().addActiveTask(actionKey, bukkitTask.getTaskId());
                }

            } else {
                Bukkit.getScheduler().runTask(this.plugin, () -> this.processSingleAction(finalAction, player));
            }
        }
    }

    private void processSingleAction(String action, Player specificPlayer) {
        String prefix = this.plugin.getPrefix();

        // El bloque principal que maneja todas las acciones
        if (action.startsWith("[RANDOM] ")) {
            String[] randomActions = action.substring(9).trim().split("\\;");
            String selectedAction = randomActions[this.random.nextInt(randomActions.length)].trim();
            this.processSingleAction(selectedAction, specificPlayer);

        } else {
            // El bloque 'else' maneja todas las acciones no-random
            if (action.startsWith("[ACTIONBAR] ")) {
                String message = action.substring(12).trim();
                Component component = this.miniMessage.deserialize(message.replace("{prefix}", prefix));
                if (specificPlayer != null) {
                    specificPlayer.sendActionBar(component);
                } else {
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        player.sendActionBar(component);
                    }
                }
            } else if (action.startsWith("[MESSAGE] ")) {
                String message = action.substring(10).trim();
                String[] messageLines = message.split("\\|");

                for(String line : messageLines) {
                    Component component = this.miniMessage.deserialize(line.trim().replace("{prefix}", prefix));
                    if (specificPlayer != null) {
                        specificPlayer.sendMessage(component);
                    } else {
                        Bukkit.broadcast(component);
                    }
                }
            } else if (action.startsWith("[CONSOLE] ")) {
                String command = action.substring(10).trim().replace("{prefix}", prefix);

                // 1. Lógica para jugador específico (sin cambios)
                if (specificPlayer != null) {
                    command = command.replace("{player}", specificPlayer.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    // 2. Lógica para TODOS (Keyall)
                } else if (command.contains("{everyone}")) {

                    // --- INICIO: LÓGICA DE FILTRADO Y NOTIFICACIÓN ---

                    // Mapa para rastrear 1 jugador elegido por cada IP
                    Map<String, Player> playersToReward = new HashMap<>();
                    // Set para almacenar a los jugadores que deben ser notificados del bloqueo
                    Set<Player> filteredPlayers = new HashSet<>();

                    // 1. Recorrer a TODOS y llenar ambos sets
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getAddress() != null) {
                            String ipAddress = player.getAddress().getAddress().getHostAddress();

                            if (!playersToReward.containsKey(ipAddress)) {
                                // Este es el JUGADOR ELEGIDO (El que recibe el premio)
                                playersToReward.put(ipAddress, player);
                            } else {
                                // Este es el JUGADOR BLOQUEADO (Multicuenta)
                                filteredPlayers.add(player);
                            }
                        }
                    }

                    // 2. Ejecutar el comando SOLO para los jugadores elegidos (1 por IP)
                    for (Player player : playersToReward.values()) {
                        String playerCommand = command.replace("{everyone}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), playerCommand);
                    }

                    // 3. ENVIAR MENSAJE A LOS BLOQUEADOS (CRÍTICO: debe ser síncrono)
                    if (!filteredPlayers.isEmpty()) {

                        // OBTENER EL MENSAJE CONFIGURABLE
                        String rawMessage = this.plugin.getLangConfig().getString(
                                "messages.error.ip_limit_blocked",
                                "<red>{prefix} »</red> <gray>No recibiste el premio por límite de IP.</gray>" // Fallback
                        );

                        // Reemplazamos {prefix} y parseamos con MiniMessage
                        Component messageComponent = MiniMessage.miniMessage().deserialize(
                                rawMessage.replace("{prefix}", prefix)
                        );

                        Bukkit.getScheduler().runTask(this.plugin, () -> {
                            for (Player blocked : filteredPlayers) {
                                if (blocked.isOnline()) {
                                    blocked.sendMessage(messageComponent);
                                }
                            }
                        });
                    }

                    // --- FIN: LÓGICA DE FILTRADO Y NOTIFICACIÓN ---

                } else {
                    // Lógica para un comando de consola general (sin cambios)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }

            } else if (action.startsWith("[TITLE] ")) {
                String[] parts = action.substring(8).trim().split("\\|", 2);
                String title = parts[0].trim();
                String subtitle = parts.length > 1 ? parts[1].trim() : "";
                Component titleComponent = this.miniMessage.deserialize(title.replace("{prefix}", prefix));
                Component subtitleComponent = this.miniMessage.deserialize(subtitle.replace("{prefix}", prefix));
                Title titleObj = Title.title(titleComponent, subtitleComponent, Times.times(Duration.ofMillis(500L), Duration.ofMillis(3000L), Duration.ofMillis(500L)));
                if (specificPlayer != null) {
                    specificPlayer.showTitle(titleObj);
                } else {
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(titleObj);
                    }
                }
            } else if (action.startsWith("[SOUND] ")) {
                String[] parts = action.substring(8).trim().split("\\|", 3);
                String soundName = parts[0].trim();
                float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0F;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0F;

                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    if (specificPlayer != null) {
                        specificPlayer.playSound(specificPlayer.getLocation(), sound, volume, pitch);
                    } else {
                        for(Player player : Bukkit.getOnlinePlayers()) {
                            player.playSound(player.getLocation(), sound, volume, pitch);
                        }
                    }
                } catch (IllegalArgumentException var12) {
                    this.plugin.getLogger().warning("Invalid sound name: " + soundName);
                }
            }
        }
    }
}