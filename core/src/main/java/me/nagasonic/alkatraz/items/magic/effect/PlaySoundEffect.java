package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

final class PlaySoundEffect implements Effect {

    private final Sound sound;
    private final float volume;
    private final float pitch;

    PlaySoundEffect(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public void execute(TriggerContext context) {
        context.playerActor().ifPresent(player ->
                player.playSound(player.getLocation(), sound, volume, pitch));
    }

    static Effect fromConfig(Map<String, Object> config) {
        Sound sound = Sound.valueOf(String.valueOf(config.get("sound")).toUpperCase(Locale.ROOT));
        float volume = Float.parseFloat(String.valueOf(config.getOrDefault("volume", 1.0f)));
        float pitch = Float.parseFloat(String.valueOf(config.getOrDefault("pitch", 1.0f)));
        return new PlaySoundEffect(sound, volume, pitch);
    }
}
