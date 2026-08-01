package pl.twojadomena.smp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SMPCustomization extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("smpconfig") != null) {
            getCommand("smpconfig").setExecutor(this);
            getCommand("smpconfig").setTabCompleter(this);
        }
    }

    private void enforceLimits(Player player) {
        checkAndDropLimit(player, Material.COBWEB, getConfig().getInt("limits.cobweb", 16));
        checkAndDropLimit(player, Material.ENDER_PEARL, getConfig().getInt("limits.ender_pearl", 0));
        checkAndDropLimit(player, Material.GOLDEN_APPLE, getConfig().getInt("limits.golden_apple", 32));
    }

    private void checkAndDropLimit(Player player, Material material, int maxLimit) {
        int total = countItems(player, material);
        if (total > maxLimit) {
            int toRemove = total - maxLimit;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    int amount = item.getAmount();
                    if (amount <= toRemove) {
                        toRemove -= amount;
                        player.getInventory().removeItem(item);
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    } else {
                        item.setAmount(amount - toRemove);
                        ItemStack dropped = new ItemStack(material, toRemove);
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                        toRemove = 0;
                    }
                }
                if (toRemove <= 0) break;
            }
            player.sendMessage(ChatColor.RED + "Masz za duzo " + material.name() + "! Przedmioty spadly na ziemie.");
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            getServer().getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    enforceLimits(player);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            getServer().getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    enforceLimits(player);
                }
            });
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!getConfig().getBoolean("netherite.blocked_except_sword", true)) return;
        Material result = event.getRecipe().getResult().getType();
        if (isNetheriteItem(result) && result != Material.NETHERITE_SWORD) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(ChatColor.RED + "Tworzenie tego przedmiotu z netheritu jest zablokowane!");
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            Material mainHand = player.getInventory().getItemInMainHand().getType();

            if (mainHand.name().equalsIgnoreCase("MACE")) {
                int cdSeconds = getConfig().getInt("cooldowns.mace", 0);
                if (cdSeconds > 0) {
                    if (player.hasCooldown(mainHand)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Mace jest na cooldownie!");
                        return;
                    }
                    player.setCooldown(mainHand, cdSeconds * 20);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.TRIDENT) {
            int cdSeconds = getConfig().getInt("cooldowns.spear", 0);
            if (cdSeconds > 0) {
                if (player.hasCooldown(Material.TRIDENT)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Trójząb jest na cooldownie!");
                    return;
                }
                player.setCooldown(Material.TRIDENT, cdSeconds * 20);
            }
        }

        if (item.getType() == Material.ENDER_PEARL) {
            int max = getConfig().getInt("limits.ender_pearl", 0);
            if (max <= 0) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Perły są całkowicie wyłączone!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        enforceLimits(event.getPlayer());
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
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /smpconfig [netherite/cobweb/pearls/gapples/mace_cooldown/spear_cooldown] [wartość]");
            return true;
        }

        String option = args[0].toLowerCase();
        String val = args[1];

        try {
            if (option.equals("netherite")) {
                getConfig().set("netherite.blocked_except_sword", Boolean.parseBoolean(val));
            } else if (option.equals("cobweb")) {
                getConfig().set("limits.cobweb", Integer.parseInt(val));
            } else if (option.equals("pearls")) {
                getConfig().set("limits.ender_pearl", Integer.parseInt(val));
            } else if (option.equals("gapples")) {
                getConfig().set("limits.golden_apple", Integer.parseInt(val));
            } else if (option.equals("mace_cooldown")) {
                getConfig().set("cooldowns.mace", Integer.parseInt(val));
            } else if (option.equals("spear_cooldown")) {
                getConfig().set("cooldowns.spear", Integer.parseInt(val));
            } else {
                sender.sendMessage(ChatColor.RED + "Nieznana opcja!");
                return true;
            }

            saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Ustawiono " + option + " na " + val + "!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Błędna wartość!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        if (args.length == 1) {
            List<String> options = Arrays.asList("netherite", "cobweb", "pearls", "gapples", "mace_cooldown", "spear_cooldown");
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("netherite")) {
                completions.addAll(Arrays.asList("true", "false"));
            } else {
                completions.addAll(Arrays.asList("0", "5", "10", "16", "32"));
            }
        }
        return completions;
    }
}
