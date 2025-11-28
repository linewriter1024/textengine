---
applyTo: "**"
---

# Running the Game

## Standard Usage

When running the text engine game for testing or demonstration, always use pre-determined commands via `printf` to ensure consistent and reproducible output.

### Procedural World Generation with Seeds

The game uses procedural generation for creating the world dynamically. You can specify a seed for deterministic, repeatable world layouts:

```bash
# Use a specific seed for repeatable testing
printf "look\ngo forest\nlook\ngo meadow\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345"
```

```bash
# Same seed with logging to observe generation
printf "look\ngo forest\nlook\ngo meadow\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345 --showlog"

# Same seed with API debug to see structured data
printf "look\ngo forest\nlook\ngo meadow\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345 --apidebug"
```

The seed ensures that:
- The same world layout is generated each time
- Biome distributions are deterministic
- Exit connections remain consistent
- Useful for debugging specific world configurations

If no seed is provided, the game uses the current timestamp as a seed, creating a different world each run.

### Basic Test Sequence

```bash
printf "look\ngo forest\nlook\ngo clearing\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"
```

### With Logging Enabled

```bash
printf "look\ngo forest\nlook\ngo clearing\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog"
```

### With API Debug Information

Shows the structured Message data (key-value pairs) sent between client and game, in addition to human-readable text:

```bash
printf "look\ngo forest\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--apidebug"
```

**Important**: The `--apidebug` flag shows entity IDs in the API output which can be used for testing.

### With Both Logging and API Debug

```bash
printf "look\ngo forest\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog --apidebug"
```

## Using Entity IDs for Testing

**For simpler, more consistent tests (especially with randomized descriptions), you can reference entities by their IDs using the `#` prefix.**

All commands that accept entity references also accept entity IDs with # prefix:
- `take #1234` instead of `take coin`
- `drop #5678` instead of `drop sword`
- `examine #9012` instead of `examine chest`
- `use #1111 on #2222` instead of `use axe on tree`
- `open #3456` instead of `open chest`
- `put #1234 in #5678` instead of `put coin in chest`
- `take #1234 from #5678` instead of `take coin from chest`
- `go #7890` instead of `go forest`

**Note**: Disambiguation numeric IDs (1, 2, 3...) do NOT require the # prefix - those are temporary IDs shown when ambiguous matches are found.

### How to Use Entity IDs in Tests

1. **Run with `--apidebug` to see entity IDs** in the structured API output
2. **Extract the IDs** from the first run's API output
3. **Use those IDs** in subsequent test commands for consistency

**Example workflow**:

```bash
# First: Run with --apidebug to discover entity IDs
printf "look\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345 --apidebug"

# Output shows entity IDs in the API data:
# (entity_id: '1234', description: 'a wooden chest')
# (entity_id: '5678', description: 'a gold coin')

# Then: Use those IDs with # prefix in tests for consistency
printf "open #1234\ntake #5678 from #1234\ninventory\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345"
```

**When to use entity IDs**:
- ✅ Testing core command functionality (does "take from" work?)
- ✅ When entity descriptions are randomized (avoids guessing "coin" vs "tarnished coin")
- ✅ For regression tests that need to be stable across code changes
- ✅ When testing with complex scenarios where naming is ambiguous

**When to use entity names**:
- ✅ Testing fuzzy matching and disambiguation logic
- ✅ Demonstrating user-facing behavior
- ✅ Testing specific entity name patterns
- ✅ Examples in documentation

**Note**: Entity IDs are **not shown** in normal human-readable output - they only appear in the `--apidebug` API data. This keeps the user experience clean while providing a powerful testing mechanism.

### Testing Persistence

Use the `--database` option to specify a persistent database file instead of the default timestamped temporary file:

```bash
# First run - create world and do some actions
printf "look\nwait 3 hours\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--seed 12345 --database /tmp/mygame.db"

# Second run - same database continues from where we left off
printf "look\nwait 2 hours\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--database /tmp/mygame.db"
```

**Important**: When resuming from a persistent database:
- The `--seed` argument is only used on the **first run** (when creating a new database)
- Subsequent runs should **NOT** include `--seed` - the world already exists
- All game state (entity positions, tick times, etc.) persists between runs
- Clocks will not re-chime hours they already chimed in previous sessions

### Extended Test Sequence (Procedural Exploration)

Navigation uses single-word landmarks visible from your current location:

```bash
printf "look\ngo forest\nlook\ngo river\nlook\ngo meadow\nlook\ngo hills\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"
```

### Custom Command Sequence

When testing specific features, create a custom sequence:

```bash
printf "look\n[your commands here]\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"
```

## Why Use printf?

1. **Reproducible**: Same commands every time, easier to verify changes
2. **Automated**: No manual input required
3. **Testing**: Can be integrated into CI/CD pipelines
4. **Documentation**: Command sequences serve as usage examples

## Alternative: Using Here-Documents

For longer sequences, you can use a here-document:

```bash
mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" <<'EOF'
look
go north
look
go east
look
quit
EOF
```

## Interactive Mode

For development and debugging, you can still run interactively without piped input:

```bash
mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog"
```

Or with API debug to see the structured message data:

```bash
mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog --apidebug"
```

Then type commands manually at the prompt.

## Database Storage

### Development Database Location

**In development, the database is stored in `/tmp/textengine/` with timestamped filenames.**

Each game run creates a new database file:
```bash
/tmp/textengine/1764214970.sqlitedb  # Timestamp: seconds since epoch
/tmp/textengine/1764214722.sqlitedb  # Previous run
```

**Why timestamped files?**
- **No conflicts**: Each run gets its own database
- **Easy debugging**: Can inspect databases from previous runs
- **No cleanup needed**: Temp directory automatically cleared on reboot
- **Testing isolation**: Tests don't interfere with each other

### Inspecting the Database

To examine the most recent database:

```bash
# Find the latest database
DB=$(ls -t /tmp/textengine/*.sqlitedb | head -1)

# Check spatial positions
sqlite3 "$DB" "SELECT COUNT(*) FROM spatial_position;"

# View unique types (human-readable string → integer mapping)
sqlite3 "$DB" "SELECT * FROM unique_type;" | grep scale

# Example output:
# 0|scale_continent|1011

# See which positions use which scale
sqlite3 "$DB" "SELECT entity_id, scale_id, x, y FROM spatial_position LIMIT 5;"

# Example output:
# 1032|1011|0|0
# 1050|1011|1|0
# 1065|1011|0|-1
```

### Production Database Configuration

For production or persistent development, modify `Main.java` to use a fixed database path instead of the timestamped temporary location.

Change:
```java
String directory = "/tmp/textengine";
String filename = String.format("%d.sqlitedb", System.currentTimeMillis() / 1000L);
```

To:
```java
String directory = "/path/to/persistent/storage";
String filename = "textengine.db";
```
