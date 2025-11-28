# Text Engine

**Version:** 0.0.1  
**License:** AGPL-3.0  
**Reference:** https://benleskey.com/blog/textengine_intro

## Overview

Dynamic simulation engine for text-based worlds. Everything emerges from **entity relationships**, not pre-scripted content.

### Core Philosophy

1. **Entity-Relationship Architecture**: Everything (cities, people, items, locations) is an entity in relationships
2. **LLM-Enhanced Consistency**: AI describes what the engine determines, not the other way around
3. **Genre Agnostic**: Same engine powers medieval castles, space stations, pirate ships

## Vision

- **Dynamic Generation**: Create entities on-demand (e.g., "hide under wagon" generates `underneath_wagon`)
- **Scale-Adaptive**: Micro simulation near player, macro elsewhere
- **Temporal Events**: All state changes timestamped, supports time-travel queries
- **Genre Flexible**: Same engine for fantasy, sci-fi, historical settings
- **LLM Flavor**: AI generates descriptions/dialogue, not game state

## Architecture

**Stack**: Java 21, Maven, SQLite, LangChain4j/Ollama

**Key Systems**:
- **EntitySystem**: Entity creation and type registration
- **RelationshipSystem**: Entity-to-entity connections
- **EventSystem**: Temporal events with cancellation
- **SpatialSystem**: Multi-scale N-dimensional positions
- **DisambiguationSystem**: Resolves ambiguous input, supports `#ID` format
- **UniqueTypeSystem**: String→integer mapping for DB efficiency
- **ItemSystem**: Procedural item generation with containers
- **InventorySystem**: Item carrying and management

**Machine-Readable API**:
Every command output includes structured data (`--apidebug` to see):
```java
output.put("success", true)
      .put("item", itemData)
      .put("container_contents", itemsList)
      .text("You take the coin.");
```
Clients read structured fields, not text parsing.

**Plugins**:
- **CorePlugin**: UniqueType system
- **EventPlugin**: Temporal events
- **EntityPlugin**: Entities, relationships, spatial positions
- **ProceduralWorldPlugin**: Procedural generation with seeds
- **NavigationPlugin**: Movement and pathfinding
- **InteractionPlugin**: Look command
- **ItemPlugin**: Item generation
- **ItemInteractionPlugin**: Take, drop, open, close, put, examine
- **InventoryPlugin**: Inventory management

**Design Patterns**:
- Hook-based lifecycle (OnPluginInitialize, OnCoreSystemsReady, etc.)
- Dependency resolution for plugins and systems
- Temporal event sourcing (time-travel queries)
- Transaction-per-command (rollback on error)

## Status

### ✅ Core Features
- Plugin/system architecture with dependency resolution
- Entity-relationship model with temporal tracking
- Multi-scale spatial positioning (N-dimensional)
- Procedural world generation (deterministic seeds)
- Navigation with pathfinding and distant landmarks
- **Container system** (items in items, "take from", "put in")
- **Inventory management** (carrying capacity, weight)
- **Item interactions** (take, drop, examine, open, close, use)
- **Disambiguation** (numeric IDs, fuzzy matching, `#ID` format)
- **Machine-readable API** (complete structured output)
- Markup formatting (bold, italic, escape)
- LLM integration (Ollama, streaming)

### ❌ Not Implemented
- Combat, skills, attributes
- NPC AI, dialogue, social systems
- Economics, politics, factions
- Time progression, weather
- Multi-scale simulation (micro/macro)
- Dynamic entity generation (beyond places/items)
- Network multiplayer, web interface

## Quick Start

**Prerequisites**: Java 21+, Maven 3.6+

```bash
# Build
mvn clean compile

# Run with deterministic world
printf "look\ngo forest\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345"

# With API debug (shows entity IDs)
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --apidebug"
```

**Commands**:
- `look` - Surroundings, exits, landmarks, items
- `go <landmark>` - Navigate (supports fuzzy match and `#ID`)
- `take <item>` / `take <item> from <container>` - Pick up items
- `drop <item>` - Drop items
- `open <container>` - Open containers
- `examine <target>` - Detailed look with container contents
- `inventory` - List carried items
- `quit` - Exit

**Example Session**:
```bash
> look
Rocky hillside. Exits: peaceful glade [1], gnarled willow [2].
Items: battered chest [3], chunk of granite [4]

> open #3
You open the battered chest, revealing a tarnished coin and a rusty key.

> take coin from #3
You take the tarnished coin from the battered chest.

> inventory
Carrying: tarnished coin
```

## Project Structure

```
src/main/java/com/benleskey/textengine/
├── Game.java, Plugin.java, GameSystem.java
├── cli/              # CLI client and Main entry point
├── commands/         # Command parsing and execution
├── entities/         # Actor, Place, Item
├── model/            # Entity, Event, Relationship, DTime
├── plugins/          # Core, Event, Entity, Navigation, Item, Inventory
├── systems/          # Entity, Relationship, Event, Spatial, Disambiguation
├── llm/              # LLM integration (Ollama)
└── util/             # HookManager, Logger, Message
```

## Roadmap

**Phase 1: Foundation** ✅
- Core architecture, entities, relationships, events, persistence

**Phase 2: Items & Interaction** ✅
- Navigation, procedural world, items, inventory, containers

**Phase 3: Character Systems** (Next)
- Attributes, skills, equipment, status effects

**Phase 4: Combat**
- Turn-based combat, weapons, armor, damage types

**Phase 5: NPCs & Social**
- NPC AI, dialogue (LLM), reputation, factions

**Phase 6: World Simulation**
- Economics, politics, dynamic events, macro/micro scaling

**Phase 7: Multiplayer**
- Network protocol, shared world state

**Phase 8: Polish**
- Web client, mobile, accessibility, testing

## Development

**Adding Entities**:
```java
EntitySystem es = game.getSystem(EntitySystem.class);
es.registerEntityType(MyEntity.class);
MyEntity entity = es.add(MyEntity.class);
```

**Adding Systems**:
```java
public class MySystem extends SingletonGameSystem implements OnSystemInitialize {
    @Override
    public void onSystemInitialize() {
        // Create tables, initialize state
    }
}
```

**Adding Commands**:
Register in plugin with `game.registerCommand(pattern, handler)`.

**Key Patterns**:
- Never pass systems as parameters (fetch locally)
- Never hide errors (let exceptions propagate)
- Register entity types in OnCoreSystemsReady
- Use seeds for deterministic testing
- Test with `printf` for reproducibility

## License

AGPL-3.0 - See [LICENSE](LICENSE).

**Reference**: https://benleskey.com/blog/textengine_intro
