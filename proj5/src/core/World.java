package core;

import edu.princeton.cs.algs4.WeightedQuickUnionUF;
import tileengine.TETile;
import tileengine.Tileset;
import utils.RandomUtils;

import java.util.*;

public class World {
    // World dimensions in tiles
    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;
    // Minimum distance between cave centers (prevents overlapping caves)
    private static final int MIN_CAVERN_DISTANCE = 8;
    // Number of caves to generate (randomized between min and max)
    private static final int MIN_CAVERNS = 30;
    private static final int MAX_CAVERNS = 45;
    // Size constraints for compact rooms (number of floor tiles)
    private static final int MIN_CAVERN_SIZE = 20;
    private static final int MAX_CAVERN_SIZE = 80;
    // Probability of adding extra tunnels beyond MST (creates loops for more interesting layouts)
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

    /**
     * Selects cave center positions using Poisson disk sampling.
     * Ensures caves maintain minimum separation distance (MIN_CAVERN_DISTANCE).
     * Uses rejection sampling: randomly generate candidates until valid position found.
     */
    private void pickCaveCenters() {
        int numCaverns = RandomUtils.uniform(rng, MIN_CAVERNS, MAX_CAVERNS + 1);
        Set<Position> usedPositions = new HashSet<>();
        int attempts = 0;
        // Maximum attempts prevents infinite loops if world is too constrained
        int maxAttempts = 5000;

        while (cavernCenters.size() < numCaverns && attempts < maxAttempts) {
            // Generate random candidate position (avoid world edges)
            int x = RandomUtils.uniform(rng, 2, WIDTH - 2);
            int y = RandomUtils.uniform(rng, 2, HEIGHT - 2);
            Position candidate = new Position(x, y);

            // Check Euclidean distance to all existing caves
            // Must be at least MIN_CAVERN_DISTANCE tiles away
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

    /**
     * Connects all caves using Kruskal's Minimum Spanning Tree algorithm.
     * Ensures all caves are reachable while minimizing total tunnel length.
     * Additional tunnels (EXTRA_TUNNEL_PROB probability) create loops for more interesting layouts.
     * Enforces maximum degree of 2 connections per cave to maintain natural layout.
     */
    private void connectCaves() {
        if (caverns.size() < 2) return;

        // Build complete graph of all cave-to-cave distances
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < caverns.size(); i++) {
            for (int j = i + 1; j < caverns.size(); j++) {
                int distance = manhattanDistance(caverns.get(i).center, caverns.get(j).center);
                edges.add(new Edge(i, j, distance));
            }
        }

        // Sort edges by distance for greedy MST construction
        Collections.sort(edges);

        // Kruskal's algorithm: use Union-Find to detect cycles
        WeightedQuickUnionUF uf = new WeightedQuickUnionUF(caverns.size());
        List<Edge> mstEdges = new ArrayList<>();
        // Track connections per cave to enforce max degree of 2
        // This prevents over-connected caves and maintains natural layout
        Map<Integer, Integer> roomConnections = new HashMap<>();

        // Construct MST: add edges in order if they don't create cycles
        // and both caves have fewer than 2 connections
        for (Edge edge : edges) {
            if (!uf.connected(edge.cavern1Index, edge.cavern2Index)) {
                int conn1 = roomConnections.getOrDefault(edge.cavern1Index, 0);
                int conn2 = roomConnections.getOrDefault(edge.cavern2Index, 0);
                
                // Enforce max degree of 2 per cave
                if (conn1 < 2 && conn2 < 2) {
                    uf.union(edge.cavern1Index, edge.cavern2Index);
                    mstEdges.add(edge);
                    roomConnections.put(edge.cavern1Index, conn1 + 1);
                    roomConnections.put(edge.cavern2Index, conn2 + 1);
                }
            }
        }

        // Dig tunnels for all MST edges (guarantees connectivity)
        for (Edge edge : mstEdges) {
            Position start = getRoomEdge(caverns.get(edge.cavern1Index));
            Position end = getRoomEdge(caverns.get(edge.cavern2Index));
            digTunnelWithTurns(start, end);
        }

        // Add extra tunnels beyond MST (15% probability) to create loops
        // This makes the dungeon more interesting but not required for connectivity
        for (Edge edge : edges) {
            if (!mstEdges.contains(edge) && RandomUtils.bernoulli(rng, EXTRA_TUNNEL_PROB)) {
                int conn1 = roomConnections.getOrDefault(edge.cavern1Index, 0);
                int conn2 = roomConnections.getOrDefault(edge.cavern2Index, 0);
                
                // Still respect degree constraint
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

    /**
     * Gets a random edge tile from a cavern for tunnel connection.
     * Edge tiles are those with at least one non-floor neighbor.
     * @param cavern the cavern to get edge tile from
     * @return a position on the edge of the cavern, or center if no floor tiles exist
     */
    private Position getRoomEdge(Cavern cavern) {
        // Fallback: if cavern has no floor tiles, use center
        if (cavern.floorTiles.isEmpty()) {
            return cavern.center;
        }
        List<Position> edgeTiles = new ArrayList<>();
        // Check 4-directional neighbors (up, down, left, right)
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        
        // Find all edge tiles (tiles with non-floor neighbors)
        for (Position pos : cavern.floorTiles) {
            boolean isEdge = false;
            for (int i = 0; i < 4; i++) {
                int nx = pos.x + dx[i];
                int ny = pos.y + dy[i];
                // Tile is on edge if neighbor is out of bounds or not a floor
                if (!isValidPosition(nx, ny) || world[nx][ny] != Tileset.FLOOR) {
                    isEdge = true;
                    break;
                }
            }
            if (isEdge) {
                edgeTiles.add(pos);
            }
        }
        
        // Fallback: if no edge tiles found, return random floor tile
        // (shouldn't happen in practice, but handles edge case)
        if (edgeTiles.isEmpty()) {
            return cavern.floorTiles.get(RandomUtils.uniform(rng, cavern.floorTiles.size()));
        }
        // Return random edge tile
        return edgeTiles.get(RandomUtils.uniform(rng, edgeTiles.size()));
    }
    /**
     * Digs a tunnel between two positions using L-shaped or Z-shaped paths.
     * Short distances (≤8 tiles): Single-turn L-path
     * Long distances (>8 tiles): Two-turn Z-path with randomized intermediate points
     * This creates natural-looking tunnels that avoid straight corridors while maintaining efficiency.
     */
    private void digTunnelWithTurns(Position start, Position end) {
        int dx = end.x - start.x;
        int dy = end.y - start.y;
        
        if (dx == 0 && dy == 0) return;
        
        // Very short distances: just dig straight line
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
            digStraightLine(start.x, start.y, end.x, end.y);
            return;
        }
        
        int totalDistance = Math.abs(dx) + Math.abs(dy);
        
        // For short distances, use simple L-path (one turn)
        if (totalDistance <= 8) {
            // Randomly choose horizontal-first or vertical-first
            boolean horizontalFirst = RandomUtils.bernoulli(rng);
            int midX, midY;
            if (horizontalFirst) {
                // Move horizontally first, then vertically
                int minX = Math.min(start.x, end.x);
                int maxX = Math.max(start.x, end.x);
                // Randomize turn point within bounds to avoid predictable paths
                if (maxX - minX > 2) {
                    midX = RandomUtils.uniform(rng, minX + 1, maxX);
                } else {
                    midX = (start.x + end.x) / 2;
                }
                midY = start.y;
            } else {
                // Move vertically first, then horizontally
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
            // For long distances, use Z-path (two turns)
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
    
    /**
     * Digs a straight line between two points using step-by-step movement.
     * Handles both horizontal and vertical movement, converting walls/nothing to floors.
     * Moves horizontally first, then vertically.
     */
    private void digStraightLine(int x1, int y1, int x2, int y2) {
        int x = x1;
        int y = y1;
        
        // Step towards destination one tile at a time
        while (x != x2 || y != y2) {
            // Convert current position to floor if it's wall or nothing
            if (isValidPosition(x, y)) {
                if (world[x][y] == Tileset.NOTHING || world[x][y] == Tileset.WALL || world[x][y].description().equals("wall")) {
                    world[x][y] = Tileset.FLOOR;
                }
            }
            
            // Move one step towards destination
            // Prioritize horizontal movement, then vertical
            if (x < x2) x++;
            else if (x > x2) x--;
            else if (y < y2) y++;
            else if (y > y2) y--;
        }
        
        // Don't forget to dig the destination tile
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
    /**
     * Checks if all floor tiles are connected using Union-Find data structure.
     * Uses WeightedQuickUnionUF to efficiently detect disconnected components.
     * @return true if all floor tiles form a single connected component
     */
    private boolean isWorldConnected() {
        // Collect all floor tiles and map them to indices
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

        // Initialize with each tile as its own component
        connectivityTracker = new WeightedQuickUnionUF(floorTiles.size());
        int components = floorTiles.size();  // Start with max components

        // Check 4-directional neighbors (up, down, left, right)
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        // Union adjacent floor tiles
        // Each union reduces component count by 1
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
                        components--;  // Merged two components into one
                    }
                }
            }
        }

        // World is connected if only one component remains
        return components == 1;
    }

    /**
     * Automatically connects disconnected components by digging tunnels between them.
     * Called when ensureConnectivity() detects multiple components.
     * Connects each component to the next one in sequence, ensuring all become one connected graph.
     */
    private void connectDisconnectedComponents() {
        // Collect all floor tiles
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

        // Build Union-Find structure to identify components
        connectivityTracker = new WeightedQuickUnionUF(floorTiles.size());

        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        // Union adjacent floor tiles to identify connected components
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

        // Group floor tiles by their root component
        Map<Integer, List<Position>> components = new HashMap<>();
        for (int i = 0; i < floorTiles.size(); i++) {
            int root = connectivityTracker.find(i);
            components.putIfAbsent(root, new ArrayList<>());
            components.get(root).add(floorTiles.get(i));
        }

        // Connect each component to the next one in sequence
        // This ensures all components become one connected graph
        List<Integer> componentRoots = new ArrayList<>(components.keySet());
        for (int i = 0; i < componentRoots.size() - 1; i++) {
            List<Position> comp1 = components.get(componentRoots.get(i));
            List<Position> comp2 = components.get(componentRoots.get(i + 1));

            // Pick random positions from each component and dig tunnel between them
            Position p1 = comp1.get(RandomUtils.uniform(rng, comp1.size()));
            Position p2 = comp2.get(RandomUtils.uniform(rng, comp2.size()));
            digTunnelWithTurns(p1, p2);
        }
    }

    /**
     * Removes dead-end corridors (tiles with only one floor neighbor).
     * Iteratively prunes dead ends until no more can be removed.
     * Re-validates connectivity after removal to ensure world remains connected.
     */
    private void removeDeadEnds() {
        boolean changed = true;
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        // Iterate until no more dead ends can be removed
        while (changed) {
            changed = false;
            List<Position> toRemove = new ArrayList<>();

            // Scan for dead ends (tiles with exactly one floor neighbor)
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

                        // Only remove if tile has exactly one floor neighbor
                        // (this is a dead end)
                        if (floorNeighbors == 1) {
                            // Note: neighbor count check was here but not used
                            // Keeping for potential future use
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

            // Remove all identified dead ends
            for (Position pos : toRemove) {
                world[pos.x][pos.y] = Tileset.WALL;
                changed = true;
            }
        }

        // Re-check connectivity after dead end removal
        // If removal broke connectivity, repair it
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

    /**
     * Removes walls that are completely surrounded by floors (8 neighbors) or
     * completely isolated (0 floor neighbors). These are aesthetic artifacts
     * that don't serve a functional purpose.
     */
    private void removeOrphanedWalls() {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Position> toRemove = new ArrayList<>();
            
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (world[x][y] == Tileset.WALL) {
                        int floorCount = 0;
                        // Check all 8 neighbors (including diagonals)
                        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
                        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
                        
                        for (int i = 0; i < 8; i++) {
                            int nx = x + dx[i];
                            int ny = y + dy[i];
                            if (isValidPosition(nx, ny) && world[nx][ny] == Tileset.FLOOR) {
                                floorCount++;
                            }
                        }
                        
                        // Wall is orphaned if:
                        // - Surrounded by 8 floors (should be floor, not wall)
                        // - Has 0 floor neighbors (isolated, serves no purpose)
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

    /**
     * Gets the generated world as a 2D array of tiles.
     * @return the world grid (WIDTH x HEIGHT)
     */
    public TETile[][] getWorld() {
        return world;
    }

    /**
     * Gets the width of the world in tiles.
     * @return world width
     */
    public static int getWidth() {
        return WIDTH;
    }

    /**
     * Gets the height of the world in tiles.
     * @return world height
     */
    public static int getHeight() {
        return HEIGHT;
    }

    /**
     * Gets a valid starting position for the avatar.
     * Returns the first floor tile found (sorted by y, then x).
     * @return a valid floor position, or world center if no floors exist
     */
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
        if (isValidPosition(avatarPos.x, avatarPos.y)) {
            worldWithAvatar[avatarPos.x][avatarPos.y] = Tileset.AVATAR;
        }
        return worldWithAvatar;
    }

    /**
     * Checks if the avatar can move to the specified position.
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if position is valid and contains a floor tile
     */
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
