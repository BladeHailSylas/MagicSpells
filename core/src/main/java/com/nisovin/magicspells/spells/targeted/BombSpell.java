package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellAnimation;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class BombSpell extends TargetedSpell implements TargetedLocationSpell {

	private Set<Block> blocks;

	private Material material;
	private String materialName;

	private ConfigData<Integer> fuse;
	private ConfigData<Integer> interval;

	private Subspell targetSpell;
	private String targetSpellName;
	
	public BombSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		materialName = getConfigString("block", "stone");
		material = Util.getMaterial(materialName);
		if (material == null || !material.isBlock()) {
			MagicSpells.error("BombSpell '" + internalName + "' has an invalid block defined!");
			material = null;
		}

		fuse = getConfigDataInt("fuse", 100);
		interval = getConfigDataInt("interval", 20);

		targetSpellName = getConfigString("spell", "");

		blocks = new HashSet<>();
	}
	
	@Override
	public void initialize() {
		super.initialize();

		targetSpell = new Subspell(targetSpellName);
		if (!targetSpell.process() || !targetSpell.isTargetedLocationSpell()) {
			if (!targetSpellName.isEmpty()) MagicSpells.error("BombSpell '" + internalName + "' has an invalid spell defined!");
			targetSpell = null;
		}
	}

	@Override
	public void turnOff() {
		super.turnOff();

		for (Block b : blocks) {
			b.setType(Material.AIR);
		}

		blocks.clear();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			List<Block> blocks = getLastTwoTargetedBlocks(caster, power, args);
			if (blocks.size() != 2) return noTarget(caster, args);
			if (!blocks.get(1).getType().isSolid()) return noTarget(caster, args);

			Block target = blocks.get(0);
			boolean ok = bomb(caster, target.getLocation(), power, args);
			if (!ok) return noTarget(caster, args);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return bomb(caster, target, power, args);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return bomb(caster, target, power, null);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return bomb(null, target, power, args);
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		return bomb(null, target, power, null);
	}

	private boolean bomb(LivingEntity livingEntity, Location loc, float power, String[] args) {
		if (material == null) return false;
		Block block = loc.getBlock();
		if (!BlockUtils.isAir(block.getType())) return false;

		blocks.add(block);
		block.setType(material);

		SpellData data = new SpellData(livingEntity, power, args);
		if (livingEntity != null) playSpellEffects(livingEntity, loc.add(0.5, 0, 0.5), data);
		else playSpellEffects(EffectPosition.TARGET, loc.add(0.5, 0, 0.5), data);

		final int interval = this.interval.get(livingEntity, null, power, args);
		final int fuse = this.fuse.get(livingEntity, null, power, args);
		new SpellAnimation(interval, interval, true) {

			private final Location l = block.getLocation().add(0.5, 0, 0.5);
			private int time = 0;

			@Override
			protected void onTick(int tick) {
				time += interval;
				if (time >= fuse) {
					stop(true);
					if (material.equals(block.getType())) {
						blocks.remove(block);
						block.setType(Material.AIR);
						playSpellEffects(EffectPosition.DELAYED, l, data);
						if (targetSpell != null) targetSpell.castAtLocation(livingEntity, l, power);
					}
				} else if (!material.equals(block.getType())) stop(true);
				else playSpellEffects(EffectPosition.SPECIAL, l, data);
			}
				
		};

		return true;
	}

}
