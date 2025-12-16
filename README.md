# Alkatraz
Alkatraz is a Magic plugin that allows players to use wands to cast spells using a combination of button clicks. Each spell uses a unique code to cast, for example, Magic Missile uses the code `RRRRR` to cast. 'R' is right click, 'L' is left click, and 'S' is hand swap. Codes can be viewed from the /spell menu.

## Using a wand
When holding a wand, the experience bar will transform into a **Mana Bar**, which shows how much mana you have. Each spell uses a certain amount of mana to cast, which can be seen in the /spell command.

## Features:
- 2 Wand
- 12 Spells
- Mastery System
- Advanced Casting System
- Stat Points

## WIKI:
https://github.com/NagasonicDev/Alkatraz/wiki

## Wands:
- **Wooden Wand**: The most basic wand, can only cast up to circle 2 spells.
- **Reinforced Wand**: An upgraded form of the wooden wand, and can cast up to 5th circle spells.

## Spells:
- **Magic Missile**: The basic magic spell, which every player discovered on joining. Shoots a beam up to 20 blocks in front of the player, doing small damage to entities it collides with. Cannot pass through solid blocks.
- **Fireball**: Shoots a fireball, exploding and dealing damaging to entities and blocks.
- **Water Sphere**: Summons a slow-moving water ball that does damage to entities.
- **Air Burst**: Shoots out a burst of air, pushing back all enemies.
- **Earth Throw**: Throws a chunk of the ground at enemies, launching them into the air. Caster must be touching the ground for the spell to activate.
- **Lesser Heal**: Heals a target(look at a player) or yourself(shift) by a small amount of health. (1-2.5 hearts, depending on Light Affinity)
- **Fire Blast**: Shoots a large fireball, harshly damaging entities, and igniting the surroundings.
- **Detect**: Scans the surroundings of the caster, making any nearby entities glow. Detect range varies depending on circle level, which can be seen in the `detect.yml`.
- **Stealth**: Hides the player, including their armor, from other players. The player will appear transparent to player's if they have a greater circle level than the caster.
- **Disguise**: Allows the player to disguise as a different player.
- **Swift**: Uses a burst of air to launch the player forwards.
- **Fire Wall**: Creates a wall of flames in the direction the player looks, and curves when the player looks around. Blocks enemies with the risk of taking heavy damage when jumping through the wall.

## Stat Points and Stats GUI
Invest skill points into elements to boost their affinity and resistance. Stat points are currently only obtainable by modifying the player's data and the amount given when joining the server for the first time.

The amounts of points and reset tokens (used to reset stat points) given on first join is configurable in the `config.yml` file.

### Stats GUI
The Stats GUI, opened through the `/alkatraz stats` command, shows the targets current stats, and also allows them to invest points into the various elements.
There is also a `Reset Stats` button, which, as in the name, allows them to recieve a complete refund of their invested stat points, at the cost of a Reset Token.

## Commands (Permissions):
- `/alkatraz discoverspell <spell> <player> `(`alkatraz.command.discoverspell`): makes a player 'discover' a spell, meaning they can use it without permission.
- `/alkatraz undiscoverspell <spell> <player>` (`alkatraz.command.undiscoverspell`): makes a player 'undiscover' a spell, meaning they cannot use it without permission.
- `/alkatraz give <item> <player>` (`alkatraz.command.give`): Gives an Alkatraz item to a player, currently only `wooden_wand`
- `/alkatraz mastery <spell> <add|set> <number> [<player>]` (`alkatraz.command.mastery`): Modifies a player's mastery of a spell.
- `/alkatraz circle <add|set> <number> [<player>]` (`alkatraz.command.circle`): Modifies the circle level of a player. Automatically updates the max mana and mana recovery of the player.
- `/alkatraz experience <add|set> <number> [<player>]` (`alkatraz.command.experience`): Changes the magic experience of the player, will automatically increase the circle level of the player if enough experience is given to level up.
- `/alkatraz stats [<player>]` (`alkatraz.command.stats.other`): Opens the target's Stats GUI to the sender.
- `/alkatraz reload` (`alkatraz.command.reload`): Reloads spell config. Some changes may require a server restart.
- `alkatraz.allspells`: allows a player to use all spells even if they have not discovered it.
- `/spells <player>` (`alkatraz.command.spells.other`): opens a gui that shows the current spells that the player can use. `<player>` argument is if you want to view a different player's spells.

## Plans:
- Skill Tree/Perks
- More Spells and Wands(ofc)
- Element Advantages (blazes weak to water spells and stuff.)
