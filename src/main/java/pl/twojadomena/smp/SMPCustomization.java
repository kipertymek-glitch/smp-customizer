package pl.twojadomena.smp;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

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

    // --- SPRAWDZANIE LIMITÓW PRZEDMIOTÓW I POTEK ---
    private void enforceLimits(Player player) {
        checkAndDropLimit(player, Material.COBWEB, getConfig().getInt("limits.cobweb", 16));
        checkAndDropLimit(player, Material.ENDER_PEARL, getConfig().getInt("limits.ender_pearl", 0));
        checkAndDropLimit(player, Material.GOLDEN_APPLE, getConfig().getInt("limits.golden_apple", 32));
        
        checkAndDropPotionLimit(player, PotionType.STRENGTH, getConfig().getInt("limits.strength_2", 2));
        checkAndDropPotionLimit(player, PotionType.SWIFTNESS, getConfig().getInt("limits.speed_2", 2));
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
            player.sendMessage(ChatColor.RED + "Masz za dużo " + material.name() + "! Przedmioty spadły na ziemię.");
        }
    }

    private void checkAndDropPotionLimit(Player player, PotionType targetType, int maxLimit) {
        int total = countPotions(player, targetType);
        if (total > maxLimit) {
            int toRemove = total - maxLimit;
            for (ItemStack item : player.getInventory().getContents()) {
                if (isPotionOfTypeAndTier2(item, targetType)) {
                    int amount = item.getAmount();
                    if (amount <= toRemove) {
                        toRemove -= amount;
                        player.getInventory().removeItem(item);
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    } else {
                        item.setAmount(amount - toRemove);
                        ItemStack dropped = item.clone();
                        dropped.setAmount(toRemove);
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                        toRemove = 0;
                    }
                }
                if (toRemove <= 0) break;
            }
            player.sendMessage(ChatColor.RED + "Masz za dużo potek " + targetType.name() + " II! Przedmioty spadły na ziemię.");
        }
    }

    private boolean isPotionOfTypeAndTier2(ItemStack item, PotionType targetType) {
        if (item == null) return false;
        Material type = item.getType();
        if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
            if (item.getItemMeta() instanceof PotionMeta meta) {
                if (meta.getBasePotionData() != null) {
                    return meta.getBasePotionData().getType() == targetType && meta.getBasePotionData().isUpgraded();
                }
            }
        }
        return false;
    }

    private int countPotions(Player player, PotionType targetType) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isPotionOfTypeAndTier2(item, targetType)) {
                count += item.getAmount();
            }
        }
        return count;
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

    // --- BLOKOWANIE STOŁU KOWALSKIEGO DLA NETHERITU ---
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result != null && isBlockedNetheriteItem(result.getType())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        Material result = event.getRecipe().getResult().getType();
        if (isBlockedNetheriteItem(result)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Tworzenie tego przedmiotu z netheritu jest zablokowane!");
            }
        }
    }

    private boolean isBlockedNetheriteItem(Material m) {
        if (!m.name().startsWith("NETHERITE_") || m == Material.NETHERITE_SWORD || m == Material.NETHERITE_INGOT || m == Material.NETHERITE_SCRAP) {
            return false;
        }

        if (getConfig().getBoolean("netherite.blocked_except_sword", true)) {
            return true;
        }

        String itemName = m.name().toLowerCase().replace("netherite_", "");
        return getConfig().getBoolean("netherite.blocked_items." + itemName, false);
    }

    // --- ATAKI Z MACA ORAZ TRIDENTA ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand.getType().name().equalsIgnoreCase("MACE")) {
                int cdSeconds = getConfig().getInt("cooldowns.mace", 0);
                if (cdSeconds > 0) {
                    if (player.hasCooldown(mainHand.getType())) {
                        event.setCancelled(true);
                        return;
                    }
                    player.setCooldown(mainHand.getType(), cdSeconds * 20);
                }
            }

            if (mainHand.getType() == Material.TRIDENT) {
                int cdSeconds = getConfig().getInt("cooldowns.trident", 0);
                if (cdSeconds > 0) {
                    if (player.hasCooldown(Material.TRIDENT)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    // --- INNE INTERAKCJE (PERŁY) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.ENDER_PEARL) {
            int max = getConfig().getInt("limits.ender_pearl", 0);
            if (max <= 0) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Perły są całkowicie wyłączone!");
            }
        }
    }

    // --- NAKŁADANIE COOLDOWNU PO FAKTYCZNYM RZUCIE TRÓJZAŁEM ---
    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident && event.getEntity().getShooter() instanceof Player player) {
            int cdSeconds = getConfig().getInt("cooldowns.trident", 0);
            if (cdSeconds > 0) {
                player.setCooldown(Material.TRIDENT, cdSeconds * 20);
            }
        }
    }

    // --- NAKŁADANIE COOLDOWNU PO FAKTYCZNYM UŻYCIU RIPTIDE ---
    @EventHandler
    public void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        int cdSeconds = getConfig().getInt("cooldowns.trident", 0);
        if (cdSeconds > 0) {
            player.setCooldown(Material.TRIDENT, cdSeconds * 20);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        enforceLimits(event.getPlayer());
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    // --- KOMENDA KONFIGURACYJNA ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /smpconfig [opcja] [wartość]");
            return true;
        }

        String option = args[0].toLowerCase();
        String val = args[1];

        try {
            if (option.equals("netherite")) {
                getConfig().set("netherite.blocked_except_sword", Boolean.parseBoolean(val));
            } else if (option.startsWith("netherite_")) {
                String subItem = option.replace("netherite_", "");
                getConfig().set("netherite.blocked_items." + subItem, Boolean.parseBoolean(val));
            } else if (option.equals("cobweb")) {
                getConfig().set("limits.cobweb", Integer.parseInt(val));
            } else if (option.equals("pearls")) {
                getConfig().set("limits.ender_pearl", Integer.parseInt(val));
            } else if (option.equals("gapples")) {
                getConfig().set("limits.golden_apple", Integer.parseInt(val));
            } else if (option.equals("strength_2")) {
                getConfig().set("limits.strength_2", Integer.parseInt(val));
            } else if (option.equals("speed_2")) {
                getConfig().set("limits.speed_2", Integer.parseInt(val));
            } else if (option.equals("mace_cooldown")) {
                getConfig().set("cooldowns.mace", Integer.parseInt(val));
            } else if (option.equals("trident_cooldown")) {
                getConfig().set("cooldowns.trident", Integer.parseInt(val));
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

    // --- TAB COMPLETER ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = Arrays.asList(
                "netherite", 
                "netherite_helmet", 
                "netherite_chestplate", 
                "netherite_leggings", 
                "netherite_boots", 
                "netherite_pickaxe", 
                "netherite_axe", 
                "netherite_shovel", 
                "netherite_hoe", 
                "cobweb", 
                "pearls", 
                "gapples", 
                "strength_2", 
                "speed_2", 
                "mace_cooldown", 
                "trident_cooldown"
            );
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
        } else if (args.length == 2) {
            if (args[0].toLowerCase().startsWith("netherite")) {
                completions.addAll(Arrays.asList("true", "false"));
            } else {
                completions.addAll(Arrays.asList("0", "1", "2", "3", "5", "10", "16", "32"));
            }
        }
        return completions;
    }
}
