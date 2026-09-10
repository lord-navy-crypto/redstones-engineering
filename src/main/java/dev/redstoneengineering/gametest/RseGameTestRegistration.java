package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Registers RSE runtime engineering tests when NeoForge enables the GameTest system. */
@Mod(RedstoneEngineering.MOD_ID)
public final class RseGameTestRegistration {
    public RseGameTestRegistration(IEventBus modBus) {
        modBus.addListener(RseGameTestRegistration::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseTopologyGameTests.class);
        event.register(RseCopperGameTests.class);
        event.register(RseMetrologyGameTests.class);
        event.register(RseCommissioningGameTests.class);
        event.register(RseEngineeringUxGameTests.class);
        event.register(RseAcceptanceGameTests.class);
        event.register(RseFunctionalCorrectnessGameTests.class);
        event.register(RseEngineeringUiGameTests.class);
        event.register(RseOperationsTimelineGameTests.class);
        event.register(RseOperationsIncidentGameTests.class);
        event.register(RseElectricalReliabilityGameTests.class);
        event.register(RseCommunicationIdentityGameTests.class);
        event.register(RseSignalJunctionTopologyGameTests.class);
        event.register(RseFirstTenDesignGameTests.class);
        event.register(RseSecondTenDesignGameTests.class);
        event.register(RseThirdTenDesignBugGameTests.class);
        event.register(RseFourthTenDesignBugGameTests.class);
        event.register(RseFifthTenDesignBugGameTests.class);
        event.register(RseSixthTenDesignBugGameTests.class);
        event.register(RseSeventhTenDesignBugGameTests.class);
        event.register(RseEighthTenDesignBugGameTests.class);
        event.register(RseNinthTenDesignBugGameTests.class);
        event.register(RseTenthTenDesignBugGameTests.class);
        event.register(RseEleventhTenDesignBugGameTests.class);
        event.register(RseTwelfthTenDesignBugGameTests.class);
        event.register(RseFinalTwoEvidenceClosureGameTests.class);
        event.register(RseFirstEightAcceptanceGameTests.class);
        event.register(RseSecondEightAcceptanceGameTests.class);
        event.register(RseThirdEightAcceptanceGameTests.class);
        event.register(RseFourthEightAcceptanceGameTests.class);
        event.register(RseFifthEightAcceptanceGameTests.class);
        event.register(RseSixthEightAcceptanceGameTests.class);
        event.register(RseSeventhEightAcceptanceGameTests.class);
        event.register(RseEighthEightAcceptanceGameTests.class);
        event.register(RseNinthEightAcceptanceGameTests.class);
        event.register(RseTenthEightAcceptanceGameTests.class);
        event.register(RseEleventhEightAcceptanceGameTests.class);
        event.register(RseTwelfthEightAcceptanceGameTests.class);
        event.register(RseThirteenthTenAcceptanceGameTests.class);
        event.register(RseFourteenthTenAcceptanceGameTests.class);
        event.register(RseFifteenthSevenAcceptanceGameTests.class);
        event.register(RseTotalAuditClosureGameTests.class);
        event.register(RseInterconnectRefactorGameTests.class);
        event.register(RseSystemLevelClosurePhase1GameTests.class);
        event.register(RsePairwiseValidation01GameTests.class);
        event.register(RseFiveBlockMeasurementFeedbackGameTests.class);
        event.register(RseFiveBlockMediumToolsGameTests.class);
        event.register(RseFiveBlockCopperMediumToolsGameTests.class);
        event.register(RseFiveBlockDataBusMediumToolsGameTests.class);
        event.register(RseFiveBlockSerialMediumToolsGameTests.class);
        event.register(RseFiveBlockDifferentialMediumToolsGameTests.class);
        event.register(RseFiveBlockLapisMediumToolsGameTests.class);
        event.register(RseFiveBlockQuartzMediumToolsGameTests.class);
        event.register(RseAmethystMediumSystemGameTests.class);
        event.register(RseRadioMediumSystemGameTests.class);
        event.register(RseInductionMediumSystemGameTests.class);
        event.register(RseMagneticMeasurementSystemGameTests.class);
        event.register(RseThermalMeasurementSystemGameTests.class);
        event.register(RseLapisMeasurementSystemGameTests.class);
        event.register(RseOpticalMeasurementSystemGameTests.class);
        event.register(RseNetworkBudgetSystemGameTests.class);
        event.register(RseQuartzNetworkBudgetSystemGameTests.class);
        event.register(RseRedstoneCableNetworkBudgetSystemGameTests.class);
        event.register(RseAmethystNetworkBudgetSystemGameTests.class);
        event.register(RseAmethystProcessorBudgetSystemGameTests.class);
        event.register(RseCopperNetworkBudgetSystemGameTests.class);
        event.register(RseDataBusNetworkBudgetSystemGameTests.class);
        event.register(RseSerialNetworkBudgetSystemGameTests.class);
        event.register(RseDifferentialNetworkBudgetSystemGameTests.class);
        event.register(RsePneumaticNetworkBudgetSystemGameTests.class);
        event.register(RseCopperFuseBudgetSystemGameTests.class);
        event.register(RseFreeSpaceOpticsCoverageSystemGameTests.class);
        event.register(RseMagneticCoverageSystemGameTests.class);
        event.register(RseDomainNetworkCoverageSystemGameTests.class);
        event.register(RseDomainDriverLifecycleSystemGameTests.class);
        event.register(RseQuartzLabConfigurationLifecycleSystemGameTests.class);
        event.register(RseCopperConfigurationLifecycleSystemGameTests.class);
    }
}
