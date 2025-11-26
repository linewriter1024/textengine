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

Then type commands manually at the prompt.
