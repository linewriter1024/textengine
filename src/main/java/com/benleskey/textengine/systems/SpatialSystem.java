package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.util.*;

/**
 * SpatialSystem manages entity positions in n-dimensional space with scale
 * support.
 * Positions are persisted in the database and separated by scale (continent,
 * building, universe, etc.).
 * Supports 2D, 3D, or higher dimensional coordinate systems.
 * Generic and reusable across different world generation strategies.
 */
public class SpatialSystem extends SingletonGameSystem implements OnSystemInitialize {

	// Spatial scale constant - currently only using continent scale for world
	// generation
	public static UniqueType SCALE_CONTINENT;

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
			// Create spatial_position table
			// Stores entity positions with scale separation
			// Uses flexible column structure to support N dimensions
			try (var stmt = game.db().prepareStatement("""
						CREATE TABLE spatial_position (
							entity_id INTEGER NOT NULL,
							scale_id INTEGER NOT NULL,
							x INTEGER NOT NULL,
							y INTEGER NOT NULL,
							z INTEGER DEFAULT 0,
							w INTEGER DEFAULT 0,
							PRIMARY KEY (entity_id, scale_id)
						)
					""")) {
				stmt.execute();
			} catch (Exception e) {
				throw new RuntimeException("Failed to create spatial_position table", e);
			}

			try (var stmt = game.db().prepareStatement("""
						CREATE INDEX idx_spatial_scale ON spatial_position(scale_id)
					""")) {
				stmt.execute();
			} catch (Exception e) {
				throw new RuntimeException("Failed to create spatial index", e);
			}

			try (var stmt = game.db().prepareStatement("""
						CREATE INDEX idx_spatial_coords ON spatial_position(scale_id, x, y)
					""")) {
				stmt.execute();
			} catch (Exception e) {
				throw new RuntimeException("Failed to create coordinate index", e);
			}

			getSchema().setVersionNumber(1);
		}

		// Initialize the scale constant we're currently using
		SCALE_CONTINENT = game.getUniqueTypeSystem().getType("scale_continent");
	}

	/**
	 * Set the position of an entity at a specific scale.
	 * 
	 * @param entity The entity to position
	 * @param scale  The spatial scale (e.g., SCALE_CONTINENT, SCALE_BUILDING)
	 * @param coords Coordinates (must match dimensionality, up to 4 dimensions
	 *               supported)
	 */
	public void setPosition(Entity entity, UniqueType scale, int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
					String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}
		if (coords.length > 4) {
			throw new IllegalArgumentException("Maximum 4 dimensions supported in current schema");
		}

		try (var stmt = game.db().prepareStatement("""
					INSERT OR REPLACE INTO spatial_position (entity_id, scale_id, x, y, z, w)
					VALUES (?, ?, ?, ?, ?, ?)
				""")) {
			stmt.setLong(1, entity.getId());
			stmt.setLong(2, scale.type());
			stmt.setInt(3, coords.length > 0 ? coords[0] : 0);
			stmt.setInt(4, coords.length > 1 ? coords[1] : 0);
			stmt.setInt(5, coords.length > 2 ? coords[2] : 0);
			stmt.setInt(6, coords.length > 3 ? coords[3] : 0);
			stmt.executeUpdate();
		} catch (Exception e) {
			throw new RuntimeException("Failed to set position", e);
		}
	}

	/**
	 * Get the position of an entity at a specific scale.
	 * 
	 * @param entity The entity
	 * @param scale  The spatial scale
	 * @return Coordinates, or null if entity has no position at this scale
	 */
	public int[] getPosition(Entity entity, UniqueType scale) {
		try (var stmt = game.db().prepareStatement("""
					SELECT x, y, z, w FROM spatial_position
					WHERE entity_id = ? AND scale_id = ?
				""")) {
			stmt.setLong(1, entity.getId());
			stmt.setLong(2, scale.type());

			try (var rs = stmt.executeQuery()) {
				if (rs.next()) {
					int[] coords = new int[dimensions];
					for (int i = 0; i < dimensions && i < 4; i++) {
						coords[i] = rs.getInt(i + 1);
					}
					return coords;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to get position", e);
		}
		return null;
	}

	/**
	 * Get the entity at a specific position and scale.
	 * 
	 * @param scale  The spatial scale
	 * @param coords Coordinates
	 * @return Entity at that position, or null if empty
	 */
	public Entity getEntityAt(UniqueType scale, int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
					String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}

		try (var stmt = game.db().prepareStatement("""
					SELECT entity_id FROM spatial_position
					WHERE scale_id = ? AND x = ? AND y = ?
					AND (z = ? OR ? >= 3)
					AND (w = ? OR ? >= 4)
				""")) {
			stmt.setLong(1, scale.type());
			stmt.setInt(2, coords.length > 0 ? coords[0] : 0);
			stmt.setInt(3, coords.length > 1 ? coords[1] : 0);
			stmt.setInt(4, coords.length > 2 ? coords[2] : 0);
			stmt.setInt(5, dimensions);
			stmt.setInt(6, coords.length > 3 ? coords[3] : 0);
			stmt.setInt(7, dimensions);

			try (var rs = stmt.executeQuery()) {
				if (rs.next()) {
					return game.getSystem(EntitySystem.class).get(rs.getLong(1));
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to get entity at position", e);
		}
		return null;
	}

	/**
	 * Check if a position is occupied at a specific scale.
	 * 
	 * @param scale  The spatial scale
	 * @param coords Coordinates
	 * @return True if an entity exists at that position
	 */
	public boolean isOccupied(UniqueType scale, int... coords) {
		return getEntityAt(scale, coords) != null;
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
	 * Get all entities within a certain distance of a position at a specific scale.
	 * 
	 * @param scale       The spatial scale
	 * @param coords      Center position
	 * @param maxDistance Maximum distance
	 * @return List of entities within range
	 */
	public List<Entity> getEntitiesInRange(UniqueType scale, int[] coords, double maxDistance) {
		List<Entity> result = new ArrayList<>();

		try (var stmt = game.db().prepareStatement("""
					SELECT entity_id, x, y, z, w FROM spatial_position
					WHERE scale_id = ?
				""")) {
			stmt.setLong(1, scale.type());

			try (var rs = stmt.executeQuery()) {
				while (rs.next()) {
					int[] entityCoords = new int[dimensions];
					for (int i = 0; i < dimensions && i < 4; i++) {
						entityCoords[i] = rs.getInt(i + 2); // +2 because column 1 is entity_id
					}

					if (distance(coords, entityCoords) <= maxDistance) {
						Entity entity = game.getSystem(EntitySystem.class).get(rs.getLong(1));
						if (entity != null) {
							result.add(entity);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to get entities in range", e);
		}

		return result;
	}

	/**
	 * Remove position data for an entity at a specific scale.
	 * 
	 * @param entity The entity
	 * @param scale  The spatial scale
	 */
	public void removePosition(Entity entity, UniqueType scale) {
		try (var stmt = game.db().prepareStatement("""
					DELETE FROM spatial_position
					WHERE entity_id = ? AND scale_id = ?
				""")) {
			stmt.setLong(1, entity.getId());
			stmt.setLong(2, scale.type());
			stmt.executeUpdate();
		} catch (Exception e) {
			throw new RuntimeException("Failed to remove position", e);
		}
	}

	/**
	 * Get all positioned entities at a specific scale.
	 * 
	 * @param scale The spatial scale
	 * @return Set of all entities with positions at this scale
	 */
	public Set<Entity> getAllPositionedEntities(UniqueType scale) {
		Set<Entity> result = new HashSet<>();

		try (var stmt = game.db().prepareStatement("""
					SELECT entity_id FROM spatial_position
					WHERE scale_id = ?
				""")) {
			stmt.setLong(1, scale.type());

			try (var rs = stmt.executeQuery()) {
				while (rs.next()) {
					Entity entity = game.getSystem(EntitySystem.class).get(rs.getLong(1));
					if (entity != null) {
						result.add(entity);
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to get all positioned entities", e);
		}

		return result;
	}

	/**
	 * Find the entity from a list that is closest to a target position.
	 * This is used for pathfinding - selecting which adjacent exit to take
	 * when navigating toward a distant landmark.
	 * 
	 * @param scale      The spatial scale
	 * @param candidates List of candidate entities (e.g., exit destinations)
	 * @param target     The target entity we're trying to reach
	 * @return The candidate entity closest to the target, or null if no candidates
	 *         have positions
	 */
	public Entity findClosestToTarget(UniqueType scale, List<Entity> candidates, Entity target) {
		int[] targetPos = getPosition(target, scale);
		if (targetPos == null) {
			return null; // Target has no position
		}

		Entity closest = null;
		double bestDistance = Double.MAX_VALUE;

		for (Entity candidate : candidates) {
			int[] candidatePos = getPosition(candidate, scale);
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
}
