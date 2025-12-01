# 2025-11-28

The first real game will be a simple adventure ["West Marches"](https://arsludi.lamemage.com/index.php/78/grand-experiments-west-marches/)-style game.

There will be a hub city, populated with people living their lives, politics, markets, etc. This will enable developing the social side of the game. Then, there will be quests, shops, etc. in the city to send the player (and NPCs) out into the wilderness to progressively more dangerous areas to collect items, bounties, rescues, and so forth.

- [ ] This will be a new game (plugin) outside of procgen1 and highfantasy (the initial prototypes). The new plugin will be codenamed "Ember".

- [ ] The game will use the DiceSystem for its skill checks; with a <attribute + skill>d6, success threshold of 5, explosion threshold of 6. This should be encapsulated in a custom PoolDiceRoll class within the game with the default thresholds automatically set.

We need:

- [ ] High level entities - the city, wilderness areas, monster clans, bandit groups, etc.
- [ ] Low level entities - monsters, people, shops, buildings
- [ ] Combat

- [ ] Social systems
  - [ ] Trade
  - [ ] Conversation
  - [ ] Rumors/quests
  - [ ] Autonomy (rival adventurers, bandits, etc.)
