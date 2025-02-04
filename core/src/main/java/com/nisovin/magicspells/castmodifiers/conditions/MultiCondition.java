package com.nisovin.magicspells.castmodifiers.conditions;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.castmodifiers.Modifier;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.castmodifiers.Condition;
import com.nisovin.magicspells.castmodifiers.IModifier;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;

/*
 * Just a heads up that for the modifier actions inside this, I recommend that you use
 * stop rather than denied most of the time, because the denied action will actually cancel
 * the event being processed whereas the stop action will just say that this specific check
 * counts as a fail.
 * 
 * in the general config, you can define a set of modifiers like this
 * 
 * general:
 *     modifiers:
 *         modifier_name:
 *             checks:
 *                 - condition condition_var action action_var
 *                 - condition condition_var action action_var
 *             pass-condition: a string value that can be one of the following ANY, ALL, XOR
 * 
 * You can also define some in the spell*.yml files as follows
 * 
 * modifiers:
 *     modifier_name:
 *         checks:
 *             - condition condition_var action action_var
 *             - condition condition_var action action_var
 *         pass-condition: a string value that can be one of the following ANY, ALL, XOR
 *
 * to reference the modifier collection, you just slip this into your modifiers listed on a spell
 * - collection <modifier_name> action action_var
 * where <modifier_name> is the name that you assigned to the modifier collection as shown above
 */

public class MultiCondition extends Condition implements IModifier {
	
	private String configPrefix = "general.modifiers.";
	private List<Modifier> modifiers;
	
	//the condition on which this condition as a whole may pass
	private PassCondition passCondition = PassCondition.ALL;
	
	@Override
	public boolean initialize(String var) {
		configPrefix += var;
		MagicConfig config = MagicSpells.plugin.getMagicConfig();
		if (!(config.contains(configPrefix) && config.isSection(configPrefix))) return false;
		
		List<String> modifierStrings = config.getStringList(configPrefix + ".checks", null);
		if (modifierStrings == null) return false;
		
		String passConditionString = config.getString(configPrefix + ".pass-condition", "ALL").toUpperCase();
		try {
			passCondition = PassCondition.valueOf(passConditionString);
		} catch (IllegalArgumentException badPassCondition) {
			MagicSpells.error("Invalid value for \"pass-condition\" of \"" + passConditionString + "\".");
			// To preserve old behavior, just default it to "ALL"
			MagicSpells.error("Defaulting pass-condition to \"ALL\"");
			passCondition = PassCondition.ALL;
		}
		
		modifiers = new ArrayList<>();
		for (String modString : modifierStrings) {
			Modifier m = new Modifier();
			m.process(modString);

			if (m.isInitialized()) modifiers.add(m);
			else {
				String extra = "";
				if (m.getCustomActionData() != null) extra = ": " + m.getCustomActionData().getInvalidText();
				MagicSpells.error("Problem in reading predefined modifier '" + modString + "' from '" + var + "'" + extra);
			}
		}
		
		if (modifiers == null || modifiers.isEmpty()) {
			MagicSpells.error("Could not load any modifier checks for predefined modifier \"" + var + '\"');
			return false;
		}
		
		return true;
	}

	@Override
	public boolean check(LivingEntity livingEntity) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.check(livingEntity);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, livingEntity, null);
			}
			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean check(LivingEntity livingEntity, LivingEntity target) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.check(livingEntity, target);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, livingEntity, null);
			}
			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean check(LivingEntity livingEntity, Location location) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.check(livingEntity, location);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, livingEntity, null);
			}
			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean apply(SpellCastEvent event) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.apply(event);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, event.getCaster(), null);
			}
			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean apply(ManaChangeEvent event) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.apply(event);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, event.getPlayer(), null);
			}

			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean apply(SpellTargetEvent event) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.apply(event);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, event.getCaster(), null);
			}

			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean apply(SpellTargetLocationEvent event) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.apply(event);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, event.getCaster(), null);
			}

			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public boolean apply(MagicSpellsGenericPlayerEvent event) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			boolean check = m.apply(event);
			if (check) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, event.getPlayer(), null);
			}

			if (!passCondition.shouldContinue(pass, fail)) return passCondition.hasPassed(pass, fail);
		}
		return passCondition.hasPassed(pass, fail);
	}

	@Override
	public ModifierResult apply(LivingEntity caster, SpellData data) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			ModifierResult result = m.apply(caster, data);
			data = result.data();
			if (result.check()) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, caster, result.data().args());
			}
			if (!passCondition.shouldContinue(pass, fail)) return new ModifierResult(data, passCondition.hasPassed(pass, fail));
		}
		return new ModifierResult(data, passCondition.hasPassed(pass, fail));
	}

	@Override
	public ModifierResult apply(LivingEntity caster, LivingEntity target, SpellData data) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			ModifierResult result = m.apply(caster, target, data);
			data = result.data();
			if (result.check()) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, caster, result.data().args());
			}
			if (!passCondition.shouldContinue(pass, fail)) return new ModifierResult(data, passCondition.hasPassed(pass, fail));
		}
		return new ModifierResult(data, passCondition.hasPassed(pass, fail));
	}

	@Override
	public ModifierResult apply(LivingEntity caster, Location target, SpellData data) {
		int pass = 0;
		int fail = 0;
		for (Modifier m : modifiers) {
			ModifierResult result = m.apply(caster, target, data);
			data = result.data();
			if (result.check()) pass++;
			else {
				fail++;
				String msg = m.getStrModifierFailed();
				if (msg != null) MagicSpells.sendMessage(msg, caster, result.data().args());
			}
			if (!passCondition.shouldContinue(pass, fail)) return new ModifierResult(data, passCondition.hasPassed(pass, fail));
		}
		return new ModifierResult(data, passCondition.hasPassed(pass, fail));
	}

	public enum PassCondition {
		
		ALL{

			@Override
			public boolean hasPassed(int passes, int fails) {
				return fails == 0;
			}

			@Override
			public boolean shouldContinue(int passes, int fails) {
				return fails == 0;
			}
			
		},
		
		ANY {
			@Override
			public boolean hasPassed(int passes, int fails) {
				return passes > 0;
			}

			@Override
			public boolean shouldContinue(int passes, int fails) {
				return passes == 0;
			}
		},

		NONE {
			@Override
			public boolean hasPassed(int passes, int fails) {
				return passes == 0;
			}

			@Override
			public boolean shouldContinue(int passes, int fails) {
				return passes == 0;
			}
		},

		XOR {

			@Override
			public boolean hasPassed(int passes, int fails) {
				return passes == 1;
			}

			@Override
			public boolean shouldContinue(int passes, int fails) {
				return passes <= 1;
			}
			
		}
		
		;
		
		PassCondition() {
			
		}
		
		public abstract boolean hasPassed(int passes, int fails);
		public abstract boolean shouldContinue(int passes, int fails);
		
	}
	
}
