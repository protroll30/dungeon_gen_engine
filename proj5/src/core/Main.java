package core;

import tileengine.TERenderer;
import tileengine.TETile;

public class Main {
    public static void main(String[] args) {
        long seed;
        if (args.length > 0) {
            seed = Long.parseLong(args[0]);
        } else {
            seed = System.currentTimeMillis();
        }

        World world = new World(seed);

        TERenderer ter = new TERenderer();
        ter.initialize(World.getWidth(), World.getHeight());

        ter.renderFrame(world.getWorld());
    }
}
