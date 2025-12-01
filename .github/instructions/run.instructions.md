---
applyTo: "**"
---

# Running the Game

Always use scripted input via `printf` for reproducible testing.

## Seeds for Deterministic Generation

```bash
# Same seed = same world
printf "look\ngo forest\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345"

# With logging
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --showlog"

# With API debug (shows entity IDs)
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --apidebug"
```

**Seed behavior**:

- Same seed → same world (biomes, exits, items)
- No seed → uses current timestamp
- Useful for debugging and regression tests

## Loading Plugins

Use `--plugin <class name>` to load additional plugins (can be specified multiple times):

```bash
# Load the high fantasy plugin
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --plugin com.benleskey.textengine.plugins.highfantasy.HighFantasyPlugin"

# Load multiple plugins
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--plugin com.example.PluginA --plugin com.example.PluginB"
```

**Plugin behavior**:

- Plugins are registered after core plugins but before initialization
- Tentative plugins (like `ProceduralWorldPlugin`) are auto-activated if a loaded plugin depends on them
- Plugin class must have a `(Game game)` constructor

## Entity IDs for Testing

**Use `#` prefix for entity IDs**: `take #1234`, `open #1366`, `go #7890`

**Workflow**:

1. Discover IDs with `--apidebug`
2. Use `#ID` in subsequent tests

```bash
# Discover IDs
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --apidebug"
# Output: (entity_id: '1366', description: 'a battered chest')

# Use IDs in tests
printf "open #1366\ntake #1377 from #1366\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345"
```

**When to use**:

- ✅ Core functionality tests (avoids randomized descriptions)
- ✅ Regression tests (stable across code changes)
- ❌ Testing fuzzy matching/disambiguation

**Note**: Disambiguation IDs (1, 2, 3) don't need `#` prefix—only permanent entity IDs.

## Persistent Database

```bash
# First run - create world
printf "look\nwait 3 hours\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --database /tmp/game.db"

# Second run - resume (NO --seed)
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--database /tmp/game.db"
```

**Important**: `--seed` only on first run; subsequent runs load existing world.

## Development Database

**Location**: `/tmp/textengine/TIMESTAMP.sqlitedb` (auto-created per run)

**Why timestamped**:

- No conflicts between runs
- Easy to inspect previous runs
- Auto-cleanup on reboot

**Inspect database**:

```bash
DB=$(ls -t /tmp/textengine/*.sqlitedb | head -1)
sqlite3 "$DB" "SELECT * FROM unique_type;"
```

## Why Printf?

1. Reproducible (same commands every time)
2. Automated (no manual input)
3. Self-documenting (command sequences = examples)
4. CI/CD friendly

## Debug Logging

When debugging or developing new features, **always add detailed log statements** to trace execution flow:

```java
game.log.log("EntityName %d doing action: param1=%s, param2=%d",
    getId(), someString, someNumber);
```

**Benefits**:

- Trace execution flow without debugger
- Verify timing and state changes
- Understand NPC behavior
- Diagnose why actions aren't executing

**Usage**: Add `--showlog` to see all log output:

```bash
printf "look\nquit\n" | mvn -q exec:java ... -Dexec.args="--seed 12345 --showlog"
```

Log statements are essential for understanding complex interactions between systems, especially for timing-sensitive operations like NPC ticks and action queueing.

**Important Note on Entity Lifecycle**: Entity instances are recreated from the database frequently (not singletons). Any state stored in instance fields will be lost unless persisted using PropertiesSubSystem or similar. Use logging to trace when constructors are called to debug state loss issues.
