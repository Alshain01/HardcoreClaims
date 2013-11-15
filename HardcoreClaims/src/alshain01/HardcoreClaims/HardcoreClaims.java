/* Copyright 2013 Kevin Seiden. All rights reserved.

 This works is licensed under the Creative Commons Attribution-NonCommercial 3.0

 You are Free to:
    to Share — to copy, distribute and transmit the work
    to Remix — to adapt the work

 Under the following conditions:
    Attribution — You must attribute the work in the manner specified by the author (but not in any way that suggests that they endorse you or your use of the work).
    Non-commercial — You may not use this work for commercial purposes.

 With the understanding that:
    Waiver — Any of the above conditions can be waived if you get permission from the copyright holder.
    Public Domain — Where the work or any of its elements is in the public domain under applicable law, that status is in no way affected by the license.
    Other Rights — In no way are any of the following rights affected by the license:
        Your fair dealing or fair use rights, or other applicable copyright exceptions and limitations;
        The author's moral rights;
        Rights other persons may have either in the work itself or in how the work is used, such as publicity or privacy rights.

 Notice — For any reuse or distribution, you must make clear to others the license terms of this work. The best way to do this is with a link to this web page.
 http://creativecommons.org/licenses/by-nc/3.0/
 */

package alshain01.HardcoreClaims;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import alshain01.Flags.Director;
import alshain01.Flags.Flag;
import alshain01.Flags.Flags;
import alshain01.Flags.SystemType;
import alshain01.Flags.area.Default;
import alshain01.Flags.area.GriefPreventionClaim;

/**
 * HardcoreClaims
 * 
 * @author Alshain01
 */
public class HardcoreClaims extends JavaPlugin {
	private class CommandGuard implements Listener {
		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		private void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
			if (e.getPlayer().hasPermission("hardcoreclaims.admin")) {
				return;
			}

			// If the world isn't configured for HardcoreClaims, ignore this
			if (hcFlag != null
					&& !new Default(e.getPlayer().getWorld()).getValue((Flag) hcFlag, false)) {
				return;
			}

			// Block commands that would allow cheating the hardcore system
			if (e.getMessage().toLowerCase().contains("abandonclaim")
					|| e.getMessage().toLowerCase().contains("abandontoplevelclaim")) {
				e.getPlayer().sendMessage(ChatColor.RED	+ "You may not abandon claims in hardcore worlds.");
				e.setCancelled(true);
			}
		}

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
			case LOCKED_CHEST:
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
			// Is the player in an area that would cause a hardcore deletion
			if (delFlag != null
					&& !Director.getAreaAt(e.getEntity().getLocation()).getValue((Flag) delFlag, false)) {
				return;
			}

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

				GriefPrevention.instance.dataStore.deleteClaim(c);
				GriefPrevention.instance.restoreClaim(c, 0);
				removeTamedAnimals(e.getEntity());
			}
		}
		
		private void removeTamedAnimals(Player player) {
			for(World w : Bukkit.getWorlds()) {
				if (hcFlag != null
						&& !new Default(w).getValue((Flag) hcFlag, false)) {
					continue;
				}

				for(Entity e : w.getEntitiesByClasses(Ocelot.class, Wolf.class)) {
					if(((Tameable)e).isTamed() && ((Tameable)e).getOwner().getName().equals(player.getName())) {
						((Tameable)e).setOwner(null);
					}
				}
			}
		}
	}

	private Object hcFlag = null, delFlag = null;
	private final Listener reaper = new Reaper();
	private final Listener containerGuard = new ContainerGuard();
	private final Listener commandGuard = new CommandGuard();

	@Override
	public void onDisable() {
		// Cleanup
		EntityDeathEvent.getHandlerList().unregister(reaper);
		PlayerInteractEvent.getHandlerList().unregister(containerGuard);
		PlayerCommandPreprocessEvent.getHandlerList().unregister(commandGuard);
	}

	@Override
	public void onEnable() {
		// Required Plug-in Check
		if (!getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
			getLogger().info("Grief Prevention is not installed. HardcoreClaims is shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Register Flags
		if (getServer().getPluginManager().isPluginEnabled("Flags")) {
			getLogger().info("Enabling Flags Integration");
			delFlag = Flags.getRegistrar()
					.register("HardcoreDeletion", "Toggles whether the player will lose hardcore claims if they die in the area.", true, getName());

			if (SystemType.getActive() == SystemType.GRIEF_PREVENTION) {
				hcFlag = Flags.getRegistrar()
						.register("HardcoreClaim", "Toggles the claim's hardcore status (area/default only).", true, getName());
			}
		}

		// Event Listeners
		getServer().getPluginManager().registerEvents(reaper, this);
		getServer().getPluginManager().registerEvents(containerGuard, this);
		getServer().getPluginManager().registerEvents(commandGuard, this);
	}
}
