package me.nagasonic.alkatraz.progression;

import me.nagasonic.alkatraz.progression.research.ResearchPointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceBoosterMultiplierTest {
    @BeforeEach
    void setUp() {
        ProgressionService.clearArcaneKnowledgeBoosters();
        ResearchPointService.clearResearchPointBoosters();
        ProgressionMultiplierService.reset();
    }

    @AfterEach
    void cleanup() {
        ProgressionService.clearArcaneKnowledgeBoosters();
        ResearchPointService.clearResearchPointBoosters();
        ProgressionMultiplierService.reset();
    }

    @Test
    void arcaneBoosterComposesWithGlobal() {
        ProgressionService.clearArcaneKnowledgeBoosters();
        ResearchPointService.clearResearchPointBoosters();
        ProgressionMultiplierService.reset();

        ProgressionService.addArcaneKnowledgeBooster("test", 2.0);
        ProgressionMultiplierService.setGlobalMultiplier(3.0);
        assertEquals(2.0, ProgressionService.getArcaneKnowledgeMultiplier(), 1e-9);
    }

    @Test
    void researchBoosterDefaultIsOne() {
        ResearchPointService.clearResearchPointBoosters();
        ProgressionMultiplierService.reset();
        assertEquals(1.0, ResearchPointService.getResearchPointMultiplier(), 1e-9);
    }
}