package nl.rmcservers.birthdays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChainFactory;
import co.aikar.taskchain.TaskChain;
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

import org.json.simple.JSONObject;
import org.bukkit.configuration.file.FileConfiguration;

import nl.rmcservers.birthdays.Utils;

public class Birthdays extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Map<UUID, String> birthdays = new HashMap<>();
    private File dataFile;
    private String birthdayCommand;
    private TaskChainFactory taskChainFactory;

    @Override
    public void onEnable() {
        loadConfig();
        loadBirthdays();

        // Initialize TaskChainFactory
        taskChainFactory = BukkitTaskChainFactory.create(this);

        // Set up command executor
        getCommand("birthday").setExecutor(this);
        getCommand("birthday").setTabCompleter(this);

        // Calculate the time until midnight
        Calendar now = Calendar.getInstance();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);

        long delayUntilMidnight = midnight.getTimeInMillis() - now.getTimeInMillis();

        // Schedule the task to execute at midnight
        taskChainFactory.newSharedChain("BirthdayCheck")
                .delay((int) delayUntilMidnight)
                .execute(this::checkBirthdays);
    }


    private void loadConfig() {
        getLogger().info("Loading configuration...");
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        birthdayCommand = config.getString("birthday_command", "luckperms user %player% parent addtemp jarig 24h accumulate");
        getLogger().info("Configuration loaded!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("birthday")) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /birthday <set|list|remove>");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "set":
                    if (!sender.hasPermission("birthday.set") && !sender.isOp()) {
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
                        sender.sendMessage("Failed to set birthday for " + setPlayerName + "! Player not found or invalid birthday format. Make sure to use the birthday format 'MM-DD'.");
                    }
                    return true;

                case "list":
                    if (!sender.hasPermission("birthday.list") && !sender.isOp()) {
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
                    if (!sender.hasPermission("birthday.remove") && !sender.isOp()) {
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
                default:
                    sender.sendMessage("Invalid subcommand. Usage: /birthday <set|list|remove>");
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
                getLogger().severe("Failed to create birthdays.json file!");
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
    public boolean setPlayerBirthday(String setPlayerName, String birthday) {
        getLogger().info("Setting player's birthday for player '" + setPlayerName + "'...");

        // Check if the birthday format is valid (format = 'MM-DD')
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
        // The expected format is "MM-DD"
        getLogger().info("Validating date format...");
        return date.matches("\\d{2}-\\d{2}");
    }

    // Check birthdays and execute command if it's someone's birthday
    public void checkBirthdays() {
        getLogger().info("Checking birthdays...");
        for (UUID playerId : birthdays.keySet()) {
            String birthday = birthdays.get(playerId);
            // Check if it's today
            // For simplicity, let's assume the birthday format is 'MM-DD'
            String today = Utils.getCurrentDate();
            if (today.equals(birthday)) {
                executeBirthdayCommand(playerId);
            }
            getLogger().info("Checked birthday of player '" + playerId + "'!");
        }
    }

    // List all birthdays in alphabetical order
    private List<String> listBirthdays() {
        getLogger().info("Looking up birthdays...");
        List<String> birthdayList = new ArrayList<>();
        getLogger().info("Birthdays acquired.");
        getLogger().info("Listing birthdays...");
        for (UUID playerId : birthdays.keySet()) {
            String listPlayerName = getServer().getOfflinePlayer(playerId).getName();
            String birthday = birthdays.get(playerId);
            birthdayList.add(listPlayerName + " - " + birthday);
        }
        getLogger().info("Birthdays listed.");
        getLogger().info("Sorting birthdays...");
        Collections.sort(birthdayList);
        getLogger().info("Birthdays sorted.");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("birthday")) {
            if (args.length == 1) {
                // If no arguments are provided after "/birthday", suggest subcommands
                List<String> subCommands = new ArrayList<>();
                subCommands.add("set");
                subCommands.add("list");
                subCommands.add("remove");
                return subCommands;
            } else if (args.length == 2) {
                // If one argument is provided after "/birthday", suggest online player names
                List<String> onlinePlayerNames = new ArrayList<>();
                for (Player player : getServer().getOnlinePlayers()) {
                    onlinePlayerNames.add(player.getName());
                }
                return onlinePlayerNames;
            }
        }
        return null; // Return null if no suggestions are available
    }
}