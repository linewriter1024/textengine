---
applyTo: "**"
---

# Code Guidelines & Non-Intuitive Insights

This document captures important patterns, gotchas, and non-obvious implementation details discovered during development.

## Error Handling

### Never Hide Errors

**Critical**: NEVER catch exceptions just to hide them or log and continue. Always let exceptions propagate with full stack traces.

```java
// ❌ BAD: Hiding errors
try {
    entitySystem.add(Tree.class);
} catch (Exception e) {
    log.log("Could not create tree: " + e.getMessage());
    return entitySystem.add(Item.class);  // Silent fallback
}

// ✅ GOOD: Let errors propagate
public Item createEntity(Class<? extends Item> clazz) throws InternalException {
    return entitySystem.add(clazz);  // Throws if entity type not registered
}

// ✅ GOOD: Wrap with app-specific exception if needed
try {
    entitySystem.add(Tree.class);
} catch (SQLException e) {
    throw new DatabaseException("Failed to create tree entity", e);
}
```

**Why**: Hidden errors make debugging impossible. If something is wrong, it should fail loudly with a full stack trace so you can fix the root cause.

**Approved exception types**:
- `InternalException` - Internal game engine errors
- `DatabaseException` - Database operation failures
- `ConsistencyException` - Data consistency violations

## Plugin System

### Plugin Initialization Order

**Critical**: Plugins execute in dependency order, NOT registration order.

```java
public class MyPlugin extends Plugin implements OnPluginInitialize {
    @Override
    public Set<Plugin> getDependencies() {
        // This plugin runs AFTER EntityPlugin and EventPlugin
        return Set.of(
            game.getPlugin(EntityPlugin.class), 
            game.getPlugin(EventPlugin.class)
        );
    }
}
```

**Why this matters**: If you try to use a system (like ConnectionSystem) before its plugin has initialized, you'll get `NoSuchElementException`.

### Plugin Lifecycle Hooks

Different hooks run at different times:

1. **OnRegister** - Plugin registered, systems can be registered here
2. **OnPluginInitialize** - Systems are registered but NOT initialized
3. **OnCoreSystemsReady** - Systems are initialized, register custom entity types here
4. **OnEntityTypesRegistered** - All entity types registered, safe to generate world with custom entities
5. **OnStart** - Game is ready to start
6. **OnStartClient** - New client connected

**Gotcha**: Don't create entities in `OnPluginInitialize` - systems aren't ready yet. Use `OnCoreSystemsReady` or later.

```java
@Override
public void onPluginInitialize() {
    // ✅ GOOD: Register systems
    game.registerSystem(new WorldSystem(game));
}

@Override
public void onCoreSystemsReady() {
    // ✅ GOOD: Register custom entity types
    EntitySystem es = game.getSystem(EntitySystem.class);
    es.registerEntityType(Tree.class);
    es.registerEntityType(Axe.class);
}

@Override
public void onEntityTypesRegistered() {
    // ✅ GOOD: Generate world after entity types are registered
    EntitySystem es = game.getSystem(EntitySystem.class);
    Tree tree = es.add(Tree.class);  // Works because Tree was registered in onCoreSystemsReady
}
```

**Pattern**: Content plugins register custom entity types in `OnCoreSystemsReady`, world generation plugins use `OnEntityTypesRegistered` to generate entities.

### Plugin Logging

**Always use `log`, never `game.log`**

```java
public class MyPlugin extends Plugin {
    @Override
    public void onPluginInitialize() {
        // ✅ GOOD: Uses plugin-specific logger with automatic prefix
        log.log("Initializing...");  
        // Output: [MyPlugin] Initializing...
        
        // ❌ BAD: Uses game-wide logger, no context
        game.log.log("Initializing...");  
        // Output: Initializing...
    }
}
```

**Why**: Plugin logger automatically adds class name prefix, making logs much easier to trace.

## Entity System

### Entity Type Registration

**Must register entity types before creating entities**

```java
EntitySystem es = game.getSystem(EntitySystem.class);

// ✅ GOOD: Register first
es.registerEntityType(Place.class);
Place place = es.add(Place.class);

// ❌ BAD: Create without registering
Place place = es.add(Place.class);  // NullPointerException!
```

**Why**: EntitySystem maintains a map of entity types. Without registration, it doesn't know the Class<T> for the UniqueType.

### Entity Type Discovery

Entity types are discovered via `Entity.getEntityType()` which calls:
```java
game.getUniqueTypeSystem().getType(this.getClass().getSimpleName())
```

This means the UniqueTypeSystem must be initialized before creating entities.

## System Initialization

### System Initialization Timing

Systems go through two phases:

1. **Registration** (in plugin's `onPluginInitialize`)
   ```java
   game.registerSystem(new MySystem(game));
   ```

2. **Initialization** (automatic, after all plugins initialized)
   ```java
   system.onSystemInitialize();  // Called by Game.initialize()
   ```

**Gotcha**: Systems registered late may not be initialized when you need them.

### Getting Systems Safely

```java
// ✅ GOOD: In OnCoreSystemsReady or later
MySystem sys = game.getSystem(MySystem.class);

// ❌ BAD: In OnPluginInitialize (system might not be registered yet)
MySystem sys = game.getSystem(MySystem.class);  // NoSuchElementException!
```

## UniqueType System

### String-to-Integer Mapping for Database Efficiency

**UniqueTypeSystem converts human-readable strings to compact integers for database storage.**

```java
// At initialization - registers "scale_continent" and gets/creates ID
UniqueType scaleContinent = game.getUniqueTypeSystem().getType("scale_continent");
// Returns: UniqueType(1011)

// In database - stores integer, not string
spatialSystem.setPosition(place, SpatialSystem.SCALE_CONTINENT, coords);
// Database stores: scale_id = 1011 (not "scale_continent")
```

**Why this matters**:
- **Space savings**: Integer (4-8 bytes) vs string (variable, often 20+ bytes)
- **Performance**: Integer comparisons are faster than string comparisons
- **Consistency**: Typos in strings become impossible after initialization
- **Human-readable**: The `unique_type` table maps IDs back to strings for debugging

**Database structure**:
```sql
-- unique_type table preserves human readability
SELECT * FROM unique_type;
0|scale_continent|1011
0|relationship_contains|1004

-- Other tables use compact integers
SELECT * FROM spatial_position;
entity_id | scale_id | x | y
1032      | 1011     | 0 | 0  -- scale_id references unique_type
```

### Where to Define UniqueType Constants

**Define constants in the system that uses them, not in UniqueTypeSystem.**

```java
// ✅ GOOD: In SpatialSystem
public class SpatialSystem extends SingletonGameSystem {
    public static UniqueType SCALE_CONTINENT;
    
    @Override
    public void onSystemInitialize() {
        SCALE_CONTINENT = game.getUniqueTypeSystem().getType("scale_continent");
    }
}

// ❌ BAD: In UniqueTypeSystem
public class UniqueTypeSystem extends SingletonGameSystem {
    public static UniqueType SCALE_CONTINENT;  // Wrong place!
}
```

**Pattern**: Only define the types you need NOW. Add others when they're actually used (YAGNI principle).

## Event System

### Event Ordering

Events use `event_order` (auto-increment PRIMARY KEY), NOT timestamps, for ordering.

**Why**: Eliminates need for manual time management. Events naturally order themselves.

### Cancel Events

Cancel events work by matching `event.reference`, not `event.event_id`.

```sql
-- ✅ CORRECT: Compare reference fields
SELECT * FROM event 
WHERE event.reference NOT IN (
    SELECT cancel_event.reference FROM event AS cancel_event 
    WHERE cancel_event.event_type = 'cancel_event'
)

-- ❌ WRONG: Compare event_id with reference
WHERE event.event_id NOT IN (SELECT cancel_event.reference ...)
```

**Why**: Both original event and cancel event store the same `reference` (e.g., relationship_id). The `event_id` is unique per event.

## Markup System

### Safe vs Raw Markup

The `Markup.Safe` type forces explicit escaping choices:

```java
// ✅ GOOD: Explicit escaping
CommandOutput.text(Markup.escape(userInput));      // Escapes HTML entities
CommandOutput.text(Markup.raw("<em>keyword</em>"));  // Trusts markup

// ❌ BAD: Removed due to ambiguity
CommandOutput.text(String)  // Which one? Escape or raw?
```

### Markup Composition

Build complex markup safely:

```java
Markup.Safe message = Markup.concat(
    Markup.raw("You see "),
    Markup.em("the forest"),     // <em>the forest</em>
    Markup.raw(" to the "),
    Markup.escape(direction)      // User input, escaped
);
```

**Pattern**: Use `raw()` for your markup, `escape()` for user content, `em()` for emphasis.

### Rendering Markup

Use `Markup.toPlainText()` to convert markup to plain text for display or logging:

```java
String landmarkName = "<em>dark forest</em>";
String plainText = Markup.toPlainText(Markup.raw(landmarkName));  // "dark forest"
```

**Why this matters**: Markup rendering is a presentation concern, not a matching concern. `FuzzyMatcher` uses `Markup.toPlainText()` internally to strip markup when comparing user input to entity descriptions.

### Numeric ID Disambiguation

When multiple entities have identical or similar descriptions, users can reference them by numeric ID to disambiguate.

**How it works**:
1. **Display commands** (`look`, `inventory`) - Show entities WITHOUT numeric IDs to keep output clean
2. **Interaction commands** (`take`, `examine`, `drop`, `put`) - If user input is ambiguous, the system displays options WITH numeric IDs
3. **Client-side mapping**: Each client stores a `numericIdMap` that maps integers to entities
4. **User interaction**: After seeing numbered options, users can type `take 1` or `take 3` to select
5. **Context-specific**: The mapping is rebuilt for each ambiguous command

**Example flow**:
```
> look
Items: a wet leaf, a weathered plank, a wet leaf

> take leaf
Which leaf did you mean? a wet leaf [1], and a wet leaf [2]

> take 1
You take a wet leaf.
```

**Implementation**:
- `Client.setNumericIdMap(Map<Integer, Entity>)` - stores the mapping
- `Client.getEntityByNumericId(int)` - retrieves entity by numeric ID
- `DisambiguationSystem.buildDisambiguatedList()` - assigns IDs when presenting ambiguous choices
- `DisambiguationSystem.resolveEntityWithAmbiguity()` - checks numeric IDs first, then fuzzy matches
- Commands check numeric IDs first, then fall back to fuzzy matching

**Pattern**: IDs appear only when needed for disambiguation, keeping normal output clean. Users see IDs only after an ambiguous command, then use numbers to clarify their intent.

### Navigation to Duplicate Destinations

When multiple exits or landmarks have similar names, navigation commands handle ambiguity the same way as item commands.

**Example flow**:
```
> look
You can see a peaceful glade, a peaceful glade, and a dense forest.
In the distance you can see a gnarled willow, and a gnarled willow.

> go glade
Which glade did you mean? a peaceful glade [1], and a peaceful glade [2]

> go 1
You go to a peaceful glade.

> go willow
Which willow did you mean? a gnarled willow [3], and a gnarled willow [4]

> go 3
You move closer toward a gnarled willow.
```

**Why this matters**: The fuzzy matcher cannot disambiguate identical or highly similar descriptions. Numeric IDs provide unambiguous selection after the user sees the options.

**Implementation note**: NavigationPlugin uses DisambiguationSystem.resolveEntity() which checks numeric IDs first, then falls back to fuzzy matching. When fuzzy matching finds multiple strong matches, it prompts the user with numbered choices rather than guessing.

## World Generation

### Deterministic Generation

**Always use seeds for reproducible worlds**

```java
public class ProceduralWorldPlugin extends Plugin {
    private final Random random;
    private final long seed;
    
    public ProceduralWorldPlugin(Game game, long seed) {
        super(game);
        this.seed = seed;
        this.random = new Random(seed);
    }
    
    public ProceduralWorldPlugin(Game game) {
        this(game, System.currentTimeMillis());
    }
}
```

**Why**: 
- Testing: Same seed = same world
- Debugging: Reproduce issues reliably
- Sharing: Players can share interesting worlds by seed

### Spatial Pathfinding

**Use SpatialSystem for pathfinding logic**

```java
// ✅ GOOD: Clean separation of concerns
SpatialSystem spatial = game.getSystem(SpatialSystem.class);
Entity closestExit = spatial.findClosestToTarget(exitDestinations, landmark);

// ❌ BAD: Pathfinding logic scattered in navigation code
for (Entity exit : exits) {
    int[] pos = spatial.getPosition(exit);
    double dist = spatial.distance(pos, landmarkPos);
    // ... manual distance comparison
}
```

**Why**: SpatialSystem encapsulates spatial logic, keeping navigation code clean and reusable.

## Database Schema

### Schema Versioning

Systems manage their own schema versions:

```java
@Override
public void onSystemInitialize() {
    int v = getSchema().getVersionNumber();
    
    if (v == 0) {
        // Create tables
        getSchema().setVersionNumber(1);
    }
    
    if (v < 2) {
        // Migration for version 2
        getSchema().setVersionNumber(2);
    }
}
```

**Pattern**: Use `if (v < N)` for migrations, not `else if`. Allows skipping versions.

## Common Anti-Patterns

### ❌ Creating Entities Too Early

```java
@Override
public void onPluginInitialize() {
    // WRONG: Systems not initialized yet
    Place place = game.getSystem(EntitySystem.class).add(Place.class);
}
```

**Fix**: Use `OnCoreSystemsReady` hook.

### ❌ Using game.log Instead of log

```java
public class MyPlugin extends Plugin {
    public void doSomething() {
        game.log.log("Doing something");  // WRONG: No context
    }
}
```

**Fix**: Use `log` (plugin-specific logger).

### ❌ Hardcoding Exit Directions

```java
// WRONG: Special cases for specific exit types
if (direction.equals("cross")) {
    // Special handling
}
```

**Fix**: Make exit names grammatically correct on their own, use generic display format.

### ❌ Method Overloading Ambiguity

```java
// WRONG: Creates ambiguity
public void text(String s) { }
public void text(Markup.Safe s) { }
```

**Fix**: Use type wrappers (like `Markup.Safe`) to force explicit choices.

### ❌ Forgetting Dependencies

```java
// WRONG: No dependencies declared
public class WorldPlugin extends Plugin implements OnPluginInitialize {
    @Override
    public void onPluginInitialize() {
        game.getSystem(EntitySystem.class);  // Might not exist yet!
    }
}
```

**Fix**: Declare dependencies via `getDependencies()`.

## Testing Patterns

### Use Pre-Determined Commands

Always test with scripted input, never manual interaction:

```bash
printf "look\ngo north\nlook\nquit\n" | mvn -q exec:java ...
```

**Why**:
- Reproducible results
- Easy to verify changes
- Can be automated
- Self-documenting

See `.github/instructions/run.md` for details.

### World Generation Testing

Test with fixed seed for reproducibility:

```java
@Test
public void testWorldGeneration() {
    ProceduralWorldPlugin plugin = new ProceduralWorldPlugin(game, 12345L);
    // Same seed always produces same world
}
```

## Performance Considerations

### Don't Simulate What Players Can't Observe

From mission.md:
- Far-away city = macro-level simulation
- Player enters city = individual NPCs spawn
- Player leaves = NPCs despawn, back to macro

**Pattern**: Lazy initialization, aggressive cleanup.

### Event System is Append-Only

Events are NEVER deleted, only cancelled. This means:
- Database grows over time
- Historical queries are cheap
- Time-travel is possible

**Implication**: May need archival strategy for long-running games.

## Documentation Conventions

### Code Comments

```java
/**
 * Brief description of what this does.
 * 
 * Implementation note: Why it's done this way.
 * 
 * @param x What x represents
 * @return What gets returned
 */
```

### TODO Comments

```java
// TODO: Feature not yet implemented (needs UniqueType)
// FIXME: Known bug, needs investigation
// NOTE: Important implementation detail
```

## Summary of Key Insights

1. **Plugin order matters** - use getDependencies()
2. **Create entities in OnCoreSystemsReady**, not OnPluginInitialize
3. **Always use plugin log**, never game.log
4. **Register entity types before creating entities**
5. **Use seeds for deterministic generation**
6. **Markup.Safe forces explicit escaping**
7. **Events order by event_order, not time**
8. **Cancel events match on reference, not event_id**
9. **Test with scripted input, not manual commands**
10. **Lazy initialization for scale-adaptive simulation**
