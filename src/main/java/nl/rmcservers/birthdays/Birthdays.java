package nl.rmcservers.birthdays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.simple.JSONObject;
import org.bukkit.configuration.file.FileConfiguration;

import nl.rmcservers.birthdays.Utils;

public class Birthdays extends JavaPlugin implements CommandExecutor {

    private Map<UUID, String> birthdays = new HashMap<>();
    private File dataFile;
    private String birthdayCommand;

    @Override
    public void onEnable() {
        loadConfig();
        loadBirthdays();
        getCommand("setbirthday").setExecutor(this);
    }

    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        birthdayCommand = config.getString("birthday_command", "say This is a test, %player%!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setbirthday")) {
            if (!sender.hasPermission("birthday.set") && !sender.isOp()) {
                sender.sendMessage("You don't have permission to use this command!");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage("Usage: /setbirthday <player> <birthday>");
                return true;
            }

            // Extract player name and birthday from command arguments
            String playerName = args[0];
            String birthday = args[1];
            
            // Set the player's birthday
            boolean success = setPlayerBirthday(playerName, birthday);
            if (success) {
                sender.sendMessage("Birthday for " + playerName + " set successfully!");
            } else {
                sender.sendMessage("Failed to set birthday for " + playerName + ". Player not found or invalid birthday format.");
            }
            return true;
        }
        return false;
    }

    private void loadBirthdays() {
        dataFile = new File(getDataFolder(), "birthdays.json");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Failed to create birthdays.json file!");
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
        JSONObject json = new JSONObject();
        for (UUID uuid : birthdays.keySet()) {
            json.put(uuid.toString(), birthdays.get(uuid));
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write(json.toJSONString());
        } catch (IOException e) {
            getLogger().warning("Failed to save birthdays to file!");
            e.printStackTrace();
        }
    }

    private void executeBirthdayCommand(UUID playerId) {
        String command = birthdayCommand.replace("%player%", getServer().getOfflinePlayer(playerId).getName());
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    // Set the birthday for a player
    public boolean setPlayerBirthday(String playerName, String birthday) {
        UUID playerId = Utils.getPlayerUUID(playerName);
        if (playerId != null) {
            birthdays.put(playerId, birthday);
            saveBirthdays(); // Save birthdays after adding or updating
            return true;
        } else {
            return false; // Player not found
        }
    }

    // Check birthdays and execute command if it's someone's birthday
    public void checkBirthdays() {
        for (UUID playerId : birthdays.keySet()) {
            String birthday = birthdays.get(playerId);
            // Check if it's today
            // For simplicity, let's assume the birthday format is 'MM-DD'
            String today = Utils.getCurrentDate();
            if (today.equals(birthday)) {
                executeBirthdayCommand(playerId);
            }
        }
    }
}