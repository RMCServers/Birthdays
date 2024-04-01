package nl.rmcservers.birthdays;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.json.simple.JSONObject;
import org.bukkit.configuration.file.FileConfiguration;

import nl.rmcservers.birthdays.Utils;

public class Birthdays extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Map<UUID, String> birthdays = new HashMap<>();
    private File dataFile;
    private String birthdayCommand;
    private int taskId = -1; // Declare taskId as a class-level variable

    @Override
    public void onEnable() {
        loadConfig();
        loadBirthdays();

        // Schedule the task to run every day at 00:00 (midnight)
        scheduleDailyTask();

        // Set up command executor and tab completer
        getCommand("birthday").setExecutor(this);
        getCommand("birthday").setTabCompleter(this);

        getLogger().info("Birthdays enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel the scheduled task if it was previously scheduled
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            getLogger().info("Scheduled task canceled.");
        }

        // Unregister command and auto-completion
        getCommand("birthday").setExecutor(null);
        getCommand("birthday").setTabCompleter(null);

        // Save birthdays
        saveBirthdays();

        getLogger().info("Birthdays disabled!");
    }

    private void scheduleDailyTask() {
        // Get the system default timezone
        ZoneId zone = ZoneId.systemDefault();

        // Get the current time in the system timezone
        Instant now = Instant.now(Clock.system(zone));

        // Calculate the delay until next midnight
        Instant nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        long delay = nextMidnight.toEpochMilli() - now.toEpochMilli();

        // Schedule the task to run at midnight and repeat every 24 hours
        taskId = Bukkit.getScheduler().runTaskTimer(this, this::checkBirthdays, delay / 50L, 24 * 60 * 60 * 20L).getTaskId(); // Convert milliseconds to ticks, schedule the task and store the task ID
        getLogger().info("Task scheduled.");
    }


    private void loadConfig() {
        getLogger().info("Loading configuration...");
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        birthdayCommand = config.getString("birthday_command", "say Today is the birthday of %player%!");
        getLogger().info("Configuration loaded!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("birthday")) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /birthday <set|list|remove|get>");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            // Map abbreviations to full subcommands
            Map<String, String> subCommandMap = new HashMap<>();
            subCommandMap.put("s", "set");
            subCommandMap.put("l", "list");
            subCommandMap.put("r", "remove");
            subCommandMap.put("g", "get");

            // If the provided subcommand is an abbreviation, replace it with the full subcommand
            if (subCommandMap.containsKey(subCommand)) {
                subCommand = subCommandMap.get(subCommand);
            }

            switch (subCommand) {
                case "set":
                    if (!sender.hasPermission("birthdays.set") && !sender.isOp()) {
                        sender.sendMessage("You don't have permission to use this command!");
                        return true;
                    }

                    if (args.length != 3) {
                        sender.sendMessage("Usage: /birthday set <player> <birthday>");
                        return true;
                    }

                    // Extract player name and birthday from command arguments
                    String setPlayerName = args[1];
                    String birthday = args[2];

                    // Set the player's birthday
                    boolean setSuccess = setPlayerBirthday(setPlayerName, birthday);
                    if (setSuccess) {
                        sender.sendMessage("Birthday for " + setPlayerName + " set successfully!");
                    } else {
                        sender.sendMessage("Failed to set birthday for " + setPlayerName + "! Player not found or invalid birthday format. Make sure to use the birthday format 'MM-dd'.");
                    }
                    return true;

                case "list":
                    if (!sender.hasPermission("birthdays.list") && !sender.isOp()) {
                        sender.sendMessage("You don't have permission to use this command!");
                        return true;
                    }

                    // List known birthdays
                    List<String> birthdayList = listBirthdays();
                    getLogger().info("Sending list...");
                    sender.sendMessage("Birthdays:");
                    for (String entry : birthdayList) {
                        sender.sendMessage(entry);
                    }
                    getLogger().info("List sent.");
                    return true;

                case "remove":
                    if (!sender.hasPermission("birthdays.remove") && !sender.isOp()) {
                        sender.sendMessage("You don't have permission to use this command!");
                        return true;
                    }

                    if (args.length != 2) {
                        sender.sendMessage("Usage: /birthday remove <player>");
                        return true;
                    }

                    // Remove the player's birthday
                    String removePlayerName = args[1];
                    boolean removeSuccess = removePlayerBirthday(removePlayerName);
                    if (removeSuccess) {
                        sender.sendMessage("Birthday for " + removePlayerName + " removed successfully!");
                    } else {
                        sender.sendMessage("Failed to remove birthday for " + removePlayerName + "! Player not found.");
                    }
                    return true;

                case "get":
                    if (!sender.hasPermission("birthdays.get") && !sender.isOp()) {
                        sender.sendMessage("You don't have permission to use this command!");
                        return true;
                    }

                    if (args.length != 2) {
                        sender.sendMessage("Usage: /birthday get <player>");
                        return true;
                    }

                    // Get the player's birthday
                    String getPlayerName = args[1];
                    String getBirthday = getPlayerBirthday(getPlayerName);
                    if (getBirthday != null) {
                        sender.sendMessage("Birthday of " + getPlayerName + ": " + getBirthday);
                    } else {
                        sender.sendMessage("No birthday found for " + getPlayerName);
                    }
                    return true;

                default:
                    sender.sendMessage("Invalid subcommand. Usage: /birthday <set|list|remove|get>");
                    return true;
                }
            }
        return false;
    }

    private void loadBirthdays() {
        getLogger().info("Loading birthdays...");
        dataFile = new File(getDataFolder(), "birthdays.json");
        if (!dataFile.exists()) {
            try {
                getLogger().info("Creating birthdays.json...");
                dataFile.createNewFile();
                getLogger().info("Successfully created birthdays.json!");
            } catch (IOException e) {
                getLogger().severe("Failed to create birthdays.json!");
                e.printStackTrace();
            }
            return;
        }

        // Load from JSON
        JSONObject json = Utils.loadJSONFromFile(dataFile);
        if (json != null) {
            for (Object key : json.keySet()) {
                String uuidString = (String) key;
                String birthday = (String) json.get(uuidString);
                birthdays.put(UUID.fromString(uuidString), birthday);
            }
        }
    }

    private void saveBirthdays() {
        getLogger().info("Saving birthdays to file...");
        JSONObject json = new JSONObject();
        for (UUID uuid : birthdays.keySet()) {
            json.put(uuid.toString(), birthdays.get(uuid));
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write(json.toJSONString());
        } catch (IOException e) {
            getLogger().severe("Failed to save birthdays to file!");
            e.printStackTrace();
        }
    }

    private void executeBirthdayCommand(UUID playerId) {
        getLogger().info("Executing birthday command...");
        String command = birthdayCommand.replace("%player%", getServer().getOfflinePlayer(playerId).getName());
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    // Set the player's birthday
    private boolean setPlayerBirthday(String setPlayerName, String birthday) {
        getLogger().info("Setting player's birthday for player '" + setPlayerName + "'...");

        // Check if the birthday format is valid (format = 'MM-dd')
        if (!isValidDateFormat(birthday)) {
        getLogger().warning("Failed to set birthday for " + setPlayerName + ". Invalid birthday format.");
        return false;
        }

        // Check if the player is online
        Player player = getServer().getPlayerExact(setPlayerName);
        if (player != null) {
            UUID playerId = player.getUniqueId();
            birthdays.put(playerId, birthday);
            saveBirthdays(); // Save birthdays after adding or updating
            getLogger().info("Birthday for player '" + setPlayerName + "' set to '" + birthday + "'.");
            return true;
        } else {
            // Player is offline, attempt to retrieve UUID
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(setPlayerName);
            if (offlinePlayer.hasPlayedBefore()) {
                UUID playerId = offlinePlayer.getUniqueId();
                birthdays.put(playerId, birthday);
                saveBirthdays(); // Save birthdays after adding or updating
                getLogger().info("Birthday for player '" + setPlayerName + "' set to '" + birthday + "'.");
                return true;
            } else {
                getLogger().warning("Player '" + setPlayerName + "' not found or never played on this server.");
                return false;
            }
        }
    }

    private boolean isValidDateFormat(String date) {
        // The expected format is "MM-dd"
        getLogger().info("Validating date format...");
        return date.matches("\\d{2}-\\d{2}");
    }

    // Check all birthdays and execute the configured command if the system date matches someone's birthday
    private void checkBirthdays() {
        getLogger().info("Checking birthdays...");
        for (UUID playerId : birthdays.keySet()) {
            String birthday = birthdays.get(playerId);
            // Check if the system date matches someone's birthday
            // The date format is 'MM-dd'
            String today = Utils.getCurrentDate();
            if (today.equals(birthday)) {
                executeBirthdayCommand(playerId);
            }
            getLogger().info("Checked birthday of player with UUID '" + playerId + "'!");
        }
    }

    // List all birthdays in alphabetical order
    private List<String> listBirthdays() {
        // Convert 'playerList' from 'List' to 'ArrayList'
        List<String> playerList = new ArrayList<>();

        // Adding player names to list
        getLogger().info("Looking up players and putting them in a list...");
        for (UUID playerId : birthdays.keySet()) {
            String listPlayerName = getServer().getOfflinePlayer(playerId).getName();
            getLogger().info("Found '" + listPlayerName + "'!");
            playerList.add(listPlayerName);
            getLogger().info("Added '" + listPlayerName + "' to list!");
        }

        // Convert 'playerList' from 'ArrayList' to 'Array'
        String[] playerArray = playerList.toArray(new String[playerList.size()]);

        // Sort the list alphabetically
        Arrays.sort(playerArray, String.CASE_INSENSITIVE_ORDER);
        getLogger().info("Players sorted.");

        // Convert the sorted array back to ArrayList
        playerList = new ArrayList<>(Arrays.asList(playerArray));

        // Make the final list
        List<String> birthdayList = new ArrayList<>();
        getLogger().info("Adding players and birthdays to list...");
        for (String playerName : playerList) {
            birthdayList.add(playerName + " - " + birthdays.get(getServer().getOfflinePlayer(playerName).getUniqueId()));
            getLogger().info("Added '" + playerName + " - " + birthdays.get(getServer().getOfflinePlayer(playerName).getUniqueId()) + "' to list!");
        }
        getLogger().info("Players and birthdays added to list.");

        return birthdayList;
    }

    // Remove the player's birthday
    private boolean removePlayerBirthday(String removePlayerName) {
        getLogger().info("Removing birthday of player '" + removePlayerName + "'...");

        // Check if the player is online
        Player player = getServer().getPlayerExact(removePlayerName);
        if (player != null) {
            UUID playerId = player.getUniqueId();
            if (birthdays.containsKey(playerId)) {
                birthdays.remove(playerId);
                saveBirthdays();
                getLogger().info("Birthday for player '" + removePlayerName + "' removed.");
                return true;
            } else {
                getLogger().warning("No birthday found for player '" + removePlayerName + "'.");
                return false;
            }
        } else {
            // Player is offline, attempt to retrieve UUID
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(removePlayerName);
            if (offlinePlayer.hasPlayedBefore()) {
                UUID playerId = offlinePlayer.getUniqueId();
                if (birthdays.containsKey(playerId)) {
                    birthdays.remove(playerId);
                    saveBirthdays();
                    getLogger().info("Birthday for player '" + removePlayerName + "' removed.");
                    return true;
                } else {
                    getLogger().warning("No birthday found for player '" + removePlayerName + "'.");
                    return false;
                }
            } else {
                getLogger().warning("Player '" + removePlayerName + "' not found or never played on this server.");
                return false;
            }
        }
    }

    // Get the player's birthday
    private String getPlayerBirthday(String getPlayerName) {
        getLogger().info("Getting birthday of player '" + getPlayerName + "'...");

        // Check if the player is online
        Player player = Bukkit.getPlayerExact(getPlayerName);
        if (player != null) {
            UUID playerId = player.getUniqueId();
            String playerBirthday = birthdays.get(playerId);
            if (playerBirthday != null) {
                getLogger().info("Birthday of " + getPlayerName + ": " + playerBirthday);
                return playerBirthday;
            } else {
                getLogger().warning("No birthday found for " + getPlayerName);
                return null;
            }
        } else {
            // Player is offline, attempt to retrieve UUID
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(getPlayerName);
            if (offlinePlayer.hasPlayedBefore()) {
                UUID playerId = offlinePlayer.getUniqueId();
                String playerBirthday = birthdays.get(playerId);
                if (playerBirthday != null) {
                    getLogger().info("Birthday of " + getPlayerName + ": " + playerBirthday);
                    return playerBirthday;
                } else {
                    getLogger().warning("No birthday found for " + getPlayerName);
                    return null;
                }
            } else {
                getLogger().warning("Player '" + getPlayerName + "' not found or never played on this server.");
                return null;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("birthday")) {
            if (args.length == 1) {
                // If no arguments are provided after "/birthday", suggest subcommands
                List<String> subCommands = new ArrayList<>();
                subCommands.add("set");
                subCommands.add("list");
                subCommands.add("remove");
                subCommands.add("get");
                return subCommands;
            } else if (("get".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]) || "g".equalsIgnoreCase(args[0]) || "r".equalsIgnoreCase(args[0])) && args.length == 2) {
                // If two arguments are provided after "/birthday" and the first argument is "get" or "remove",
                // provide auto-completion based on player names from the birthdays list
                
                getLogger().info("Populating player names for auto-completion...");

                // Convert 'birthdayPlayerNames' from 'List' to 'ArrayList'
                List<String> birthdayPlayerNames = new ArrayList<>();

                // Adding player names to auto-completion list
                getLogger().info("Looking up player names...");
                for (UUID playerId : birthdays.keySet()) {
                    String suggestPlayerName = getServer().getOfflinePlayer(playerId).getName();
                    getLogger().info("Found '" + suggestPlayerName + "'!");
                    birthdayPlayerNames.add(suggestPlayerName);
                    getLogger().info("Added player name to auto-completion list: " + suggestPlayerName);
                }

                // Convert 'birthdayPlayerNames' from 'ArrayList' to 'Array'
                String[] birthdayPlayerNamesArray = birthdayPlayerNames.toArray(new String[birthdayPlayerNames.size()]);

                // Sort the list alphabetically
                Arrays.sort(birthdayPlayerNamesArray, String.CASE_INSENSITIVE_ORDER);
                getLogger().info("Auto-completion list sorted.");

                // Convert the sorted array back to ArrayList
                birthdayPlayerNames = new ArrayList<>(Arrays.asList(birthdayPlayerNamesArray));

                getLogger().info("Auto-completion list populated: " + birthdayPlayerNames);
                return birthdayPlayerNames;
            } else if (("set".equalsIgnoreCase(args[0]) || "s".equalsIgnoreCase(args[0])) && args.length == 2) {
                // If two arguments are provided after "/birthday" and the first argument is "set" or "s"
                if (args[1].isEmpty()) {
                    List<String> onlinePlayerNames = new ArrayList<>();
                    for (Player player : getServer().getOnlinePlayers()) {
                        onlinePlayerNames.add(player.getName());
                    }
                    return onlinePlayerNames;
                }
            } else if (("set".equalsIgnoreCase(args[0]) || "s".equalsIgnoreCase(args[0])) && args.length == 3) {
                // If three arguments are provided after "/birthday" and the first argument is "set" or "s"
                return Collections.emptyList(); // Do not suggest anything
            } else {
                getLogger().info("No conditions matched for auto-completion.");
                // If more than two arguments are provided after "/birthday"
                return Collections.emptyList(); // Do not suggest anything
            }
        }
        return null; // Return null if no suggestions are available
    }
}