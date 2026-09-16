package dev.redstoneengineering.validation;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Registers the local/manual RSE Validation Factory and self-test command surface. */
@Mod(RedstoneEngineering.MOD_ID)
public final class RseValidationFactoryModule {
    public RseValidationFactoryModule(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RseValidationFactoryModule::registerCommands);
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
