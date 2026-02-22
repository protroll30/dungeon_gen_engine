package core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single cave/room in the generated world.
 * Stores the center position and all floor tiles that belong to this cavern.
 * Used for tracking room boundaries and finding connection points for tunnels.
 */
public class Cavern {
    public final Position center;
    public final List<Position> floorTiles;

    public Cavern(Position center) {
        this.center = center;
        this.floorTiles = new ArrayList<>();
    }

    /**
     * Adds a floor tile to this cavern.
     * @param pos the position of the floor tile
     */
    public void addFloorTile(Position pos) {
        floorTiles.add(pos);
    }
}

