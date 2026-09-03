package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Bounded graph recomputation for RSE non-redstone domains.
 * Processors are intentionally NOT transparent network nodes: they read one segment and drive another.
 */
public final class DomainNetwork {
    private static final int MAX_NODES = NetworkKernel.MAX_NODES;
    private DomainNetwork() {}

    public record LapisSample(int value, boolean valid) {}
    public record QuartzSample(boolean active, int periodTicks, boolean valid) {}
    public record AmethystSample(boolean active, int frequency, int amplitude) {}
    public record OpticalSample(int intensity, int channel, boolean valid) {}

    // ---------------- Lapis: precision continuous-like domain ----------------
    public static void recomputeLapis(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collectHorizontalEdges(level, start, "lapis", p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof LapisSignalLineBlock
                    || b instanceof LapisPrecisionSourceBlock
                    || b instanceof LapisNoiseSourceBlock;
        }, (a,b,d)->surfaceEdgeAllowed(level,a,b,d,LapisSignalLineBlock.class));
        if (nodes.isEmpty()) return;

        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "lapis", nodes));
        Set<BlockPos> rawSeen = new HashSet<>();
        for (BlockPos p : nodes) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof LapisPrecisionSourceBlock && rawSeen.add(p)) {
                claims.add(new DomainDriverRegistry.Claim(p, p, state.getValue(LapisPrecisionSourceBlock.VALUE), 0, 0, state.getBlock().getClass().getName()));
            } else if (state.getBlock() instanceof LapisNoiseSourceBlock && rawSeen.add(p)) {
                claims.add(new DomainDriverRegistry.Claim(p, p, LapisNoiseSourceBlock.currentValue(level,p,state), 0, 0, state.getBlock().getClass().getName()));
            }
        }
        NetworkKernel.recordDriverState(level, "lapis", claims.size());
        boolean valid = claims.size() == 1;
        int resolved = valid ? EngineeringMath.clamp(claims.get(0).a(),0,100) : 0;
        for (BlockPos p : nodes) {
            if (level.getBlockState(p).getBlock() instanceof LapisSignalLineBlock) {
                LapisSignalLineBlock.setSignal(level, p, resolved, valid);
            }
        }
    }

    public static LapisSample sampleLapis(Level level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (s.getBlock() instanceof LapisSignalLineBlock) return new LapisSample(LapisSignalLineBlock.value(level,pos), LapisSignalLineBlock.valid(level,pos));
        if (s.getBlock() instanceof LapisPrecisionSourceBlock) return new LapisSample(s.getValue(LapisPrecisionSourceBlock.VALUE), true);
        if (s.getBlock() instanceof LapisNoiseSourceBlock) return new LapisSample(LapisNoiseSourceBlock.currentValue(level, pos, s), true);
        return new LapisSample(0, false);
    }

    public static void driveLapis(ServerLevel level, BlockPos start, BlockPos driverPos, int value, boolean valid) {
        if (valid) DomainDriverRegistry.claim(level, "lapis", driverPos, start, EngineeringMath.clamp(value,0,100), 0, 0);
        else DomainDriverRegistry.release(level, "lapis", driverPos, start);
        Set<BlockPos> nodes = collectHorizontalEdges(level,start,"lapis",p -> level.getBlockState(p).getBlock() instanceof LapisSignalLineBlock,(a,b,d)->surfaceEdgeAllowed(level,a,b,d,LapisSignalLineBlock.class));
        if (nodes.isEmpty()) return;
        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "lapis", nodes));
        addRawLapisClaims(level, nodes, claims);
        NetworkKernel.recordDriverState(level, "lapis", claims.size());
        boolean ok = claims.size() == 1;
        int resolved = ok ? EngineeringMath.clamp(claims.get(0).a(),0,100) : 0;
        for (BlockPos p : nodes) LapisSignalLineBlock.setSignal(level, p, resolved, ok);
    }

    /** Legacy/internal convenience for raw network code; no driver claim is registered. */
    public static void driveLapis(ServerLevel level, BlockPos start, int value, boolean valid) {
        Set<BlockPos> nodes = collectHorizontalEdges(level,start,"lapis",p -> level.getBlockState(p).getBlock() instanceof LapisSignalLineBlock,(a,b,d)->surfaceEdgeAllowed(level,a,b,d,LapisSignalLineBlock.class));
        for (BlockPos p : nodes) LapisSignalLineBlock.setSignal(level, p, EngineeringMath.clamp(value,0,100), valid);
    }

    // ---------------- Quartz: timing/clock domain ----------------
    public static void recomputeQuartz(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collectHorizontalEdges(level, start, "quartz", p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof QuartzTimingLineBlock || b instanceof QuartzOscillatorBlock || b instanceof QuartzLabOscillatorBlock;
        }, (a,b,d)->surfaceEdgeAllowed(level,a,b,d,QuartzTimingLineBlock.class));
        if (nodes.isEmpty()) return;

        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "quartz", nodes));
        Set<BlockPos> rawSeen = new HashSet<>();
        for (BlockPos p : nodes) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof QuartzOscillatorBlock && rawSeen.add(p)) {
                claims.add(new DomainDriverRegistry.Claim(p,p,state.getValue(QuartzOscillatorBlock.ACTIVE)?1:0,QuartzTimingLineBlock.periodTicks(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX)),0,state.getBlock().getClass().getName()));
            } else if (state.getBlock() instanceof QuartzLabOscillatorBlock && rawSeen.add(p)) {
                claims.add(new DomainDriverRegistry.Claim(p,p,state.getValue(QuartzLabOscillatorBlock.ACTIVE)?1:0,QuartzTimingLineBlock.periodTicks(state.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX)),0,state.getBlock().getClass().getName()));
            }
        }
        NetworkKernel.recordDriverState(level, "quartz", claims.size());
        boolean valid = claims.size() == 1;
        boolean active = valid && claims.get(0).a() == 1;
        int period = valid ? Math.max(1,claims.get(0).b()) : 0;
        for (BlockPos p : nodes) {
            if (level.getBlockState(p).getBlock() instanceof QuartzTimingLineBlock) {
                QuartzTimingLineBlock.setTiming(level,p,active,period,valid);
            }
        }
    }

    public static QuartzSample sampleQuartz(Level level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (s.getBlock() instanceof QuartzTimingLineBlock) return new QuartzSample(QuartzTimingLineBlock.active(level,pos), QuartzTimingLineBlock.period(level,pos), QuartzTimingLineBlock.valid(level,pos));
        if (s.getBlock() instanceof QuartzOscillatorBlock) return new QuartzSample(s.getValue(QuartzOscillatorBlock.ACTIVE), QuartzTimingLineBlock.periodTicks(s.getValue(QuartzOscillatorBlock.PERIOD_INDEX)), true);
        if (s.getBlock() instanceof QuartzLabOscillatorBlock) return new QuartzSample(s.getValue(QuartzLabOscillatorBlock.ACTIVE), QuartzTimingLineBlock.periodTicks(s.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX)), true);
        return new QuartzSample(false, 0, false);
    }

    public static void driveQuartz(ServerLevel level, BlockPos start, BlockPos driverPos, boolean active, int periodTicks, boolean valid) {
        if (valid) DomainDriverRegistry.claim(level, "quartz", driverPos, start, active ? 1 : 0, Math.max(1,periodTicks), 0);
        else DomainDriverRegistry.release(level, "quartz", driverPos, start);
        Set<BlockPos> nodes = collectHorizontalEdges(level,start,"quartz",p -> level.getBlockState(p).getBlock() instanceof QuartzTimingLineBlock,(a,b,d)->surfaceEdgeAllowed(level,a,b,d,QuartzTimingLineBlock.class));
        if (nodes.isEmpty()) return;
        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "quartz", nodes));
        addRawQuartzClaims(level, nodes, claims);
        NetworkKernel.recordDriverState(level, "quartz", claims.size());
        boolean ok = claims.size() == 1;
        boolean resolvedActive = ok && claims.get(0).a() == 1;
        int resolvedPeriod = ok ? Math.max(1, claims.get(0).b()) : 0;
        for (BlockPos p : nodes) QuartzTimingLineBlock.setTiming(level, p, resolvedActive, resolvedPeriod, ok);
    }

    public static void driveQuartz(ServerLevel level, BlockPos start, boolean active, int periodTicks, boolean valid) {
        Set<BlockPos> nodes = collectHorizontalEdges(level,start,"quartz",p -> level.getBlockState(p).getBlock() instanceof QuartzTimingLineBlock,(a,b,d)->surfaceEdgeAllowed(level,a,b,d,QuartzTimingLineBlock.class));
        for (BlockPos p : nodes) QuartzTimingLineBlock.setTiming(level, p, active, periodTicks, valid);
    }

    // ---------------- Amethyst: vibration/resonance domain ----------------
    public static void recomputeAmethyst(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collectHorizontalEdges(level, start, "amethyst", p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof AmethystResonanceDustBlock || b instanceof AmethystResonatorBlock;
        }, (a,b,d)->surfaceEdgeAllowed(level,a,b,d,AmethystResonanceDustBlock.class));
        if (nodes.isEmpty()) return;

        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos p : nodes) {
            var s = level.getBlockState(p);
            if (s.getBlock() instanceof AmethystResonatorBlock && AmethystResonatorBlock.isActive(level, p)) sources.add(p);
        }

        Map<BlockPos,Map<BlockPos,Integer>> resonanceDistance=new HashMap<>();
        for(BlockPos src:sources) resonanceDistance.put(src,distancesHorizontalTrace(level,nodes,src,AmethystResonanceDustBlock.class));

        for (BlockPos p : nodes) {
            var state = level.getBlockState(p);
            if (!(state.getBlock() instanceof AmethystResonanceDustBlock)) continue;
            int bestAmp = 0;
            int bestFreq = 0;
            boolean tieConflict = false;
            for (BlockPos src : sources) {
                var ss = level.getBlockState(src);
                int amp = ss.getValue(AmethystResonatorBlock.AMPLITUDE);
                Integer dist = resonanceDistance.get(src).get(p);
                if(dist==null) continue;
                int arriving = Math.max(0, amp - dist / 4);
                if (arriving > bestAmp) {
                    bestAmp = arriving;
                    bestFreq = ss.getValue(AmethystResonatorBlock.FREQUENCY);
                    tieConflict = false;
                } else if (arriving > 0 && arriving == bestAmp && bestFreq != ss.getValue(AmethystResonatorBlock.FREQUENCY)) {
                    tieConflict = true;
                }
            }
            boolean active = bestAmp > 0 && !tieConflict;
            AmethystResonanceDustBlock.setResonance(level, p, active ? bestFreq : 0, active ? bestAmp : 0);
        }
    }

    public static AmethystSample sampleAmethyst(Level level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (s.getBlock() instanceof AmethystResonanceDustBlock) return new AmethystSample(AmethystResonanceDustBlock.active(level,pos), AmethystResonanceDustBlock.frequency(level,pos), AmethystResonanceDustBlock.amplitude(level,pos));
        if (s.getBlock() instanceof AmethystResonatorBlock) return new AmethystSample(AmethystResonatorBlock.isActive(level, pos), s.getValue(AmethystResonatorBlock.FREQUENCY), s.getValue(AmethystResonatorBlock.AMPLITUDE));
        return new AmethystSample(false, 0, 0);
    }

    public static void driveAmethyst(ServerLevel level, BlockPos start, boolean active, int frequency, int amplitude) {
        Set<BlockPos> nodes = collectHorizontalEdges(level,start,"amethyst",p -> level.getBlockState(p).getBlock() instanceof AmethystResonanceDustBlock,(a,b,d)->surfaceEdgeAllowed(level,a,b,d,AmethystResonanceDustBlock.class));
        if (nodes.isEmpty()) return;
        Map<BlockPos,Integer> distance = distancesHorizontalTrace(level,nodes,start,AmethystResonanceDustBlock.class);
        for (BlockPos p : nodes) {
            var s = level.getBlockState(p);
            int amp = active ? Math.max(0, amplitude - distance.getOrDefault(p, 0) / 4) : 0;
            AmethystResonanceDustBlock.setResonance(level, p, amp > 0 ? EngineeringMath.clamp(frequency,1,15) : 0, EngineeringMath.clamp(amp,0,15));
        }
    }

    // ---------------- Optical: fiber/light domain ----------------
    public static void recomputeOptical(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collectOptical(level, start, p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof OpticalFiberBlock || b instanceof OpticalFiberJunctionBlock || b instanceof OpticalEmitterBlock || b instanceof OpticalReceiverBlock;
        });
        if (nodes.isEmpty()) return;

        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "optical", nodes));
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : nodes) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof OpticalEmitterBlock && seen.add(p) && state.getValue(OpticalEmitterBlock.INTENSITY) > 0) {
                claims.add(new DomainDriverRegistry.Claim(p,p,state.getValue(OpticalEmitterBlock.INTENSITY),state.getValue(OpticalEmitterBlock.CHANNEL),0,state.getBlock().getClass().getName()));
            }
        }
        NetworkKernel.recordDriverState(level, "optical", claims.size());
        boolean valid = claims.size() == 1;
        DomainDriverRegistry.Claim claim = valid ? claims.get(0) : null;
        int intensity = valid ? claim.a() : 0;
        int channel = valid ? claim.b() : 0;
        BlockPos source = valid ? claim.outputStart() : start;
        Map<BlockPos,Integer> distance = distancesOptical(level,nodes,source);
        for (BlockPos p : nodes) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof OpticalFiberBlock) && !(state.getBlock() instanceof OpticalFiberJunctionBlock) && !(state.getBlock() instanceof OpticalReceiverBlock)) continue;
            int arriving = valid ? Math.max(0,intensity - distance.getOrDefault(p,0)/16) : 0;
            boolean ok = valid && arriving > 0;
            if (state.getBlock() instanceof OpticalFiberBlock) OpticalFiberBlock.setOptical(level,p,arriving,channel,ok);
            else if (state.getBlock() instanceof OpticalFiberJunctionBlock) OpticalFiberJunctionBlock.setOptical(level,p,arriving,channel,ok);
            else OpticalReceiverBlock.setOptical(level,p,arriving,channel,ok);
        }
    }

    public static OpticalSample sampleOptical(Level level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (s.getBlock() instanceof OpticalFiberBlock) return new OpticalSample(OpticalFiberBlock.intensity(level,pos), OpticalFiberBlock.channel(level,pos), OpticalFiberBlock.valid(level,pos));
        if (s.getBlock() instanceof OpticalFiberJunctionBlock) return new OpticalSample(OpticalFiberJunctionBlock.intensity(level,pos), OpticalFiberJunctionBlock.channel(level,pos), OpticalFiberJunctionBlock.valid(level,pos));
        if (s.getBlock() instanceof OpticalReceiverBlock) return new OpticalSample(OpticalReceiverBlock.intensity(level,pos), OpticalReceiverBlock.channel(level,pos), OpticalReceiverBlock.valid(level,pos));
        if (s.getBlock() instanceof OpticalEmitterBlock) return new OpticalSample(s.getValue(OpticalEmitterBlock.INTENSITY), s.getValue(OpticalEmitterBlock.CHANNEL), true);
        return new OpticalSample(0,0,false);
    }

    public static void driveOptical(ServerLevel level, BlockPos start, BlockPos driverPos, int intensity, int channel, boolean valid) {
        if (valid && intensity > 0) DomainDriverRegistry.claim(level, "optical", driverPos, start, EngineeringMath.clamp(intensity,0,15), EngineeringMath.clamp(channel,0,15), 0);
        else DomainDriverRegistry.release(level, "optical", driverPos, start);
        Set<BlockPos> nodes = collectOptical(level, start, p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof OpticalFiberBlock || b instanceof OpticalFiberJunctionBlock || b instanceof OpticalReceiverBlock;
        });
        if (nodes.isEmpty()) return;
        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "optical", nodes));
        addRawOpticalClaims(level, nodes, claims);
        NetworkKernel.recordDriverState(level, "optical", claims.size());
        boolean one = claims.size() == 1;
        DomainDriverRegistry.Claim claim = one ? claims.get(0) : null;
        int baseIntensity = one ? claim.a() : 0;
        int resolvedChannel = one ? claim.b() : 0;
        BlockPos sourceStart = one ? claim.outputStart() : start;
        Map<BlockPos,Integer> distance = distancesOptical(level, nodes, sourceStart);
        for (BlockPos p : nodes) {
            var state = level.getBlockState(p);
            int arriving = one ? Math.max(0, baseIntensity - distance.getOrDefault(p, 0) / 16) : 0;
            boolean ok = one && arriving > 0;
            if (state.getBlock() instanceof OpticalFiberBlock) OpticalFiberBlock.setOptical(level, p, arriving, resolvedChannel, ok);
            else if (state.getBlock() instanceof OpticalFiberJunctionBlock) OpticalFiberJunctionBlock.setOptical(level, p, arriving, resolvedChannel, ok);
            else if (state.getBlock() instanceof OpticalReceiverBlock) OpticalReceiverBlock.setOptical(level, p, arriving, resolvedChannel, ok);
        }
    }

    public static void driveOptical(ServerLevel level, BlockPos start, int intensity, int channel, boolean valid) {
        Set<BlockPos> nodes = collectOptical(level, start, p -> { var b=level.getBlockState(p).getBlock(); return b instanceof OpticalFiberBlock || b instanceof OpticalFiberJunctionBlock || b instanceof OpticalReceiverBlock; });
        Map<BlockPos,Integer> distance=distancesOptical(level,nodes,start);
        for(BlockPos p:nodes){var state=level.getBlockState(p);int arriving=valid?Math.max(0,intensity-distance.getOrDefault(p,0)/16):0;boolean ok=valid&&arriving>0;if(state.getBlock() instanceof OpticalFiberBlock)OpticalFiberBlock.setOptical(level,p,arriving,EngineeringMath.clamp(channel,0,15),ok);else if(state.getBlock() instanceof OpticalFiberJunctionBlock)OpticalFiberJunctionBlock.setOptical(level,p,arriving,EngineeringMath.clamp(channel,0,15),ok);else if(state.getBlock() instanceof OpticalReceiverBlock)OpticalReceiverBlock.setOptical(level,p,arriving,EngineeringMath.clamp(channel,0,15),ok);}
    }

    // ---------------- Copper: simplified macroscopic electrical domain ----------------
    public static void recomputeCopper(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collectCopper(level, start, p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof CopperWireBlock || b instanceof CopperCableJunctionBlock || b instanceof CopperVoltageSourceBlock || b instanceof CopperResistiveLoadBlock || b instanceof ElectromagnetBlock || b instanceof ThermalHeaterBlock;
        });
        if (nodes.isEmpty()) return;

        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "copper", nodes));
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : nodes) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof CopperVoltageSourceBlock && seen.add(p) && state.getValue(CopperVoltageSourceBlock.VOLTAGE) > 0) {
                claims.add(new DomainDriverRegistry.Claim(p,p,state.getValue(CopperVoltageSourceBlock.VOLTAGE),0,0,state.getBlock().getClass().getName()));
            }
        }
        NetworkKernel.recordDriverState(level, "copper", claims.size());
        Map<BlockPos,Integer> best = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (claims.size() == 1) {
            DomainDriverRegistry.Claim claim = claims.get(0);
            BlockPos source = claim.outputStart();
            if (nodes.contains(source)) {
                best.put(source, EngineeringMath.clamp(claim.a(),0,15)*8);
                queue.add(source);
            }
        }
        spreadScore(level,nodes,best,queue,1);
        applyCopper(level,nodes,best);
    }

    public static int sampleCopperVoltage(Level level, BlockPos pos) {
        return sampleCopperVoltage(level, pos, null);
    }

    /**
     * Sample a physical copper node. When observerPos is supplied, directional
     * processors expose their input or output voltage only on the corresponding
     * physical face; probing a side face is invalid/zero rather than silently
     * returning an unrelated network value.
     */
    public static int sampleCopperVoltage(Level level, BlockPos pos, BlockPos observerPos) {
        var s = level.getBlockState(pos);
        if (s.getBlock() instanceof CopperWireBlock) return CopperWireBlock.voltage(level,pos);
        if (s.getBlock() instanceof CopperCableJunctionBlock) return CopperCableJunctionBlock.voltage(level,pos);
        if (s.getBlock() instanceof CopperVoltageSourceBlock) return s.getValue(CopperVoltageSourceBlock.VOLTAGE);
        if (s.getBlock() instanceof CopperResistiveLoadBlock) return s.getValue(CopperResistiveLoadBlock.VOLTAGE);

        if (observerPos != null && s.hasProperty(DirectionalDomainBlock.FACING)) {
            Direction facing = s.getValue(DirectionalDomainBlock.FACING);
            BlockPos output = pos.relative(facing);
            BlockPos input = pos.relative(facing.getOpposite());
            if (observerPos.equals(output)) {
                if (s.getBlock() instanceof CopperSeriesResistorBlock) return CopperSeriesResistorBlock.outputVoltage(level,pos);
                if (s.getBlock() instanceof CopperCapacitorBlock) return CopperCapacitorBlock.outputVoltage(level,pos);
                if (s.getBlock() instanceof CopperFuseBlock) return CopperFuseBlock.outputVoltage(level,pos);
                if (s.getBlock() instanceof InductionCoilBlock) return InductionCoilBlock.outputVoltage(level,pos);
            }
            if (observerPos.equals(input)) {
                // The processor input node is the adjacent copper medium, not an
                // invented value stored on the processor body itself.
                return sampleCopperVoltage(level, input);
            }
        }
        return 0;
    }

    public static void driveCopper(ServerLevel level, BlockPos start, BlockPos driverPos, int voltage) {
        if (voltage > 0) DomainDriverRegistry.claim(level, "copper", driverPos, start, EngineeringMath.clamp(voltage,0,15), 0, 0);
        else DomainDriverRegistry.release(level, "copper", driverPos, start);
        Set<BlockPos> nodes = collectCopper(level, start, p -> {
            var b = level.getBlockState(p).getBlock();
            return b instanceof CopperWireBlock || b instanceof CopperCableJunctionBlock || b instanceof CopperResistiveLoadBlock || b instanceof ElectromagnetBlock || b instanceof ThermalHeaterBlock;
        });
        if (nodes.isEmpty()) return;
        List<DomainDriverRegistry.Claim> claims = new ArrayList<>(DomainDriverRegistry.activeClaims(level, "copper", nodes));
        addRawCopperClaims(level, nodes, claims);
        NetworkKernel.recordDriverState(level, "copper", claims.size());
        Map<BlockPos,Integer> best = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        if (claims.size() == 1) {
            DomainDriverRegistry.Claim claim = claims.get(0);
            if (nodes.contains(claim.outputStart())) { best.put(claim.outputStart(), EngineeringMath.clamp(claim.a(),0,15)*8); q.add(claim.outputStart()); }
        }
        spreadScore(level,nodes,best,q,1);
        applyCopper(level,nodes,best);
    }

    public static void driveCopper(ServerLevel level, BlockPos start, int voltage) {
        Set<BlockPos> nodes = collectCopper(level, start, p -> { var b=level.getBlockState(p).getBlock(); return b instanceof CopperWireBlock || b instanceof CopperCableJunctionBlock || b instanceof CopperResistiveLoadBlock || b instanceof ElectromagnetBlock || b instanceof ThermalHeaterBlock; });
        if(nodes.isEmpty())return;Map<BlockPos,Integer>best=new HashMap<>();ArrayDeque<BlockPos>q=new ArrayDeque<>();if(nodes.contains(start)){best.put(start,EngineeringMath.clamp(voltage,0,15)*8);q.add(start);}spreadScore(level,nodes,best,q,1);applyCopper(level,nodes,best);
    }

    private static void spreadScore(ServerLevel level, Set<BlockPos> nodes, Map<BlockPos,Integer> best, ArrayDeque<BlockPos> q, int decrement) {
        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            int score = best.getOrDefault(p, 0);
            if (score <= 0) continue;
            var currentBlock = level.getBlockState(p).getBlock();
            // Loads consume voltage but are not transparent conductors.
            if (currentBlock instanceof CopperResistiveLoadBlock
                    || currentBlock instanceof ElectromagnetBlock
                    || currentBlock instanceof ThermalHeaterBlock) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!nodes.contains(n) || !copperEdgeAllowed(level,p,n,d)) continue;
                int ns = score - decrement;
                if (ns > best.getOrDefault(n, -1)) { best.put(n, ns); q.addLast(n); }
            }
        }
    }

    private static void applyCopper(ServerLevel level, Set<BlockPos> nodes, Map<BlockPos,Integer> best) {
        for (BlockPos p : nodes) {
            var s = level.getBlockState(p);
            int score = best.getOrDefault(p, 0);
            int voltage = score <= 0 ? 0 : Math.min(15, (score + 7) / 8);
            if (s.getBlock() instanceof CopperWireBlock)
                CopperWireBlock.setVoltage(level,p,voltage);
            else if (s.getBlock() instanceof CopperCableJunctionBlock)
                CopperCableJunctionBlock.setVoltage(level,p,voltage);
            else if (s.getBlock() instanceof CopperResistiveLoadBlock && s.getValue(CopperResistiveLoadBlock.VOLTAGE) != voltage)
                level.setBlock(p, s.setValue(CopperResistiveLoadBlock.VOLTAGE, voltage), Block.UPDATE_CLIENTS);
            if (s.getBlock() instanceof ElectromagnetBlock magnet) level.scheduleTick(p, magnet, 1);
        }
    }

    private static boolean opticalEdgeAllowed(ServerLevel level, BlockPos a, BlockPos b, Direction d) {
        BlockState sa=level.getBlockState(a), sb=level.getBlockState(b);
        if(!(sa.getBlock() instanceof ConnectedCableBlock) && !(sb.getBlock() instanceof ConnectedCableBlock)) return false;
        if(sa.getBlock() instanceof ConnectedCableBlock ca && (!ca.topologyValid(sa) || !ConnectedCableBlock.connected(sa,d))) return false;
        if(sb.getBlock() instanceof ConnectedCableBlock cb && (!cb.topologyValid(sb) || !ConnectedCableBlock.connected(sb,d.getOpposite()))) return false;
        return true;
    }
    private static boolean copperEdgeAllowed(ServerLevel level, BlockPos a, BlockPos b, Direction d) {
        BlockState sa=level.getBlockState(a), sb=level.getBlockState(b);
        if(sa.getBlock() instanceof ConnectedCableBlock ca && (!ca.topologyValid(sa) || !ConnectedCableBlock.connected(sa,d))) return false;
        if(sb.getBlock() instanceof ConnectedCableBlock cb && (!cb.topologyValid(sb) || !ConnectedCableBlock.connected(sb,d.getOpposite()))) return false;
        return true;
    }
    private static Set<BlockPos> collectOptical(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockPos> allowed) {
        return collectEdges(level,start,"optical",allowed,(a,b,d)->opticalEdgeAllowed(level,a,b,d), p -> {
            var block = level.getBlockState(p).getBlock();
            return block instanceof OpticalReceiverBlock || block instanceof OpticalEmitterBlock;
        });
    }
    private static Set<BlockPos> collectCopper(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockPos> allowed) {
        return collectEdges(level,start,"copper",allowed,(a,b,d)->copperEdgeAllowed(level,a,b,d), p -> {
            var block = level.getBlockState(p).getBlock();
            return block instanceof CopperResistiveLoadBlock || block instanceof ElectromagnetBlock
                    || block instanceof ThermalHeaterBlock || block instanceof CopperVoltageSourceBlock;
        });
    }
    private interface EdgeRule { boolean ok(BlockPos a, BlockPos b, Direction d); }
    private static Set<BlockPos> collectEdges(ServerLevel level, BlockPos start, String domain, java.util.function.Predicate<BlockPos> allowed, EdgeRule rule) {
        return collectEdges(level,start,domain,allowed,rule,p -> false);
    }
    private static Set<BlockPos> collectEdges(ServerLevel level, BlockPos start, String domain, java.util.function.Predicate<BlockPos> allowed, EdgeRule rule, java.util.function.Predicate<BlockPos> terminal) {
        Set<BlockPos> visited=new LinkedHashSet<>();ArrayDeque<BlockPos> q=new ArrayDeque<>();
        if(level.hasChunkAt(start)&&allowed.test(start))q.add(start);else for(Direction d:Direction.values()){BlockPos n=start.relative(d);if(level.hasChunkAt(n)&&allowed.test(n))q.add(n);}
        while(!q.isEmpty()&&visited.size()<MAX_NODES){
            BlockPos p=q.removeFirst();
            if(!visited.add(p))continue;
            // Loads/receivers are endpoints. The explicit start may expand so a
            // source placed into the world can seed its adjacent conductor(s).
            if(!p.equals(start)&&terminal.test(p))continue;
            for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(!visited.contains(n)&&level.hasChunkAt(n)&&allowed.test(n)&&rule.ok(p,n,d))q.addLast(n);}
        }
        NetworkKernel.recordScan(level,domain,visited.size(),!q.isEmpty());
        return visited;
    }
    private static Map<BlockPos,Integer> distancesOptical(ServerLevel level, Set<BlockPos> nodes, BlockPos start) {
        Map<BlockPos,Integer> dist = new HashMap<>();
        if (!nodes.contains(start)) return dist;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start, 0);
        q.add(start);
        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            int d0 = dist.get(p);
            var block = level.getBlockState(p).getBlock();
            // A receiver terminates an optical path. An emitter is only allowed
            // to fan out when it is the source of this path, never as a relay.
            if (p != start && (block instanceof OpticalReceiverBlock || block instanceof OpticalEmitterBlock)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (nodes.contains(n) && !dist.containsKey(n) && opticalEdgeAllowed(level,p,n,dir)) {
                    dist.put(n, d0 + 1);
                    q.addLast(n);
                }
            }
        }
        return dist;
    }

    private static Map<BlockPos,Integer> distancesHorizontalTrace(ServerLevel level,Set<BlockPos> nodes,BlockPos start,Class<?> mediumClass){Map<BlockPos,Integer>d=new HashMap<>();if(!nodes.contains(start))return d;ArrayDeque<BlockPos>q=new ArrayDeque<>();d.put(start,0);q.add(start);while(!q.isEmpty()){BlockPos p=q.removeFirst();int d0=d.get(p);for(Direction dir:Direction.Plane.HORIZONTAL){BlockPos n=p.relative(dir);if(nodes.contains(n)&&!d.containsKey(n)&&surfaceEdgeAllowed(level,p,n,dir,mediumClass)){d.put(n,d0+1);q.addLast(n);}}}return d;}

    private static Map<BlockPos,Integer> distancesHorizontal(Set<BlockPos> nodes,BlockPos start){Map<BlockPos,Integer>d=new HashMap<>();if(!nodes.contains(start))return d;ArrayDeque<BlockPos>q=new ArrayDeque<>();d.put(start,0);q.add(start);while(!q.isEmpty()){BlockPos p=q.removeFirst();int d0=d.get(p);for(Direction dir:Direction.Plane.HORIZONTAL){BlockPos n=p.relative(dir);if(nodes.contains(n)&&!d.containsKey(n)){d.put(n,d0+1);q.addLast(n);}}}return d;}

    private static void addRawLapisClaims(ServerLevel level, Set<BlockPos> nodes, List<DomainDriverRegistry.Claim> claims) {
        Set<BlockPos> seen=new HashSet<>();
        for(BlockPos p:nodes) for(Direction d:Direction.Plane.HORIZONTAL){BlockPos n=p.relative(d);if(!level.hasChunkAt(n)||!seen.add(n))continue;BlockState s=level.getBlockState(n);if(s.getBlock() instanceof LapisPrecisionSourceBlock)claims.add(new DomainDriverRegistry.Claim(n,p,s.getValue(LapisPrecisionSourceBlock.VALUE),0,0,s.getBlock().getClass().getName()));else if(s.getBlock() instanceof LapisNoiseSourceBlock)claims.add(new DomainDriverRegistry.Claim(n,p,LapisNoiseSourceBlock.currentValue(level,n,s),0,0,s.getBlock().getClass().getName()));}
    }
    private static void addRawQuartzClaims(ServerLevel level, Set<BlockPos> nodes, List<DomainDriverRegistry.Claim> claims) {
        Set<BlockPos> seen=new HashSet<>();
        for(BlockPos p:nodes) for(Direction d:Direction.Plane.HORIZONTAL){BlockPos n=p.relative(d);if(!level.hasChunkAt(n)||!seen.add(n))continue;BlockState s=level.getBlockState(n);if(s.getBlock() instanceof QuartzOscillatorBlock)claims.add(new DomainDriverRegistry.Claim(n,p,s.getValue(QuartzOscillatorBlock.ACTIVE)?1:0,QuartzTimingLineBlock.periodTicks(s.getValue(QuartzOscillatorBlock.PERIOD_INDEX)),0,s.getBlock().getClass().getName()));else if(s.getBlock() instanceof QuartzLabOscillatorBlock)claims.add(new DomainDriverRegistry.Claim(n,p,s.getValue(QuartzLabOscillatorBlock.ACTIVE)?1:0,QuartzTimingLineBlock.periodTicks(s.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX)),0,s.getBlock().getClass().getName()));}
    }
    private static void addRawOpticalClaims(ServerLevel level, Set<BlockPos> nodes, List<DomainDriverRegistry.Claim> claims) {
        Set<BlockPos> seen=new HashSet<>();
        for(BlockPos p:nodes) for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(!level.hasChunkAt(n)||!seen.add(n))continue;BlockState s=level.getBlockState(n);if(s.getBlock() instanceof OpticalEmitterBlock && opticalEdgeAllowed(level,p,n,d))claims.add(new DomainDriverRegistry.Claim(n,p,s.getValue(OpticalEmitterBlock.INTENSITY),s.getValue(OpticalEmitterBlock.CHANNEL),0,s.getBlock().getClass().getName()));}
    }
    private static void addRawCopperClaims(ServerLevel level, Set<BlockPos> nodes, List<DomainDriverRegistry.Claim> claims) {
        Set<BlockPos> seen=new HashSet<>();
        for(BlockPos p:nodes) for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(!level.hasChunkAt(n)||!seen.add(n))continue;BlockState s=level.getBlockState(n);if(s.getBlock() instanceof CopperVoltageSourceBlock && copperEdgeAllowed(level,p,n,d))claims.add(new DomainDriverRegistry.Claim(n,p,s.getValue(CopperVoltageSourceBlock.VOLTAGE),0,0,s.getBlock().getClass().getName()));}
    }

    // ---------------- Graph helpers ----------------
    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX()-b.getX()) + Math.abs(a.getY()-b.getY()) + Math.abs(a.getZ()-b.getZ());
    }

    private static Map<BlockPos,Integer> distances(Set<BlockPos> nodes, BlockPos start) {
        Map<BlockPos,Integer> dist = new HashMap<>();
        if (!nodes.contains(start)) return dist;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start,0); q.add(start);
        while(!q.isEmpty()) {
            BlockPos p=q.removeFirst(); int d0=dist.get(p);
            for(Direction d:Direction.values()) {
                BlockPos n=p.relative(d);
                if(nodes.contains(n)&&!dist.containsKey(n)){dist.put(n,d0+1);q.addLast(n);}
            }
        }
        return dist;
    }

    private static boolean surfaceEdgeAllowed(ServerLevel level,BlockPos a,BlockPos b,Direction d,Class<?> mediumClass){
        if (d.getAxis() == Direction.Axis.Y) return false;
        BlockState sa=level.getBlockState(a),sb=level.getBlockState(b);
        boolean am=mediumClass.isInstance(sa.getBlock()),bm=mediumClass.isInstance(sb.getBlock());
        // Devices do not directly wire to one another; a physical trace must participate.
        if(!am&&!bm)return false;
        if(am && !SurfaceTraceBlock.connected(sa,d))return false;
        if(bm && !SurfaceTraceBlock.connected(sb,d.getOpposite()))return false;
        return true;
    }
    private static Set<BlockPos> collectHorizontalEdges(ServerLevel level,BlockPos start,String domain,java.util.function.Predicate<BlockPos> allowed,EdgeRule rule){
        Set<BlockPos> visited=new LinkedHashSet<>();ArrayDeque<BlockPos> q=new ArrayDeque<>();
        if(level.hasChunkAt(start)&&allowed.test(start))q.add(start);else for(Direction d:Direction.Plane.HORIZONTAL){BlockPos n=start.relative(d);if(level.hasChunkAt(n)&&allowed.test(n))q.add(n);}
        while(!q.isEmpty()&&visited.size()<MAX_NODES){BlockPos p=q.removeFirst();if(!visited.add(p))continue;for(Direction d:Direction.Plane.HORIZONTAL){BlockPos n=p.relative(d);if(!visited.contains(n)&&level.hasChunkAt(n)&&allowed.test(n)&&rule.ok(p,n,d))q.addLast(n);}}
        NetworkKernel.recordScan(level,domain,visited.size(),!q.isEmpty());
        return visited;
    }

    private static Set<BlockPos> collectHorizontal(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockPos> allowed) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        if (level.hasChunkAt(start) && allowed.test(start)) q.add(start);
        else for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = start.relative(d);
            if (level.hasChunkAt(n) && allowed.test(n)) q.add(n);
        }
        while (!q.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos p = q.removeFirst();
            if (!visited.add(p)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (!visited.contains(n) && level.hasChunkAt(n) && allowed.test(n)) q.addLast(n);
            }
        }
        return visited;
    }

    private static Set<BlockPos> collect(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockPos> allowed) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        if (level.hasChunkAt(start) && allowed.test(start)) q.add(start);
        else for (Direction d : Direction.values()) {
            BlockPos n = start.relative(d);
            if (level.hasChunkAt(n) && allowed.test(n)) q.add(n);
        }
        while (!q.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos p = q.removeFirst();
            if (!visited.add(p)) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!visited.contains(n) && level.hasChunkAt(n) && allowed.test(n)) q.addLast(n);
            }
        }
        return visited;
    }
}
