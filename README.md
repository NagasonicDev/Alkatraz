# Alkatraz
Alkatraz is a Magic plugin that allows players to use wands to cast spells using a combination of button clicks. Each spell uses a unique code to cast, for example, Magic Missile uses the code `RRRRR` to cast. 'R' is right click, 'L' is left click, and 'S' is hand swap. Codes can be viewed from the /spell menu.

## Using a wand
When holding a wand, the experience bar will transform into a **Mana Bar**, which shows how much mana you have. Each spell uses a certain amount of mana to cast, which can be seen in the /spell command. 

**WARNING: ALTHOUGH I HAVE THOUROUGHLY TESTED THE MANA BAR, THERE MAY BE GLITCHES THAT CAUSE THE EXPERIENCE BAR TO SAVE WRONG**

## Features:
- 1 Wand
- 5 Spells
- Mastery System
- Advanced Casting System

## Spells:
- Magic Missile: The basic magic spell, which every player discovered on joining. Shoots a beam up to 20 blocks in front of the player, doing small damage to entities it collides with. Cannot pass through solid blocks.
- Fireball: Shoots a fireball, exploding and dealing damaging to entities and blocks.
- Water Sphere: Summons a slow-moving water ball that does damage to entities.
- Air Burst: Shoots out a burst of air, pushing back all enemies.
- Lesser Heal: Heals a target(look at a player) or yourself(shift) by a small amount of health. (1-2.5 hearts, depending on Light Affinity)

## Commands (Permissions):
- `/alkatraz discoverspell <spell> <player> `(`alkatraz.command.discoverspell`): makes a player 'discover' a spell, meaning they can use it without permission.
- `/alkatraz undiscoverspell <spell> <player>` (`alkatraz.command.undiscoverspell`): makes a player 'undiscover' a spell, meaning they cannot use it without permission.
- `/alkatraz give <item> <player>` (`alkatraz.command.give`): Gives an Alkatraz item to a player, currently only `wooden_wand`
- `/alkatraz mastery <spell> <add|set> <number> [<player>]` (`alkatraz.command.mastery`): Modifies a player's mastery of a spell.
- `/alkatraz circle <add|set> <number> [<player>]` (`alkatraz.command.circle`): Modifies the circle level of a player. Automatically updates the max mana and mana recovery of the player.
- `/alkatraz experience <add|set> <number> [<player>]` (`alkatraz.command.experience`): Changes the magic experience of the player, will automatically increase the circle level of the player if enough experience is given to level up.
- `alkatraz.allspells`: allows a player to use all spells even if they have not discovered it.
- `/spells <player>`: opens a gui that shows the current spells that the player can use. `<player>` argument is if you want to view a different player's spells.

## Plans:
- Circle/Level System
- More Spells and Wands(ofc)
- Element Advantages (blazes weak to water and stuff.)
