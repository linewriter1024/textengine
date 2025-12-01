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
- [ ] Review event cancelling logic to point at events, not references. The existing event cancel method should accept an _event_ to cancel, not its subject. Also, the get valid events subquery should check to see that the _event_ has been canceled, not that its reference has been. Perhaps add a helper accepting an event type, entity, and reference to cancel all events of that type on that entity regarding that reference. That way we don't have to have the extra complexity of fetching the event itself.
- [ ] Review actions. Actions should be be a kind of Reference and used instead of Reference in the action system. ActionDescriptors and actions in the database should support arbitrary integer keys and values, which would be UniqueTypes and reference IDs (keys would be UniqueTypes, reference IDs could be either). The current properties (Actor, Target, Time Required) would be placed into this new system. Looking at it again: we don't need ActionDescriptors since the new Reference-based Action class will be used for everything, and simply have methods to get and set its properties which delegate to the Action system for the actual behavior.
