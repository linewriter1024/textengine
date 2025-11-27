package com.benleskey.textengine.cli;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Version;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.util.Logger;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.action.StoreTrueArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
	public static void main(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor(Version.toHumanString()).build();
		parser.addArgument("--apidebug").help("Print API debug information with each command").action(new StoreTrueArgumentAction());
		parser.addArgument("--showlog").help("Print game log to standard output").action(new StoreTrueArgumentAction());
		parser.addArgument("--seed").help("World generation seed for deterministic procedural generation").type(Long.class);
		parser.addArgument("--database").help("Database file path for persistence (default: timestamped temp file)").type(String.class);

		Namespace ns = parser.parseArgsOrFail(args);

		boolean apiDebug = ns.getBoolean("apidebug");
		boolean showLog = ns.getBoolean("showlog");
		Long seed = ns.getLong("seed");
		String databasePath = ns.getString("database");

		Logger logger = Logger.builder()
			.stream(showLog ? System.out : OutputStream.nullOutputStream())
			.build();

		// Use specified database path or default to timestamped temp file
		String dbFile;
		if (databasePath != null) {
			dbFile = databasePath;
			// Create parent directory if needed
			File dbFileObj = new File(dbFile);
			File parentDir = dbFileObj.getParentFile();
			if (parentDir != null) {
				parentDir.mkdirs();
			}
		} else {
			String directory = "/tmp/textengine";
			String filename = String.format("%d.sqlitedb", System.currentTimeMillis() / 1000L);
			(new File(directory)).mkdir();
			dbFile = directory + "/" + filename;
		}

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
			try {
				Game.GameBuilder builder = Game.builder().log(logger).databaseConnection(connection);
				if (seed != null) {
					builder.seed(seed);
				}
				Game game = builder.build();

				game.initialize();

				Client client = Client.builder().game(game).apiDebug(apiDebug).build();
				game.registerClient(client);

				game.loopWithClients();
			} catch (InternalException e) {
				System.err.println("Encountered internal game engine error: " + e);
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.err.println("Unexpected error encountered: " + e);
			e.printStackTrace();
		}
	}
}
