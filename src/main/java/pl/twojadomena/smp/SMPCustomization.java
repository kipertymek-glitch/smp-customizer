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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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

    // --- ENFORCE ITEM AND POTION LIMITS ---
    private void enforceLimits(Player player) {
        checkAndDropLimit(player, Material.COBWEB, getConfig().getInt("limits.cobweb", 16));
        checkAndDropLimit(player, Material.GOLDEN_APPLE, getConfig().getInt("limits.golden_apple", 48));
        
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
            player.sendMessage(ChatColor.RED + "You have too many " + material.name() + "! Excess items dropped on the ground.");
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
            player.sendMessage(ChatColor.RED + "You have too many " + targetType.name() + " II potions! Excess items dropped on the ground.");
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

    // --- SMITHING TABLE NETHERITE BLOCKING ---
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result != null && isBlockedNetheriteItem(result.getType())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            getServer().getScheduler().runTask(this, () -> enforceLimits(player));

            if (event.getInventory().getType() == InventoryType.SMITHING) {
                if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                    ItemStack currentItem = event.getCurrentItem();
                    if (currentItem != null && isBlockedNetheriteItem(currentItem.getType())) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Crafting this Netherite item is blocked!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        Material result = event.getRecipe().getResult().getType();
        if (isBlockedNetheriteItem(result)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Crafting this Netherite item is blocked!");
            }
        }
    }

    private boolean isBlockedNetheriteItem(Material m) {
        if (!m.name().startsWith("NETHERITE_") || m == Material.NETHERITE_SWORD || m == Material.NETHERITE_INGOT || m == Material.NETHERITE_SCRAP) {
            return false;
        }

        if (getConfig().getBoolean("netherite.allow_crafting", false)) {
            return false;
        }

        String itemName = m.name().toLowerCase().replace("netherite_", "");
        if (getConfig().contains("netherite.blocked_items." + itemName)) {
            return getConfig().getBoolean("netherite.blocked_items." + itemName, true);
        }

        return true;
    }

    // --- WEAPON ATTACKS (MACE, TRIDENT, SPEAR) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            String matName = mainHand.getType().name();

            if (matName.equalsIgnoreCase("MACE")) {
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

            if (matName.contains("SPEAR")) {
                int cdSeconds = getConfig().getInt("cooldowns.spear", 0);
                if (cdSeconds > 0) {
                    if (player.hasCooldown(mainHand.getType())) {
                        event.setCancelled(true);
                        return;
                    }
                    player.setCooldown(mainHand.getType(), cdSeconds * 20);
                }
            }
        }
    }

    // --- BLOCK THROWING ENDER PEARLS WHEN SET TO OFF ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.ENDER_PEARL) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                boolean pearlEnabled = getConfig().getBoolean("limits.ender_pearl_enabled", true);
                if (!pearlEnabled) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Throwing Ender Pearls is disabled on this server!");
                }
            }
        }
    }

    // --- TRIDENT THROW COOLDOWN ---
    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident && event.getEntity().getShooter() instanceof Player player) {
            int cdSeconds = getConfig().getInt("cooldowns.trident", 0);
            if (cdSeconds > 0) {
                player.setCooldown(Material.TRIDENT, cdSeconds * 20);
            }
        }
    }

    // --- RIPTIDE COOLDOWN ---
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

    // --- CONFIG COMMAND ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /smpconfig [option] [value]");
            return true;
        }

        String option = args[0].toLowerCase();
        String val = args[1].toLowerCase();

        try {
            // Opcje typu ON / OFF (dla pereł i netheritu)
            if (option.equals("pearls") || option.startsWith("netherite")) {
                boolean state = val.equals("on") || val.equals("true") || val.equals("enable");
                
                if (option.equals("pearls")) {
                    getConfig().set("limits.ender_pearl_enabled", state);
                } else if (option.equals("netherite")) {
                    getConfig().set("netherite.allow_crafting", state);
                } else if (option.startsWith("netherite_")) {
                    String subItem = option.replace("netherite_", "");
                    getConfig().set("netherite.blocked_items." + subItem, state);
                }
            } 
            // Opcje z LICZBAMI (limity przedmiotów i cooldowny)
            else {
                int numberVal = Integer.parseInt(val);

                if (option.equals("cobweb")) {
                    getConfig().set("limits.cobweb", numberVal);
                } else if (option.equals("gapples")) {
                    getConfig().set("limits.golden_apple", numberVal);
                } else if (option.equals("strength_2")) {
                    getConfig().set("limits.strength_2", numberVal);
                } else if (option.equals("speed_2")) {
                    getConfig().set("limits.speed_2", numberVal);
                } else if (option.equals("mace_cooldown")) {
                    getConfig().set("cooldowns.mace", numberVal);
                } else if (option.equals("trident_cooldown")) {
                    getConfig().set("cooldowns.trident", numberVal);
                } else if (option.equals("spear_cooldown")) {
                    getConfig().set("cooldowns.spear", numberVal);
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown option!");
                    return true;
                }
            }

            saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Set " + option + " to " + val.toUpperCase() + "!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Invalid value!");
        }

        return true;
    }

    // --- TAB COMPLETER ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = Arrays.asList(
                "pearls",
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
                "gapples", 
                "strength_2", 
                "speed_2", 
                "mace_cooldown", 
                "trident_cooldown",
                "spear_cooldown"
            );
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
        } else if (args.length == 2) {
            // Perły i netherit podpowiadają ON / OFF
            if (args[0].toLowerCase().equals("pearls") || args[0].toLowerCase().startsWith("netherite")) {
                completions.addAll(Arrays.asList("on", "off"));
            } 
            // Przedmioty podpowiadają przykładowe ilości (np. 16, 32, 48, 64)
            else {
                completions.addAll(Arrays.asList("0", "1", "2", "3", "5", "10", "16", "32", "48", "64"));
            }
        }
        return completions;
    }
}
