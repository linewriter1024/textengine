package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Place;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.hooks.core.OnStart;
import com.benleskey.textengine.hooks.core.OnStartClient;
import com.benleskey.textengine.systems.*;

/**
 * WorldPlugin creates and manages the game world.
 * 
 * TEMPORARY IMPLEMENTATION NOTE:
 * This plugin currently creates a hardcoded example forest world for testing
 * the navigation, visibility, and connection systems. In the future, this will
 * be replaced with a procedural generation system that creates worlds dynamically
 * based on generation rules rather than pre-authored content.
 */
public class WorldPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady, OnStart, OnStartClient {
	// World locations - TEMPORARY for testing
	private Place forestClearing;      // Starting point
	private Place denseForest;
	private Place riverbank;
	private Place ruinsNearRiver;
	private Place oldTower;
	private Place castle;
	private Place forestHut;
	private Place meadow;
	private Place forestPath;
	// River system
	private Place riverSource;
	private Place riverUpperReach;
	private Place riverFord;
	private Place riverLowerReach;
	private Place westRiverbank;
	private Place stoneCircle;

	public WorldPlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new WorldSystem(game));
	}

	@Override
	public void onCoreSystemsReady() {
		EntitySystem es = game.getSystem(EntitySystem.class);
		es.registerEntityType(Actor.class);
		es.registerEntityType(Place.class);
	}

	@Override
	public void onStart() throws InternalException {
		// TEMPORARY: Hardcoded world for testing navigation system
		// TODO: Replace with procedural generation system
		
		createExampleForestWorld();
	}

	/**
	 * TEMPORARY METHOD - Creates hardcoded example world.
	 * This demonstrates the systems but violates the mission.md principle
	 * of dynamic entity generation. Will be replaced with generation rules.
	 */
	private void createExampleForestWorld() throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
		VisibilitySystem vs = game.getSystem(VisibilitySystem.class);
		
		// Create places
		forestClearing = es.add(Place.class);
		ls.addLook(forestClearing, "basic", "a small clearing in the forest");
		
		denseForest = es.add(Place.class);
		ls.addLook(denseForest, "basic", "dense forest with thick undergrowth");
		
		// River locations (from upstream to downstream)
		riverSource = es.add(Place.class);
		ls.addLook(riverSource, "basic", "a bubbling spring where the river begins");
		
		riverUpperReach = es.add(Place.class);
		ls.addLook(riverUpperReach, "basic", "the upper reaches of the river, flowing swiftly");
		
		riverbank = es.add(Place.class);
		ls.addLook(riverbank, "basic", "the bank of a flowing river");
		
		riverFord = es.add(Place.class);
		ls.addLook(riverFord, "basic", "a shallow ford where the river can be crossed");
		
		riverLowerReach = es.add(Place.class);
		ls.addLook(riverLowerReach, "basic", "the river widens here, flowing more slowly");
		
		// West side of river (across from ford)
		stoneCircle = es.add(Place.class);
		ls.addLook(stoneCircle, "basic", "a mysterious stone circle in a secluded glade");
		vs.setProminent(stoneCircle, true);  // Mystical places are notable
		
		westRiverbank = es.add(Place.class);
		ls.addLook(westRiverbank, "basic", "the western bank of the river");
		
		ruinsNearRiver = es.add(Place.class);
		ls.addLook(ruinsNearRiver, "basic", "ancient stone ruins overgrown with vines");
		vs.setProminent(ruinsNearRiver, true);  // Visible from nearby areas
		
		oldTower = es.add(Place.class);
		ls.addLook(oldTower, "basic", "a crumbling stone tower rising above the trees");
		vs.setProminent(oldTower, true);  // Towers are visible from distance
		
		castle = es.add(Place.class);
		ls.addLook(castle, "basic", "an imposing castle with weathered battlements");
		vs.setProminent(castle, true);  // Castles are very visible
		
		forestHut = es.add(Place.class);
		ls.addLook(forestHut, "basic", "a small wooden hut nestled among the trees");
		
		meadow = es.add(Place.class);
		ls.addLook(meadow, "basic", "a peaceful meadow with wildflowers");
		
		forestPath = es.add(Place.class);
		ls.addLook(forestPath, "basic", "a winding path through the forest");
		
		// Create connections (bidirectional)
		// Forest clearing is the hub
		cs.connectBidirectional(forestClearing, denseForest, "north", "south");
		cs.connectBidirectional(forestClearing, forestPath, "east", "west");
		cs.connectBidirectional(forestClearing, meadow, "south", "north");
		
		// Dense forest connects to river and tower
		cs.connectBidirectional(denseForest, riverbank, "west", "east");
		cs.connectBidirectional(denseForest, oldTower, "north", "south");
		
		// River system - upstream/downstream navigation (east side)
		cs.connectBidirectional(riverSource, riverUpperReach, "downstream", "upstream");
		cs.connectBidirectional(riverUpperReach, riverbank, "downstream", "upstream");
		cs.connectBidirectional(riverbank, riverFord, "downstream", "upstream");
		cs.connectBidirectional(riverFord, riverLowerReach, "downstream", "upstream");
		
		// Also provide cardinal directions along river for convenience
		cs.connect(riverSource, riverUpperReach, "south");
		cs.connect(riverUpperReach, riverSource, "north");
		cs.connect(riverUpperReach, riverbank, "south");
		cs.connect(riverbank, riverUpperReach, "north");
		cs.connect(riverbank, riverFord, "south");
		cs.connect(riverFord, riverbank, "north");
		cs.connect(riverFord, riverLowerReach, "south");
		cs.connect(riverLowerReach, riverFord, "north");
		
		// Ford crossing - can cross the river here
		cs.connectBidirectional(riverFord, westRiverbank, "cross", "cross");
		cs.connectBidirectional(riverFord, westRiverbank, "west", "east");
		
		// West side connections
		cs.connectBidirectional(westRiverbank, stoneCircle, "north", "south");
		
		// Ruins accessible from lower river
		cs.connectBidirectional(riverLowerReach, ruinsNearRiver, "east", "west");
		
		// Path leads to hut and castle
		cs.connectBidirectional(forestPath, forestHut, "north", "south");
		cs.connectBidirectional(forestPath, castle, "east", "west");
		
		// Contextual navigation to landmarks
		cs.connect(forestClearing, castle, "castle");
		cs.connect(forestClearing, oldTower, "tower");
		cs.connect(meadow, castle, "castle");
		
		// Make distant landmarks visible from certain locations
		vs.makeVisibleFrom(forestClearing, oldTower);
		vs.makeVisibleFrom(forestClearing, castle);
		vs.makeVisibleFrom(meadow, castle);
		vs.makeVisibleFrom(denseForest, oldTower);
		vs.makeVisibleFrom(forestPath, castle);
		vs.makeVisibleFrom(riverFord, stoneCircle);  // Can see stone circle from ford
		
		log.log("TEMPORARY: Created hardcoded example forest world with river system (%d locations)", 15);
		log.log("TODO: Replace with procedural generation system");
	}

	@Override
	public void onStartClient(Client client) throws InternalException {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);

		Actor actor = Actor.create(game);
		client.setEntity(actor);

		// Place actor in the forest clearing (starting location)
		rs.add(forestClearing, actor, rs.rvContains);
		
		log.log("Client actor spawned in forest clearing");
	}
}
