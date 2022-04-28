package org.minecraft.MCUtil.discordConn;

import java.util.Collections;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import lombok.Getter;

public class DiscordConnectionMaker {
	@Getter
	private static JDA jda;

	public static void connect(String token) throws Exception {
		jda = JDABuilder.createLight(token, Collections.emptyList()).addEventListeners(new DiscordCommandListner())
				.setActivity(Activity.playing("")).build();
		jda.upsertCommand("help", "Shows you my commands").queue();
	}
}
