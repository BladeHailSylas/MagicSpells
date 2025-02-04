package com.nisovin.magicspells.util.trackers;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.block.BlockFace;
import org.bukkit.util.EulerAngle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.TrackerMoveEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.zones.NoMagicZoneManager;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.ParticleProjectileHitEvent;
import com.nisovin.magicspells.spelleffects.util.EffectlibSpellEffect;
import com.nisovin.magicspells.spells.instant.ParticleProjectileSpell;

import org.apache.commons.math3.util.FastMath;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.effect.ModifiedEffect;

public class ParticleProjectileTracker implements Runnable, Tracker {

	private final Random rand = ThreadLocalRandom.current();

	private NoMagicZoneManager zoneManager;

	private Set<EffectlibSpellEffect> effectSet;
	private Set<Entity> entitySet;
	private Set<ArmorStand> armorStandSet;

	private LivingEntity caster;

	private SpellData data;
	private String[] args;
	private float power;
	private long startTime;
	private Location startLocation;
	private Location previousLocation;
	private Location currentLocation;
	private Vector currentVelocity;
	private Vector startDirection;
	private Vector effectOffset;
	private int currentX;
	private int currentZ;
	private int counter;
	private int taskId;
	private BoundingBox hitBox;
	private List<Block> nearBlocks;
	private List<LivingEntity> inRange;
	private Set<LivingEntity> immune;
	private int maxHitLimit;
	private ValidTargetChecker entitySpellChecker;
	private ParticleProjectileTracker tracker;
	private ParticleProjectileSpell spell;
	private Set<Material> groundMaterials;
	private Set<Material> disallowedGroundMaterials;
	private ValidTargetList targetList;
	private ModifierSet projectileModifiers;
	private Map<String, Subspell> interactionSpells;

	private boolean stopped = false;

	// projectile options
	private double maxDuration;
	private double maxDistanceSquared;

	private boolean hugSurface;
	private boolean callEvents;
	private boolean changePitch;
	private boolean controllable;
	private boolean stopOnHitGround;
	private boolean stopOnModifierFail;
	private boolean allowCasterInteract;
	private boolean powerAffectsVelocity;

	private boolean hitGround;
	private boolean hitAirAtEnd;
	private boolean hitAirDuring;
	private boolean hitAirAfterDuration;

	private float acceleration;
	private float startXOffset;
	private float startYOffset;
	private float startZOffset;
	private float targetYOffset;
	private float ticksPerSecond;
	private float heightFromSurface;
	private float verticalHitRadius;
	private float horizontalHitRadius;

	private float projectileTurn;
	private float projectileVelocity;
	private double verticalRotation;
	private double horizontalRotation;
	private double xRotation;
	private float projectileVertOffset;
	private float projectileVertSpread;
	private float projectileHorizOffset;
	private float projectileHorizSpread;
	private float projectileVertGravity;
	private float projectileHorizGravity;

	private int tickInterval;
	private int spellInterval;
	private int tickSpellLimit;
	private int maxEntitiesHit;
	private int accelerationDelay;
	private int intermediateEffects;
	private int intermediateHitboxes;
	private int specialEffectInterval;
	private int groundVerticalHitRadius;
	private int groundHorizontalHitRadius;

	private Subspell airSpell;
	private Subspell tickSpell;
	private Subspell entitySpell;
	private Subspell casterSpell;
	private Subspell groundSpell;
	private Subspell durationSpell;
	private Subspell modifierSpell;
	private Subspell entityLocationSpell;

	private int ticks = 0;

	private static final double ANGLE_Y = FastMath.toRadians(-90);

	public ParticleProjectileTracker(LivingEntity caster, float power, String[] args) {
		this.caster = caster;
		this.power = power;
		this.args = args;

		data = new SpellData(caster, power, args);
	}

	public void start(Location from) {
		startTime = System.currentTimeMillis();
		if (!changePitch) from.setPitch(0F);
		startLocation = from.clone();

		// Changing the start location
		Util.applyRelativeOffset(startLocation, from.getDirection().normalize(), startXOffset, startYOffset, startZOffset);

		previousLocation = startLocation.clone();
		currentLocation = startLocation.clone();
		currentVelocity = from.getDirection();

		initialize();
	}

	public void startTarget(Location from, LivingEntity target) {
		startTarget(from, target.getLocation());
	}

	public void startTarget(Location from, Location target) {
		startTime = System.currentTimeMillis();
		if (!changePitch) from.setPitch(0F);
		startLocation = from.clone();

		// Changing the target location
		Location targetLoc = target.clone();
		targetLoc.add(0, targetYOffset,0);
		Vector dir = targetLoc.clone().subtract(from.clone()).toVector();

		// Changing the start location
		startDirection = dir.clone().normalize();
		Vector horizOffset = new Vector(-startDirection.getZ(), 0.0, startDirection.getX()).normalize();
		startLocation.add(horizOffset.multiply(startZOffset));
		startLocation.add(startLocation.getDirection().multiply(startXOffset));
		startLocation.setY(startLocation.getY() + startYOffset);

		dir = targetLoc.clone().subtract(startLocation.clone()).toVector();

		previousLocation = startLocation.clone();
		currentLocation = startLocation.clone();
		currentVelocity = LocationUtil.setDirection(from, dir).getDirection();

		initialize();
	}

	@Override
	public void initialize() {
		zoneManager = MagicSpells.getNoMagicZoneManager();
		counter = 0;

		Vector dir = currentVelocity.clone().normalize();
		Vector dirNormalized = dir.clone().normalize();

		Vector angleZ = Util.makeFinite(new Vector(-dirNormalized.getZ(), 0D, dirNormalized.getX()).normalize());
		Vector angleY = Util.makeFinite(dir.clone().rotateAroundAxis(angleZ, ANGLE_Y).normalize());
		Vector angleX = Util.makeFinite(dir.clone());

		if (verticalRotation != 0) currentVelocity.rotateAroundAxis(angleZ, verticalRotation);
		if (horizontalRotation != 0) currentVelocity.rotateAroundAxis(angleY, horizontalRotation);
		if (xRotation != 0) currentVelocity.rotateAroundAxis(angleX, xRotation);

		if (projectileHorizOffset != 0) Util.rotateVector(currentVelocity, projectileHorizOffset);
		if (projectileVertOffset != 0) currentVelocity.add(new Vector(0, projectileVertOffset, 0)).normalize();
		if (projectileVertSpread > 0 || projectileHorizSpread > 0) {
			float rx = -1 + rand.nextFloat() * 2;
			float ry = -1 + rand.nextFloat() * 2;
			float rz = -1 + rand.nextFloat() * 2;
			currentVelocity.add(new Vector(rx * projectileHorizSpread, ry * projectileVertSpread, rz * projectileHorizSpread));
		}

		if (hugSurface) {
			currentLocation.setY(currentLocation.getY() + heightFromSurface);
			currentVelocity.setY(0).normalize();
			currentLocation.setPitch(0);
		}

		if (powerAffectsVelocity) currentVelocity.multiply(power);
		currentVelocity.multiply(projectileVelocity / ticksPerSecond);

		nearBlocks = new ArrayList<>();
		if (targetList != null) inRange = new ArrayList<>();
		immune = new HashSet<>();

		maxHitLimit = 0;
		hitBox = new BoundingBox(currentLocation, horizontalHitRadius, verticalHitRadius);
		LocationUtil.setDirection(currentLocation, currentVelocity);
		tracker = this;

		if (spell != null) {
			effectSet = spell.playEffectsProjectile(EffectPosition.PROJECTILE, currentLocation, data);
			entitySet = spell.playEntityEffectsProjectile(EffectPosition.PROJECTILE, currentLocation, data);
			armorStandSet = spell.playArmorStandEffectsProjectile(EffectPosition.PROJECTILE, currentLocation, data);
			ParticleProjectileSpell.getProjectileTrackers().add(tracker);
		}

		taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
	}

	@Override
	public void run() {
		currentVelocity = Util.makeFinite(currentVelocity);
		currentLocation = Util.makeFinite(currentLocation);
		previousLocation = Util.makeFinite(previousLocation);

		if (caster != null && !caster.isValid()) {
			stop();
			return;
		}

		if (zoneManager.willFizzle(currentLocation, spell)) {
			stop();
			return;
		}

		if (maxDuration > 0 && startTime + maxDuration < System.currentTimeMillis()) {
			if (hitAirAfterDuration && durationSpell != null && durationSpell.isTargetedLocationSpell()) {
				durationSpell.castAtLocation(caster, currentLocation, power);
				if (spell != null) spell.playEffects(EffectPosition.TARGET, currentLocation, data);
			}
			stop();
			return;
		}

		if (projectileModifiers != null) {
			ModifierResult result = projectileModifiers.apply(caster, data);
			data = result.data();
			power = data.power();
			args = data.args();

			if (!result.check()) {
				if (modifierSpell != null) modifierSpell.castAtLocation(caster, currentLocation, power);
				if (stopOnModifierFail) stop();
				return;
			}
		}

		if (controllable) {
			currentVelocity = caster.getLocation().getDirection();
			if (hugSurface) currentVelocity.setY(0).normalize();
			currentVelocity.multiply(projectileVelocity / ticksPerSecond);
			LocationUtil.setDirection(currentLocation, currentVelocity);
		}

		currentVelocity = Util.makeFinite(currentVelocity);

		// Move projectile and apply gravity
		previousLocation = currentLocation.clone();
		currentLocation.add(currentVelocity);

		currentLocation = Util.makeFinite(currentLocation);
		previousLocation = Util.makeFinite(previousLocation);

		if (callEvents) {
			TrackerMoveEvent trackerMoveEvent = new TrackerMoveEvent(this, previousLocation, currentLocation);
			EventUtil.call(trackerMoveEvent);
			if (stopped) {
				return;
			}
		}

		if (hugSurface && (currentLocation.getBlockX() != currentX || currentLocation.getBlockZ() != currentZ)) {
			Block b = currentLocation.subtract(0, heightFromSurface, 0).getBlock();

			int attempts = 0;
			boolean ok = false;
			while (attempts++ < 10) {
				if (BlockUtils.isPathable(b)) {
					b = b.getRelative(BlockFace.DOWN);
					if (BlockUtils.isPathable(b)) currentLocation.add(0, -1, 0);
					else {
						ok = true;
						break;
					}
				} else {
					b = b.getRelative(BlockFace.UP);
					currentLocation.add(0, 1, 0);
					if (BlockUtils.isPathable(b)) {
						ok = true;
						break;
					}
				}
			}
			if (!ok) {
				stop();
				return;
			}

			currentLocation.setY((int) currentLocation.getY() + heightFromSurface);
			currentX = currentLocation.getBlockX();
			currentZ = currentLocation.getBlockZ();

			// Apply vertical gravity
		} else if (projectileVertGravity != 0) currentVelocity.setY(currentVelocity.getY() - (projectileVertGravity / ticksPerSecond));

		// Apply turn
		if (projectileTurn != 0) Util.rotateVector(currentVelocity, projectileTurn);

		// Apply horizontal gravity
		if (projectileHorizGravity != 0) Util.rotateVector(currentVelocity, (projectileHorizGravity / ticksPerSecond) * counter);

		// Rotate effects properly
		LocationUtil.setDirection(currentLocation, currentVelocity);

		if (effectSet != null) {
			Effect effect;
			Location effectLoc;
			for (EffectlibSpellEffect spellEffect : effectSet) {
				if (spellEffect == null) continue;
				effect = spellEffect.getEffect();
				if (effect == null) continue;

				effectLoc = spellEffect.getSpellEffect().applyOffsets(currentLocation.clone(), data);
				effect.setLocation(effectLoc);

				if (effect instanceof ModifiedEffect mod) {
					Effect modifiedEffect = mod.getInnerEffect();
					if (modifiedEffect != null) modifiedEffect.setLocation(effectLoc);
				}
			}
		}

		if (armorStandSet != null || entitySet != null) {
			// Changing the effect location
			EulerAngle angle;

			Vector dir = currentLocation.getDirection().normalize();
			Vector horizOffset = new Vector(-dir.getZ(), 0.0, dir.getX()).normalize();
			Location effectLoc = currentLocation.clone();
			effectLoc.add(horizOffset.multiply(effectOffset.getZ()));
			effectLoc.add(effectLoc.getDirection().multiply(effectOffset.getX()));
			effectLoc.setY(effectLoc.getY() + effectOffset.getY());

			effectLoc = Util.makeFinite(effectLoc);

			angle = EulerAngle.ZERO.setX(FastMath.toRadians(effectLoc.getPitch()));

			if (armorStandSet != null) {
				for (ArmorStand armorStand : armorStandSet) {
					armorStand.teleportAsync(effectLoc);
					armorStand.setHeadPose(angle);
					armorStand.setLeftArmPose(angle);
					armorStand.setRightArmPose(angle);
				}
			}

			if (entitySet != null) {
				for (Entity entity : entitySet) {
					entity.teleportAsync(effectLoc);
				}
			}
		}

		// Play effects
		if (spell != null && specialEffectInterval > 0 && counter % specialEffectInterval == 0) spell.playEffects(EffectPosition.SPECIAL, currentLocation, data);

		// Acceleration
		if (acceleration != 0 && accelerationDelay > 0 && counter % accelerationDelay == 0) currentVelocity.multiply(acceleration);

		// Intermediate effects
		if (intermediateEffects > 0) playIntermediateEffects(previousLocation, currentVelocity);

		// Intermediate hitboxes
		if (intermediateHitboxes > 0) checkIntermediateHitboxes(previousLocation, currentVelocity);

		if (stopped) return;

		counter++;

		// Cast spell mid air
		if (hitAirDuring && counter % spellInterval == 0 && tickSpell != null) {
			if (tickSpellLimit <= 0 || ticks < tickSpellLimit) {
				tickSpell.castAtLocation(caster, currentLocation.clone(), power);
				ticks++;
			}
		}

		if (groundHorizontalHitRadius == 0 || groundVerticalHitRadius == 0) {
			nearBlocks = new ArrayList<>();
			nearBlocks.add(currentLocation.getBlock());
		} else nearBlocks = BlockUtils.getNearbyBlocks(currentLocation, groundHorizontalHitRadius, groundVerticalHitRadius);

		for (Block b : nearBlocks) {
			if (!groundMaterials.contains(b.getType()) || disallowedGroundMaterials.contains(b.getType())) continue;
			if (hitGround && groundSpell != null) {
				Util.setLocationFacingFromVector(previousLocation, currentVelocity);
				groundSpell.castAtLocation(caster, previousLocation, power);
				if (spell != null) spell.playEffects(EffectPosition.TARGET, currentLocation, data);
			}
			if (stopOnHitGround) {
				stop();
				return;
			}
		}

		if (currentLocation.distanceSquared(startLocation) >= maxDistanceSquared) {
			if (hitAirAtEnd && airSpell != null) {
				airSpell.castAtLocation(caster, currentLocation.clone(), power);
				if (spell != null) spell.playEffects(EffectPosition.TARGET, currentLocation, data);
			}
			stop();
			return;
		}

		checkHitbox(currentLocation);
		if (stopped) return;

		if (spell == null || interactionSpells == null || interactionSpells.isEmpty()) return;
		Set<ParticleProjectileTracker> toRemove = new HashSet<>();
		Set<ParticleProjectileTracker> trackers = new HashSet<>(ParticleProjectileSpell.getProjectileTrackers());
		for (ParticleProjectileTracker collisionTracker : trackers) {
			if (!canInteractWith(collisionTracker)) continue;

			Subspell collisionSpell = interactionSpells.get(collisionTracker.spell.getInternalName());
			if (collisionSpell == null) {
				toRemove.add(collisionTracker);
				toRemove.add(tracker);
				collisionTracker.stop(false);
				tracker.stop(false);
				continue;
			}

			double x = (tracker.currentLocation.getX() + collisionTracker.currentLocation.getX()) / 2D;
			double y = (tracker.currentLocation.getY() + collisionTracker.currentLocation.getY()) / 2D;
			double z = (tracker.currentLocation.getZ() + collisionTracker.currentLocation.getZ()) / 2D;

			Location middleLoc = new Location(tracker.currentLocation.getWorld(), x, y, z);
			collisionSpell.castAtLocation(tracker.caster, middleLoc, tracker.power);
			toRemove.add(collisionTracker);
			toRemove.add(tracker);
			collisionTracker.stop(false);
			tracker.stop(false);
		}

		ParticleProjectileSpell.getProjectileTrackers().removeAll(toRemove);
		toRemove.clear();
		trackers.clear();
	}

	private boolean canInteractWith(ParticleProjectileTracker collisionTracker) {
		if (collisionTracker == null) return false;
		if (tracker == null) return false;
		if (tracker.caster == null) return false;
		if (collisionTracker.caster == null) return false;
		if (collisionTracker.equals(tracker)) return false;
		if (!interactionSpells.containsKey(collisionTracker.spell.getInternalName())) return false;
		if (!collisionTracker.currentLocation.getWorld().equals(tracker.currentLocation.getWorld())) return false;
		if (!collisionTracker.hitBox.contains(tracker.currentLocation) && !tracker.hitBox.contains(collisionTracker.currentLocation)) return false;
		if (!allowCasterInteract && collisionTracker.caster.equals(tracker.caster)) return false;
		return true;
	}

	private void playIntermediateEffects(Location old, Vector movement) {
		int divideFactor = intermediateEffects + 1;
		Vector v = movement.clone();

		v.setX(v.getX() / divideFactor);
		v.setY(v.getY() / divideFactor);
		v.setZ(v.getZ() / divideFactor);

		for (int i = 0; i < intermediateEffects; i++) {
			old = LocationUtil.setDirection(old.add(v), v);
			if (spell != null && specialEffectInterval > 0 && counter % specialEffectInterval == 0) spell.playEffects(EffectPosition.SPECIAL, old, data);
		}
	}

	private void checkIntermediateHitboxes(Location old, Vector movement) {
		int divideFactor = intermediateHitboxes + 1;
		Vector v = movement.clone();

		v.setX(v.getX() / divideFactor);
		v.setY(v.getY() / divideFactor);
		v.setZ(v.getZ() / divideFactor);

		for (int i = 0; i < intermediateHitboxes; i++) {
			old = LocationUtil.setDirection(old.add(v), v);
			checkHitbox(old);
		}
	}

	private void checkHitbox(Location currentLoc) {
		if (inRange == null) return;
		if (currentLoc == null) return;
		if (currentLoc.getWorld() == null) return;

		hitBox.setCenter(currentLoc);
		inRange.clear();

		double x = horizontalHitRadius / 2D;
		double y = verticalHitRadius / 2D;
		double z = horizontalHitRadius / 2D;

		for (LivingEntity entity : currentLoc.getWorld().getNearbyLivingEntities(currentLoc, x, y, z)) {
			if (!targetList.canTarget(caster, entity)) continue;
			if (immune.contains(entity)) continue;
			inRange.add(entity);
		}

		LivingEntity target;
		ParticleProjectileHitEvent projectileEvent;
		SpellTargetEvent event;

		for (int i = 0; i < inRange.size(); i++) {
			target = inRange.get(i);
			if (target.isDead()) continue;

			projectileEvent = new ParticleProjectileHitEvent(caster, target, tracker, spell, power);
			EventUtil.call(projectileEvent);

			if (stopped) return;

			if (projectileEvent.isCancelled()) {
				inRange.remove(i);
				break;
			}

			float power = this.power;

			event = new SpellTargetEvent(spell, caster, target, power, args);
			EventUtil.call(event);
			if (event.isCancelled()) {
				inRange.remove(i);
				break;
			} else {
				target = event.getTarget();
				power = event.getPower();
			}

			if (entitySpell != null && entitySpell.isTargetedEntitySpell()) {
				entitySpellChecker = entitySpell.getSpell().getValidTargetChecker();
				if (entitySpellChecker != null && !entitySpellChecker.isValidTarget(target)) {
					inRange.remove(i);
					break;
				}

				entitySpell.castAtEntity(caster, target, power);
				if (spell != null) spell.playEffects(EffectPosition.TARGET, target, data);
			} else if (entitySpell != null && entitySpell.isTargetedLocationSpell()) {
				entitySpell.castAtLocation(caster, currentLoc.clone(), power);
				if (spell != null) spell.playEffects(EffectPosition.TARGET, currentLoc, data);
			}

			if (entityLocationSpell != null) entityLocationSpell.castAtLocation(caster, currentLoc, power);

			inRange.remove(i);
			immune.add(target);
			maxHitLimit++;

			if (maxEntitiesHit > 0 && maxHitLimit >= maxEntitiesHit) stop();
			break;
		}
	}

	@Override
	public void stop() {
		stop(true);
	}

	public void stop(boolean removeTracker) {
		if (removeTracker && spell != null) ParticleProjectileSpell.getProjectileTrackers().remove(tracker);
		if (spell != null) spell.playEffects(EffectPosition.DELAYED, currentLocation, data);
		MagicSpells.cancelTask(taskId);
		if (effectSet != null) {
			for (EffectlibSpellEffect spellEffect : effectSet) {
				if (spellEffect == null) continue;
				if (spellEffect.getEffect() == null) continue;
				spellEffect.getEffect().cancel();
			}
			effectSet.clear();
		}
		if (armorStandSet != null) {
			for (ArmorStand armorStand : armorStandSet) {
				armorStand.remove();
			}
			armorStandSet.clear();
		}
		if (entitySet != null) {
			for (Entity entity : entitySet) {
				entity.remove();
			}
			entitySet.clear();
		}
		caster = null;
		startLocation = null;
		previousLocation = null;
		currentLocation = null;
		currentVelocity = null;
		if (immune != null) {
			immune.clear();
			immune = null;
		}
		if (inRange != null) {
			inRange.clear();
			inRange = null;
		}
		stopped = true;
	}

	public LivingEntity getCaster() {
		return caster;
	}

	public void setCaster(LivingEntity caster) {
		this.caster = caster;
	}

	public float getPower() {
		return power;
	}

	public void setPower(float power) {
		this.power = power;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public Location getStartLocation() {
		return startLocation;
	}

	public void setStartLocation(Location startLocation) {
		this.startLocation = startLocation;
	}

	public Location getPreviousLocation() {
		return previousLocation;
	}

	public void setPreviousLocation(Location previousLocation) {
		this.previousLocation = previousLocation;
	}

	public Location getCurrentLocation() {
		return currentLocation;
	}

	public void setCurrentLocation(Location currentLocation) {
		this.currentLocation = currentLocation;
	}

	public Vector getCurrentVelocity() {
		return currentVelocity;
	}

	public void setCurrentVelocity(Vector currentVelocity) {
		this.currentVelocity = currentVelocity;
	}

	public Vector getStartDirection() {
		return startDirection;
	}

	public void setStartDirection(Vector startDirection) {
		this.startDirection = startDirection;
	}

	public int getCurrentX() {
		return currentX;
	}

	public void setCurrentX(int currentX) {
		this.currentX = currentX;
	}

	public int getCurrentZ() {
		return currentZ;
	}

	public void setCurrentZ(int currentZ) {
		this.currentZ = currentZ;
	}

	public int getTaskId() {
		return taskId;
	}

	public void setTaskId(int taskId) {
		this.taskId = taskId;
	}

	public BoundingBox getHitBox() {
		return hitBox;
	}

	public void setHitBox(BoundingBox hitBox) {
		this.hitBox = hitBox;
	}

	public List<LivingEntity> getInRange() {
		return inRange;
	}

	public void setInRange(List<LivingEntity> inRange) {
		this.inRange = inRange;
	}

	public int getMaxHitLimit() {
		return maxHitLimit;
	}

	public void setMaxHitLimit(int maxHitLimit) {
		this.maxHitLimit = maxHitLimit;
	}

	public Set<LivingEntity> getImmune() {
		return immune;
	}

	public void setImmune(Set<LivingEntity> immune) {
		this.immune = immune;
	}

	public ValidTargetChecker getEntitySpellChecker() {
		return entitySpellChecker;
	}

	public void setEntitySpellChecker(ValidTargetChecker entitySpellChecker) {
		this.entitySpellChecker = entitySpellChecker;
	}

	public ParticleProjectileTracker getTracker() {
		return tracker;
	}

	public void setTracker(ParticleProjectileTracker tracker) {
		this.tracker = tracker;
	}

	public ParticleProjectileSpell getSpell() {
		return spell;
	}

	public void setSpell(ParticleProjectileSpell spell) {
		this.spell = spell;
	}

	public Set<Material> getGroundMaterials() {
		return groundMaterials;
	}

	public void setGroundMaterials(Set<Material> groundMaterials) {
		this.groundMaterials = groundMaterials;
	}

	public Set<Material> getDisallowedGroundMaterials() {
		return disallowedGroundMaterials;
	}

	public void setDisallowedGroundMaterials(Set<Material> disallowedGroundMaterials) {
		this.disallowedGroundMaterials = disallowedGroundMaterials;
	}

	public ValidTargetList getTargetList() {
		return targetList;
	}

	public void setTargetList(ValidTargetList targetList) {
		this.targetList = targetList;
	}

	public ModifierSet getProjectileModifiers() {
		return projectileModifiers;
	}

	public void setProjectileModifiers(ModifierSet projectileModifiers) {
		this.projectileModifiers = projectileModifiers;
	}

	public Map<String, Subspell> getInteractionSpells() {
		return interactionSpells;
	}

	public void setInteractionSpells(Map<String, Subspell> interactionSpells) {
		this.interactionSpells = interactionSpells;
	}

	public int getCounter() {
		return counter;
	}

	public void setCounter(int counter) {
		this.counter = counter;
	}

	public double getMaxDuration() {
		return maxDuration;
	}

	public void setMaxDuration(double maxDuration) {
		this.maxDuration = maxDuration;
	}

	public double getMaxDistanceSquared() {
		return maxDistanceSquared;
	}

	public void setMaxDistanceSquared(double maxDistanceSquared) {
		this.maxDistanceSquared = maxDistanceSquared;
	}

	public boolean shouldHugSurface() {
		return hugSurface;
	}

	public void setHugSurface(boolean hugSurface) {
		this.hugSurface = hugSurface;
	}

	public boolean shouldChangePitch() {
		return changePitch;
	}

	public void setChangePitch(boolean changePitch) {
		this.changePitch = changePitch;
	}

	public boolean isControllable() {
		return controllable;
	}

	public void setControllable(boolean controllable) {
		this.controllable = controllable;
	}

	public boolean shouldCallEvents() {
		return callEvents;
	}

	public void setCallEvents(boolean callEvents) {
		this.callEvents = callEvents;
	}

	public boolean shouldStopOnHitGround() {
		return stopOnHitGround;
	}

	public void setStopOnHitGround(boolean stopOnHitGround) {
		this.stopOnHitGround = stopOnHitGround;
	}

	public boolean shouldStopOnModifierFail() {
		return stopOnModifierFail;
	}

	public void setStopOnModifierFail(boolean stopOnModifierFail) {
		this.stopOnModifierFail = stopOnModifierFail;
	}

	public boolean isCasterAllowedToInteract() {
		return allowCasterInteract;
	}

	public void setAllowCasterInteract(boolean allowCasterInteract) {
		this.allowCasterInteract = allowCasterInteract;
	}

	public boolean isPowerAffectedByVelocity() {
		return powerAffectsVelocity;
	}

	public void setPowerAffectsVelocity(boolean powerAffectsVelocity) {
		this.powerAffectsVelocity = powerAffectsVelocity;
	}

	public boolean canHitGround() {
		return hitGround;
	}

	public void setHitGround(boolean hitGround) {
		this.hitGround = hitGround;
	}

	public boolean canHitAirAtEnd() {
		return hitAirAtEnd;
	}

	public void setHitAirAtEnd(boolean hitAirAtEnd) {
		this.hitAirAtEnd = hitAirAtEnd;
	}

	public boolean canHitAirDuring() {
		return hitAirDuring;
	}

	public void setHitAirDuring(boolean hitAirDuring) {
		this.hitAirDuring = hitAirDuring;
	}

	public boolean canHitAirAfterDuration() {
		return hitAirAfterDuration;
	}

	public void setHitAirAfterDuration(boolean hitAirAfterDuration) {
		this.hitAirAfterDuration = hitAirAfterDuration;
	}

	public float getAcceleration() {
		return acceleration;
	}

	public void setAcceleration(float acceleration) {
		this.acceleration = acceleration;
	}

	public float getProjectileTurn() {
		return projectileTurn;
	}

	public void setProjectileTurn(float projectileTurn) {
		this.projectileTurn = projectileTurn;
	}

	public float getProjectileVelocity() {
		return projectileVelocity;
	}

	public void setProjectileVelocity(float projectileVelocity) {
		this.projectileVelocity = projectileVelocity;
	}

	public void setVerticalRotation(double verticalRotation) {
		this.verticalRotation = verticalRotation;
	}

	public double getVerticalRotation() {
		return verticalRotation;
	}

	public void setHorizontalRotation(double horizontalRotation) {
		this.horizontalRotation = horizontalRotation;
	}

	public double getHorizontalRotation() {
		return horizontalRotation;
	}

	public void setXRotation(double xRotation) {
		this.xRotation = xRotation;
	}

	public double getXRotation() {
		return xRotation;
	}

	public float getProjectileVertOffset() {
		return projectileVertOffset;
	}

	public void setProjectileVertOffset(float projectileVertOffset) {
		this.projectileVertOffset = projectileVertOffset;
	}

	public float getProjectileVertSpread() {
		return projectileVertSpread;
	}

	public void setProjectileVertSpread(float projectileVertSpread) {
		this.projectileVertSpread = projectileVertSpread;
	}

	public float getProjectileHorizOffset() {
		return projectileHorizOffset;
	}

	public void setProjectileHorizOffset(float projectileHorizOffset) {
		this.projectileHorizOffset = projectileHorizOffset;
	}

	public float getProjectileHorizSpread() {
		return projectileHorizSpread;
	}

	public void setProjectileHorizSpread(float projectileHorizSpread) {
		this.projectileHorizSpread = projectileHorizSpread;
	}

	public float getProjectileVertGravity() {
		return projectileVertGravity;
	}

	public void setProjectileVertGravity(float projectileVertGravity) {
		this.projectileVertGravity = projectileVertGravity;
	}

	public float getProjectileHorizGravity() {
		return projectileHorizGravity;
	}

	public void setProjectileHorizGravity(float projectileHorizGravity) {
		this.projectileHorizGravity = projectileHorizGravity;
	}

	public int getTickInterval() {
		return tickInterval;
	}

	public void setTickInterval(int tickInterval) {
		this.tickInterval = tickInterval;
	}

	public int getSpellInterval() {
		return spellInterval;
	}

	public void setSpellInterval(int spellInterval) {
		this.spellInterval = spellInterval;
	}

	public int getTickSpellLimit() {
		return tickSpellLimit;
	}

	public void setTickSpellLimit(int tickSpellLimit) {
		this.tickSpellLimit = tickSpellLimit;
	}

	public int getMaxEntitiesHit() {
		return maxEntitiesHit;
	}

	public void setMaxEntitiesHit(int maxEntitiesHit) {
		this.maxEntitiesHit = maxEntitiesHit;
	}

	public int getAccelerationDelay() {
		return accelerationDelay;
	}

	public void setAccelerationDelay(int accelerationDelay) {
		this.accelerationDelay = accelerationDelay;
	}

	public int getIntermediateEffects() {
		return intermediateEffects;
	}

	public void setIntermediateEffects(int intermediateEffects) {
		this.intermediateEffects = intermediateEffects;
	}

	public int getIntermediateHitboxes() {
		return intermediateHitboxes;
	}

	public void setIntermediateHitboxes(int intermediateHitboxes) {
		this.intermediateHitboxes = intermediateHitboxes;
	}

	public int getSpecialEffectInterval() {
		return specialEffectInterval;
	}

	public void setSpecialEffectInterval(int specialEffectInterval) {
		this.specialEffectInterval = specialEffectInterval;
	}

	public int getGroundVerticalHitRadius() {
		return groundVerticalHitRadius;
	}

	public void setGroundVerticalHitRadius(int groundVerticalHitRadius) {
		this.groundVerticalHitRadius = groundVerticalHitRadius;
	}

	public int getGroundHorizontalHitRadius() {
		return groundHorizontalHitRadius;
	}

	public void setGroundHorizontalHitRadius(int groundHorizontalHitRadius) {
		this.groundHorizontalHitRadius = groundHorizontalHitRadius;
	}

	public float getStartXOffset() {
		return startXOffset;
	}

	public void setStartXOffset(float startXOffset) {
		this.startXOffset = startXOffset;
	}

	public float getStartYOffset() {
		return startYOffset;
	}

	public void setStartYOffset(float startYOffset) {
		this.startYOffset = startYOffset;
	}

	public float getStartZOffset() {
		return startZOffset;
	}

	public void setStartZOffset(float startZOffset) {
		this.startZOffset = startZOffset;
	}

	public float getTargetYOffset() {
		return targetYOffset;
	}

	public void setTargetYOffset(float targetYOffset) {
		this.targetYOffset = targetYOffset;
	}

	public Vector getEffectOffset() {
		return effectOffset;
	}

	public void setEffectOffset(Vector effectOffset) {
		this.effectOffset = effectOffset;
	}

	public float getTicksPerSecond() {
		return ticksPerSecond;
	}

	public void setTicksPerSecond(float ticksPerSecond) {
		this.ticksPerSecond = ticksPerSecond;
	}

	public float getHeightFromSurface() {
		return heightFromSurface;
	}

	public void setHeightFromSurface(float heightFromSurface) {
		this.heightFromSurface = heightFromSurface;
	}

	public float getVerticalHitRadius() {
		return verticalHitRadius;
	}

	public void setVerticalHitRadius(float verticalHitRadius) {
		this.verticalHitRadius = verticalHitRadius;
	}

	public float getHorizontalHitRadius() {
		return horizontalHitRadius;
	}

	public void setHorizontalHitRadius(float horizontalHitRadius) {
		this.horizontalHitRadius = horizontalHitRadius;
	}

	public Subspell getAirSpell() {
		return airSpell;
	}

	public void setAirSpell(Subspell airSpell) {
		this.airSpell = airSpell;
	}

	public Subspell getTickSpell() {
		return tickSpell;
	}

	public void setTickSpell(Subspell tickSpell) {
		this.tickSpell = tickSpell;
	}

	public Subspell getEntitySpell() {
		return entitySpell;
	}

	public void setEntitySpell(Subspell entitySpell) {
		this.entitySpell = entitySpell;
	}

	public Subspell getCasterSpell() {
		return casterSpell;
	}

	public void setCasterSpell(Subspell casterSpell) {
		this.casterSpell = casterSpell;
	}

	public Subspell getGroundSpell() {
		return groundSpell;
	}

	public void setGroundSpell(Subspell groundSpell) {
		this.groundSpell = groundSpell;
	}

	public Subspell getDurationSpell() {
		return durationSpell;
	}

	public void setDurationSpell(Subspell durationSpell) {
		this.durationSpell = durationSpell;
	}

	public Subspell getModifierSpell() {
		return modifierSpell;
	}

	public void setModifierSpell(Subspell modifierSpell) {
		this.modifierSpell = modifierSpell;
	}

	public Subspell getEntityLocationSpell() {
		return entityLocationSpell;
	}

	public void setEntityLocationSpell(Subspell entityLocationSpell) {
		this.entityLocationSpell = entityLocationSpell;
	}

	public String[] getArgs() {
		return args;
	}

	public SpellData getSpellData() {
		return data;
	}

}
