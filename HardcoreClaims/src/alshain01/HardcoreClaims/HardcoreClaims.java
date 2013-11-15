/* Copyright 2013 Kevin Seiden. All rights reserved.

 This works is licensed under the Creative Commons Attribution-NonCommercial 3.0

 You are Free to:
    to Share � to copy, distribute and transmit the work
    to Remix � to adapt the work

 Under the following conditions:
    Attribution � You must attribute the work in the manner specified by the author (but not in any way that suggests that they endorse you or your use of the work).
    Non-commercial � You may not use this work for commercial purposes.

 With the understanding that:
    Waiver � Any of the above conditions can be waived if you get permission from the copyright holder.
    Public Domain � Where the work or any of its elements is in the public domain under applicable law, that status is in no way affected by the license.
    Other Rights � In no way are any of the following rights affected by the license:
        Your fair dealing or fair use rights, or other applicable copyright exceptions and limitations;
        The author's moral rights;
        Rights other persons may have either in the work itself or in how the work is used, such as publicity or privacy rights.

 Notice � For any reuse or distribution, you must make clear to others the license terms of this work. The best way to do this is with a link to this web page.
 http://creativecommons.org/licenses/by-nc/3.0/
 */

package alshain01.HardcoreClaims;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import alshain01.Flags.Director;
import alshain01.Flags.Flag;
import alshain01.Flags.Flags;
import alshain01.Flags.SystemType;
import alshain01.Flags.area.GriefPreventionClaim;

/**
 * HardcoreClaims
 * 
 * @author Alshain01
 */
public class HardcoreClaims extends JavaPlugin {
	private class Reaper implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		private void onPlayerDeath(PlayerDeathEvent e) {
			if (delFlag != null
					&& !Director.getAreaAt(e.getEntity().getLocation()).getValue((Flag) delFlag, false)) {
				return;
			}
			
			for (long id : GriefPrevention.instance.dataStore.getClaimIds()) {
				Claim claim = GriefPrevention.instance.dataStore.getClaim(id);
				
				if (claim == null || !claim.inDataStore || !claim.getOwnerName().equals(e.getEntity().getName())) {
					continue;
				}

				if (hcFlag != null
						&& !new GriefPreventionClaim(claim.getGreaterBoundaryCorner()).getValue((Flag) hcFlag, false)) {
					continue;
				}

				GriefPrevention.instance.dataStore.deleteClaim(claim);
				GriefPrevention.instance.restoreClaim(claim, 0);
			}
		}
	}

	private class ContainerGuard implements Listener {
		@EventHandler(priority = EventPriority.LOW)
		private void onBlockPlace(BlockPlaceEvent e) {
			switch(e.getBlock().getType()) {
				case CHEST:
				case ENDER_CHEST:
				case TRAPPED_CHEST:
				case BREWING_STAND:
				case DISPENSER:
				case FURNACE:
				case LOCKED_CHEST:
				case HOPPER:
				case HOPPER_MINECART:
				case STORAGE_MINECART:
					Claim claim = GriefPrevention.instance.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);
					if(claim == null || !claim.getOwnerName().equals(e.getPlayer().getName())) {
						e.getPlayer().sendMessage(ChatColor.RED + "You may not place containers outside your claim on hardcore worlds.");
						e.setCancelled(true);
					}
					break;
				default:
					break;
			}
		}
	}
	
	private Object hcFlag = null;
	private Object delFlag = null;
	private final Listener reaper = new Reaper();
	private final Listener containerGuard = new ContainerGuard();
	
	@Override
	public void onEnable() {
		if(!getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
			this.getLogger().info("Grief Prevention is not installed. HardcoreClaims is shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		getServer().getPluginManager().registerEvents(reaper, this);
		getServer().getPluginManager().registerEvents(containerGuard, this);

		if (getServer().getPluginManager().isPluginEnabled("Flags")) {
			this.getLogger().info("Enabling Flags Integration");
			delFlag = Flags.getRegistrar().register("HardcoreDeletion",
					"Toggles whether the player will lose hardcore claims if they die in the area.",
					true, getName());
				
			if(SystemType.getActive() == SystemType.GRIEF_PREVENTION) {
				hcFlag = Flags.getRegistrar().register("HardcoreClaim",
						"Toggles the claim's hardcore status (area/default only).",
						true, getName());
			}
		}
	}
	
	@Override
	public void onDisable() {
		PlayerDeathEvent.getHandlerList().unregister(reaper);
		PlayerInteractEvent.getHandlerList().unregister(containerGuard);
	}
}
