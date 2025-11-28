# Toward the ultimate text adventure game engine

**Original Article**: https://benleskey.com/blog/textengine_intro  
**Date**: 2023-04-21

---

## Text adventure

### Why text?

Text-based games have an extremely good complexity/development time ratio. There are no graphics to worry about, no programmer art beyond writing (what luck, I happen to enjoy writing), no controls beyond text processing, no frames per second, et cetera, et cetera. Fully text based games can be even more complex than turn-based 2D roguelike games like [Cataclysm: DDA](https://cataclysmdda.org/). There's just less extra to worry about—the development focus has one of the most basic interfaces imaginable. Additionally, fully text-based games are perfect for screen readers due to their simple interface (see [this article](https://writing-games.com/building-a-better-mud/) about writing a screen-reader friendly text-based game).

The specific style of text-based game I'm talking about is the text adventure game. The computer tells you about your environment, your circumstances, and your character, and you choose what your character does or says. It's simple, straightforward, and very reminiscent of the conversational style of a tabletop RPG.

The problem is, a human game master will always be better than a computer here, right? A human game master can simulate any situation, can mediate any conflict, can act any character. (I exaggerate, but not very much.) A computer must be programmed to handle every specific situation in the game's world.

### AI?

You can make [ChatGPT](https://chat.openai.com/chat) play a roleplaying game with you. ChatGPT can be the game master, simulate the environment, accept the actions of your character, whatever you might want. A couple of pitfalls prevent full immersion, however. ChatGPT forgets details and must be reminded, and it can't stay on track with a game system, being a more freeform storyteller. I want something better.

### MUDs?

The system I have in mind is similar to the [MUD](https://en.wikipedia.org/w/index.php?title=MUD&oldid=1140275602) [gameplay](https://medium.com/@williamson.f93/multi-user-dungeons-muds-what-are-they-and-how-to-play-af3ec0f29f4a) [style](https://writing-games.com/what-is-a-mud-multi-user-dungeon/), which is fully text-based as you pilot your character through a multiplayer world. MUDs are, however, limited in a number of ways: they are generally room based, their multiplayer nature requires real-time interaction, their systems are quite gamey, and the "dungeon master" is primarily concerned with combat mechanics.

### Something new

I've started work on a new system at [https://github.com/linewriter1024/textengine](https://github.com/linewriter1024/textengine). This text engine is an effort toward building a flexible text-based game engine for simulating any scenario or world.

## Goals

• To simulate combat and action mechanics  
• To handle social interactions  
• To keep track of world events: political affiliations, kingdom economics, famines, shipping routes  
• To support singleplayer play or play with a small party  
• To support any genre of game: high fantasy action, low fantasy economic, sci-fi space battle, zombie apocalypse, etc.

These are very ambitious goals, but the text engine format lends itself well to managing all this complexity without the added requirements of graphical display and control.

This will be a long-term project, so development may be quite slow. There is no time pressure, however, so I can take it at my pace.

### Learning Go

This project is also a way for me to learn the Go language, which has some unique syntax and ideas that I think will be perfect for a large text-based game engine like this.

## Design

The basic structure is thus: everything in the world, whether a city, a man, a kingdom, a sword, or a lake, is an entity in relationship with other entities, capable of whatever actions and interactions make sense for it.

Let's example a sample scenario to illustrate the idea: a covered **wagon** of four **merchants** traveling down a **road** through a **forest**, about to be robbed by two **bandits** with **crossbows**. You are the lone **guard** for the **wagon**, wielding only a **sword** riding in the **front** with the **driver** while the other three **merchants** are sitting in the **back**.

Every bold word in that previous paragraph would be an entity (or a few entities). As an example, the wagon is an entity with two entities making it up: the front and the back. In the event that you wanted to hide underneath the wagon, an entity for the underneath would be generated as well. The front of the wagon is an entity that _contains_ you and the driver. This _contains_ relationship would be mirrored by _in_ relationships that you and the driver have with the wagon. The wagon itself is _on_ the road, while the road _contains_ the wagon. The road itself is within the forest, and so forth.

You can see the complex interactions that can happen here. Each of the merchants and bandits has an AI corresponding with their generated personalities and skills, so some of the merchants might draw weapons while others might seek cover in the wagon from the bandits' crossbows.

Dialogue would be represented by ideas, such as "The king expresses his gratitude with an undercurrent of disdain." or "The bandit threatens you with no trace of reason in his eyes." You can choose to respond however you see fit, by threatening, cajoling, warmly thanking, etc. to influence character's perceptions, attitudes, and ultimately actions.

Attitudes and actions would bubble upward as well to influence cities and kingdoms. If the player was not currently in a city it could be simulated on a macro level rather than each individual character in the city, so that the city's influence and politics would change and existing characters in the city would have their life stories altered without the need for a granular simulation.

I still need to work through the details of the system, and see what implementation issues I run into, but this should provide a base for further development in a new and exciting direction.

## Github repository

I've started work at [https://github.com/linewriter1024/textengine](https://github.com/linewriter1024/textengine). The prototype is not yet a functioning game, but there is a command processing system and the basis of a generic game engine capable of singleplayer and multiplayer text-based gameplay.
