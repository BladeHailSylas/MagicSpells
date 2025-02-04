package com.nisovin.magicspells.spells.buff.ext;

import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.EntityData;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.*;
import me.libraryaddict.disguise.disguisetypes.watchers.*;
import me.libraryaddict.disguise.utilities.parser.DisguiseParser;

// NOTE: LIBSDISGUISES IS REQUIRED FOR THIS
public class DisguiseSpell extends BuffSpell {

	private final Set<UUID> entities;

	private ConfigData<Disguise> disguiseData;

	private EntityData entityData;

	private Disguise disguise;

	private String playerName;
	private String skinName;

	private short data;

	private boolean burning;
	private boolean glowing;

	public DisguiseSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		entities = new HashSet<>();

		playerName = getConfigString("player-name", "");
		skinName = getConfigString("skin-name", playerName);

		data = (short) getConfigInt("data", 0);

		burning = getConfigBoolean("burning", false);
		glowing = getConfigBoolean("glowing", false);

		if (isConfigSection("disguise")) {
			ConfigurationSection disguiseSection = getConfigSection("disguise");
			if (disguiseSection != null) entityData = new EntityData(disguiseSection);

			MagicSpells.error("DisguiseSpell '" + internalName + "' is using the legacy 'disguise' section, which is planned for removal. Please switch to a 'disguise' string.");
			return;
		}

		String disguiseString = getConfigString("disguise", null);
		if (disguiseString == null) {
			MagicSpells.error("DisguiseSpell '" + internalName + "' has an invalid or no 'disguise' defined.");
			disguiseData = null;
			return;
		}

		ConfigData<String> supplier = ConfigDataUtil.getString(disguiseString);
		if (supplier.isConstant()) {
			try {
				Disguise disguise = DisguiseParser.parseDisguise(disguiseString);
				disguiseData = (caster, target, power, args) -> disguise;
			} catch (Throwable t) {
				MagicSpells.error("DisguiseSpell '" + internalName + "' has an invalid 'disguise' defined.");
				DebugHandler.debug(t);
				disguiseData = null;
			}

			return;
		}

		disguiseData = (caster, target, power, args) -> {
			try {
				return DisguiseParser.parseDisguise(supplier.get(caster, target, power, args));
			} catch (Throwable ignored) {
				return null;
			}
		};
	}

	@Override
	public void initialize() {
		super.initialize();

		if (disguiseData != null) return;

		if (entityData == null || entityData.getEntityType() == null) {
			MagicSpells.error("DisguiseSpell '" + internalName + "' has an invalid disguise defined!");
			return;
		}

		if (entityData.isPlayer()) disguise = new PlayerDisguise(playerName, skinName);
		else if (entityData.isMob()) disguise = new MobDisguise(DisguiseType.getType(entityData.getEntityType()), !entityData.isBaby());
		else if (entityData.isMisc()) disguise = new MiscDisguise(DisguiseType.getType(entityData.getEntityType()), entityData.getMaterial(), data);
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		// STRING
		if (disguiseData != null) {
			Disguise disguise = disguiseData.get(entity, null, power, args);
			if (disguise == null) return false;

			DisguiseAPI.disguiseEntity(entity, disguise);
			entities.add(entity.getUniqueId());

			return true;
		}

		// CONFIG SECTION
		if (disguise == null) return false;

		FlagWatcher watcher = disguise.getWatcher();

		watcher.setBurning(burning);
		watcher.setGlowing(glowing);

		if (watcher instanceof AbstractHorseWatcher) {
			((AbstractHorseWatcher) watcher).setSaddled(entityData.isSaddled());
			if (watcher instanceof MuleWatcher) {
				((MuleWatcher) watcher).setCarryingChest(entityData.isChested());
			}
			if (watcher instanceof HorseWatcher) {
				((HorseWatcher) watcher).setColor(entityData.getHorseColor());
				((HorseWatcher) watcher).setStyle(entityData.getHorseStyle());
			}
			if (watcher instanceof LlamaWatcher) {
				((LlamaWatcher) watcher).setColor(entityData.getLlamaColor());
			}
		} else if (watcher instanceof CreeperWatcher) {
			((CreeperWatcher) watcher).setPowered(entityData.isPowered());
		} else if (watcher instanceof VillagerWatcher) {
			if (entityData.getProfession() != null) ((VillagerWatcher) watcher).setProfession(entityData.getProfession());
		} else if (watcher instanceof SheepWatcher) {
			if (entityData.getDyeColor() != null) ((SheepWatcher) watcher).setColor(entityData.getDyeColor());
			((SheepWatcher) watcher).setSheared(entityData.isSheared());
		} else if (watcher instanceof WolfWatcher) {
			if (entityData.getDyeColor() != null) ((WolfWatcher) watcher).setCollarColor(entityData.getDyeColor());
			((WolfWatcher) watcher).setTamed(entityData.isTamed());
		} else if (watcher instanceof PigWatcher) {
			((PigWatcher) watcher).setSaddled(entityData.isSaddled());
		} else if (watcher instanceof FallingBlockWatcher) {
			((FallingBlockWatcher) watcher).setBlock(new ItemStack(entityData.getMaterial()));
		} else if (watcher instanceof DroppedItemWatcher) {
			((DroppedItemWatcher) watcher).setItemStack(new ItemStack(entityData.getMaterial()));
		} else if (watcher instanceof PufferFishWatcher) {
			((PufferFishWatcher) watcher).setPuffState(entityData.getSize());
		} else if (watcher instanceof TropicalFishWatcher) {
			((TropicalFishWatcher) watcher).setBodyColor(entityData.getDyeColor());
			((TropicalFishWatcher) watcher).setPatternColor(entityData.getPatternDyeColor());
			((TropicalFishWatcher) watcher).setPattern(entityData.getFishPattern());
		} else if (watcher instanceof ParrotWatcher) {
			((ParrotWatcher) watcher).setVariant(entityData.getParrotVariant());
		} else if (watcher instanceof SlimeWatcher) {
			((SlimeWatcher) watcher).setSize(entityData.getSize());
		} else if (watcher instanceof EndermanWatcher) {
			((EndermanWatcher) watcher).setItemInMainHand(entityData.getMaterial());
		}

		DisguiseAPI.disguiseEntity(entity, disguise);
		entities.add(entity.getUniqueId());
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.contains(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		entities.remove(entity.getUniqueId());
		DisguiseAPI.undisguiseToAll(entity);
	}

	@Override
	protected void turnOff() {
		for (UUID id : entities) {
			Entity entity = Bukkit.getEntity(id);
			if (entity == null) continue;
			DisguiseAPI.undisguiseToAll(entity);
		}
		entities.clear();
	}

	public Set<UUID> getEntities() {
		return entities;
	}

	public EntityData getEntityData() {
		return entityData;
	}

	public void setEntityData(EntityData entityData) {
		this.entityData = entityData;
	}

	public Disguise getDisguise() {
		return disguise;
	}

	public void setDisguise(Disguise disguise) {
		this.disguise = disguise;
	}

}
