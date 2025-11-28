---
applyTo: "**"
---

# Text Engine Mission

## Core Philosophy

**Everything is dynamic. Nothing is pre-built.**

This engine simulates worlds through **emergent entity relationships**, not scripted content. It combines:
- **Entity-Relationship Architecture**: Everything (cities, people, objects, locations, abstract concepts) is an entity in relationships with other entities
- **LLM-Enhanced Consistency**: AI generates descriptions/dialogue while the engine enforces systematic consistency
- **Genre Agnostic**: The engine supports any setting or style—high fantasy adventure, space opera, ocean-going voyage, cyberpunk intrigue, historical drama, horror survival, or any other genre you can imagine

## Non-Negotiable Principles

### 1. Dynamic Entity Generation
- **Entities are created on-demand**, not pre-defined in content files
- Example: Player says "hide under wagon" → system generates "underneath_wagon" entity if it doesn't exist
- **No hardcoded rooms, items, or NPCs** beyond bootstrap/example code
- World content emerges from simulation rules, not authored scenarios
- **Genre flexibility**: The same entity system can represent a medieval castle, a universe of spaceships and planets, or a bay filled with pirate vessels and ports across the ocean.

### 2. Relationship-Driven Simulation  
- **All interactions through entity relationships**, not object properties
- "Container" is not an item type—it's any entity with a "contains" relationship
- Spatial location is a relationship ("within", "on", "under"), not coordinates
- Social dynamics are relationships ("ally_of", "employed_by", "distrusts")

### 3. Temporal Event System
- **All state changes are timestamped events**, not mutations
- Current state = latest non-cancelled event
- Enables time-travel queries and consistent history
- Cancel-events reverse previous events without deleting history

### 4. Scale-Adaptive Simulation
- **Granularity adjusts to player proximity/relevance**
- Far-away city = macro-level simulation (population trends, economics)
- Player enters city = individual NPCs with personalities and goals
- **Never simulate what players can't observe** unless it affects observable state

### 5. Generic Systems Over Specific Features
- Build systems that handle **classes of interactions**, not individual use cases
- Don't implement "chest opening"—implement "state change with prerequisites"
- Don't implement "quest tracking"—implement "goal representation with progress queries"
- **Reusable abstractions** beat custom handlers

### 6. LLM as Flavor, Not Logic
- LLM generates **descriptions and dialogue**, not game state
- LLM output is non-canonical—it describes what the system already determined
- Example: System decides "NPC is hostile" → LLM describes *how* hostility manifests
- **Never let LLM hallucinate entities or relationships** that don't exist in the database

### 7. Machine-Readable API, Human-Readable Text
- **All game state goes in structured Message key-value pairs**, not just text output
- CommandOutput/CommandInput use `put(key, value)` for machine-readable data
- The `text` field is for human display only—clients should read structured fields
- Example: `CommandOutput.make(TAKE).put(M_SUCCESS, true).put(M_ITEM, item).text("You take the sword")`
- **Why**: Enables rich clients (GUIs, bots, tests) to react to game state without parsing text
- **Pattern**: Every command output should include:
  - Command ID (e.g., `TAKE`, `DROP`, `LOOK`)
  - Success/error flags (`M_SUCCESS`, `M_ERROR`)
  - Relevant entity references (`M_ITEM`, `M_ACTOR`, `M_PLACE`)
  - Context data (names, quantities, states)
  - **Complete structured data** - all information in text should also be in structured fields
  - Lists of entities (e.g., container contents) with entity IDs and names
  - Human-readable `text` field last
- **Critical**: Machine-readable API must provide COMPLETE information - never require text parsing
- Use `--apidebug` flag to see the structured data: `(input: 'take sword', item: 'entity_123', success: 'true', text: 'You take...')`
- **Examples of complete API data**:
  - Container contents: `items: [{entity_id: '1234', item_name: 'coin'}, {entity_id: '5678', item_name: 'key'}]`
  - Inventory: `items: [{entity_id: '9012', item_name: 'sword', weight: '2.5'}]`
  - Room exits: `exits: [{entity_id: '3456', direction: 'north', destination: 'forest'}]`

## Anti-Patterns to Reject

❌ **Pre-authored content** - "Create 5 rooms with items"  
✅ **Generation rules** - "Places can have adjacent places; items spawn based on place type"

❌ **Hardcoded entity types** - Separate classes for Chest, Door, Key  
✅ **Generic entities with relationships** - Any entity can "lock", "unlock", "contain"

❌ **Special-case commands** - `open_chest`, `pick_lock`, `read_book`  
✅ **Generic interaction** - `interact_with(entity, action)` → system determines validity

❌ **Player-centric simulation** - Only simulate what player sees  
✅ **World-centric simulation** - World evolves independently; player observes subset

❌ **Fixed descriptions** - "You see a rusty sword"  
✅ **Generated descriptions** - LLM describes sword based on its properties/relationships/state

❌ **Quest scripts** - "If player has 3 gems, unlock door"  
✅ **Emergent goals** - NPCs have goals; players can adopt/interfere with them

❌ **Text-only output** - `output.text("You found 3 gold")`  
✅ **Structured + text** - `output.put(M_GOLD, 3).put(M_SUCCESS, true).text("You found 3 gold")`

## Implementation Tests

Does your code pass these tests?

1. **Entity Flexibility**: Can this entity type be repurposed for unintended uses?
2. **Relationship Generality**: Does this relationship type work for multiple scenarios?
3. **Temporal Integrity**: Can this state change be reversed/queried historically?
4. **Scale Independence**: Does this system work at both micro (individual) and macro (population) scales?
5. **LLM Separation**: If the LLM fails, does the game state remain consistent?

## Current Status Implications

**Acceptable**:
- Bootstrap entities (initial "home" location, player actor) for testing
- Example plugins demonstrating patterns
- Hardcoded entity types (Actor, Place) as **proof-of-concept**, not final taxonomy

**Unacceptable**:
- Authored "dungeon" with named rooms and placed treasures
- Item catalog with fixed properties
- Scripted NPC behaviors ("shopkeeper sells items")
- Quest database
- Handwritten room descriptions

## Development Priorities

1. **Systems for emergence** (relationship types, interaction rules, simulation clock)
2. **Generative infrastructure** (LLM integration, procedural logic, context building)
3. **Flexible abstractions** (generic commands, pluggable systems, extensible entity types)
4. **Content authoring tools** (dead last—and only for defining generation parameters)

---

**Remember**: You are building a **simulation engine**, not a game. Games are what emerge from well-designed simulation rules.
