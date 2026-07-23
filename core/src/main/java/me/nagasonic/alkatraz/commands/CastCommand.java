package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.gui.grimoire.GrimoireLecternState;
import me.nagasonic.alkatraz.gui.implementation.GrimoirePageMenu;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellCastValidator;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CastCommand implements CommandExecutor {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    public static boolean castFromGrimoire(Player player) {
        GrimoireLecternState state = GrimoireLecternState.get(player);
        if (state == null || state.getCastToken() == null) return false;

        int currentPage = state.getCurrentPage();
        ItemStack grimoireStack = state.getGrimoireStack();
        var instance = state.getInstance();

        GrimoireLecternState.remove(player);

        if (grimoireStack == null || instance == null) return false;

        List<String> pages = GrimoirePageMenu.getPagesStatic(instance);
        if (currentPage < 0 || currentPage >= pages.size()) return false;

        String spellId = pages.get(currentPage);
        if (spellId == null || spellId.isEmpty()) return false;

        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return false;

        if (!SpellCastValidator.canCast(player, null, spell)) return false;

        spell.cast(player, null);
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        String token = args[0];
        GrimoireLecternState state = GrimoireLecternState.get(player);
        if (state == null) {
            player.sendMessage(lang().get("commands.cast_no_session"));
            return true;
        }

        if (!token.equals(state.getCastToken())) {
            player.sendMessage(lang().get("commands.cast_invalid_token"));
            return true;
        }

        return castFromGrimoire(player);
    }
}
