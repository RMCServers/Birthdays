import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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

public class BirthdayPlugin extends JavaPlugin implements CommandExecutor {
    
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
            if (!sender.hasPermission("birthday.set") && !(sender instanceof ConsoleCommandSender) && !(sender.isOp())) {
                sender.sendMessage("You don't have permission to use this command!");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage("Usage: /setbirthday <player> <birthday>");
                return true;
            }

            // Check if the player is online
            Player targetPlayer = getServer().getPlayer(args[0]);
            if (targetPlayer == null) {
                sender.sendMessage("Player not found or not online!");
                return true;
            }

            String playerName = targetPlayer.getName();
            String birthday = args[1];
            birthdays.put(targetPlayer.getUniqueId(), birthday);
            saveBirthdays();
            sender.sendMessage("Birthday for " + playerName + " set successfully!");
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
            e.printStackTrace();
        }
    }

    private void executeBirthdayCommand(UUID playerId) {
        String command = birthdayCommand.replace("%player%", getServer().getOfflinePlayer(playerId).getName());
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
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