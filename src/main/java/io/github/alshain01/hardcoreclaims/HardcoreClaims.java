/* Copyright 2013 Kevin Seiden. All rights reserved.

 This works is licensed under the Creative Commons Attribution-NonCommercial 3.0

 You are Free to:
    to Share: to copy, distribute and transmit the work
    to Remix: to adapt the work

 Under the following conditions:
    Attribution: You must attribute the work in the manner specified by the author (but not in any way that suggests that they endorse you or your use of the work).
    Non-commercial: You may not use this work for commercial purposes.

 With the understanding that:
    Waiver: Any of the above conditions can be waived if you get permission from the copyright holder.
    Public Domain: Where the work or any of its elements is in the public domain under applicable law, that status is in no way affected by the license.
    Other Rights: In no way are any of the following rights affected by the license:
        Your fair dealing or fair use rights, or other applicable copyright exceptions and limitations;
        The author's moral rights;
        Rights other persons may have either in the work itself or in how the work is used, such as publicity or privacy rights.

 Notice: For any reuse or distribution, you must make clear to others the license terms of this work. The best way to do this is with a link to this web page.
 http://creativecommons.org/licenses/by-nc/3.0/
 */

package io.github.alshain01.hardcoreclaims;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.alshain01.flags.Flag;
import io.github.alshain01.flags.Flags;
import io.github.alshain01.flags.area.Default;
import io.github.alshain01.flags.area.GriefPreventionClaim;
import io.github.alshain01.flags.CuboidType;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Ocelot.Type;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * HardcoreClaims
 * 
 * @author Alshain01
 */
public class HardcoreClaims extends JavaPlugin {
	// Flags need to be Objects to guard against cases where
	// The Flags plugin in is not installed.  We will cast them back later.
	private Object hcFlag = null, delFlag = null;

	@Override
	public void onEnable() {
		// Required Plug-in Check
		if (!getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
			getLogger().info("Grief Prevention is not installed. HardcoreClaims is shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Register Non-Player Flags
		if (getServer().getPluginManager().isPluginEnabled("Flags")) {
			getLogger().info("Enabling Flags Integration");
			delFlag = Flags.getRegistrar()
					.register("HardcoreDeletion", "Toggles whether the player will lose hardcore claims if they die in the area.", true, getName());

			if (CuboidType.getActive() == CuboidType.GRIEF_PREVENTION) {
				hcFlag = Flags.getRegistrar()
						.register("HardcoreClaim", "Toggles the claim's hardcore status (area/default only).", true, getName());
			}
		}

		// Event Listeners
		getServer().getPluginManager().registerEvents(new Reaper(), this);
		getServer().getPluginManager().registerEvents(new ContainerGuard(), this);
		getServer().getPluginManager().registerEvents(new Orphanage(), this);

        try {
            MetricsLite metrics = new MetricsLite(this);
            metrics.start();
        } catch (IOException ex) {
            this.getLogger().info("Failed to Start Metrics");
        }
	}
	
	@Override
	public void onDisable() {
		HandlerList.unregisterAll(this);
	}
	
	private class ContainerGuard implements Listener {
		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		private void onBlockPlace(BlockPlaceEvent e) {
			if (e.getPlayer().hasPermission("hardcoreclaims.admin")) {
				return;
			}

			// If the world isn't configured for HardcoreClaims, ignore this
			if (hcFlag != null
					&& !new Default(e.getBlock().getWorld()).getValue((Flag) hcFlag, false)) {
				return;
			}

			// Block all container placement outside claims to prevent cheating
			switch (e.getBlock().getType()) {
			case CHEST:
				// Don't cancel it if they don't have a claim.
				if (GriefPrevention.instance.dataStore.getPlayerData(e.getPlayer().getName()).claims.size() == 0) {
					break;
				}
			case ENDER_CHEST:
			case TRAPPED_CHEST:
			case BREWING_STAND:
			case DISPENSER:
			case FURNACE:
			case HOPPER:
			case HOPPER_MINECART:
			case STORAGE_MINECART:
				final Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);
				if (claim == null || !claim.getOwnerName().equals(e.getPlayer().getName())) {
					e.getPlayer().sendMessage(ChatColor.RED	+ "You may not place containers outside your claim in hardcore worlds.");
					e.setCancelled(true);
				}
				break;
			default:
				break;
			}
		}
	}

	private class Reaper implements Listener {
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		private void onPlayerDeath(PlayerDeathEvent e) {
            Set<Claim> deleteClaims = new HashSet<Claim>();
            
			// Is the player in an area that would cause a hardcore deletion
			if (delFlag != null
					&& !CuboidType.getActive().getAreaAt(e.getEntity().getLocation()).getValue((Flag) delFlag, false)) {
				return;
			}
			
			removeTamedAnimals(e.getEntity());
			for (final Claim c : GriefPrevention.instance.dataStore.getPlayerData(e.getEntity().getName()).claims) {
				// Does the claim belong to the corpse
				if (c == null 
						|| !c.inDataStore
						|| !c.getOwnerName().equals(e.getEntity().getName())) {
					continue;
				}

				// Is the claim subject to a hardcore deletion
				if (hcFlag != null
						&& !new GriefPreventionClaim(c.getGreaterBoundaryCorner()).getValue((Flag) hcFlag, false)) {
					continue;
				}

				// Done for synchronization
                deleteClaims.add(c);
			}
			
            for(final Claim c : deleteClaims) {
                GriefPrevention.instance.dataStore.deleteClaim(c);
                GriefPrevention.instance.restoreClaim(c, 0);
            }
            
		}
		
		private void removeTamedAnimals(Player player) {
			for(World w : Bukkit.getWorlds()) {
				if (hcFlag != null
						&& !new Default(w).getValue((Flag) hcFlag, false)) {
					continue;
				}

				for(Entity e : w.getEntitiesByClasses(Horse.class, Ocelot.class, Wolf.class)) {
					if(((Tameable)e).isTamed() && ((Tameable)e).getOwner().getName().equals(player.getName())) {
                        if(e instanceof Ocelot) {
                            ((Ocelot)e).setCatType(Type.WILD_OCELOT);
                            ((Ocelot)e).setSitting(false);
                        }
                    
                        if(e instanceof Wolf) {
                            ((Wolf)e).setSitting(false);
                        }
                        
						if(e instanceof Horse) {
							((Horse)e).setCarryingChest(false);
							((Horse)e).getInventory().setArmor(new ItemStack(Material.AIR));
							((Horse)e).getInventory().setSaddle(new ItemStack(Material.AIR));
							((Horse)e).setDomestication(0);
						}
						((LivingEntity)e).setLeashHolder(null);
						((Tameable)e).setOwner(null);
					}
				}
			}
		}
	}
	
	private class Orphanage implements Listener {
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		private void onClaimDeleted(ClaimDeletedEvent e) {
			if (hcFlag != null
					&& !new GriefPreventionClaim(e.getClaim().getGreaterBoundaryCorner()).getValue((Flag) hcFlag, false)) {
				return;
			}
			
			GriefPrevention.instance.restoreClaim(e.getClaim(), 0);
		}
	}
}
