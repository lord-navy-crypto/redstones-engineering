package dev.redstoneengineering.validation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Registers the local/manual RSE validation fixtures, integrated plant, and Mega factory. */
@Mod(RedstoneEngineering.MOD_ID)
public final class RseValidationFactoryModule {
    public RseValidationFactoryModule(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RseValidationFactoryModule::registerCommands);
        NeoForge.EVENT_BUS.addListener(RseValidationFactoryModule::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        RseValidationSelfTestService.tickAll(event.getServer());
        RseValidationPlantService.tick(event.getServer());
        RseMegaValidationService.tick(event.getServer());
        RseIntegratedDemoService.tick(event.getServer());
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("rsevalidation")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("build")
                                .executes(context -> {
                                    var source = context.getSource();
                                    BlockPos origin = BlockPos.containing(source.getPosition());
                                    return send(source, RseValidationFactoryService.build(source.getLevel(), origin));
                                }))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            BlockPos origin = BlockPos.containing(source.getPosition());
                                            return send(source, RseValidationFactoryService.reset(source.getLevel(), origin, "all"));
                                        }))
                                .then(Commands.argument("station", StringArgumentType.word())
                                        .executes(context -> {
                                            var source = context.getSource();
                                            BlockPos origin = BlockPos.containing(source.getPosition());
                                            String station = StringArgumentType.getString(context, "station");
                                            return send(source, RseValidationFactoryService.reset(source.getLevel(), origin, station));
                                        })))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    var source = context.getSource();
                                    return send(source, RseValidationFactoryService.status(source.getLevel()));
                                }))
                        .then(Commands.literal("selftest")
                                .then(Commands.literal("list")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseValidationSelfTestService.list(source.getLevel()));
                                        }))
                                .then(Commands.literal("place-all")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            BlockPos origin = BlockPos.containing(source.getPosition());
                                            return send(source, RseValidationSelfTestYardService.placeAll(source.getLevel(), origin));
                                        }))
                                .then(Commands.literal("place")
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    BlockPos origin = BlockPos.containing(source.getPosition());
                                                    String id = StringArgumentType.getString(context, "id");
                                                    return send(source, RseValidationSelfTestService.place(source.getLevel(), origin, id));
                                                })))
                                .then(Commands.literal("check")
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    String id = StringArgumentType.getString(context, "id");
                                                    return send(source, RseValidationSelfTestService.check(source.getLevel(), id));
                                                }))))
                        .then(Commands.literal("demo")
                                .then(Commands.literal("place")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            var player = source.getPlayerOrException();
                                            BlockPos origin = BlockPos.containing(source.getPosition()).offset(2, 0, 6);
                                            return send(source, RseIntegratedDemoService.place(player, origin));
                                        }))
                                .then(Commands.literal("status")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseIntegratedDemoService.status(source.getPlayerOrException()));
                                        }))
                                .then(Commands.literal("stage")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 10))
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    int number = IntegerArgumentType.getInteger(context, "number");
                                                    return send(source, RseIntegratedDemoService.stage(source.getPlayerOrException(), number));
                                                })))
                                .then(Commands.literal("nodes")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseIntegratedDemoService.nodes(source.getPlayerOrException()));
                                        }))
                                .then(Commands.literal("node")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    String id = StringArgumentType.getString(context, "id");
                                                    return send(source, RseIntegratedDemoService.node(source.getPlayerOrException(), id));
                                                })))
                                .then(Commands.literal("setpoint")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    int value = IntegerArgumentType.getInteger(context, "value");
                                                    return send(source, RseIntegratedDemoService.setpoint(source.getPlayerOrException(), value));
                                                })))
                                .then(Commands.literal("load")
                                        .then(Commands.argument("profile", IntegerArgumentType.integer(0, 3))
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    int profile = IntegerArgumentType.getInteger(context, "profile");
                                                    return send(source, RseIntegratedDemoService.load(source.getPlayerOrException(), profile));
                                                })))
                                .then(Commands.literal("excite")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseIntegratedDemoService.excite(source.getPlayerOrException()));
                                        }))
                                .then(Commands.literal("retest")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseIntegratedDemoService.retest(source.getPlayerOrException()));
                                        })))
                        .then(Commands.literal("plant")
                                .then(Commands.literal("place")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            BlockPos origin = BlockPos.containing(source.getPosition());
                                            return send(source, RseValidationPlantService.place(source.getLevel(), origin));
                                        }))
                                .then(Commands.literal("status")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseValidationPlantService.status(source.getLevel()));
                                        }))
                                .then(Commands.literal("retest")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseValidationPlantService.retest(source.getLevel()));
                                        })))
                        .then(Commands.literal("mega")
                                .then(Commands.literal("place")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            BlockPos origin = BlockPos.containing(source.getPosition());
                                            return send(source, RseMegaValidationService.place(source.getLevel(), origin));
                                        }))
                                .then(Commands.literal("status")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseMegaValidationService.status(source.getLevel()));
                                        }))
                                .then(Commands.literal("diagnose")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            // Reporter owns attribution/export, then delegates live evaluation to
                                            // RseMegaValidationService.diagnose(level) so the runtime remains authoritative.
                                            return send(source, RseMegaDiagnosticReporter.diagnose(source.getLevel()));
                                        }))
                                .then(Commands.literal("station")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 40))
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    int number = IntegerArgumentType.getInteger(context, "number");
                                                    return send(source, RseMegaValidationService.station(source.getLevel(), number));
                                                })))
                                .then(Commands.literal("cell")
                                        .then(Commands.argument("cell", StringArgumentType.word())
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    String cell = StringArgumentType.getString(context, "cell");
                                                    return send(source, RseMegaValidationService.cell(source.getLevel(), cell));
                                                })))
                                .then(Commands.literal("report")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseMegaValidationService.report(source.getLevel()));
                                        }))
                                .then(Commands.literal("retest")
                                        .executes(context -> {
                                            var source = context.getSource();
                                            return send(source, RseMegaValidationService.retest(source.getLevel()));
                                        })))
        );
    }

    private static int send(net.minecraft.commands.CommandSourceStack source, RseValidationFactoryService.Result result) {
        if (result.lines().isEmpty()) {
            if (result.success()) source.sendSuccess(() -> Component.literal("Validation command completed."), false);
            else source.sendFailure(Component.literal("Validation command failed."));
            return result.success() ? 1 : 0;
        }
        for (Component line : result.lines()) {
            if (result.success()) source.sendSuccess(() -> line, false);
            else source.sendFailure(line);
        }
        return result.success() ? 1 : 0;
    }
}
