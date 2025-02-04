package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class CarpetSpell extends TargetedSpell implements TargetedLocationSpell {

	private Map<Block, CarpetData> blocks;

	private Material material;
	private String materialName;

	private int touchCheckInterval;
	private ConfigData<Integer> radius;
	private ConfigData<Integer> duration;

	private boolean circle;
	private boolean removeOnTouch;
	private boolean powerAffectsRadius;

	private String spellOnTouchName;
	private Subspell spellOnTouch;

	private TouchChecker checker;
	
	public CarpetSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		materialName = getConfigString("block", "white_carpet");
		material = Util.getMaterial(materialName);
		if (material == null || !material.isBlock()) {
			MagicSpells.error("CarpetSpell '" + internalName + "' has an invalid block defined!");
			material = null;
		}

		radius = getConfigDataInt("radius", 1);
		duration = getConfigDataInt("duration", 0);
		touchCheckInterval = getConfigInt("touch-check-interval", 3);

		circle = getConfigBoolean("circle", false);
		removeOnTouch = getConfigBoolean("remove-on-touch", true);
		powerAffectsRadius = getConfigBoolean("power-affects-radius", true);

		spellOnTouchName = getConfigString("spell-on-touch", "");

		blocks = new HashMap<>();
	}
	
	@Override
	public void initialize() {
		super.initialize();

		spellOnTouch = new Subspell(spellOnTouchName);
		if (!spellOnTouch.process() || !spellOnTouch.isTargetedEntitySpell()) {
			if (!spellOnTouchName.isEmpty()) MagicSpells.error("CarpetSpell '" + internalName + "' has an invalid spell-on-touch defined!");
			spellOnTouch = null;
		}

		if (spellOnTouch != null) checker = new TouchChecker();
	}
	
	@Override
	public void turnOff() {
		super.turnOff();

		for (Block block : blocks.keySet()) {
			block.setType(Material.AIR);
		}
		blocks.clear();
		if (checker != null) checker.stop();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player player) {
			Location loc = null;
			if (targetSelf) loc = player.getLocation();
			else {
				Block b = getTargetedBlock(player, power, args);
				if (b != null && b.getType() != Material.AIR) loc = b.getLocation();
			}

			if (loc == null) return noTarget(player, args);

			layCarpet(player, loc, power, args);
		}
		return PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		if (!(caster instanceof Player)) return false;
		if (targetSelf) layCarpet((Player) caster, caster.getLocation(), power, args);
		else layCarpet((Player) caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return castAtLocation(caster, target, power, null);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		layCarpet(null, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		layCarpet(null, target, power, null);
		return true;
	}

	private void layCarpet(Player player, Location loc, float power, String[] args) {
		if (!loc.getBlock().getType().isOccluding()) {
			int c = 0;
			while (!loc.getBlock().getRelative(0, -1, 0).getType().isOccluding() && c <= 2) {
				loc.subtract(0, 1, 0);
				c++;
			}
		} else {
			int c = 0;
			while (loc.getBlock().getType().isOccluding() && c <= 2) {
				loc.add(0, 1, 0);
				c++;
			}
		}

		Block b;
		int y = loc.getBlockY();

		int rad = this.radius.get(player, null, power, args);
		if (powerAffectsRadius) rad = Math.round(rad * power);

		SpellData data = new SpellData(player, power, args);

		final List<Block> blockList = new ArrayList<>();
		for (int x = loc.getBlockX() - rad; x <= loc.getBlockX() + rad; x++) {
			for (int z = loc.getBlockZ() - rad; z <= loc.getBlockZ() + rad; z++) {
				b = loc.getWorld().getBlockAt(x, y, z);
				if (circle && loc.getBlock().getLocation().distanceSquared(b.getLocation()) > rad * rad) continue;

				if (b.getType().isOccluding()) b = b.getRelative(0, 1, 0);
				else if (!b.getRelative(0, -1, 0).getType().isOccluding()) b = b.getRelative(0, -1, 0);

				if (!BlockUtils.isAir(b.getType()) && !b.getRelative(0, -1, 0).getType().isSolid()) continue;

				b.setType(material, false);
				blockList.add(b);
				blocks.put(b, new CarpetData(player, power, args));
				playSpellEffects(EffectPosition.TARGET, b.getLocation().add(0.5, 0, 0.5), data);
			}
		}

		int duration = this.duration.get(player, null, power, args);
		if (duration > 0 && !blockList.isEmpty()) {
			MagicSpells.scheduleDelayedTask(() -> {
				for (Block b1 : blockList) {
					if (!material.equals(b1.getType())) continue;
					b1.setType(Material.AIR);
					if (blocks != null) blocks.remove(b1);
				}
			}, duration);
		}
		if (player != null) playSpellEffects(EffectPosition.CASTER, player, data);
	}

	private record CarpetData(LivingEntity caster, float power, String[] args) {}
	
	private class TouchChecker implements Runnable {
		
		private int taskId;
		
		private TouchChecker() {
			taskId = MagicSpells.scheduleRepeatingTask(this, touchCheckInterval, touchCheckInterval);
		}
		
		@Override
		public void run() {
			if (blocks.isEmpty()) return;
			for (Player player : Bukkit.getOnlinePlayers()) {

				Block b = player.getLocation().getBlock();
				CarpetData data = blocks.get(b);

				if (data == null) continue;
				if (player.equals(data.caster)) continue;
				if (!material.equals(b.getType())) continue;

				if (removeOnTouch) {
					b.setType(Material.AIR);
					blocks.remove(b);
				}

				if (spellOnTouch != null) {
					SpellTargetEvent event = new SpellTargetEvent(CarpetSpell.this, data.caster, player, data.power, data.args);
					if (!event.callEvent()) continue;

					spellOnTouch.castAtEntity(data.caster, event.getTarget(), event.getPower());
				}
			}
		}
		
		private void stop() {
			MagicSpells.cancelTask(taskId);
		}
		
	}

}
