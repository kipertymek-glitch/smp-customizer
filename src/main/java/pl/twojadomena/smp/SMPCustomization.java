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

    // --- 1. SPRAWDZANIE LIMITÓW W EQ (Ekwipunek, Podnoszenie, Klikanie) ---

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
            player.sendMessage(ChatColor.RED + "Masz za dużo " + material.name() + " w eq! Nadmiar wyrzucono na ziemię (Limit: " + maxLimit + ").");
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            getServer().getScheduler().runTask(this, () -> enforceLimits(player));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            getServer().getScheduler().runTask(this, () -> enforceLimits(player));
        }
    }

    // --- 2. BLOKADA CRAFTINGU NETHERITU ---

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!getConfig().getBoolean("netherite.blocked_except_sword", true)) return;
        Material result = event.getRecipe().getResult().getType();
        if (isNetheriteItem(result) && result != Material.NETHERITE_SWORD) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Tworzenie tej części Netheritu jest zablokowane!");
            }
        }
    }

    // --- 3. COOLDOWN DLA MACE I SPEAR / TRIDENT oraz PERŁY / REFIE ---

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Material mainHand = player.getInventory().getItemInMainHand().getType();

            // Mace Cooldown
            if (mainHand == Material.MACE) {
                int cdSeconds = getConfig().getInt("cooldowns.mace", 0);
                if (cdSeconds > 0) {
                    if (player.hasCooldown(Material.MACE)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Buława (Mace) odnawia się!");
                        return;
                    }
                    player.setCooldown(Material.MACE, cdSeconds * 20);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // Spear (Trident) Cooldown przy użyciu/rzucie
        if (item.getType() == Material.TRIDENT) {
            int cdSeconds = getConfig().getInt("cooldowns.spear", 0);
            if (cdSeconds > 0) {
                if (player.hasCooldown(Material.TRIDENT)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Oszczep/Trójząb odnawia się!");
                    return;
                }
                player.setCooldown(Material.TRIDENT, cdSeconds * 20);
            }
        }

        // Blokada pereł przy kliknięciu jeśli limit = 0
        if (item.getType() == Material.ENDER_PEARL) {
            int max = getConfig().getInt("limits.ender_pearl", 0);
            if (max <= 0) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Perły Endera są całkowicie zablokowane!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        enforceLimits(event.getPlayer());
    }

    // --- POMOCNICZE METODY ---

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

    // --- 4. KOMENDA Z ZAPISYWANIEM USTAWIEŃ ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /smpconfig [netherite/cobweb/pearls/gapples/mace_cooldown/spear_cooldown] [wartość]");
            return true;
        }

        String option = args[0].toLowerCase();
        String val = args[1];

        try {
            switch (option) {
                case "netherite" -> getConfig().set("netherite.blocked_except_sword", Boolean.parseBoolean(val));
                case "cobweb" -> getConfig().set("limits.cobweb", Integer.parseInt(val));
                case "pearls" -> getConfig().set("limits.ender_pearl", Integer.parseInt(val));
                case "gapples" -> getConfig().set("limits.golden_apple", Integer.parseInt(val));
                case "mace_cooldown" -> getConfig().set("cooldowns.mace", Integer.parseInt(val));
                case "spear_cooldown" -> getConfig().set("cooldowns.spear", Integer.parseInt(val));
                default -> {
                    sender.sendMessage(ChatColor.RED + "Nieznana opcja!");
                    return true;
                }
            }
            saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Pomyślnie zmieniono " + option + " na " + val + "!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Niepoprawna wartość!");
        }

        return true;
    }

    // --- 5. TAB COMPLETER (Podpowiedzi komend) ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
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
