package com.nisovin.magicspells.spelleffects;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;

public class SmokeSwirlEffect extends SpellEffect {

	int interval;
	int duration = TimeUtil.TICKS_PER_SECOND;

	@Override
	public void loadFromConfig(ConfigurationSection config) {
		interval = config.getInt("interval", 1);
		duration = config.getInt("duration", duration);
	}

	int[] x = {1, 1, 0, -1, -1, -1, 0, 1};
	int[] z = {0, 1, 1, 1, 0, -1, -1, -1};
	int[] v = {7, 6, 3, 0, 1, 2, 5, 8};
	
	@Override
	public Runnable playEffectLocation(Location location) {		
		new Animator(location, interval, duration);
		return null;
	}
	
	@Override
	public Runnable playEffectEntity(Entity entity) {
		new Animator(entity, interval, duration);
		return null;
	}
	
	private class Animator implements Runnable {
		
		private Entity entity;
		private Location location;
		private int interval;
		private int animatorDuration;
		private int iteration;
		private int animatorTaskId;
		
		public Animator(Location location, int interval, int duration) {
			this(interval, duration);
			this.location = location;
		}
		
		public Animator(Entity entity, int interval, int duration) {
			this(interval, duration);
			this.entity = entity;
		}
		
		public Animator(int interval, int duration) {
			this.interval = interval;
			this.animatorDuration = duration;
			this.iteration = 0;
			this.animatorTaskId = MagicSpells.scheduleRepeatingTask(this, 0, interval);
		}
		
		@Override
		public void run() {
			if (iteration * interval > animatorDuration) {
				Bukkit.getScheduler().cancelTask(animatorTaskId);
			} else {
				int i = iteration % 8;
				Location loc;
				if (location != null) {
					loc = location;
				} else {
					loc = entity.getLocation();
				}
				loc.getWorld().playEffect(loc.clone().add(x[i], 0, z[i]), Effect.SMOKE, v[i]);
				iteration++;
			}
		}
		
	}
	
}
