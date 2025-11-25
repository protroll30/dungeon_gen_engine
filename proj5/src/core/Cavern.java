package core;

import java.util.ArrayList;
import java.util.List;

public class Cavern {
    public final Position center;
    public final List<Position> floorTiles;

    public Cavern(Position center) {
        this.center = center;
        this.floorTiles = new ArrayList<>();
    }

    public void addFloorTile(Position pos) {
        floorTiles.add(pos);
    }
}

