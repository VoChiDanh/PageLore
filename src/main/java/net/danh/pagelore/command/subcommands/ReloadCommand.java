package net.danh.pagelore.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;

/**
 * Handles the reload subcommand.
 */
public class ReloadCommand {

    /**
     * Builds the reload subcommand.
     *
     * @param plugin The PageLore instance.
     * @return The LiteralArgumentBuilder for the reload command.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> build(PageLore plugin) {
        return Commands.literal("reload").executes(context -> {
            plugin.getSettings().reload();
            plugin.getMessages().reload();
            plugin.loadCache();
            plugin.startTask();

            context.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessages().getString("reload-success")));

            return Command.SINGLE_SUCCESS;
        });
    }
}