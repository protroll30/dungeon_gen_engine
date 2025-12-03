package core;

import edu.princeton.cs.algs4.WeightedQuickUnionUF;
import tileengine.TETile;
import tileengine.Tileset;
import utils.RandomUtils;

import java.util.*;

public class World {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;
    private static final int MIN_CAVERN_DISTANCE = 8;
    private static final int MIN_CAVERNS = 30;
    private static final int MAX_CAVERNS = 45;
    private static final int MIN_CAVERN_SIZE = 20;
    private static final int MAX_CAVERN_SIZE = 80;
    private static final double EXTRA_TUNNEL_PROB = 0.15;

    private final Random rng;
    private final TETile[][] world;
    private final List<Position> cavernCenters;
    private final List<Cavern> caverns;
    private WeightedQuickUnionUF connectivityTracker;

    public World(long seed) {
        this.rng = new Random(seed);
        this.world = new TETile[WIDTH][HEIGHT];
        this.cavernCenters = new ArrayList<>();
        this.caverns = new ArrayList<>();
        generateWorld();
    }

    private void generateWorld() {
        initializeGrid();
        pickCaveCenters();
        digCaves();
        connectCaves();
        buildWalls();
        ensureConnectivity();
        removeDeadEnds();
        cleanBorders();
        removeOrphanedWalls();
    }
    private void initializeGrid() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                world[x][y] = Tileset.NOTHING;
            }
        }
    }

    private void pickCaveCenters() {
        int numCaverns = RandomUtils.uniform(rng, MIN_CAVERNS, MAX_CAVERNS + 1);
        Set<Position> usedPositions = new HashSet<>();
        int attempts = 0;
        int maxAttempts = 5000;

        while (cavernCenters.size() < numCaverns && attempts < maxAttempts) {
            int x = RandomUtils.uniform(rng, 2, WIDTH - 2);
            int y = RandomUtils.uniform(rng, 2, HEIGHT - 2);
            Position candidate = new Position(x, y);

            boolean tooClose = false;
            for (Position existing : usedPositions) {
                int dx = candidate.x - existing.x;
                int dy = candidate.y - existing.y;
                int distance = (int) Math.sqrt(dx * dx + dy * dy);
                if (distance < MIN_CAVERN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                cavernCenters.add(candidate);
                usedPositions.add(candidate);
            }
            attempts++;
        }
    }

    private void digCaves() {
        for (Position center : cavernCenters) {
            Cavern cavern = new Cavern(center);
            boolean isRectangular = RandomUtils.bernoulli(rng, 0.7);
            if (isRectangular) {
                digRectangularRoom(cavern, center);
            } else {
                int targetSize = RandomUtils.uniform(rng, MIN_CAVERN_SIZE, MAX_CAVERN_SIZE + 1);
                digCompactRoom(cavern, center, targetSize);
            }
            caverns.add(cavern);
        }
    }

    private void digRectangularRoom(Cavern cavern, Position center) {
        int minWidth = 5;
        int maxWidth = 12;
        int minHeight = 5;
        int maxHeight = 10;
        
        int width = RandomUtils.uniform(rng, minWidth, maxWidth + 1);
        int height = RandomUtils.uniform(rng, minHeight, maxHeight + 1);
        
        int startX = Math.max(1, Math.min(center.x - width / 2, WIDTH - width - 1));
        int startY = Math.max(1, Math.min(center.y - height / 2, HEIGHT - height - 1));
        
        boolean canPlace = true;
        for (int x = startX; x < startX + width; x++) {
            for (int y = startY; y < startY + height; y++) {
                if (isValidPosition(x, y) && world[x][y].description().equals("floor")) {
                    canPlace = false;
                    break;
                }
            }
            if (!canPlace) break;
        }
        
        if (!canPlace) return;
        
        for (int x = startX; x < startX + width; x++) {
            for (int y = startY; y < startY + height; y++) {
                if (isValidPosition(x, y) && x > 0 && x < WIDTH - 1 && y > 0 && y < HEIGHT - 1) {
                    Position pos = new Position(x, y);
                    world[x][y] = Tileset.FLOOR;
                    cavern.addFloorTile(pos);
                }
            }
        }
    }

    private void digCompactRoom(Cavern cavern, Position start, int targetSize) {
        int width = RandomUtils.uniform(rng, 5, 10);
        int height = RandomUtils.uniform(rng, 5, 8);
        
        int startX = Math.max(1, Math.min(start.x - width / 2, WIDTH - width - 1));
        int startY = Math.max(1, Math.min(start.y - height / 2, HEIGHT - height - 1));
        
        boolean canPlace = true;
        for (int x = startX; x < startX + width; x++) {
            for (int y = startY; y < startY + height; y++) {
                if (isValidPosition(x, y) && world[x][y].description().equals("floor")) {
                    canPlace = false;
                    break;
                }
            }
            if (!canPlace) break;
        }
        
        if (!canPlace) return;
        
        for (int x = startX; x < startX + width; x++) {
            for (int y = startY; y < startY + height; y++) {
                if (isValidPosition(x, y) && x > 0 && x < WIDTH - 1 && y > 0 && y < HEIGHT - 1) {
                    if (world[x][y] == Tileset.NOTHING) {
                        Position pos = new Position(x, y);
                        world[x][y] = Tileset.FLOOR;
                        cavern.addFloorTile(pos);
                    }
                }
            }
        }
    }

    private void connectCaves() {
        if (caverns.size() < 2) return;

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < caverns.size(); i++) {
            for (int j = i + 1; j < caverns.size(); j++) {
                int distance = manhattanDistance(caverns.get(i).center, caverns.get(j).center);
                edges.add(new Edge(i, j, distance));
            }
        }

        Collections.sort(edges);

        WeightedQuickUnionUF uf = new WeightedQuickUnionUF(caverns.size());
        List<Edge> mstEdges = new ArrayList<>();
        Map<Integer, Integer> roomConnections = new HashMap<>();

        for (Edge edge : edges) {
            if (!uf.connected(edge.cavern1Index, edge.cavern2Index)) {
                int conn1 = roomConnections.getOrDefault(edge.cavern1Index, 0);
                int conn2 = roomConnections.getOrDefault(edge.cavern2Index, 0);
                
                if (conn1 < 2 && conn2 < 2) {
                    uf.union(edge.cavern1Index, edge.cavern2Index);
                    mstEdges.add(edge);
                    roomConnections.put(edge.cavern1Index, conn1 + 1);
                    roomConnections.put(edge.cavern2Index, conn2 + 1);
                }
            }
        }

        for (Edge edge : mstEdges) {
            Position start = getRoomEdge(caverns.get(edge.cavern1Index));
            Position end = getRoomEdge(caverns.get(edge.cavern2Index));
            digTunnelWithTurns(start, end);
        }

        for (Edge edge : edges) {
            if (!mstEdges.contains(edge) && RandomUtils.bernoulli(rng, EXTRA_TUNNEL_PROB)) {
                int conn1 = roomConnections.getOrDefault(edge.cavern1Index, 0);
                int conn2 = roomConnections.getOrDefault(edge.cavern2Index, 0);
                
                if (conn1 < 2 && conn2 < 2) {
                    Position start = getRoomEdge(caverns.get(edge.cavern1Index));
                    Position end = getRoomEdge(caverns.get(edge.cavern2Index));
                    digTunnelWithTurns(start, end);
                    roomConnections.put(edge.cavern1Index, conn1 + 1);
                    roomConnections.put(edge.cavern2Index, conn2 + 1);
                }
            }
        }
    }

    private Position getRoomEdge(Cavern cavern) {
        if (cavern.floorTiles.isEmpty()) {
            return cavern.center;
        }
        List<Position> edgeTiles = new ArrayList<>();
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        
        for (Position pos : cavern.floorTiles) {
            boolean isEdge = false;
            for (int i = 0; i < 4; i++) {
                int nx = pos.x + dx[i];
                int ny = pos.y + dy[i];
                if (!isValidPosition(nx, ny) || world[nx][ny] != Tileset.FLOOR) {
                    isEdge = true;
                    break;
                }
            }
            if (isEdge) {
                edgeTiles.add(pos);
            }
        }
        
        if (edgeTiles.isEmpty()) {
            return cavern.floorTiles.get(RandomUtils.uniform(rng, cavern.floorTiles.size()));
        }
        return edgeTiles.get(RandomUtils.uniform(rng, edgeTiles.size()));
    }
    private void digTunnelWithTurns(Position start, Position end) {
        int dx = end.x - start.x;
        int dy = end.y - start.y;
        
        if (dx == 0 && dy == 0) return;
        
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
            digStraightLine(start.x, start.y, end.x, end.y);
            return;
        }
        
        int totalDistance = Math.abs(dx) + Math.abs(dy);
        
        if (totalDistance <= 8) {
            boolean horizontalFirst = RandomUtils.bernoulli(rng);
            int midX, midY;
            if (horizontalFirst) {
                int minX = Math.min(start.x, end.x);
                int maxX = Math.max(start.x, end.x);
                if (maxX - minX > 2) {
                    midX = RandomUtils.uniform(rng, minX + 1, maxX);
                } else {
                    midX = (start.x + end.x) / 2;
                }
                midY = start.y;
            } else {
                int minY = Math.min(start.y, end.y);
                int maxY = Math.max(start.y, end.y);
                if (maxY - minY > 2) {
                    midY = RandomUtils.uniform(rng, minY + 1, maxY);
                } else {
                    midY = (start.y + end.y) / 2;
                }
                midX = start.x;
            }
            digStraightLine(start.x, start.y, midX, midY);
            digStraightLine(midX, midY, end.x, end.y);
        } else {
            int midX, midY;
            boolean horizontalFirst = RandomUtils.bernoulli(rng);
            if (horizontalFirst) {
                int minX = Math.min(start.x, end.x);
                int maxX = Math.max(start.x, end.x);
                int lowerBound = minX + Math.abs(dx) / 3;
                int upperBound = maxX - Math.abs(dx) / 3;
                if (upperBound > lowerBound) {
                    midX = RandomUtils.uniform(rng, lowerBound, upperBound);
                } else {
                    midX = (start.x + end.x) / 2;
                }
                midY = start.y;
            } else {
                int minY = Math.min(start.y, end.y);
                int maxY = Math.max(start.y, end.y);
                int lowerBound = minY + Math.abs(dy) / 3;
                int upperBound = maxY - Math.abs(dy) / 3;
                if (upperBound > lowerBound) {
                    midY = RandomUtils.uniform(rng, lowerBound, upperBound);
                } else {
                    midY = (start.y + end.y) / 2;
                }
                midX = start.x;
            }
            
            int secondMidX, secondMidY;
            if (horizontalFirst) {
                secondMidX = midX;
                int minY = Math.min(midY, end.y);
                int maxY = Math.max(midY, end.y);
                if (maxY - minY > 2) {
                    secondMidY = RandomUtils.uniform(rng, minY + 1, maxY);
                } else {
                    secondMidY = (midY + end.y) / 2;
                }
            } else {
                secondMidY = midY;
                int minX = Math.min(midX, end.x);
                int maxX = Math.max(midX, end.x);
                if (maxX - minX > 2) {
                    secondMidX = RandomUtils.uniform(rng, minX + 1, maxX);
                } else {
                    secondMidX = (midX + end.x) / 2;
                }
            }
            
            digStraightLine(start.x, start.y, midX, midY);
            digStraightLine(midX, midY, secondMidX, secondMidY);
            digStraightLine(secondMidX, secondMidY, end.x, end.y);
        }
    }
    
    private void digStraightLine(int x1, int y1, int x2, int y2) {
        int x = x1;
        int y = y1;
        
        while (x != x2 || y != y2) {
            if (isValidPosition(x, y)) {
                if (world[x][y] == Tileset.NOTHING || world[x][y] == Tileset.WALL || world[x][y].description().equals("wall")) {
                    world[x][y] = Tileset.FLOOR;
                }
            }
            
            if (x < x2) x++;
            else if (x > x2) x--;
            else if (y < y2) y++;
            else if (y > y2) y--;
        }
        
        if (isValidPosition(x2, y2)) {
            if (world[x2][y2] == Tileset.NOTHING || world[x2][y2] == Tileset.WALL || world[x2][y2].description().equals("wall")) {
                world[x2][y2] = Tileset.FLOOR;
            }
        }
    }
    private void buildWalls() {
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (world[x][y].description().equals("floor")) {
                    for (int i = 0; i < 8; i++) {
                        int nx = x + dx[i];
                        int ny = y + dy[i];
                        if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.NOTHING) {
                        world[nx][ny] = Tileset.WALL;
                        }
                    }
                }
            }
        }
    }

    private void ensureConnectivity() {
        if (!isWorldConnected()) {
            connectDisconnectedComponents();
        }
    }
    private boolean isWorldConnected() {
        List<Position> floorTiles = new ArrayList<>();
        Map<Position, Integer> positionToIndex = new HashMap<>();

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (world[x][y].description().equals("floor")) {
                    Position pos = new Position(x, y);
                    floorTiles.add(pos);
                    positionToIndex.put(pos, floorTiles.size() - 1);
                }
            }
        }

        if (floorTiles.isEmpty()) return true;

        connectivityTracker = new WeightedQuickUnionUF(floorTiles.size());
        int components = floorTiles.size();

        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        for (Position pos : floorTiles) {
            int idx1 = positionToIndex.get(pos);
            for (int i = 0; i < 4; i++) {
                int nx = pos.x + dx[i];
                int ny = pos.y + dy[i];
                Position neighbor = new Position(nx, ny);
                if (positionToIndex.containsKey(neighbor)) {
                    int idx2 = positionToIndex.get(neighbor);
                    if (!connectivityTracker.connected(idx1, idx2)) {
                        connectivityTracker.union(idx1, idx2);
                        components--;
                    }
                }
            }
        }

        return components == 1;
    }

    private void connectDisconnectedComponents() {
        List<Position> floorTiles = new ArrayList<>();
        Map<Position, Integer> positionToIndex = new HashMap<>();

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (world[x][y].description().equals("floor")) {
                    Position pos = new Position(x, y);
                    floorTiles.add(pos);
                    positionToIndex.put(pos, floorTiles.size() - 1);
                }
            }
        }

        if (floorTiles.isEmpty()) return;

        connectivityTracker = new WeightedQuickUnionUF(floorTiles.size());

        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        for (Position pos : floorTiles) {
            int idx1 = positionToIndex.get(pos);
            for (int i = 0; i < 4; i++) {
                int nx = pos.x + dx[i];
                int ny = pos.y + dy[i];
                Position neighbor = new Position(nx, ny);
                if (positionToIndex.containsKey(neighbor)) {
                    int idx2 = positionToIndex.get(neighbor);
                    if (!connectivityTracker.connected(idx1, idx2)) {
                        connectivityTracker.union(idx1, idx2);
                    }
                }
            }
        }

        Map<Integer, List<Position>> components = new HashMap<>();
        for (int i = 0; i < floorTiles.size(); i++) {
            int root = connectivityTracker.find(i);
            components.putIfAbsent(root, new ArrayList<>());
            components.get(root).add(floorTiles.get(i));
        }

        List<Integer> componentRoots = new ArrayList<>(components.keySet());
        for (int i = 0; i < componentRoots.size() - 1; i++) {
            List<Position> comp1 = components.get(componentRoots.get(i));
            List<Position> comp2 = components.get(componentRoots.get(i + 1));

            Position p1 = comp1.get(RandomUtils.uniform(rng, comp1.size()));
            Position p2 = comp2.get(RandomUtils.uniform(rng, comp2.size()));
            digTunnelWithTurns(p1, p2);
        }
    }

    private void removeDeadEnds() {
        boolean changed = true;
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        while (changed) {
            changed = false;
            List<Position> toRemove = new ArrayList<>();

            for (int x = 1; x < WIDTH - 1; x++) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    if (world[x][y].description().equals("floor")) {
                        int floorNeighbors = 0;
                        Position neighborPos = null;
                        for (int i = 0; i < 4; i++) {
                            int nx = x + dx[i];
                            int ny = y + dy[i];
                            if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.FLOOR) {
                                floorNeighbors++;
                                neighborPos = new Position(nx, ny);
                            }
                        }

                        if (floorNeighbors == 1) {
                            int neighborCount = 0;
                            if (neighborPos != null) {
                                for (int i = 0; i < 4; i++) {
                                    int nx = neighborPos.x + dx[i];
                                    int ny = neighborPos.y + dy[i];
                                    if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.FLOOR) {
                                        neighborCount++;
                                    }
                                }
                            }
                            
                            toRemove.add(new Position(x, y));
                        }
                    }
                }
            }

            for (Position pos : toRemove) {
                world[pos.x][pos.y] = Tileset.WALL;
                changed = true;
            }
        }

        if (!isWorldConnected()) {
            connectDisconnectedComponents();
        }
    }

    private void cleanBorders() {
        for (int x = 0; x < WIDTH; x++) {
            if (world[x][0] == Tileset.FLOOR) {
                world[x][0] = Tileset.WALL;
            }
            if (world[x][HEIGHT - 1] == Tileset.FLOOR) {
                world[x][HEIGHT - 1] = Tileset.WALL;
            }
        }

        for (int y = 0; y < HEIGHT; y++) {
            if (world[0][y] == Tileset.FLOOR) {
                world[0][y] = Tileset.WALL;
            }
            if (world[WIDTH - 1][y] == Tileset.FLOOR) {
                world[WIDTH - 1][y] = Tileset.WALL;
            }
        }

        for (int x = 1; x < WIDTH - 1; x++) {
            for (int y = 1; y < HEIGHT - 1; y++) {
                if (world[x][y].description().equals("floor")) {
                    int[] dx = {0, 0, 1, -1};
                    int[] dy = {1, -1, 0, 0};
                    for (int i = 0; i < 4; i++) {
                        int nx = x + dx[i];
                        int ny = y + dy[i];
                        if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.NOTHING) {
                            world[nx][ny] = Tileset.WALL;
                        }
                    }
                }
            }
        }
    }

    private void removeOrphanedWalls() {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Position> toRemove = new ArrayList<>();
            
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (world[x][y] == Tileset.WALL) {
                        int floorCount = 0;
                        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
                        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
                        
                        for (int i = 0; i < 8; i++) {
                            int nx = x + dx[i];
                            int ny = y + dy[i];
                            if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.FLOOR) {
                                floorCount++;
                            }
                        }
                        
                        if (floorCount == 8 || floorCount == 0) {
                            toRemove.add(new Position(x, y));
                        }
                    }
                }
            }
            
            for (Position pos : toRemove) {
                world[pos.x][pos.y] = Tileset.NOTHING;
                changed = true;
            }
        }
    }

    public TETile[][] getWorld() {
        return world;
    }

    public static int getWidth() {
        return WIDTH;
    }

    public static int getHeight() {
        return HEIGHT;
    }

    public Position getAvatarStartPosition() {
        List<Position> floorTiles = new ArrayList<>();
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (world[x][y].description().equals("floor")) {
                    floorTiles.add(new Position(x, y));
                }
            }
        }
        if (floorTiles.isEmpty()) {
            return new Position(WIDTH / 2, HEIGHT / 2);
        }
        Collections.sort(floorTiles, new java.util.Comparator<Position>() {
            @Override
            public int compare(Position a, Position b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
        return floorTiles.get(0);
    }

    public TETile[][] getWorldWithAvatar(Position avatarPos) {
        TETile[][] worldWithAvatar = new TETile[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                worldWithAvatar[x][y] = world[x][y];
            }
        }
        worldWithAvatar[avatarPos.x][avatarPos.y] = Tileset.AVATAR;
        return worldWithAvatar;
    }

    public boolean canMoveTo(int x, int y) {
        return isValidPosition(x, y) && world[x][y].description().equals("floor");
    }

    private boolean isValidPosition(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    private int manhattanDistance(Position a, Position b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
