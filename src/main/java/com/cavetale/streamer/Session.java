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
    int noMove = 0;

    void timer() {
        Location eye = player.getEyeLocation();
        final int x = eye.getBlockX();
        final int y = eye.getBlockY();
        final int z = eye.getBlockZ();
        if (x == lx && y == ly && z == lz) {
            noMove += 1;
        } else {
            noMove = 0;
        }
        lx = x;
        ly = y;
        lz = z;
    }
}
