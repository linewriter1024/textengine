# 2025-11-28

The first real game will be a simple adventure ["West Marches"](https://arsludi.lamemage.com/index.php/78/grand-experiments-west-marches/)-style game.

There will be a hub city, populated with people living their lives, politics, markets, etc. This will enable developing the social side of the game. Then, there will be quests, shops, etc. in the city to send the player (and NPCs) out into the wilderness to progressively more dangerous areas to collect items, bounties, rescues, and so forth.

We need:

- [x] Name generation system - to ensure everybody is uniquely, thematically, and concisely named
- [x] Player actions in same queue - instead of executing immediately, player actions are enqueued and execute in time with NPC actions
- [ ] Actions execute() do not return output, just success or failure of even that
- [ ] Dice system - to support randomness, circumstances, combat, social encounters, etc.
- [ ] High level entities - the city, wilderness areas, monster clans, bandit groups, etc.
- [ ] Low level entities - monsters, people, shops, buildings
- [ ] Combat

- [ ] Social systems
  - [ ] Trade
  - [ ] Conversation
  - [ ] Rumors/quests
  - [ ] Autonomy (rival adventurers, bandits, etc.)

# Longterm

- [x] readline-style line processing in CLI client
- [ ] Remove Tickable from entities such as the grandfather clock, and turn them into Acting entities. Tickable will be removed entirely, allowing us to greatly simplify the Tick system.
- [ ] Only process Avatar entities when they have pending actions.
- [ ] Replace look types such as "basic" with UniqueType constants
- [ ] Unify examine/take/look/etc. to operate on all entities instead of just items
