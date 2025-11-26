package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;

import java.util.*;

/**
 * SpatialSystem manages entity positions in n-dimensional space.
 * Supports 2D, 3D, or higher dimensional coordinate systems.
 * Generic and reusable across different world generation strategies.
 */
public class SpatialSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Map from entity to its coordinates
	private final Map<Entity, int[]> entityToCoords = new HashMap<>();
	
	// Map from coordinates to entity (for quick lookup)
	private final Map<CoordKey, Entity> coordsToEntity = new HashMap<>();
	
	// Dimensionality of the space (2D, 3D, etc.)
	private int dimensions = 2;
	
	public SpatialSystem(Game game) {
		super(game);
	}
	
	/**
	 * Set the dimensionality of the spatial system.
	 * Must be called before any positions are set.
	 * 
	 * @param dimensions Number of dimensions (2 for 2D, 3 for 3D, etc.)
	 */
	public void setDimensions(int dimensions) {
		if (!entityToCoords.isEmpty()) {
			throw new IllegalStateException("Cannot change dimensions after positions have been set");
		}
		if (dimensions < 1) {
			throw new IllegalArgumentException("Dimensions must be at least 1");
		}
		this.dimensions = dimensions;
	}
	
	public int getDimensions() {
		return dimensions;
	}
	
	@Override
	public void onSystemInitialize() {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - all in-memory for performance
			getSchema().setVersionNumber(1);
		}
	}
	
	/**
	 * Set the position of an entity.
	 * 
	 * @param entity The entity to position
	 * @param coords Coordinates (must match dimensionality)
	 */
	public synchronized void setPosition(Entity entity, int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
				String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}
		
		// Remove old position if exists
		int[] oldCoords = entityToCoords.get(entity);
		if (oldCoords != null) {
			coordsToEntity.remove(new CoordKey(oldCoords));
		}
		
		// Set new position
		entityToCoords.put(entity, coords.clone());
		coordsToEntity.put(new CoordKey(coords), entity);
	}
	
	/**
	 * Get the position of an entity.
	 * 
	 * @param entity The entity
	 * @return Coordinates, or null if entity has no position
	 */
	public synchronized int[] getPosition(Entity entity) {
		int[] coords = entityToCoords.get(entity);
		return coords != null ? coords.clone() : null;
	}
	
	/**
	 * Get the entity at a specific position.
	 * 
	 * @param coords Coordinates
	 * @return Entity at that position, or null if empty
	 */
	public synchronized Entity getEntityAt(int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
				String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}
		return coordsToEntity.get(new CoordKey(coords));
	}
	
	/**
	 * Check if a position is occupied.
	 * 
	 * @param coords Coordinates
	 * @return True if an entity exists at that position
	 */
	public synchronized boolean isOccupied(int... coords) {
		return getEntityAt(coords) != null;
	}
	
	/**
	 * Get all adjacent positions (4 cardinal directions in 2D, 6 in 3D, etc.).
	 * Only returns positions along cardinal axes (not diagonals).
	 * 
	 * @param coords Center coordinates
	 * @return List of adjacent coordinate arrays
	 */
	public List<int[]> getAdjacentPositions(int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
				String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}
		
		List<int[]> adjacent = new ArrayList<>();
		
		// For each dimension, add positions offset by +1 and -1 in that dimension
		for (int dim = 0; dim < dimensions; dim++) {
			// Positive direction
			int[] posCoords = coords.clone();
			posCoords[dim]++;
			adjacent.add(posCoords);
			
			// Negative direction
			int[] negCoords = coords.clone();
			negCoords[dim]--;
			adjacent.add(negCoords);
		}
		
		return adjacent;
	}
	
	/**
	 * Calculate Euclidean distance between two positions.
	 * 
	 * @param coords1 First position
	 * @param coords2 Second position
	 * @return Distance
	 */
	public double distance(int[] coords1, int[] coords2) {
		if (coords1.length != dimensions || coords2.length != dimensions) {
			throw new IllegalArgumentException("Coordinate arrays must match dimensionality");
		}
		
		double sumSquares = 0;
		for (int i = 0; i < dimensions; i++) {
			int diff = coords1[i] - coords2[i];
			sumSquares += diff * diff;
		}
		return Math.sqrt(sumSquares);
	}
	
	/**
	 * Get all entities within a certain distance of a position.
	 * 
	 * @param coords Center position
	 * @param maxDistance Maximum distance
	 * @return List of entities within range
	 */
	public synchronized List<Entity> getEntitiesInRange(int[] coords, double maxDistance) {
		List<Entity> result = new ArrayList<>();
		
		for (Map.Entry<Entity, int[]> entry : entityToCoords.entrySet()) {
			if (distance(coords, entry.getValue()) <= maxDistance) {
				result.add(entry.getKey());
			}
		}
		
		return result;
	}
	
	/**
	 * Remove position data for an entity.
	 * 
	 * @param entity The entity
	 */
	public synchronized void removePosition(Entity entity) {
		int[] coords = entityToCoords.remove(entity);
		if (coords != null) {
			coordsToEntity.remove(new CoordKey(coords));
		}
	}
	
	/**
	 * Get all positioned entities.
	 * 
	 * @return Set of all entities with positions
	 */
	public synchronized Set<Entity> getAllPositionedEntities() {
		return new HashSet<>(entityToCoords.keySet());
	}
	
	/**
	 * Find the entity from a list that is closest to a target position.
	 * This is used for pathfinding - selecting which adjacent exit to take
	 * when navigating toward a distant landmark.
	 * 
	 * @param candidates List of candidate entities (e.g., exit destinations)
	 * @param target The target entity we're trying to reach
	 * @return The candidate entity closest to the target, or null if no candidates have positions
	 */
	public synchronized Entity findClosestToTarget(List<Entity> candidates, Entity target) {
		int[] targetPos = getPosition(target);
		if (targetPos == null) {
			return null; // Target has no position
		}
		
		Entity closest = null;
		double bestDistance = Double.MAX_VALUE;
		
		for (Entity candidate : candidates) {
			int[] candidatePos = getPosition(candidate);
			if (candidatePos != null) {
				double dist = distance(candidatePos, targetPos);
				if (dist < bestDistance) {
					bestDistance = dist;
					closest = candidate;
				}
			}
		}
		
		return closest;
	}
	
	/**
	 * Immutable wrapper for coordinates to use as map key.
	 */
	private static class CoordKey {
		private final int[] coords;
		private final int hashCode;
		
		CoordKey(int[] coords) {
			this.coords = coords.clone();
			this.hashCode = Arrays.hashCode(this.coords);
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			CoordKey that = (CoordKey) o;
			return Arrays.equals(coords, that.coords);
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
	}
}
