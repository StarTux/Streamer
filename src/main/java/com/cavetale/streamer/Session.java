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
    int noMove = 0;

    void timer() {
        Location eye = player.getEyeLocation();
        final int x = eye.getBlockX();
        final int y = eye.getBlockY();
        final int z = eye.getBlockZ();
        final float pitch = eye.getPitch();
        final float yaw = eye.getYaw();
        boolean samePos = x == lx && y == ly && z == lz;
        boolean sameView = lpitch == pitch && lyaw == yaw;
        if (samePos || sameView) {
            noMove += 1;
        } else {
            noMove = 0;
        }
        lx = x;
        ly = y;
        lz = z;
        lpitch = pitch;
        lyaw = yaw;
    }
}
