package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell.SpellCastState;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// Optional trigger variable of comma separated list of internal spell names to accept
public class SpellCastListener extends PassiveListener {

	private final Set<String> spellNames = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split(",");
		for (String s : split) {
			Spell sp = MagicSpells.getSpellByInternalName(s.trim());
			if (sp == null) continue;

			spellNames.add(sp.getInternalName());
		}
	}
	
	@OverridePriority
	@EventHandler
	public void onSpellCast(SpellCastEvent event) {
		LivingEntity caster = event.getCaster();
		if (!(caster instanceof Player)) return;
		if (event.getSpellCastState() != SpellCastState.NORMAL) return;

		Spellbook spellbook = MagicSpells.getSpellbook((Player) caster);
		Spell spell = event.getSpell();
		if (!spellNames.isEmpty() && !spellNames.contains(spell.getInternalName())) return;

		if (!isCancelStateOk(event.isCancelled())) return;
		if (spell.equals(passiveSpell)) return;
		if (!spellbook.hasSpell(passiveSpell, false)) return;
		boolean casted = passiveSpell.activate((Player) caster);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
