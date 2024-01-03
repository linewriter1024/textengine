package com.benleskey.textengine.cli;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Version;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.util.Logger;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.action.StoreTrueArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {
	public static void main(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor(Version.toHumanString()).build();
		parser.addArgument("--apidebug").help("Print API debug information with each command").action(new StoreTrueArgumentAction());
		parser.addArgument("--showlog").help("Print game log to standard output").action(new StoreTrueArgumentAction());

		Namespace ns = parser.parseArgsOrFail(args);

		boolean apiDebug = ns.getBoolean("apidebug");
		boolean showLog = ns.getBoolean("showlog");

		Logger logger = Logger.builder()
				.stream(showLog ? System.out : OutputStream.nullOutputStream())
				.build();

		try(Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try {
				Game game = Game.builder().log(logger).databaseConnection(connection).build();

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
