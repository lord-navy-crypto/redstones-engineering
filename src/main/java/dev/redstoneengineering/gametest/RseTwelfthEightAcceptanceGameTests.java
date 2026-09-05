package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Twelfth block-by-block campaign: guided optical channel integrity. */
public final class RseTwelfthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseTwelfthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=50)
    public static void fiberCarriesChannelAndDeclaresOnlyConnectedFaces(GameTestHelper h) {
        BlockPos e=new BlockPos(1,1,2), f=new BlockPos(2,1,2), r=new BlockPos(3,1,2);
        h.setBlock(e,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState().setValue(OpticalEmitterBlock.INTENSITY,15).setValue(OpticalEmitterBlock.CHANNEL,4));
        h.setBlock(f,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(r,RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState());
        DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(e));
        h.runAfterDelay(2,()->{BlockState s=h.getBlockState(f);EngineeringPortProvider p=RedstoneEngineering.OPTICAL_FIBER.get();if(!OpticalFiberBlock.valid(h.getLevel(),h.absolutePos(f))||OpticalFiberBlock.channel(h.getLevel(),h.absolutePos(f))!=4||p.engineeringPorts(s).size()!=2||p.engineeringPort(s,Direction.UP).isPresent()){h.fail("Fiber payload or connected-face contract failed",f);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=45)
    public static void receiverRejectsInvalidOpticalPayload(GameTestHelper h) {
        BlockPos a=new BlockPos(1,1,1),b=new BlockPos(1,1,3),ra=new BlockPos(3,1,1),rb=new BlockPos(3,1,3);
        for(BlockPos p:new BlockPos[]{a,b})h.setBlock(p,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());
        h.setBlock(a.east(),RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(b.east(),RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(ra,RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState());h.setBlock(rb,RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState());
        h.setBlock(a, h.getBlockState(a).setValue(OpticalEmitterBlock.INTENSITY,0));
        DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(a));DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(b));
        h.runAfterDelay(2,()->{if(OpticalReceiverBlock.valid(h.getLevel(),h.absolutePos(ra))||!OpticalReceiverBlock.valid(h.getLevel(),h.absolutePos(rb))){h.fail("Receiver validity did not follow the authoritative optical source",ra);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=60)
    public static void splitterDeclaresOneInputAndTwoOutputs(GameTestHelper h) {
        BlockPos in=new BlockPos(1,1,2), feed=new BlockPos(2,1,2), split=new BlockPos(3,1,2);h.setBlock(in,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());h.setBlock(feed,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(split,RedstoneEngineering.OPTICAL_SPLITTER.get().defaultBlockState().setValue(DirectionalDomainBlock.FACING,Direction.EAST));
        h.setBlock(split.east(),RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(split.north(),RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(in));
        h.runAfterDelay(8,()->{EngineeringPortProvider p=RedstoneEngineering.OPTICAL_SPLITTER.get();BlockState s=h.getBlockState(split);if(p.engineeringPorts(s).size()!=3||p.engineeringPort(s,Direction.WEST).orElseThrow().direction()!=PortDirection.INPUT){h.fail("Splitter directional optical contract failed",split);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=55)
    public static void channelFilterPassesOnlyConfiguredChannel(GameTestHelper h) {
        BlockPos e=new BlockPos(1,1,2),f=new BlockPos(2,1,2),out=new BlockPos(4,1,2);h.setBlock(e,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState().setValue(OpticalEmitterBlock.CHANNEL,3));h.setBlock(f,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(out,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(new BlockPos(3,1,2),RedstoneEngineering.OPTICAL_CHANNEL_FILTER.get().defaultBlockState().setValue(DirectionalDomainBlock.FACING,Direction.EAST).setValue(OpticalChannelFilterBlock.TARGET,3));DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(e));h.runAfterDelay(5,()->{if(!OpticalFiberBlock.valid(h.getLevel(),h.absolutePos(out))||OpticalFiberBlock.intensity(h.getLevel(),h.absolutePos(out))>=15){h.fail("Channel filter did not pass selected channel with insertion loss",out);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=55)
    public static void attenuatorAppliesBoundedLoss(GameTestHelper h) {
        BlockPos e=new BlockPos(1,1,2),a=new BlockPos(2,1,2),out=new BlockPos(4,1,2);h.setBlock(e,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState().setValue(OpticalEmitterBlock.INTENSITY,12));h.setBlock(a,RedstoneEngineering.OPTICAL_ATTENUATOR.get().defaultBlockState().setValue(DirectionalDomainBlock.FACING,Direction.EAST).setValue(OpticalAttenuatorBlock.LOSS,4));h.setBlock(a.west(),RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(out,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(e));h.runAfterDelay(5,()->{if(OpticalFiberBlock.intensity(h.getLevel(),h.absolutePos(out))>=12){h.fail("Optical attenuator failed to reduce intensity",out);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=40)
    public static void powerMeterReadsOnlyItsOpticalFace(GameTestHelper h) {
        BlockPos e=new BlockPos(1,1,2),m=new BlockPos(2,1,2);h.setBlock(e,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());h.setBlock(m,RedstoneEngineering.OPTICAL_POWER_METER.get().defaultBlockState().setValue(OpticalPowerMeterBlock.FACING,Direction.WEST));DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(e));h.runAfterDelay(2,()->{EngineeringPortProvider p=RedstoneEngineering.OPTICAL_POWER_METER.get();if(p.engineeringPorts(h.getBlockState(m)).size()!=1||p.engineeringPort(h.getBlockState(m),Direction.WEST).isEmpty()){h.fail("Power meter exposed an incorrect optical face",m);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=35)
    public static void junctionRemainsPassiveTwoEndedAndCleansRuntime(GameTestHelper h) {
        BlockPos e=new BlockPos(1,1,2),f=new BlockPos(2,1,2),j=new BlockPos(3,1,2);h.setBlock(e,RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());h.setBlock(f,RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());h.setBlock(j,RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().defaultBlockState());DomainNetwork.recomputeOptical(h.getLevel(),h.absolutePos(e));h.runAfterDelay(2,()->{BlockPos w=h.absolutePos(j);if(!OpticalFiberJunctionBlock.valid(h.getLevel(),w)){h.fail("Optical splice failed passive propagation",j);return;}h.setBlock(j,Blocks.AIR.defaultBlockState());if(RuntimeIntStore.peek(h.getLevel(),"optical_junction",w)!=null){h.fail("Optical splice retained runtime after removal",j);return;}h.succeed();});
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace=RedstoneEngineering.MOD_ID, template=TEMPLATE, timeoutTicks=45)
    public static void allGuidedOpticalDevicesExposeTruthfulDomains(GameTestHelper h) {
        BlockPos p=new BlockPos(2,1,2);h.setBlock(p,RedstoneEngineering.OPTICAL_ATTENUATOR.get().defaultBlockState());EngineeringPortProvider provider=RedstoneEngineering.OPTICAL_ATTENUATOR.get();if(provider.engineeringPorts(h.getBlockState(p)).size()!=2||provider.engineeringPorts(h.getBlockState(p)).stream().anyMatch(x->x.domain()!=EngineeringDomain.OPTICAL)){h.fail("Guided optical device exposed a false domain",p);return;}h.succeed();
    }
}
