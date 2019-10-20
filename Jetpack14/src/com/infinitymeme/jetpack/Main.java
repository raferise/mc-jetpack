package com.infinitymeme.jetpack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;
import net.minecraft.server.v1_14_R1.PacketPlayOutTitle;
import net.minecraft.server.v1_14_R1.IChatBaseComponent.ChatSerializer;
import net.minecraft.server.v1_14_R1.PacketPlayOutTitle.EnumTitleAction;

public class Main extends JavaPlugin implements Listener {
	
	public static int ENGAGE_DELAY = 15;
	public static double BRAKE_THRESHOLD = 0.18;
	public static double BRAKE_SPEED = 0.8;
	
	public static double FLIGHT_SPEED = 0.4;
	
	public static int MAX_FUEL = 256;
	
	public static Color jetcolor = Color.fromRGB(255, 224, 128);
	
	private ItemStack jetpack;
	
	private ArrayList<Player> flying;
	
	@Override
	public void onEnable() {
		loadJetpack();
		flying = new ArrayList<Player>();
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		
	}
	
	@EventHandler
	public void onInvClick(InventoryClickEvent e) {
		Player p = (Player) e.getWhoClicked();
		if ((e.getSlotType().equals(SlotType.ARMOR))&&(e.getCursor() != null)&&(e.getCursor().getType().equals(Material.BLAZE_POWDER))) {
			ItemStack chest = e.getCurrentItem();
			if ((chest != null)&&(std(chest).equals(jetpack))) {
				int fuel = getlorefuel(chest);
				e.setCancelled(true);
				int oncursor;
				if (e.isLeftClick()) oncursor = e.getCursor().getAmount();
				else oncursor = 1;
				if (fuel+oncursor > MAX_FUEL) {
					oncursor = MAX_FUEL-fuel;
				}
				fuel += oncursor;
				percentActionBar(p, fuel, MAX_FUEL);
				e.setCurrentItem(setlorefuel(chest, fuel));
				e.getCursor().setAmount(e.getCursor().getAmount()-oncursor);
				p.updateInventory();
			}
		}
		
		Inventory inv = e.getInventory();
		if (inv instanceof AnvilInventory) {
			InventoryView view = e.getView();
			int rawSlot = e.getRawSlot();
			if ((rawSlot == view.convertSlot(rawSlot))&&(rawSlot == 2)) {
				ItemStack it = ((AnvilInventory)inv).getContents()[0];
				if ((it != null)&&(std(it).equals(jetpack))) {
					e.setCancelled(true);
				}
			}
		} else if (inv instanceof GrindstoneInventory) {
			InventoryView view = e.getView();
			int rawSlot = e.getRawSlot();
			if ((rawSlot == view.convertSlot(rawSlot))&&(rawSlot == 2)) {
				for (int i=0; i<2; i++) {
					ItemStack it = ((GrindstoneInventory)inv).getContents()[i];
					if ((it != null)&&(std(it).equals(jetpack))) {
						e.setCancelled(true);
					}
				}
			}

		}
	}
	
	@EventHandler
	public void onSneakToggle(PlayerToggleSneakEvent e) {
		Player p = e.getPlayer();
		if ((p.isSprinting())&&(!p.isSwimming())&&(!p.isGliding())&&(!flying.contains(p))) {
			ItemStack chest = p.getInventory().getChestplate();
			if ((chest != null)&&(std(chest).equals(jetpack))) {
				if (!e.isSneaking()) {
					if (getlorefuel(chest) > 0) {
						flying.add(p);
						p.setGravity(false);
						p.setVelocity(p.getVelocity().add(new Vector(0,0.6,0)).add(p.getLocation().getDirection().multiply(0.4)));
						//TODO SFX LAUNCH
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1, 2);
						p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 2);
						p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1, 1.2f);
						for (int i=1; i<=9; i++) {
							Bukkit.getScheduler().runTaskLater(this, new Runnable() {public void run() {
								doParticles(p.getLocation(), 2);
							}},i);
						}
						Bukkit.getScheduler().runTaskLater(this, new Runnable() {public void run() {
							flightLoop(p, p.getLocation(), 0,true);
						}},10);
					} else {
						actionBar("&cOut of fuel.", p);
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1, 0.8f);
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1, 2);
					}
				} else {
					p.getWorld().playSound(p.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1, 1.2f);
					Bukkit.getScheduler().runTaskLater(this, new Runnable() {public void run() {
						p.getWorld().playSound(p.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1, 1.3f);
					}},3);
				}
			}
		}
	}
	
	public void flightLoop(Player p, Location last, int tick, boolean dash) {
		ItemStack chest = p.getInventory().getChestplate();
		if ((chest != null)&&(std(chest).equals(jetpack)&&(!p.isOnGround())&&(!p.isSwimming())&&(!p.isGliding())&&(p.isOnline()))) {
			int initfuel = getlorefuel(chest);
			int fuel = initfuel+0;
			if (fuel > 0) {
				p.setFallDistance(0);
				last.add(p.getVelocity());
				//TODO fuel shit
				percentActionBar(p, fuel, MAX_FUEL);
				if (p.isSneaking()) {
					if (tick == 0) fuel--;
					if (tick%60==0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, 1.2f);
					if (p.getVelocity().length() > BRAKE_THRESHOLD) {
						p.setVelocity(p.getVelocity().multiply(BRAKE_SPEED));
						doParticles(p.getLocation(), 1);
					} else {
						p.setVelocity(new Vector(0,0,0));
						dash = false;
						doParticles(p.getLocation(), 0);
					}
				} else if (p.isSprinting()) {
					if (tick%5 == 0) fuel--;
					doParticles(p.getLocation().subtract(p.getLocation().getDirection().multiply(2)), 1);
					dash = true;
					p.setVelocity(p.getVelocity().multiply(0.5).add(p.getLocation().getDirection().multiply(FLIGHT_SPEED)));
					p.getWorld().playSound(p.getLocation().add(p.getVelocity().multiply(5)), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, (float)((p.getVelocity().length()/2.0) + 1.5));
					if (p.getVelocity().length() > 1) {
						p.setVelocity(p.getVelocity().normalize());
					}
				} else if (!dash) { 
					if ((p.getVelocity().length()<0.18)&&(p.getVelocity().length()!=0)) {
						if (tick == 0) fuel--;
						doParticles(p.getLocation(),0);
						if (tick%60==0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, 1.2f);
						p.setVelocity(new Vector(0,0,0));
					} else {
						double distance = p.getLocation().distance(last);
						if ((distance > 0.02)&&(distance < 0.1)) {
							doParticles(p.getLocation(),0);
//							p.sendMessage(p.getVelocity().length()+" "+last.distance(p.getLocation()));
							if (tick%250 == 0) fuel--;
							p.setVelocity(homingvector(last,p.getLocation()).multiply(0.2));
							p.getWorld().playSound(p.getLocation().add(p.getVelocity().multiply(5)), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, (float)((p.getVelocity().length()/2.0) + 1.5));
						} else {
							doParticles(p.getLocation(),0);
							if (tick == 0) fuel--;
							if (tick%60==0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, 1.2f);
						}
					}
				} else if (p.getVelocity().length() <= 0.05) {
					p.setVelocity(new Vector(0,0,0));
					doParticles(p.getLocation(),0);
					if (tick == 0) fuel--;
					if (tick%60==0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, 1.2f);
					dash = false;
				} else {
					doParticles(p.getLocation(),0);
					if (tick == 0) fuel--;
					if (tick%60==0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.2f, 1.2f);
				}
				final Location here = p.getLocation();
				final boolean d = dash;
				if (fuel != initfuel) p.getInventory().setChestplate(setlorefuel(chest, fuel));
				Bukkit.getScheduler().runTaskLater(this, new Runnable() {public void run() {
					flightLoop(p,here,countup(tick,0,500),d);
				}},1);
			} else {
				p.setGravity(true);
				flying.remove(p);
				actionBar("&cOut of fuel.", p);
				doParticles(p.getLocation(),2);
				p.getWorld().playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1, 0.8f);
				p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1, 2);
			}
		} else {
			p.setGravity(true);
			flying.remove(p);
			doParticles(p.getLocation(),2);
			p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1, 2);
		}
	}
	
	public void doParticles(Location l, int smokesize) {
		Particle[] smokes = {Particle.SMOKE_NORMAL, Particle.SMOKE_LARGE, Particle.EXPLOSION_NORMAL};
		for (int i=-60; i<=60; i+=120) {
			Location loc = l.clone();
			loc.setYaw(loc.getYaw()+i);
			Vector facing = loc.getDirection();
			facing.setY(0);
			facing.normalize().multiply(0.4);
			facing.setY(-1.4);
			Location floc = l.clone().subtract(facing);
			l.getWorld().spawnParticle(Particle.FLAME, floc, 0, 0, -1, 0, 0.1);
			l.getWorld().spawnParticle(smokes[smokesize], floc.subtract(0,0.25,0), 0, 0, -1, 0, 0.1);
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (!e.getPlayer().hasGravity()) {
			e.getPlayer().setGravity(true);
		}
	}
	
	public ItemStack std(ItemStack item) {
		return (stdAttrib(stdColor(stdLore(item))));
	}
	
	public ItemStack stdColor(ItemStack item) {
		ItemStack i = item.clone();
		ItemMeta meta = i.getItemMeta();
		if ((meta != null)&&(meta instanceof LeatherArmorMeta)) {
			((LeatherArmorMeta) meta).setColor(jetcolor);
			i.setItemMeta(meta);
		}
		return i;
	}
	
	public ItemStack stdLore(ItemStack item) {
		ItemStack c = item.clone();
		ItemMeta meta = c.getItemMeta();
		if (meta != null) {
			List<String> lore = meta.getLore();
			if ((lore != null)&&(lore.size() >= 5)) {
				lore.remove(4);
				meta.setLore(lore);
				c.setItemMeta(meta);
				return c;
			}
		}
		return item;
	}
	
	public ItemStack stdAttrib(ItemStack item) {
		ItemStack i = item.clone();
		ItemMeta meta = i.getItemMeta();
		if (meta != null) {
			meta.removeAttributeModifier(EquipmentSlot.CHEST);
			i.setItemMeta(meta);
		}
		return i;
	}
	
	public Vector homingvector(Location l1, Location l2) {
		Vector v = l2.toVector().subtract(l1.toVector());
		v = v.multiply(1/(l1.toVector().distance(l2.toVector())));
		return v;
	}
	
	private int countup(int i, int min, int max) {
		i++;
		if (i >= max) return i=min;
		return i;
	}
	
	public int getlorefuel(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		List<String> lore = meta.getLore();
		if (lore.size() >= 5) {
			String loreline = lore.get(4);
			if (loreline.contains("FUEL: ")) {
				return Integer.parseInt(loreline.substring(12,loreline.length()));
			}
		}
		return -1;
	}
	
	public ItemStack setlorefuel(ItemStack item, int fuel) {
		ItemStack c = item.clone();
		ItemMeta meta = c.getItemMeta();
		List<String> lore = meta.getLore();
		if (lore.size() >= 5) {
			lore.set(4, ChatColor.RESET +""+ ChatColor.GRAY +""+ ChatColor.ITALIC +"FUEL: "+fuel);
			meta.setLore(lore);
			c.setItemMeta(meta);
			return c;
		}
		return item;
	}
	
	public void percentActionBar(Player p, int value, int max) {
		String o;
		if (value >= max) o="&4[&e|||||||||||||||||||||||||||||||||||||||||||||||||&4]";
		else {
			o= "&c[";
			for (int i=1; i<50; i++) {
				if (value >= max*i/50) o+="&e|";
				else o+="&7|";
			}
			o+="&c]";
			
		}
		actionBar(o+" &e&o"+String.format("%04d", value)+" FUEL", p);
	}
	
	public void actionBar(String msg, Player p) { 
		msg = msg.replace('&', '§');
		PacketPlayOutTitle packet = new PacketPlayOutTitle(
				EnumTitleAction.ACTIONBAR,
				ChatSerializer.a("{\"text\":\"" +msg+ "\"}"),
				5,
				10,
				5
				);
		((CraftPlayer)p).getHandle().playerConnection.sendPacket(packet);
	}
	
	public void loadJetpack() {
		NamespacedKey self = new NamespacedKey(this, "jetpack");
		ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
		LeatherArmorMeta meta = (LeatherArmorMeta)item.getItemMeta();
		meta.setDisplayName(ChatColor.RESET +""+ ChatColor.AQUA + "Jetpack");
		LinkedList<String> lore = new LinkedList<String>();
		lore.add(ChatColor.RESET +""+ ChatColor.GRAY +"[Sprint+Shift] to take off");
		lore.add(ChatColor.RESET +""+ ChatColor.GRAY +"[Sprint] to boost forwards");
		lore.add(ChatColor.RESET +""+ ChatColor.GRAY +"[Shift] to brake");
		lore.add(ChatColor.RESET +""+ ChatColor.GRAY +"[Inventory] click on with blaze powder to fuel");
		meta.setLore(lore);
		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		meta.setColor(jetcolor);
		item.setItemMeta(meta);
		
		ItemStack item_comparable = item.clone();
		this.jetpack = item_comparable;
		
		lore.add(ChatColor.RESET +""+ ChatColor.GRAY +""+ ChatColor.ITALIC +"FUEL: 0");
		meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "Armor", 0, Operation.ADD_NUMBER, EquipmentSlot.CHEST));
		meta.setLore(lore);
		item.setItemMeta(meta);
		
		ShapedRecipe recipe = new ShapedRecipe(self, item);
		recipe.shape("iii","fcf","drd");
		recipe.setIngredient('i', Material.IRON_INGOT);
		recipe.setIngredient('f', Material.FLINT);
		recipe.setIngredient('c', Material.LEATHER_CHESTPLATE);
		recipe.setIngredient('d', Material.DISPENSER);
		recipe.setIngredient('r', Material.REDSTONE);
		
		this.getServer().addRecipe(recipe);
	}

}
