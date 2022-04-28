package org.minecraft.MCUtil.minecraftConn;

import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minecraft.MCUtil.util.ChatUtil;

import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.entity.EntityEvent;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.packetlib.ProxyInfo;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;

import io.netty.util.concurrent.FastThreadLocal;
import net.kyori.adventure.text.Component;

public class MinecraftConnectionManager {
	public static final ArrayList<MinecraftConnectionManager> BOTLIST = new ArrayList<>();

	private String HOST = "";
	private int PORT = 25565;
	private String USERNAME;
	private final ProxyInfo PROXY = null;
	private final Proxy AUTH_PROXY = Proxy.NO_PROXY;
	private Session client;
	private int entityId = -1;

	private ScheduledExecutorService relogExecutor = Executors.newScheduledThreadPool(1);

	public static final Pattern mutePattern = Pattern.compile("You have been muted for (.*)\\.( Reason: .*)?");
	public static final Pattern usernamePattern = Pattern.compile("Successfully set your username to \"(.*)\"");

	public MinecraftConnectionManager(String username, String host, int port) {
		this.USERNAME = username;
		this.HOST = host;
		this.PORT = port;
		synchronized (BOTLIST) {
			BOTLIST.add(this);
		}
	}

	public void login() {
		MinecraftProtocol protocol;
		protocol = new MinecraftProtocol(USERNAME);
		SessionService sessionService = new SessionService();
		sessionService.setProxy(AUTH_PROXY);

		client = new TcpClientSession(HOST, PORT, protocol, PROXY);
		client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
		Thread listnerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				client.addListener(new SessionAdapter() {
					@Override
					public void packetReceived(Session session, Packet packet) {
						if (packet instanceof ClientboundLoginPacket) {
							session.send(new ServerboundChatPacket("LoginTest"));
						} else if (packet instanceof ClientboundChatPacket) {
							Component message = ((ClientboundChatPacket) packet).getMessage();
							System.out.println(ChatUtil.getFullText(message));
							String jsonMessage = gson().serialize(message);
							String strMessage = ChatUtil.getFullText(message);
							String sanitizedName = USERNAME.replaceAll("§[0-9a-frlonmk]", "");
							Matcher matcher;
							if (strMessage.startsWith("You have been muted!")) {
								sendMessage("/mute " + USERNAME + " 0s");
							} else if (strMessage.startsWith("Your voice has been silenced!")
									|| strMessage.startsWith("Your voice has been silenced for ")) {
								sendMessage("/mute " + USERNAME + " 0s");
							} else if ((matcher = mutePattern.matcher(strMessage)).matches()) {
								if (matcher.group(1).equals("now")) {
								} else {
									sendMessage("/mute " + USERNAME + " 0s");
								}
							} else if (strMessage.equals("Vanish for " + sanitizedName + ": disabled")) {
								sendMessage("/v on");
							} else if (jsonMessage.equals(
									"{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"gold\",\"text\":\"God mode\"},{\"italic\":false,\"color\":\"red\",\"text\":\" disabled\"},{\"italic\":false,\"color\":\"gold\",\"text\":\".\"}],\"text\":\"\"}")) {
								sendMessage("/god on");
							} else if (jsonMessage.equals(
									"{\"extra\":[{\"text\":\"Successfully disabled CommandSpy\"}],\"text\":\"\"}")) {
								sendMessage("/cspy on");
							} else if ((matcher = usernamePattern.matcher(strMessage)).matches()) {
								if (matcher.group(1).equals(sanitizedName)) {
								} else {
									sendMessage("/username &r" + USERNAME.replaceAll("§", "&"));
								}
							} else if (strMessage.startsWith("You already have the username ")) {
								sendMessage("/username &r" + USERNAME.replaceAll("§", "&"));
							} else if (strMessage.equals("A player with that username is already logged in")) {
								sendMessage("/username &r" + USERNAME.replaceAll("§", "&"));
							} else if (strMessage.startsWith("Your nickname is now ")) {
								sendMessage("/essentials:nick off");
							} else if (strMessage.startsWith("You now have the tag: ")) {
								sendMessage("/extras:tag off");
							}
						} else if (packet instanceof ClientboundEntityEventPacket) {
							ClientboundEntityEventPacket t_packet = (ClientboundEntityEventPacket) packet;
							if (t_packet.getEntityId() == entityId) {
								if (t_packet.getStatus() == EntityEvent.PLAYER_OP_PERMISSION_LEVEL_0) {
									sendMessage("/op @s[type=player]");
								}
							}
						}
					}

					@Override
					public void disconnected(DisconnectedEvent event) {
						if (event.getCause() != null) {
							event.getCause().printStackTrace();
						}
						FastThreadLocal.removeAll();
						relogExecutor.submit(() -> {
							FastThreadLocal.removeAll();
							processDisconnect(event);
						});
					}
				});
			}
		});
		listnerThread.start();

		client.connect();
	}

	private void processDisconnect(DisconnectedEvent event) {
		if (event.getReason().contains("Wait 5 seconds before connecting, thanks! :)")
				|| event.getReason().contains("Connection throttled! Please wait before reconnecting.")) {
			relogExecutor.schedule(() -> {
				login();
			}, 5, TimeUnit.SECONDS);
		} else {
			relogExecutor.schedule(() -> {
				login();
			}, 1, TimeUnit.SECONDS);
		}
	}

	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return relogExecutor.awaitTermination(timeout, unit);
	}

	public void stop() {
		try {
			client.disconnect("Disconnecting...");
		} catch (Exception e) {
			e.printStackTrace();
		}
		relogExecutor.shutdownNow();
		BOTLIST.remove(this);
	}

	public static void stopAllBotsAndAwaitTermination() {
		ArrayList<MinecraftConnectionManager> copiedList;
		synchronized (BOTLIST) {
			copiedList = new ArrayList<>(BOTLIST);
		}
		copiedList.forEach((bot) -> bot.stop());
		copiedList.forEach((bot) -> {
			try {
				bot.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	public void sendPacket(Packet packet) {
		client.send(packet);
	}

	public void sendMessage(String message) {
		sendPacket(new ServerboundChatPacket(message));
	}
}
