# Text Engine

**Version:** 0.0.1  
**Author:** Ben Leskey  
**License:** GNU Affero General Public License v3.0  
**Repository:** https://github.com/linewriter1024/textengine  
**Website:** https://benleskey.com/aka/textengine

## Overview

Text Engine is an ambitious project to build the ultimate text-based game engine—a flexible, powerful system for simulating complex worlds through text. Unlike traditional MUDs or text adventures limited by predefined scenarios, Text Engine aims to create a dynamic simulation framework that can handle everything from high fantasy action to sci-fi space battles, from social intrigue to economic simulation.

### Core Philosophy

The engine combines two powerful paradigms:
1. **Entity-Relationship Architecture**: Everything in the world—cities, people, kingdoms, objects, locations—is an entity that exists in relationships with other entities
2. **LLM-Enhanced Interaction**: AI-powered dialogue and descriptions that maintain consistency while providing rich, dynamic narrative experiences

This approach offers the complexity/development time advantages of text-based games while pushing beyond the limitations of both traditional game masters (limited by human bandwidth) and pure AI systems (limited by consistency and system adherence).

## Project Goals

### Primary Objectives

- **Combat and Action Mechanics**: Simulate realistic combat, skills, and physical interactions
- **Social Interaction Systems**: Handle dialogue, relationships, attitudes, and social influence
- **World Event Simulation**: Track political affiliations, economics, trade routes, disasters, and macro-level changes
- **Multiplayer Support**: Enable both solo play and small party collaboration
- **Genre Flexibility**: Support any setting—fantasy, sci-fi, historical, modern, etc.
- **Granular/Macro Simulation**: Dynamically adjust simulation detail based on player proximity and relevance

### Long-term Vision

- Create a system where player actions ripple outward to affect cities, kingdoms, and entire civilizations
- Enable dialogue representation as intentions and attitudes rather than scripted text (e.g., "The king expresses gratitude with an undercurrent of disdain")
- Allow dynamic entity generation (e.g., creating "underneath the wagon" when a player hides there)
- Implement AI-driven NPCs with personalities, goals, and emergent behaviors
- Support seamless scaling between individual character simulation and macro-level abstraction

## Architecture

### Technology Stack

- **Language**: Java 21+
- **Build Tool**: Maven
- **Database**: SQLite (for persistent game state)
- **LLM Integration**: LangChain4j with Ollama (local LLM support)
- **Logging**: SLF4J
- **CLI**: argparse4j

### Core Components

#### 1. Game Engine (`Game.java`)

The central orchestrator that manages:
- Plugin registration and lifecycle
- System initialization and dependency resolution
- Client connections and command routing
- Event hooks and execution order
- Database transaction management
- Global and session ID generation

#### 2. Plugin System

**Plugins** extend game functionality through a hook-based architecture:
- **CorePlugin**: Provides `UniqueTypeSystem` for string-to-integer type mapping
- **EventPlugin**: Manages temporal event system with cancellation
- **EntityPlugin**: Handles entities, relationships, looks, and multi-scale spatial positions
- **ProceduralWorldPlugin**: Generates infinite procedural worlds with deterministic seeds and spatial pathfinding
- **NavigationPlugin**: Handles movement with landmark-based navigation and spatial pathfinding
- **InteractionPlugin**: Implements `look` (with distant landmark support) and other player commands
- **ItemPlugin**: Manages items with procedural generation in world locations
- **Echo**: Debug command and LLM chat integration

Plugins can declare dependencies on other plugins, ensuring correct initialization order.

#### 3. Game Systems

**Systems** manage specific aspects of game state:

- **UniqueTypeSystem**: Maps human-readable strings to compact integer IDs for database efficiency
- **EventSystem**: Temporal event tracking with cancel capability and time-travel queries
- **EntitySystem**: Entity creation, registration, and type management
- **RelationshipSystem**: Entity-to-entity relationships (e.g., "contains", "visible_from")
- **LookSystem**: Visual descriptions and perception with markup support
- **SpatialSystem**: Multi-scale N-dimensional spatial positioning (database-backed)
- **ConnectionSystem**: Manages connections between places for navigation
- **VisibilitySystem**: Handles what entities can see at different distances (nearby vs distant)
- **DisambiguationSystem**: Resolves ambiguous user input with numeric IDs and fuzzy matching
- **WorldSystem**: Time management and world state
- **EntityTagSystem**: Flexible entity categorization
- **PropertiesSubSystem**: Generic key-value storage for game data
- **LandmarkTemplateSystem**: Procedural generation templates for distant landmarks
- **ItemTemplateSystem**: Procedural generation templates for items with descriptions

Systems can declare dependencies on other systems for proper initialization sequencing.

#### 4. Entity Model

**Entities** are the fundamental building blocks of the game world:

```
Reference (base class with ID)
├── Entity (actors, places, items)
├── Event (temporal occurrences)
│   └── FullEvent<T> (events with references)
├── Relationship (entity connections)
└── Look (visual descriptions)
```

Current entity types:
- **Actor**: Characters and NPCs
- **Place**: Locations and spatial containers
- **Item**: Movable objects with properties (weapons, tools, consumables)

#### 5. Event System

The temporal event system tracks changes over time:
- Events are timestamped with `DTime` (millisecond precision)
- Events can be cancelled by subsequent "cancel" events
- Complex queries retrieve current valid state by filtering cancelled events
- Supports "time travel" queries to get world state at any point in history

#### 6. Command System

Commands are parsed via regex patterns and dispatched to handlers:
- **CommandVariant**: Maps regex patterns to input processors
- **CommandInput**: Structured input from clients
- **CommandOutput**: Structured output with text and metadata
- **CommandFunction**: Business logic for each command

Example commands: `quit`, `look`, `echo`, `chat`

#### 7. Hook System

Event-driven hooks allow plugins and systems to respond to lifecycle events:
- `OnRegister`: When plugin is first registered
- `OnPluginInitialize`: During plugin initialization phase
- `OnSystemInitialize`: During system initialization phase
- `OnCoreSystemsReady`: After core systems are initialized
- `OnStart`: Before game loop begins
- `OnStartClient`: When a new client connects

Hook execution order is determined by dependency graphs.

#### 8. Client Abstraction

Clients represent player connections (CLI, future web, network):
- Abstract base class defines interface
- CLI implementation handles terminal I/O
- Support for streamed output (for LLM responses)
- Entity association (which character the client controls)
- Numeric ID mapping for disambiguation (e.g., "take 3" when multiple items share names)

#### 9. LLM Integration

AI language models enhance narrative and dialogue:
- **LlmProvider**: Manages Ollama model connections
- **DialogGenerator**: Generates context-aware NPC dialogue
- Streaming chat responses for interactive conversation
- Designed for local deployment (privacy, cost, latency)

### Database Schema

SQLite provides persistent storage with transactional integrity:

**Core Tables:**
- `system_schema`: Version tracking for system upgrades
- `system_id`: Global ID generation
- `event`: Temporal event log with auto-incrementing order
- `entity`: Entity registry with type information
- `entity_relationship`: Entity-to-entity relationships with temporal tracking
- `entity_look`: Visual descriptions with markup
- `spatial_position`: Multi-scale N-dimensional spatial coordinates (entity_id, scale_id, x, y, z, w)
- `unique_type`: String-to-integer type mapping for database efficiency
- `entity_tag`: Flexible entity categorization
- `connection`: Directional connections between places
- Various `PropertiesSubSystem` tables for key-value data

**Transaction Management:**
- Auto-commit disabled for explicit transaction control
- Each command loop iteration is a transaction
- Rollback on errors ensures consistency
- Initialization phase is one large transaction

### Design Patterns

1. **Plugin Architecture**: Modular, extensible functionality
2. **Dependency Injection**: Systems and plugins inject `Game` reference
3. **Hook/Event System**: Decoupled communication between components
4. **Builder Pattern**: Fluent object construction (Lombok)
5. **Type-Safe IDs**: `UniqueType` wraps raw IDs with system reference
6. **Temporal State Management**: Event-sourcing-like system for time travel
7. **Schema Versioning**: Systems track and migrate their own schemas

## Current Implementation Status

### ✅ Implemented

- [x] Core game engine loop with transaction management
- [x] Plugin and system architecture with dependency resolution
- [x] Command parsing and execution with regex variants
- [x] Entity and relationship management with temporal tracking
- [x] Temporal event system with cancellation and time-travel queries
- [x] Look/perception system with markup formatting
- [x] CLI client implementation with streaming support
- [x] Database persistence with SQLite and schema versioning
- [x] LLM integration via Ollama with streaming responses
- [x] **Multi-scale spatial positioning system (database-backed, N-dimensional)**
- [x] **Procedural world generation with deterministic seeds**
- [x] **Spatial pathfinding with scale-aware distance calculations**
- [x] **Navigation system with landmark-based movement**
- [x] **Dynamic place generation with loop formation and adjacency**
- [x] **Distant landmark visibility with navigation guidance**
- [x] **Disambiguation system with numeric IDs and fuzzy matching**
- [x] **Item generation system with procedural descriptions**
- [x] **Markup system for formatted terminal output (bold, italic, escape)**
- [x] **UniqueType system for efficient database storage (string→int mapping)**
- [x] Client-entity association and numeric ID mapping

### 🚧 Partially Implemented

- [x] Entity types (Actor, Place, and Item fully implemented)
- [ ] Item properties (system exists but limited property types)
- [ ] Entity tags (system exists but limited usage)
- [ ] LLM dialogue generation (prototype only)

### ❌ Not Yet Implemented

- [ ] Item interaction (take, drop, use, equip)
- [ ] Inventory system
- [ ] Container system (items containing other items)
- [ ] Combat mechanics
- [ ] Skills and attributes
- [ ] Item interactions (take, drop, use)
- [ ] Inventory management
- [ ] Container system
- [ ] NPC AI and behaviors
- [ ] Social interaction mechanics
- [ ] Economic simulation
- [ ] Political systems
- [ ] Multi-scale simulation (micro/macro)
- [ ] Dynamic entity generation beyond places
- [ ] Senses beyond vision
- [ ] Time progression and day/night cycles
- [ ] Weather and environmental systems
- [ ] Save/load functionality (uses timestamped DBs)
- [ ] Network multiplayer
- [ ] Web interface
- [ ] Content creation tools

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- (Optional) Ollama with llama3.2 model for LLM features

### Building

```bash
mvn clean compile
```

### Running

```bash
# Basic execution
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"

# With deterministic world generation
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345"

# With game log visible
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog"

# Combined: seed and logging
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 42 --showlog"

# With API debug output
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--apidebug"

# Build standalone JAR
mvn assembly:assembly
java -jar target/textengine-1.0-SNAPSHOT-jar-with-dependencies.jar --seed 42
```

### Basic Commands

- `look` - View your surroundings, exits, distant landmarks, and items
- `look <target>` - Look at a specific exit or distant landmark (e.g., `look forest`, `look keep`, `look 3`)
  - For adjacent exits: shows what you can see from there
  - For distant landmarks: shows the landmark and which exit to take to get closer
- `go <landmark>` - Move to a visible location or toward a distant landmark (e.g., `go forest`, `go keep`, `go 2`)
- `echo <text>` - Echo text back (debug command)
- `chat <message>` - Chat with LLM (requires Ollama)
- `quit` - Exit the game

### Procedural World Features

The game generates an infinite procedural world with the following features:

- **Deterministic Generation**: Use `--seed <number>` to create repeatable worlds
- **Multi-Scale Spatial System**: Places positioned on continent scale; supports multiple scales (building, region, universe, etc.)
- **Spatial Pathfinding**: Navigation automatically finds the nearest exit toward distant landmarks
- **Loop Formation**: World connects existing nearby places, creating multiple paths and exploration opportunities
- **Landmark Navigation**: Move using visible landmarks instead of compass directions
- **Distant Landmark Visibility**: See landmarks 3-6 units away and get navigation guidance
- **Item Generation**: Each location contains 3-5 procedurally generated items with varied descriptions
- **Disambiguation System**: Commands support numeric IDs when names are ambiguous (e.g., "look 3", "go 2")
- **Fuzzy Matching**: Commands match partial words (e.g., "go forest" matches "dense forest with towering trees")
- **Markup Formatting**: Important elements shown in **bold** or *italic* in terminal output
- **Biome Variety**: Five biome types (forest, meadow, river, hills, ruins) with weighted distribution
- **Database Persistence**: All spatial positions and relationships persist in SQLite

Example session:
```bash
# Start with a specific seed for reproducible world
mvn exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 42"

# You see nearby exits and distant landmarks with numeric IDs
> look
You are in a rocky hillside. You can see a peaceful glade [1], and a peaceful glade [2].
In the distance you can see a gnarled willow [3], a gnarled willow [4], and a shattered keep [5].
Items: a chunk of granite [6], some scraggly moss [7], a twisted root [8]

# Look at a distant landmark to get navigation info
> look at keep
In the distance, you see a shattered keep. To get closer, head toward a peaceful glade.

# Navigate toward the landmark using keyword
> go keep
You head toward a peaceful glade, moving closer to a shattered keep.

# Or use numeric ID for disambiguation
> go 3
You head toward a gnarled willow.
```

### Project Structure

```
src/main/java/com/benleskey/textengine/
├── cli/              # Command-line interface
│   ├── Client.java   # CLI client implementation
│   └── Main.java     # Entry point
├── commands/         # Command system
│   ├── Command.java
│   ├── CommandInput.java
│   ├── CommandOutput.java
│   └── CommandVariant.java
├── entities/         # Concrete entity types
│   ├── Actor.java
│   └── Place.java
├── exceptions/       # Custom exceptions
├── hooks/            # Hook event interfaces
│   └── core/
├── llm/              # LLM integration
│   ├── DialogGenerator.java
│   └── LlmProvider.java
├── model/            # Core data models
│   ├── Entity.java
│   ├── Event.java
│   ├── Relationship.java
│   ├── DTime.java
│   └── ...
├── plugins/          # Plugin implementations
│   └── core/
├── systems/          # Game systems
│   ├── EntitySystem.java
│   ├── EventSystem.java
│   ├── RelationshipSystem.java
│   └── ...
├── util/             # Utilities
│   ├── HookManager.java
│   ├── Logger.java
│   └── Message.java
├── Client.java       # Abstract client
├── Game.java         # Core game engine
├── GameSystem.java   # Abstract system
├── Plugin.java       # Abstract plugin
├── SchemaManager.java
└── Version.java
```

## Development Roadmap

### Phase 1: Foundation (Current)
- ✅ Core architecture
- ✅ Entity/relationship model
- ✅ Event system
- ✅ Basic persistence
- ✅ LLM integration

### Phase 2: Core Gameplay
- [x] Movement and navigation system with spatial coherence and pathfinding
- [x] Procedural world generation with landmarks and deterministic seeds
- [x] Multi-scale spatial positioning system
- [x] Distant landmark visibility with navigation guidance
- [ ] Item interaction commands (take, drop, examine)
- [ ] Inventory management system
- [ ] Expanded entity types (containers, NPCs)
- [ ] Time progression
- [ ] Enhanced perception (hearing, smell, touch)

### Phase 3: Character Systems
- [ ] Attributes and skills
- [ ] Character creation
- [ ] Advancement/progression
- [ ] Equipment system
- [ ] Status effects and conditions

### Phase 4: Combat
- [ ] Turn-based combat system
- [ ] Weapons and armor
- [ ] Combat skills and techniques
- [ ] Damage types and resistances
- [ ] Death and resurrection

### Phase 5: Social Systems
- [ ] NPC personalities and goals
- [ ] Dialogue system with LLM
- [ ] Reputation and faction systems
- [ ] Social influence mechanics
- [ ] Dynamic NPC behaviors

### Phase 6: World Simulation
- [ ] Economic systems (trade, crafting, resources)
- [ ] Political simulation (kingdoms, alliances)
- [ ] Dynamic events (wars, disasters, celebrations)
- [ ] Macro/micro simulation scaling
- [ ] Environmental systems (weather, seasons)

### Phase 7: Multiplayer
- [ ] Network protocol
- [ ] Multiple simultaneous clients
- [ ] Party system
- [ ] Player-to-player interaction
- [ ] Shared world state

### Phase 8: Content Tools
- [ ] World editor
- [ ] Entity templates
- [ ] Scripting system
- [ ] Content import/export
- [ ] Documentation generator

### Phase 9: Polish
- [ ] Web-based client
- [ ] Mobile support
- [ ] Accessibility features
- [ ] Performance optimization
- [ ] Comprehensive testing
- [ ] User documentation

## Contributing

This is a long-term personal project, but ideas and contributions may be considered. Given the scope and ambitious nature of the goals, development will be gradual.

### Code Style
- Use tabs for indentation
- Follow existing architectural patterns
- Add JavaDoc for public APIs
- Write tests for core functionality

### Adding Entities

1. Create entity class extending `Entity`
2. Register in appropriate plugin via `EntitySystem.registerEntityType()`
3. Define unique type via `UniqueTypeSystem`

### Adding Commands

1. Create command variant with regex pattern
2. Implement command function
3. Register via `game.registerCommand()` in plugin

### Adding Systems

1. Extend `GameSystem` or `SingletonGameSystem`
2. Implement `OnSystemInitialize` hook
3. Define database schema in initialization
4. Register via `game.registerSystem()` in plugin

## Architecture for AI Context

### Key Concepts for Code Generation

**Entity-Relationship Model:**
- Everything is an entity with a unique ID
- Entities relate through typed relationships (verbs like "contains")
- Relationships are temporal—tracked as events in time

**Temporal Event System:**
- All changes are events with timestamps
- Events can be cancelled by later cancel-events
- Query current state by getting latest non-cancelled event
- Enables time travel and history tracking

**Hook System:**
- Plugins and systems implement hook interfaces
- HookManager orchestrates execution order via dependencies
- Hooks called at specific lifecycle points
- Order determined automatically from dependency graphs

**Type Safety:**
- `UniqueType` wraps raw IDs with system context
- Prevents mixing IDs from different domains
- Enables human-readable labels via reverse lookup

**Transaction Integrity:**
- Each command is a transaction
- Rollback on any error
- Maintains consistent database state
- SQLite provides ACID guarantees

### Common Patterns

**Adding a new system:**
```java
public class MySystem extends SingletonGameSystem implements OnSystemInitialize {
    public MySystem(Game game) { super(game); }
    
    @Override
    public void onSystemInitialize() {
        int v = getSchema().getVersionNumber();
        if (v == 0) {
            // Create tables
            getSchema().setVersionNumber(1);
        }
        // Initialize prepared statements
    }
}
```

**Creating an entity:**
```java
EntitySystem es = game.getSystem(EntitySystem.class);
Actor actor = es.add(Actor.class);
```

**Adding a relationship:**
```java
RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
rs.add(container, item, rs.rvContains);
```

**Recording an event:**
```java
EventSystem es = game.getSystem(EventSystem.class);
FullEvent<Look> event = es.addEventNow(typeOfEvent, look);
```

## Known Limitations

- **Single-threaded**: No concurrent client handling yet
- **Local only**: No network multiplayer support
- **SQLite limitations**: Not suitable for high-concurrency scenarios
- **No save/load UI**: Must manually manage database files
- **Limited content**: Very basic world, few entity types
- **LLM dependency**: Requires local Ollama installation for AI features
- **No content persistence**: World resets each run (uses timestamped DBs)

## Technical Debt

- Need proper save/load system (currently uses timestamped databases)
- Consider moving to client-server architecture
- Add comprehensive error handling and validation
- Implement proper logging levels
- Add unit and integration tests
- Document API with JavaDoc
- Performance profiling and optimization
- Consider migration to PostgreSQL for multiplayer
- Add configuration file support

## References

- **Blog Post**: [Toward the ultimate text adventure game engine](https://benleskey.com/blog/textengine_intro)
- **Repository**: https://github.com/linewriter1024/textengine
- **Author Website**: https://benleskey.com/

## License

GNU Affero General Public License v3.0 - See [LICENSE](LICENSE) for details.

This is free software: you are free to change and redistribute it. There is NO WARRANTY, to the extent permitted by law.

---

*This is an ambitious, long-term project. Development may be slow, but there is no time pressure. The goal is to build something truly unique in the text-based game space.*
