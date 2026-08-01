package pl.twojadomena.smp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class SMPCustomization extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("smpconfig") != null) {
            getCommand("smpconfig").setExecutor(this);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!getConfig().getBoolean("netherite.blocked_except_sword")) return;
        Material result = event.getRecipe().getResult().getType();
        if (isNetheriteItem(result) && result != Material.NETHERITE_SWORD) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Tworzenie tego przedmiotu z Netheritu jest zablokowane!");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.ENDER_PEARL) {
            int max = getConfig().getInt("limits.ender_pearl");
            if (max <= 0 || countItems(player, Material.ENDER_PEARL) > max) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Uzywanie pereł jest zablokowane lub przekroczono limit!");
            }
        }

        if (item.getType() == Material.GOLDEN_APPLE) {
            int max = getConfig().getInt("limits.golden_apple");
            if (countItems(player, Material.GOLDEN_APPLE) > max) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Przekroczono limit złotych jabłek w ekwipunku!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (event.getBlockPlaced().getType() == Material.COBWEB) {
            int max = getConfig().getInt("limits.cobweb");
            if (countItems(player, Material.COBWEB) > max) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Przekroczono limit pajęczyn!");
            }
        }
    }

    private boolean isNetheriteItem(Material m) {
        return m.name().startsWith("NETHERITE_") && m != Material.NETHERITE_INGOT && m != Material.NETHERITE_SCRAP;
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /smpconfig [netherite/cobweb/pearls/gapples] [wartość]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "netherite" -> getConfig().set("netherite.blocked_except_sword", Boolean.parseBoolean(args[1]));
            case "cobweb" -> getConfig().set("limits.cobweb", Integer.parseInt(args[1]));
            case "pearls" -> getConfig().set("limits.ender_pearl", Integer.parseInt(args[1]));
            case "gapples" -> getConfig().set("limits.golden_apple", Integer.parseInt(args[1]));
        }
        saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Zapisano nową wartość!");
        return true;
    }
}
