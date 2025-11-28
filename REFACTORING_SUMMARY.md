# NPC Action System Refactoring

## Overview

Refactored NPC action logic from the Goblin entity into a centralized, registry-based `ActorActionSystem`. This makes NPCs simpler and allows all actor logic (move, take, drop, etc.) to be shared between players and NPCs.

## Key Changes

### 1. Action Registry Pattern

Created an action type registry similar to the entity type registry:

- **Action Base Class**: `Action` defines the interface for all action types
- **Concrete Actions**: `MoveAction`, `TakeItemAction`, `DropItemAction`
- **Registry**: `ActorActionSystem` registers action types with their implementation classes
- **Database Storage**: Actions stored by type ID + target entity ID (like entities)

### 2. Simplified Goblin

**Before**: Goblin contained all action execution logic (movement, item interaction, broadcasts)

**After**: Goblin only handles:
- AI decision-making (what action to take)
- Queueing actions via `ActorActionSystem`
- Reacting when actions complete

The Goblin went from ~170 lines to ~160 lines, with all generic logic moved to reusable systems.

### 3. Action Visibility

Players and NPCs can now see pending actions on other actors:

```
Nearby: a goblin (moving to a field of wildflowers).
Nearby: a goblin (taking a sword).
```

This is achieved through:
- `ActorActionSystem.getPendingActionDescription()` - gets human-readable action description
- `Action.getDescription()` - each action defines its own description
- `InteractionPlugin` (look command) - displays pending actions in parentheses

### 4. Unified Player/NPC Actions

Both players and NPCs now use the same code path:

```java
// Queue action (players auto-complete, NPCs wait)
aas.queueAction(actor, aas.ACTION_MOVE, destination, timeRequired);

// Execute action (broadcasts to nearby entities)
aas.executeAction(actor, aas.ACTION_MOVE, destination, timeRequired);
```

Player commands (go, take, drop) updated to use this system.

## Architecture

```
ActorActionSystem
  ├── Action Registry (UniqueType -> Action class)
  ├── queueAction() - delegates to PendingActionSystem
  ├── executeAction() - creates Action instance and calls execute()
  └── getPendingActionDescription() - for observers

Action (abstract base class)
  ├── getActionType() - UniqueType for database storage
  ├── execute() - perform the action, broadcast messages
  └── getDescription() - human-readable for observers

MoveAction, TakeItemAction, DropItemAction
  └── Implement Action interface with specific logic

PendingActionSystem
  └── Database storage for pending actions (unchanged)
```

## Benefits

1. **Consistency**: Players and NPCs use identical code paths
2. **Visibility**: Other players/NPCs can see what actors are doing
3. **Extensibility**: New action types just extend `Action` and register
4. **Simplicity**: NPCs focus on AI, not implementation details
5. **Maintainability**: Action logic centralized, not duplicated

## Testing

```bash
# See goblin with pending action
printf "look\nwait 2 minutes\nlook\nquit\n" | mvn -q exec:java \
  -Dexec.mainClass="com.benleskey.textengine.cli.Main" \
  -Dexec.args="--seed 12345"

# Output shows:
# Nearby: a goblin (moving to a field of wildflowers).
```

## Files Changed

- **New**: `entities/actions/Action.java` - base class
- **New**: `entities/actions/MoveAction.java` - movement implementation
- **New**: `entities/actions/TakeItemAction.java` - take item implementation
- **New**: `entities/actions/DropItemAction.java` - drop item implementation
- **Modified**: `systems/ActorActionSystem.java` - action registry and execution
- **Modified**: `systems/PendingActionSystem.java` - removed action type constants (moved to ActorActionSystem)
- **Modified**: `plugins/highfantasy/entities/Goblin.java` - simplified to just AI logic
- **Modified**: `plugins/core/InteractionPlugin.java` - display pending actions in look command
- **Modified**: `plugins/core/NavigationPlugin.java` - use action system for player movement
- **Modified**: `plugins/core/ItemInteractionPlugin.java` - use action system for player item interactions

## Design Principles Followed

✅ **Never pass systems as parameters** - Actions fetch systems locally  
✅ **Never hide errors** - Exceptions propagate with full stack traces  
✅ **Registry pattern** - Actions registered like entities  
✅ **Generic systems over specific features** - Action system handles all actor actions  
✅ **Machine-readable API** - Pending actions in structured output (`pending_action` field)  
✅ **Relationship-driven** - Actions reference entities by ID, not properties
