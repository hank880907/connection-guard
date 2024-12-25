package com.github.gerolndnr.connectionguard.spigot.listener;

import com.github.gerolndnr.connectionguard.core.ConnectionGuard;
import com.github.gerolndnr.connectionguard.core.geo.GeoResult;
import com.github.gerolndnr.connectionguard.core.vpn.VpnResult;
import com.github.gerolndnr.connectionguard.core.webhook.CGWebHookHelper;
import com.github.gerolndnr.connectionguard.spigot.ConnectionGuardSpigotPlugin;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AsyncPlayerPreLoginListener implements Listener {
    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent preLoginEvent) {
        String ipAddress = preLoginEvent.getAddress().getHostAddress();

        CompletableFuture<VpnResult> vpnResultFuture;
        CompletableFuture<Optional<GeoResult>> geoResultOptionalFuture;

        // Check if ip address is in exemption lists
        if (
                ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.vpn.exemptions").contains(ipAddress)
                || ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.vpn.exemptions").contains(preLoginEvent.getUniqueId().toString())
                || ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.vpn.exemptions").contains(preLoginEvent.getName())
        ) {
            vpnResultFuture = CompletableFuture.completedFuture(new VpnResult(ipAddress, false));
        } else {
            vpnResultFuture = ConnectionGuard.getVpnResult(ipAddress);
        }

        if (
                ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.geo.exemptions").contains(ipAddress)
                || ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.geo.exemptions").contains(preLoginEvent.getUniqueId().toString())
                || ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.geo.exemptions").contains(preLoginEvent.getName())
        ) {
            geoResultOptionalFuture = CompletableFuture.completedFuture(Optional.empty());
        } else {
            geoResultOptionalFuture = ConnectionGuard.getGeoResult(ipAddress);
        }

        CompletableFuture.allOf(vpnResultFuture, geoResultOptionalFuture).join();

        VpnResult vpnResult = vpnResultFuture.join();

        if (vpnResult.isVpn()) {
            // Check if staff should be notified
            if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.vpn.notify-staff")) {
                String notifyMessage = ChatColor.translateAlternateColorCodes(
                        '&',
                        ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.vpn-notify")
                                .replaceAll("%IP%", vpnResult.getIpAddress())
                                .replaceAll("%NAME%", preLoginEvent.getName())
                );
                Bukkit.broadcast(notifyMessage, "connectionguard.notify.vpn");
            }

            // Check if command should be executed on flag
            if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.vpn.execute-command.enabled")) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        ConnectionGuardSpigotPlugin.getInstance().getConfig().getString("behavior.vpn.execute-command.command")
                                .replaceAll("%NAME%", preLoginEvent.getName())
                                .replaceAll("%IP%", ipAddress)
                );
            }

            // Check if WebHook should be executed
            if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.vpn.send-webhook.enabled")) {
                String webhookMessage = ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.vpn-webhook")
                        .replaceAll("%NAME%", preLoginEvent.getName())
                        .replaceAll("%IP%", ipAddress);
                String webhookUrl = ConnectionGuardSpigotPlugin.getInstance().getConfig().getString("behavior.vpn.send-webhook.url");

                CGWebHookHelper.sendWebHook(webhookUrl, webhookMessage);
            }

            // Check if player should be kicked
            if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.vpn.kick-player")) {
                String kickMessage = ChatColor.translateAlternateColorCodes(
                        '&',
                        ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.vpn-block")
                                .replaceAll("%IP%", vpnResult.getIpAddress())
                                .replaceAll("%NAME%", preLoginEvent.getName())
                );

                preLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
                return;
            }
        }

        Optional<GeoResult> geoResultOptional = geoResultOptionalFuture.join();
        if (geoResultOptional.isPresent()) {
            GeoResult geoResult = geoResultOptional.get();
            boolean isGeoFlagged = false;

            switch (ConnectionGuardSpigotPlugin.getInstance().getConfig().getString("behavior.geo.type").toLowerCase()) {
                case "blacklist":
                    if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.geo.list").contains(geoResult.getCountryName()))
                        isGeoFlagged = true;
                    break;
                case "whitelist":
                    if (!ConnectionGuardSpigotPlugin.getInstance().getConfig().getStringList("behavior.geo.list").contains(geoResult.getCountryName()))
                        isGeoFlagged = true;
                    break;
                default:
                    ConnectionGuard.getLogger().info("Invalid geo behavior type. Please use BLACKLIST or WHITELIST.");
                    break;
            }

            if (isGeoFlagged) {
                // Check if staff should be notified
                if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.geo.notify-staff")) {
                    String notifyMessage = ChatColor.translateAlternateColorCodes(
                            '&',
                            ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.geo-notify")
                                    .replaceAll("%IP%", geoResult.getIpAddress())
                                    .replaceAll("%COUNTRY%", geoResult.getCountryName())
                                    .replaceAll("%CITY%", geoResult.getCityName())
                                    .replaceAll("%ISP%", geoResult.getIspName())
                                    .replaceAll("%NAME%", preLoginEvent.getName())
                    );
                    Bukkit.broadcast(notifyMessage, "connectionguard.notify.geo");
                }

                // Check if command should be executed on flag
                if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.geo.execute-command.enabled")) {
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            ConnectionGuardSpigotPlugin.getInstance().getConfig().getString("behavior.geo.execute-command.command")
                                    .replaceAll("%NAME%", preLoginEvent.getName())
                                    .replaceAll("%IP%", ipAddress)
                    );
                }

                // Check if WebHook should be executed
                if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.geo.send-webhook.enabled")) {
                    String webhookMessage = ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.geo-webhook")
                            .replaceAll("%NAME%", preLoginEvent.getName())
                            .replaceAll("%IP%", ipAddress)
                            .replaceAll("%COUNTRY%", geoResult.getCountryName())
                            .replaceAll("%CITY%", geoResult.getCityName());
                    String webhookUrl = ConnectionGuardSpigotPlugin.getInstance().getConfig().getString("behavior.geo.send-webhook.url");

                    CGWebHookHelper.sendWebHook(webhookUrl, webhookMessage);
                }

                // Check if player should be kicked
                if (ConnectionGuardSpigotPlugin.getInstance().getConfig().getBoolean("behavior.geo.kick-player")) {
                    String kickMessage = ChatColor.translateAlternateColorCodes(
                            '&',
                            ConnectionGuardSpigotPlugin.getInstance().getLanguageConfig().getString("messages.geo-block")
                                    .replaceAll("%IP%", geoResult.getIpAddress())
                                    .replaceAll("%COUNTRY%", geoResult.getCountryName())
                                    .replaceAll("%CITY%", geoResult.getCityName())
                                    .replaceAll("%ISP%", geoResult.getIspName())
                                    .replaceAll("%NAME%", preLoginEvent.getName())
                    );

                    preLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
                }
            }
        }
    }
}
