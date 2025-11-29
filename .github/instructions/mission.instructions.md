---
applyTo: "**"
---

# Text Engine Mission

**Reference**: https://benleskey.com/blog/textengine_intro  
**Philosophy Document**: [INITIAL_PHILOSOPHY.md](/home/user/projects/textengine/docs/INITIAL_PHILOSOPHY.md)

## Core Philosophy

**Everything is dynamic. Nothing is pre-built.**

This engine simulates worlds through **emergent entity relationships**, not scripted content:

- **Entity-Relationship Architecture**: Everything is an entity in relationships
- **LLM-Enhanced Consistency**: AI describes what the engine determines
- **Genre Agnostic**: Medieval castles, space stations, pirate ships—same engine

## Non-Negotiable Principles

### 1. Dynamic Entity Generation

- Entities created on-demand, not pre-defined
- Example: "hide under wagon" → generates underneath_wagon entity
- No hardcoded content beyond bootstrap/examples

### 2. Relationship-Driven Simulation

- All interactions through relationships, not properties
- "Container" = entity with "contains" relationship
- Location is relationship ("within"), not coordinates

### 3. Temporal Event System

- All state changes are timestamped events, not mutations
- Current state = latest non-cancelled event
- Enables time-travel queries and consistent history

### 4. Scale-Adaptive Simulation

- Granularity adjusts to player proximity
- Far city = macro simulation; player enters = spawn NPCs
- Never simulate what players can't observe

### 5. Generic Systems Over Specific Features

- Build systems for **classes of interactions**
- Not "chest opening"—"state change with prerequisites"
- Not "quest tracking"—"goal representation with progress queries"

### 6. LLM as Flavor, Not Logic

- LLM generates descriptions/dialogue, not game state
- System decides "NPC hostile" → LLM describes how
- Never let LLM hallucinate entities that don't exist in DB

### 7. Machine-Readable API, Human-Readable Text

- All game state in structured Message key-value pairs
- `text` field is display-only—clients read structured fields
- Example: `output.put(M_SUCCESS, true).put(M_ITEM, item).text("You take...")`
- **Every command output includes**:
  - Command ID, success/error flags
  - Entity references with IDs and names
  - Complete structured data (never require text parsing)
- Use `--apidebug` to see structured data
- Examples: Container contents, inventory, exits all include `entity_id` and names

## Anti-Patterns

❌ Pre-authored content → ✅ Generation rules  
❌ Hardcoded entity types → ✅ Generic entities with relationships  
❌ Special-case commands → ✅ Generic interaction systems  
❌ Player-centric simulation → ✅ World evolves independently  
❌ Fixed descriptions → ✅ Generated from properties/state  
❌ Quest scripts → ✅ Emergent goals  
❌ Text-only output → ✅ Structured + text

## Implementation Tests

1. Can this entity type be repurposed for unintended uses?
2. Does this relationship type work for multiple scenarios?
3. Can this state change be reversed/queried historically?
4. Does this system work at micro and macro scales?
5. If LLM fails, does game state remain consistent?

## Development Priorities

1. Systems for emergence (relationships, interactions, clock)
2. Generative infrastructure (LLM, procedural logic)
3. Flexible abstractions (generic commands, pluggable systems)
4. Content authoring tools (last—only for generation parameters)

---

**Remember**: Building a **simulation engine**, not a game. Games emerge from simulation rules.
