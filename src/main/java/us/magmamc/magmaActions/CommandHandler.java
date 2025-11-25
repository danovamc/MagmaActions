package us.magmamc.magmaActions;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final MagmaActions plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CommandHandler(MagmaActions plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            this.sendHelpMessage(sender);
            return true;
        } else {
            switch (args[0].toLowerCase()) {
                case "reload":
                    if (!sender.hasPermission("magmaactions.reload")) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.permission")));
                        return true;
                    }

                    this.plugin.reload();
                    sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.reload")));
                    return true;
                case "stop":
                    // NUEVO COMANDO STOP
                    if (!sender.hasPermission("magmaactions.stop")) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.permission")));
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.stop_usage")));
                        return true;
                    }
                    String actionToStop = args[1];
                    boolean stopped = this.plugin.getActionManager().stopAction(actionToStop);
                    if (stopped) {
                        String msg = this.getMessage("messages.action_stopped").replace("{event}", actionToStop);
                        sender.sendMessage(this.miniMessage.deserialize(msg));
                    } else {
                        String msg = this.getMessage("messages.error.no_action_running").replace("{event}", actionToStop);
                        sender.sendMessage(this.miniMessage.deserialize(msg));
                    }
                    return true;

                case "test":
                    if (!sender.hasPermission("magmaactions.test")) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.permission")));
                        return true;
                    } else if (args.length < 2) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.test_usage")));
                        return true;
                    } else {
                        String actionKey = args[1];

                        if (!this.plugin.getActionManager().getActionsKeys().contains(actionKey)) {
                            String message = this.getMessage("messages.error.event_not_found").replace("{event}", actionKey);
                            sender.sendMessage(this.miniMessage.deserialize(message));
                            return true;
                        } else {
                            Player targetPlayer = null;
                            boolean allPlayers = false;

                            if (args.length > 2) {
                                if (args[2].equals("*")) {
                                    allPlayers = true;
                                } else {
                                    targetPlayer = Bukkit.getPlayer(args[2]);
                                    if (targetPlayer == null) {
                                        String message = this.getMessage("messages.error.player_not_found").replace("{player}", args[2]);
                                        sender.sendMessage(this.miniMessage.deserialize(message));
                                        return true;
                                    }
                                }
                            } else if (sender instanceof Player) {
                                targetPlayer = (Player)sender;
                            }

                            if (allPlayers) {
                                this.plugin.getActionManager().executeAction(actionKey, (Player)null);
                                String message = this.getMessage("messages.error.test_executed_all").replace("{event}", actionKey);
                                sender.sendMessage(this.miniMessage.deserialize(message));
                            } else {
                                this.plugin.getActionManager().executeAction(actionKey, targetPlayer);
                                String message = this.getMessage("messages.error.test_executed_player").replace("{event}", actionKey).replace("{player}", targetPlayer != null ? targetPlayer.getName() : "Console");
                                sender.sendMessage(this.miniMessage.deserialize(message));
                            }

                            return true;
                        }
                    }
                case "list":
                    if (!sender.hasPermission("magmaactions.list")) {
                        sender.sendMessage(this.miniMessage.deserialize(this.getMessage("messages.error.permission")));
                        return true;
                    } else {
                        List<String> actionKeys = new ArrayList<>(this.plugin.getActionManager().getActionsKeys());

                        sender.sendMessage(this.miniMessage.deserialize("<yellow>--- Configured Actions ({count}) ---</yellow>".replace("{count}", String.valueOf(actionKeys.size()))));

                        if (actionKeys.isEmpty()) {
                            sender.sendMessage(this.miniMessage.deserialize("<gray>No hay acciones cargadas de la carpeta /actions/.</gray>"));
                            return true;
                        }

                        for(String key : actionKeys) {
                            ConfigurationSection section = this.plugin.getActionManager().getActionConfigSection(key);

                            String type = section != null ? section.getString("type", "unknown") : "unknown";
                            String details = "";

                            if (section != null) {
                                switch (type.toUpperCase()) {
                                    case "SCHEDULE":
                                        details = "Time: " + section.getString("time", "N/A");
                                        break;
                                    case "TASK":
                                        details = "Interval: " + section.getString("interval", "N/A");
                                        break;
                                    case "COMMAND":
                                        details = "Manual Command";
                                        break;
                                    case "TASK_PER_PLAYER":
                                        details = "Interval: " + section.getString("interval", "N/A") + " (Per Player)";
                                        break;
                                }
                            }
                            sender.sendMessage(this.miniMessage.deserialize("<aqua>" + key + "</aqua> - <green>" + type + "</green> (<gray>" + details + "</gray>)"));
                        }

                        return true;
                    }
                default:
                    this.sendHelpMessage(sender);
                    return true;
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        for(String line : this.getMessageList("messages.help")) {
            sender.sendMessage(this.miniMessage.deserialize(line));
        }

    }

    private List<String> getMessageList(String path) {
        List<String> messages = this.plugin.getLangConfig().getStringList(path);
        String prefix = this.plugin.getLangConfig().getString("prefix", "<red><bold>MAGMAMC</bold></red> <dark_gray>»</dark_gray> <reset>");
        List<String> processedMessages = new ArrayList();

        for(String message : messages) {
            processedMessages.add(message.replace("{prefix}", prefix));
        }

        return processedMessages;
    }

    private String getMessage(String path) {
        String message = this.plugin.getLangConfig().getString(path, "");
        String prefix = this.plugin.getLangConfig().getString("prefix", "<red><bold>MAGMAMC</bold></red> <dark_gray>»</dark_gray> <reset>");
        return message.replace("{prefix}", prefix);
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("test");
            completions.add("list");
            completions.add("stop");
            return this.filterCompletions(completions, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            for(String key : this.plugin.getActionManager().getActionsKeys()) {
                completions.add(key);
            }
            return this.filterCompletions(completions, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            // NUEVO: Tab completion para acciones en curso
            for(String key : this.plugin.getActionManager().getRunningActionKeys()) {
                completions.add(key);
            }
            return this.filterCompletions(completions, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            completions.add("*");
            for(Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            return this.filterCompletions(completions, args[2]);
        } else {
            return completions;
        }
    }

    private List<String> filterCompletions(List<String> completions, String currentArg) {
        if (currentArg.isEmpty()) {
            return completions;
        } else {
            List<String> filteredCompletions = new ArrayList();
            for(String completion : completions) {
                if (completion.toLowerCase().startsWith(currentArg.toLowerCase())) {
                    filteredCompletions.add(completion);
                }
            }
            return filteredCompletions;
        }
    }
}