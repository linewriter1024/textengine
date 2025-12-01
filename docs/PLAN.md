# Project Ember

We have a rough prototype architecture in the highfantasy and procgen1 plugins. These were good for developing the core, but are very messy. We need a better architecture, and to create our first real game.

The first real game will be a simple adventure ["West Marches"](https://arsludi.lamemage.com/index.php/78/grand-experiments-west-marches/)-style game.

There will be a hub city, populated with people living their lives, politics, markets, etc. This will enable developing the social side of the game. Then, there will be quests, shops, etc. in the city to send the player (and NPCs) out into the wilderness to progressively more dangerous areas to collect items, bounties, rescues, and so forth.

Project code name: Ember
Plugin path: plugins/ember

This new game must have a very well-built architecture.

Inspirations: Dwarf Fortress

Every place, person, region, organization, etc. must have a unique name generated using our name generation system. Ember will define appropriate name generation patterns.

We need:

- [ ] Procedurally generated world
- [ ] High level entities - the city, wilderness areas, monster clans, bandit groups, etc.
- [ ] Low level entities - monsters, people, shops, buildings
- [ ] Combat

- [ ] Social systems
  - [ ] Trade
  - [ ] Conversation
  - [ ] Rumors/quests
  - [ ] Autonomy (rival adventurers, bandits, etc.)
