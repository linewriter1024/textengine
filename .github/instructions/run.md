---
applyTo: "**"
---

# Running the Game

## Standard Usage

When running the text engine game for testing or demonstration, always use pre-determined commands via `printf` to ensure consistent and reproducible output.

### Basic Test Sequence

```bash
printf "look\ngo north\nlook\ngo south\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"
```

### With Logging Enabled

```bash
printf "look\ngo north\nlook\ngo south\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main" -Dexec.args="--showlog"
```

### Extended Test Sequence (River Navigation)

```bash
printf "look\ngo east\nlook\ngo upstream\nlook\ngo downstream\nlook\ngo cross\nlook\nquit\n" | mvn -q exec:java -Dexec.mainClass="com.benleskey.textengine.cli.Main"
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
