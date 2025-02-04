package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class SlotSelectSpell extends TargetedSpell implements TargetedEntitySpell {

	private boolean isVariable = false;

	private String variable;
	private int slot;
	private final boolean ignoreSlotBounds;

	public SlotSelectSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		if (isConfigString("slot")) {
			isVariable = true;
			variable = getConfigString("slot", null);
		} else slot = getConfigInt("slot", 0);
		ignoreSlotBounds = getConfigBoolean("ignore-slot-bounds", false);
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (isVariable && (variable == null || variable.isEmpty() || MagicSpells.getVariableManager().getVariable(variable) == null)) {
			MagicSpells.error("SlotSelectSpell '" + internalName + "' has an invalid variable specified in 'slot'!");
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player) {
			TargetInfo<Player> info = getTargetedPlayer(caster, power, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			if (!slotChange(caster, info.target(), info.power(), args)) return noTarget(caster, args);

			sendMessages(caster, info.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return validTargetList.canTarget(caster, target) && slotChange(caster, target, power, args);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return validTargetList.canTarget(caster, target) && slotChange(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return validTargetList.canTarget(target) && slotChange(null, target, power, args);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return validTargetList.canTarget(target) && slotChange(null, target, power, null);
	}

	private boolean slotChange(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player)) return false;

		int newSlot = -1;
		if (isVariable) {
			if (variable == null || variable.isEmpty() || MagicSpells.getVariableManager().getVariable(variable) == null) {
				MagicSpells.error("SlotSelectSpell '" + internalName + "' has an invalid variable specified in 'slot'!");
			} else newSlot = (int) Math.round(MagicSpells.getVariableManager().getValue(variable, player));
		} else newSlot = slot;

		try {
			player.getInventory().setHeldItemSlot(newSlot);
		} catch(IllegalArgumentException e) {
			if (!ignoreSlotBounds) {
				MagicSpells.error("SlotSelectSpell '" + internalName + "' attempted to set to a slot outside bounds (0-8)! If this is intended, set 'ignore-slot-bounds' to true.");
			}
		}

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		return true;
	}

}
