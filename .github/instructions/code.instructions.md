---
applyTo: "**"
---

# Code Guidelines

Critical patterns and gotchas.

- Always seek the least complex code.
- Reduce complexity at every opportunity.
- Seek out ways to reduce complexity and/or code size.
- When making a change, reduce the complexity of any related code or code you touch.

## Database Schema Changes

**No backwards compatibility required.** This is a pre-release project. Feel free to:

- Drop and recreate tables
- Change schema without migrations
- Reset version numbers to 0
- Delete old database files

```java
// ✅ GOOD: Just recreate the schema
if (v == 0) {
    s.executeUpdate("CREATE TABLE action (...)");
    getSchema().setVersionNumber(1);
}

// ❌ BAD: Complex migration logic for pre-release code
if (v == 1) {
    // migrate from old schema...
}
```

## System Access

**Never pass systems as parameters.** Fetch locally or store as class fields.

```java
// ❌ BAD
private void handle(Client c, LookSystem ls, WorldSystem ws) { }

// ✅ GOOD: Fetch locally
private void handle(Client c) {
    LookSystem ls = game.getSystem(LookSystem.class);
    WorldSystem ws = game.getSystem(WorldSystem.class);
}

// ✅ GOOD: Store as fields
public class MyPlugin extends Plugin {
    private LookSystem ls;
    private WorldSystem ws;

    @Override
    public void onPluginInitialize() {
        ls = game.getSystem(LookSystem.class);
        ws = game.getSystem(WorldSystem.class);
    }
}
```

## Error Handling

**Never hide errors.** Let exceptions propagate with full stack traces.

```java
// ❌ BAD
try {
    entitySystem.add(Tree.class);
} catch (Exception e) {
    log.log("Error: " + e.getMessage());
    return fallback;  // Silent failure
}

// ✅ GOOD
entitySystem.add(Tree.class);  // Throws if error
```

## Plugin Lifecycle

**Order**: OnPluginRegister → OnPluginInitialize → OnCoreSystemsReady → OnEntityTypesRegistered → OnStart → OnStartClient

- **OnPluginInitialize**: Register systems
- **OnCoreSystemsReady**: Register entity types
- **OnEntityTypesRegistered**: Create entities

Plugins execute in **dependency order**, not registration order. Use `getDependencies()`.

## Entity System

**Register entity types before creating:**

```java
EntitySystem es = game.getSystem(EntitySystem.class);
es.registerEntityType(Place.class);
Place place = es.add(Place.class);
```

## UniqueType System

**String → Integer mapping for database efficiency.**

```java
UniqueType type = game.getUniqueTypeSystem().getType("scale_continent");
// Database stores integer ID, not string
```

Define constants in the system that uses them:

```java
public class SpatialSystem {
    public static UniqueType SCALE_CONTINENT;

    @Override
    public void onSystemInitialize() {
        SCALE_CONTINENT = game.getUniqueTypeSystem().getType("scale_continent");
    }
}
```

## Markup System

**Explicit escaping:**

```java
Markup.escape(userInput);      // Escapes HTML
Markup.raw("<em>text</em>");   // Trusts markup
Markup.em("emphasized");       // <em>emphasized</em>
```

## CommandOutput Pattern

**Success = no error field. Failure = error field present.**

**Error codes must be constants**, defined where they're used (like M\_ constants).

```java
// Define error codes in the plugin/system where they're used
public class ItemInteractionPlugin extends Plugin {
    public static final String ERR_ITEM_NOT_FOUND = "item_not_found";
    public static final String ERR_TOO_HEAVY = "too_heavy";
    // ...
}

// ✅ Success
client.sendOutput(CommandOutput.make(TAKE)
    .put(M_ITEM, item.getKeyId())
    .text(...));

// ✅ Failure
client.sendOutput(CommandOutput.make(TAKE)
    .error(ERR_ITEM_NOT_FOUND)
    .text(...));

// ❌ BAD - don't use magic strings
.error("not_found")

// ❌ BAD - don't use M_SUCCESS or "success" fields
```

## Action System

**Actions handle their own validation and broadcast messages. Players and NPCs use the same queueAction() path.**

- `canExecute()` validates and returns `ActionValidation` with error `CommandOutput`
- `execute()` performs action and broadcasts `CommandOutput` to all nearby entities (including actor)
- Players auto-execute with time advance; NPCs queue for later
- Use constants (CMD*\*, ERR*\_, M\_\_) defined in action classes, not magic strings

## Entity References

- **Disambiguation IDs** (1, 2, 3): Temporary, context-specific from ambiguous matches
- **Entity IDs** (#1234): Permanent database IDs, use `--apidebug` to discover

```bash
printf "open #1366\n" | mvn -q exec:java ... -Dexec.args="--seed 12345"
```

## World Generation

**Use seeds for deterministic generation:**

```java
public class ProceduralWorldPlugin extends Plugin {
    private final Random random;

    public ProceduralWorldPlugin(Game game, long seed) {
        super(game);
        this.random = new Random(seed);
    }
}
```

## Testing

**Use scripted input:**

```bash
printf "look\ngo north\nlook\nquit\n" | mvn -q exec:java ...
```

## Logging

**Use `log`, not `game.log`** for automatic plugin name prefix.

## Key Rules

1. Never pass systems as parameters
2. Never hide errors
3. Register entity types before creating
4. Create entities in OnCoreSystemsReady or later
5. Use seeds for reproducible worlds
6. Test with scripted input
7. Use plugin log for context
8. **Never store state in entity instance fields - use relationships/properties**
9. **Actions validate and generate their own error messages**
10. **Players queue actions (auto-execute with time advance); NPCs queue for later**

## Entity Lifecycle

**Critical**: Entity instances are constantly reconstructed from the database, NOT singletons.

**Why this matters**:

- Entities are loaded from DB on demand
- Constructor called many times per entity
- Instance fields reset to initial values
- Only database state (relationships, properties, tags) persists

**Use for persistence**:

- Relationships: Entity-to-entity connections
- Properties: Entity key-value data
- Tags: Entity boolean flags
- Event system: Temporal state changes
