package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MagicBrains} static brain/displayName/meleeRange/wand
 * cache population, registerAll, and fallback defaults.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MagicBrainsTest {

    private MockedStatic<ConfigManager> cmMock;
    private MockedStatic<Alkatraz> alkatrazMock;

    @BeforeEach
    void setUp() {
        cmMock = mockStatic(ConfigManager.class);
        alkatrazMock = mockStatic(Alkatraz.class);
        alkatrazMock.when(Alkatraz::getInstance).thenReturn(mock(Alkatraz.class));
    }

    @AfterEach
    void tearDown() {
        cmMock.close();
        alkatrazMock.close();
    }

    private Config stubCfg(YamlConfiguration yaml) {
        Config cfg = mock(Config.class);
        when(cfg.get()).thenReturn(yaml);
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);
        return cfg;
    }

    @Test
    @Order(1)
    void unknownIdReturnsNullsOrDefaults() {
        assertNull(MagicBrains.brain("nope"));
        assertEquals("&f" + MagicEntityType.ZOMBIE_MAGE.getId(),
                MagicBrains.displayName(MagicEntityType.ZOMBIE_MAGE));
        assertEquals(0.0, MagicBrains.meleeRange(MagicEntityType.ZOMBIE_MAGE));
    }

    @Test
    @Order(2)
    void reloadEmptyYamlDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        stubCfg(yaml);

        MagicBrains.reload("zombie_mage");

        assertEquals("&fzombie_mage", MagicBrains.displayName(MagicEntityType.ZOMBIE_MAGE));
        assertEquals(0.0, MagicBrains.meleeRange(MagicEntityType.ZOMBIE_MAGE));
        assertNull(MagicBrains.wand(MagicEntityType.ZOMBIE_MAGE));
        assertNotNull(MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE));
        assertTrue(MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE).entries().isEmpty());
        cmMock.verify(() -> ConfigManager.getConfig("brains/zombie_mage.yml"));
    }

    @Test
    @Order(3)
    void reloadCustomDisplayNameMeleeRangeWand() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display-name", "&cCustom");
        yaml.set("melee-range", 2.5);
        yaml.set("wand", "wandid");
        stubCfg(yaml);

        MagicBrains.reload("zombie_mage");

        assertEquals("&cCustom", MagicBrains.displayName(MagicEntityType.ZOMBIE_MAGE));
        assertEquals(2.5, MagicBrains.meleeRange(MagicEntityType.ZOMBIE_MAGE));
        assertEquals("wandid", MagicBrains.wand(MagicEntityType.ZOMBIE_MAGE));
    }

    @Test
    @Order(4)
    void reloadGoalsSectionPopulatesBrain() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("goals.move.type", "water_avoiding_stroll");
        yaml.set("goals.move.priority", 2);
        yaml.set("targets.hunt.type", "hurt_by_target");
        stubCfg(yaml);

        MagicBrains.reload("zombie_mage");

        GoalBrain brain = MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE);
        assertEquals(2, brain.entries().size());

        GoalBrain.Entry goalEntry = brain.entries().stream()
                .filter(e -> !e.isTargetGoal())
                .findFirst().orElseThrow();
        assertInstanceOf(NativeGoalSpec.WaterAvoidingRandomStroll.class, goalEntry.goalOrSpec());
        assertEquals(2, goalEntry.priority());

        GoalBrain.Entry targetEntry = brain.entries().stream()
                .filter(GoalBrain.Entry::isTargetGoal)
                .findFirst().orElseThrow();
        assertInstanceOf(NativeGoalSpec.HurtByTarget.class, targetEntry.goalOrSpec());
    }

    @Test
    @Order(5)
    void applySectionTargetFlagInjectedIntoYamlEntry() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("targets.hunt.type", "hurt_by_target");
        stubCfg(yaml);

        MagicBrains.reload("zombie_mage");

        assertTrue(yaml.getConfigurationSection("targets")
                .getConfigurationSection("hunt").getBoolean("target"));
    }

    @Test
    @Order(6)
    void registerAllReloadsEveryType() {
        YamlConfiguration yaml = new YamlConfiguration();
        stubCfg(yaml);

        MagicBrains.registerAll();

        for (MagicEntityType type : MagicEntityType.values()) {
            cmMock.verify(() -> ConfigManager.getConfig("brains/" + type.getId() + ".yml"));
            assertNotNull(MagicBrains.brain(type));
        }
    }

    @Test
    @Order(7)
    void brainsKeyIsAlkatrazBrain() {
        assertEquals("alkatraz_brain", MagicBrains.BRAIN_KEY);
    }

    @Test
    @Order(8)
    void brainStringIdMatchesEnumId() {
        YamlConfiguration yaml = new YamlConfiguration();
        stubCfg(yaml);

        MagicBrains.reload("zombie_mage");

        GoalBrain fromEnum = MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE);
        GoalBrain fromString = MagicBrains.brain("zombie_mage");
        assertSame(fromEnum, fromString);
    }

    @Test
    @Order(9)
    void reloadOverwritesPreviousCache() {
        YamlConfiguration yaml1 = new YamlConfiguration();
        yaml1.set("display-name", "&aFirst");
        stubCfg(yaml1);
        MagicBrains.reload("zombie_mage");
        assertEquals("&aFirst", MagicBrains.displayName(MagicEntityType.ZOMBIE_MAGE));

        YamlConfiguration yaml2 = new YamlConfiguration();
        yaml2.set("display-name", "&bSecond");
        stubCfg(yaml2);
        MagicBrains.reload("zombie_mage");
        assertEquals("&bSecond", MagicBrains.displayName(MagicEntityType.ZOMBIE_MAGE));
    }
}
