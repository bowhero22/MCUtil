package org.minecraft.MCUtil.discordConn;

public class DiscordMinecraftChatLogger {
	public static void log(String message) {
		DiscordConnectionMaker.getJda().getTextChannelById("969099361910927371").sendMessage(message).queue();
	}
}
