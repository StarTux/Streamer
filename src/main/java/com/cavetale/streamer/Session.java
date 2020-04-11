package com.cavetale.streamer;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class Session {
    final Player player;
    int lx;
    int ly;
    int lz;
    float lpitch;
    float lyaw;
    private int noMove = 0;
    private int noView = 0;
    int afk;

    void timer() {
        Location eye = player.getEyeLocation();
        final int x = eye.getBlockX();
        final int y = eye.getBlockY();
        final int z = eye.getBlockZ();
        final float pitch = eye.getPitch();
        final float yaw = eye.getYaw();
        if (x == lx && y == ly && z == lz) {
            noMove += 1;
        } else {
            noMove = 0;
            lx = x;
            ly = y;
            lz = z;
        }
        if (lpitch == pitch && lyaw == yaw) {
            noView += 1;
        } else {
            noView = 0;
            lpitch = pitch;
            lyaw = yaw;
        }
        afk = Math.max(noMove, noView);
    }
}
