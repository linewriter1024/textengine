---
applyTo: "**"
---

# Code Guidelines & Non-Intuitive Insights

This document captures important patterns, gotchas, and non-obvious implementation details discovered during development.

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
3. **OnCoreSystemsReady** - Systems are initialized, safe to create entities
4. **OnStart** - Game is ready to start
5. **OnStartClient** - New client connected

**Gotcha**: Don't create entities in `OnPluginInitialize` - systems aren't ready yet. Use `OnCoreSystemsReady` instead.

```java
@Override
public void onPluginInitialize() {
    // ✅ GOOD: Register systems
    game.registerSystem(new WorldSystem(game));
}

@Override
public void onCoreSystemsReady() {
    // ✅ GOOD: Create entities after systems are ready
    Place place = game.getSystem(EntitySystem.class).add(Place.class);
}
```

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
