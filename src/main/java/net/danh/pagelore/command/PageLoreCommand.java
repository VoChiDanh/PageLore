package net.danh.pagelore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.danh.pagelore.PageLore;
import net.danh.pagelore.command.subcommands.ReloadCommand;
import net.danh.pagelore.utils.ColorUtils;

public class PageLoreCommand {
    private final PageLore plugin;

    public PageLoreCommand(PageLore plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("pagelore")
                .requires(source -> {
                    if (!source.getSender().hasPermission(plugin.adminPermission)) {
                        source.getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessages().getString("no-permission")));
                        return false;
                    }
                    return true;
                })
                .executes(context -> {
                    String usageMsg = plugin.getMessages().getString("usage", "<yellow>Usage: /pagelore reload");
                    context.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(usageMsg));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ReloadCommand.build(plugin))
                .build();
    }
}