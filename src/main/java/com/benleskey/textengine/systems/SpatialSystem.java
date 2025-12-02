package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.model.FullEvent;
import com.benleskey.textengine.exceptions.DatabaseException;

import java.util.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

	// Event type for spatial positions
	public UniqueType etEntityPosition;

	// Dimensionality of the space (2D, 3D, etc.)
	private int dimensions = 2;

	private PreparedStatement addPositionStatement;
	private PreparedStatement getCurrentPositionStatement;

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

		// Replace old spatial_position with event-backed entity_position
		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					// Create entity_position schema (no legacy handling)
					s.executeUpdate(
							"CREATE TABLE entity_position(position_id INTEGER PRIMARY KEY, entity_id INTEGER, scale_id INTEGER, x INTEGER, y INTEGER, z INTEGER DEFAULT 0, w INTEGER DEFAULT 0)");
					// Indexes for common lookups
					s.executeUpdate("CREATE INDEX idx_entity_position_entity ON entity_position(entity_id)");
					s.executeUpdate("CREATE INDEX idx_entity_position_scale ON entity_position(scale_id)");
					s.executeUpdate("CREATE INDEX idx_entity_position_coords ON entity_position(scale_id, x, y)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create spatial position tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		var uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);

		// Prepare statements (EventSystem exists due to SpatialPlugin dependency)
		try {
			addPositionStatement = game.db().prepareStatement(
					"INSERT INTO entity_position (position_id, entity_id, scale_id, x, y, z, w) VALUES (?, ?, ?, ?, ?, ?, ?)");
			var es = game.getSystem(EventSystem.class);
			getCurrentPositionStatement = game.db().prepareStatement(
					"SELECT entity_position.x, entity_position.y, entity_position.z, entity_position.w FROM entity_position WHERE entity_position.entity_id = ? AND entity_position.scale_id = ? AND entity_position.position_id IN "
							+ es.getValidEventsSubquery("entity_position.position_id"));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare spatial statements", e);
		}

		// Initialize types
		SCALE_CONTINENT = uniqueTypeSystem.getType("scale_continent");
		etEntityPosition = uniqueTypeSystem.getType("event_entity_position");
	}

	/**
	 * Set the position of an entity at a specific scale.
	 * 
	 * @param entity The entity to position
	 * @param scale  The spatial scale (e.g., SCALE_CONTINENT, SCALE_BUILDING)
	 * @param coords Coordinates (must match dimensionality, up to 4 dimensions
	 *               supported)
	 */
	public FullEvent<?> setPosition(Entity entity, UniqueType scale, int... coords) {
		if (coords.length != dimensions) {
			throw new IllegalArgumentException(
					String.format("Expected %d coordinates, got %d", dimensions, coords.length));
		}
		if (coords.length > 4) {
			throw new IllegalArgumentException("Maximum 4 dimensions supported in current schema");
		}

		try {
			long id = game.getNewGlobalId();
			addPositionStatement.setLong(1, id);
			addPositionStatement.setLong(2, entity.getId());
			addPositionStatement.setLong(3, scale.type());
			addPositionStatement.setInt(4, coords.length > 0 ? coords[0] : 0);
			addPositionStatement.setInt(5, coords.length > 1 ? coords[1] : 0);
			addPositionStatement.setInt(6, coords.length > 2 ? coords[2] : 0);
			addPositionStatement.setInt(7, coords.length > 3 ? coords[3] : 0);
			addPositionStatement.executeUpdate();
			return game.getSystem(EventSystem.class).addEventNow(etEntityPosition,
					new com.benleskey.textengine.model.BaseReference(id, game));
		} catch (SQLException e) {
			throw new DatabaseException("Failed to set position", e);
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
		try {
			getCurrentPositionStatement.setLong(1, entity.getId());
			getCurrentPositionStatement.setLong(2, scale.type());
			var es = game.getSystem(EventSystem.class);
			es.setValidEventsSubqueryParameters(getCurrentPositionStatement, 3, etEntityPosition,
					game.getSystem(WorldSystem.class).getCurrentTime());
			try (ResultSet rs = getCurrentPositionStatement.executeQuery()) {
				if (rs.next()) {
					int[] coords = new int[dimensions];
					for (int i = 0; i < dimensions && i < 4; i++) {
						coords[i] = rs.getInt(i + 1);
					}
					return coords;
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Failed to get position", e);
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

		try {
			var es = game.getSystem(EventSystem.class);
			PreparedStatement stmt = game.db().prepareStatement(
					"SELECT entity_position.entity_id FROM entity_position WHERE entity_position.scale_id = ? AND entity_position.x = ? AND entity_position.y = ? AND (entity_position.z = ? OR ? >= 3) AND (entity_position.w = ? OR ? >= 4) AND entity_position.position_id IN "
							+ es.getValidEventsSubquery("entity_position.position_id"));
			stmt.setLong(1, scale.type());
			stmt.setInt(2, coords.length > 0 ? coords[0] : 0);
			stmt.setInt(3, coords.length > 1 ? coords[1] : 0);
			stmt.setInt(4, coords.length > 2 ? coords[2] : 0);
			stmt.setInt(5, dimensions);
			stmt.setInt(6, coords.length > 3 ? coords[3] : 0);
			stmt.setInt(7, dimensions);
			// valid events subquery params
			es.setValidEventsSubqueryParameters(stmt, 8, etEntityPosition,
					game.getSystem(WorldSystem.class).getCurrentTime());
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return game.getSystem(EntitySystem.class).get(rs.getLong(1));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Failed to get entity at position", e);
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

		try {
			var es = game.getSystem(EventSystem.class);
			PreparedStatement stmt = game.db().prepareStatement(
					"SELECT entity_position.entity_id, entity_position.x, entity_position.y, entity_position.z, entity_position.w FROM entity_position WHERE entity_position.scale_id = ? AND entity_position.position_id IN "
							+ es.getValidEventsSubquery("entity_position.position_id"));
			stmt.setLong(1, scale.type());
			es.setValidEventsSubqueryParameters(stmt, 2, etEntityPosition,
					game.getSystem(WorldSystem.class).getCurrentTime());

			try (ResultSet rs = stmt.executeQuery()) {
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
		} catch (SQLException e) {
			throw new DatabaseException("Failed to get entities in range", e);
		}

		return result;
	}

	/**
	 * Remove position events for an entity at a specific scale.
	 * Cancels all valid position events (even if typically only one exists).
	 */
	public void removePosition(Entity entity, UniqueType scale) {
		EventSystem es = game.getSystem(EventSystem.class);
		try {
			PreparedStatement findStmt = game.db().prepareStatement(
					"SELECT event.event_id FROM event " +
							"JOIN entity_position ON entity_position.position_id = event.reference " +
							"WHERE event.type = ? AND entity_position.entity_id = ? AND entity_position.scale_id = ? " +
							"AND entity_position.position_id IN "
							+ es.getValidEventsSubquery("entity_position.position_id"));
			findStmt.setLong(1, etEntityPosition.type());
			findStmt.setLong(2, entity.getId());
			findStmt.setLong(3, scale.type());
			es.setValidEventsSubqueryParameters(findStmt, 4, etEntityPosition,
					game.getSystem(WorldSystem.class).getCurrentTime());
			try (ResultSet rs = findStmt.executeQuery()) {
				while (rs.next()) {
					es.cancelEvent(rs.getLong(1));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Failed to remove position", e);
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
				SELECT entity_position.entity_id FROM entity_position
				WHERE entity_position.scale_id = ? AND entity_position.position_id IN
				""" + game.getSystem(EventSystem.class).getValidEventsSubquery("entity_position.position_id"))) {
			stmt.setLong(1, scale.type());
			game.getSystem(EventSystem.class).setValidEventsSubqueryParameters(stmt, 2, etEntityPosition,
					game.getSystem(WorldSystem.class).getCurrentTime());

			try (var rs = stmt.executeQuery()) {
				while (rs.next()) {
					Entity entity = game.getSystem(EntitySystem.class).get(rs.getLong(1));
					if (entity != null) {
						result.add(entity);
					}
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Failed to get all positioned entities", e);
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
