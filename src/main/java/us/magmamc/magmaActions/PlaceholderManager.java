package us.magmamc.magmaActions;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderManager extends PlaceholderExpansion {
    private final MagmaActions plugin;

    public PlaceholderManager(MagmaActions plugin) {
        this.plugin = plugin;
    }

    public @NotNull String getIdentifier() {
        return "ma";
    }

    public @NotNull String getAuthor() {
        return "MagmaMC";
    }

    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("next_")) {
            String taskId = params.substring(5);

            // 1. Verificar si la tarea programada individual ya existe (ej: koth_15:00)
            if (this.plugin.getActionManager().getTaskIds().contains(taskId)) {
                return this.formatTimeString(this.plugin.getActionManager().getTimeRemaining(taskId));
            }

            // 2. Si no, buscar la CONFIGURACIÓN de la acción para SCHEDULE
            // La búsqueda DEBE usar el ActionManager, no el config.yml
            ConfigurationSection section = this.plugin.getActionManager().getActionConfigSection(taskId);

            if (section != null) {
                String type = section.getString("type", "").toUpperCase();

                if (type.equals("SCHEDULE")) {
                    String timeStr = section.getString("time", ""); // Usar la sección cargada
                    String[] times = timeStr.split(";");
                    long closestTime = Long.MAX_VALUE;
                    String closestTaskId = null;

                    for(String time : times) {
                        String scheduleTaskId = taskId + "_" + time.trim(); // Usar time.trim()
                        if (this.plugin.getActionManager().getTaskIds().contains(scheduleTaskId)) {
                            String remaining = this.plugin.getActionManager().getTimeRemaining(scheduleTaskId);

                            if (remaining != null && !remaining.equals("Unknown") && !remaining.equals("Soon")) {
                                try {
                                    long remainingMs = this.convertTimeStringToMs(remaining);
                                    if (remainingMs < closestTime) {
                                        closestTime = remainingMs;
                                        closestTaskId = scheduleTaskId;
                                    }
                                } catch (Exception var18) {
                                    // Ignorar errores de conversión
                                }
                            }
                        }
                    }

                    if (closestTaskId != null) {
                        return this.formatTimeString(this.plugin.getActionManager().getTimeRemaining(closestTaskId));
                    }
                }
            }
        }

        return null;
    }

    private String formatTimeString(String timeStr) {
        if (timeStr != null && !timeStr.equals("Unknown") && !timeStr.equals("Soon")) {
            if (timeStr.endsWith(" 0s")) {
                return timeStr.substring(0, timeStr.length() - 3).trim();
            } else {
                return timeStr.equals("0s") ? "1s" : timeStr;
            }
        } else {
            return timeStr;
        }
    }

    private long convertTimeStringToMs(String timeStr) {
        long totalMs = 0L;
        String[] parts = timeStr.split(" ");

        for(String part : parts) {
            if (part.endsWith("h")) {
                totalMs += Long.parseLong(part.replace("h", "")) * 3600000L;
            } else if (part.endsWith("m")) {
                totalMs += Long.parseLong(part.replace("m", "")) * 60000L;
            } else if (part.endsWith("s")) {
                totalMs += Long.parseLong(part.replace("s", "")) * 1000L;
            }
        }

        return totalMs;
    }
}
