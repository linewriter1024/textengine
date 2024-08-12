package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.GameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import lombok.Getter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class PropertiesSubSystem<TGroup, TProperty, TValue> extends GameSystem {
	@Getter
	private final String tableName;
	private final Handler<TGroup> groupHandler;
	private final Handler<TProperty> propertyHandler;
	private final Handler<TValue> valueHandler;
	private PreparedStatement getPreparedStatement;
	private PreparedStatement setPreparedStatement;
	private PreparedStatement deletePreparedStatement;

	public PropertiesSubSystem(Game game, String tableName, Handler<TGroup> group, Handler<TProperty> property, Handler<TValue> value) {
		super(game);
		this.log = game.log.withPrefix(this.getClass().getSimpleName() + "$" + tableName);
		this.tableName = tableName;
		this.groupHandler = group;
		this.propertyHandler = property;
		this.valueHandler = value;
	}

	public static Handler<Long> longHandler() {
		return new Handler<>("INTEGER", (rs, n) -> rs.getLong(n), (s, value, n) -> s.setLong(n, value));
	}

	public static Handler<String> stringHandler() {
		return new Handler<>("TEXT", (rs, n) -> rs.getString(n), (s, value, n) -> s.setString(n, value));
	}

	@Override
	public String getId() {
		return super.getId() + "$" + tableName;
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE " + tableName + " (pgroup " + groupHandler.sqlType() + ", property " + propertyHandler.sqlType() + ", value " + valueHandler.sqlType() + ", PRIMARY KEY (pgroup, property))");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to initialize properties table " + this, e);
			}

			getSchema().setVersionNumber(1);
		}

		try {
			getPreparedStatement = game.db().prepareStatement("SELECT value FROM " + tableName + " WHERE pgroup = ? AND property = ?");
			setPreparedStatement = game.db().prepareStatement("INSERT OR REPLACE INTO " + tableName + " (pgroup, property, value) VALUES (?, ?, ?)");
			deletePreparedStatement = game.db().prepareStatement("DELETE FROM " + tableName + " WHERE pgroup = ? AND property = ?");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare properties statements " + this, e);
		}
	}

	public synchronized Optional<TValue> get(TGroup group, TProperty property) throws DatabaseException {
		try {
			groupHandler.parameterSetter().set(getPreparedStatement, group, 1);
			propertyHandler.parameterSetter().set(getPreparedStatement, property, 2);
			try (ResultSet rs = getPreparedStatement.executeQuery()) {
				if (rs.next()) {
					return Optional.of(valueHandler.resultFetcher().get(rs, 1));
				} else {
					return Optional.empty();
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get " + this + " '" + group + "' '" + property + "'", e);
		}
	}

	public synchronized void set(TGroup group, TProperty property, TValue value) throws DatabaseException {
		try {
			if (value == null) {
				groupHandler.parameterSetter().set(deletePreparedStatement, group, 1);
				propertyHandler.parameterSetter().set(deletePreparedStatement, property, 2);
				deletePreparedStatement.execute();
			} else {
				groupHandler.parameterSetter().set(setPreparedStatement, group, 1);
				propertyHandler.parameterSetter().set(setPreparedStatement, property, 2);
				valueHandler.parameterSetter().set(setPreparedStatement, value, 3);
				setPreparedStatement.execute();
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to set " + this + " '" + group + "' '" + property + "' = " + value, e);
		}
	}

	@FunctionalInterface
	public interface ResultFetcher<T> {
		T get(ResultSet rs, int n) throws SQLException;
	}

	@FunctionalInterface
	public interface ParameterSetter<T> {
		void set(PreparedStatement s, T value, int n) throws SQLException;
	}

	public record Handler<T>(String sqlType, ResultFetcher<T> resultFetcher, ParameterSetter<T> parameterSetter) {
	}
}
