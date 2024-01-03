package com.benleskey.textengine;

import com.benleskey.textengine.exceptions.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SchemaManager {
	private final Game game;

	public SchemaManager(Game game) {
		this.game = game;
	}

	public void initialize() throws DatabaseException {
		try {
			try (PreparedStatement s = game.db().prepareStatement("CREATE TABLE IF NOT EXISTS system_schema(system_id TEXT PRIMARY KEY, version_number INTEGER)")) {
				s.execute();
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to initialize schema table", e);
		}
	}

	public Schema getSchema(String systemId) {
		return new Schema(this, systemId);
	}

	public class Schema {
		private final SchemaManager schemaManager;
		private final String systemId;

		public Schema(SchemaManager schemaManager, String systemId) {
			this.schemaManager = schemaManager;
			this.systemId = systemId;
		}

		public String getSystemId() {
			return systemId;
		}

		public int getVersionNumber() throws DatabaseException {
			try {
				try (PreparedStatement s = game.db().prepareStatement("SELECT version_number FROM system_schema WHERE system_id = ?")) {
					s.setString(1, systemId);
					try (ResultSet rs = s.executeQuery()) {
						while (rs.next()) {
							return rs.getInt(1);
						}
					}
					return 0;
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to get version number", e);
			}
		}

		public void setVersionNumber(int versionNumber) throws DatabaseException {
			try {
				try (PreparedStatement s = game.db().prepareStatement("INSERT OR REPLACE INTO system_schema(system_id, version_number) VALUES(?, ?)")) {
					s.setString(1, systemId);
					s.setInt(2, versionNumber);
					s.executeUpdate();
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to set version number", e);
			}
		}
	}
}
