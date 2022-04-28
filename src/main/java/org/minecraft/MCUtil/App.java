package org.minecraft.MCUtil;

import org.minecraft.MCUtil.minecraftConn.MinecraftConnectionManager;

public class App {
	public static void main(String[] args) {
		MinecraftConnectionManager mc = new MinecraftConnectionManager("MCUtil", "play.kaboom.pw", 25565);
		mc.login();
	}
}
