package core;

import edu.princeton.cs.algs4.In;
import edu.princeton.cs.algs4.Out;
import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;
import tileengine.Tileset;

import java.awt.Color;
import java.awt.Font;
import java.io.File;

public class Main {
    // World dimensions (full world size)
    private static final int WORLD_WIDTH = World.getWidth();  // 200 tiles
    private static final int WORLD_HEIGHT = World.getHeight();  // 120 tiles
    // Display/viewport dimensions (visible area)
    private static final int DISPLAY_WIDTH = 60;  // Visible viewport width
    private static final int DISPLAY_HEIGHT = 40;  // Visible viewport height
    private static final int HUD_HEIGHT = 2;  // Height reserved for HUD at top
    private static final int TOTAL_HEIGHT = DISPLAY_HEIGHT + HUD_HEIGHT;  // Total window height
    private static final String SAVE_FILE = "save.txt";  // Save file location

    /**
     * Main entry point for the dungeon generation game.
     * Initializes the renderer and runs the main menu loop.
     */
    public static void main(String[] args) {
        TERenderer ter = new TERenderer();
        ter.initialize(DISPLAY_WIDTH, TOTAL_HEIGHT, 0, HUD_HEIGHT);

        // Main menu loop - continues until user quits
        while (true) {
            char choice = showMainMenu(ter);
            if (choice == 'n') {
                long seed = showSeedEntry(ter);
                World world = new World(seed);
                Position avatarPos = world.getAvatarStartPosition();
                playGame(ter, world, avatarPos, seed);
            } else if (choice == 'l') {
                GameState savedState = loadGame(SAVE_FILE);
                if (savedState != null) {
                    World world = new World(savedState.seed);
                    Position avatarPos = savedState.avatarPos;
                    // Validate saved position is still a valid floor tile
                    // World generation is deterministic, but safety check prevents crashes
                    // if save file is corrupted or world parameters changed
                    if (!world.canMoveTo(avatarPos.x, avatarPos.y)) {
                        // Fallback to default start position
                        avatarPos = world.getAvatarStartPosition();
                    }
                    playGame(ter, world, avatarPos, savedState.seed);
                }
            } else if (choice == 't') {
                showTutorial(ter);
            } else if (choice == 'q') {
                System.exit(0);
            }
        }
    }

    /**
     * Displays the main menu and waits for user input.
     * @param ter the renderer to use for display
     * @return the character representing the user's menu choice ('n', 'l', 't', or 'q')
     */
    private static char showMainMenu(TERenderer ter) {
        StdDraw.clear(Color.BLACK);
        Font font = new Font("Monaco", Font.BOLD, 30);
        StdDraw.setFont(font);
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.7, "Pravit's World Generator");
        
        font = new Font("Monaco", Font.PLAIN, 20);
        StdDraw.setFont(font);
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.5, "New Game (N)");
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.4, "Load Game (L)");
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.35, "Tutorial (T)");
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.3, "Quit (Q)");
        
        StdDraw.show();

        // Wait for valid menu selection
        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 'n' || key == 'l' || key == 'q' || key == 't') {
                    return key;
                }
            }
            StdDraw.pause(20);
        }
    }

    /**
     * Displays seed entry screen and collects numeric input from user.
     * Accepts digits 0-9 and 's' to start.
     * If no seed is entered (empty string), defaults to current timestamp.
     * @param ter the renderer to use for display
     * @return the seed value (user input or current timestamp)
     */
    private static long showSeedEntry(TERenderer ter) {
        StringBuilder seedString = new StringBuilder();
        StdDraw.clear(Color.BLACK);
        Font font = new Font("Monaco", Font.PLAIN, 20);
        StdDraw.setFont(font);
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.6, "Enter Seed:");
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.4, "Press S to start");
        StdDraw.show();

        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 's') {
                    // If no seed entered, use current timestamp as default
                    if (seedString.length() == 0) {
                        return System.currentTimeMillis();
                    }
                    return Long.parseLong(seedString.toString());
                } else if (key >= '0' && key <= '9') {
                    // Append digit to seed string and update display
                    seedString.append(key);
                    StdDraw.clear(Color.BLACK);
                    StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.6, "Enter Seed:");
                    StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.4, "Press S to start");
                    StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.3, seedString.toString());
                    StdDraw.show();
                }
            }
            StdDraw.pause(20);
        }
    }

    /**
     * Displays the tutorial/controls screen.
     * Shows movement controls, command syntax, and other gameplay information.
     * @param ter the renderer to use for display
     */
    private static void showTutorial(TERenderer ter) {
        StdDraw.clear(Color.BLACK);
        Font titleFont = new Font("Monaco", Font.BOLD, 24);
        StdDraw.setFont(titleFont);
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.text(DISPLAY_WIDTH / 2.0, TOTAL_HEIGHT * 0.85, "Controls");
        
        Font font = new Font("Monaco", Font.PLAIN, 16);
        StdDraw.setFont(font);
        
        // Position text lines with consistent spacing
        double yPos = TOTAL_HEIGHT * 0.7;
        double lineSpacing = 1.2;
        
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "Movement:");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "W - Move Up");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "S - Move Down");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "A - Move Left");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "D - Move Right");
        yPos -= lineSpacing * 1.5;
        
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "Commands (press : first):");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, ":Q - Save and Quit");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, ":M - Save and Return to Menu");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, ":N - Enter Seed for New World");
        yPos -= lineSpacing * 1.5;
        
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "Other:");
        yPos -= lineSpacing;
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "Mouse Hover - View Tile Description");
        yPos -= lineSpacing * 2;
        
        Font smallFont = new Font("Monaco", Font.PLAIN, 12);
        StdDraw.setFont(smallFont);
        StdDraw.text(DISPLAY_WIDTH / 2.0, yPos, "Press any key to return to menu");
        
        StdDraw.show();
        
        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                StdDraw.nextKeyTyped();
                return;
            }
            StdDraw.pause(20);
        }
    }

    /**
     * Main game loop for interactive gameplay.
     * Handles player input, movement, camera scrolling, and rendering.
     * Supports commands (colon-prefixed) and WASD movement.
     * @param ter the renderer to use for display
     * @param world the generated world to explore
     * @param avatarPos starting position of the avatar
     * @param seed the world generation seed (used for saving)
     */
    private static void playGame(TERenderer ter, World world, Position avatarPos, long seed) {
        boolean colonPressed = false;
        int scrollThreshold = 8;  // Distance from edge before camera scrolls
        // Initialize camera to center on avatar
        int xOffset = Math.max(0, Math.min(WORLD_WIDTH - DISPLAY_WIDTH, avatarPos.x - DISPLAY_WIDTH / 2));
        int yOffset = Math.max(0, Math.min(WORLD_HEIGHT - DISPLAY_HEIGHT, avatarPos.y - DISPLAY_HEIGHT / 2));
        String saveMessage = null;  // Optional message to display in HUD
        int saveMessageFrames = 0;  // Frame counter for message display
        
        ter.initialize(DISPLAY_WIDTH, TOTAL_HEIGHT, 0, HUD_HEIGHT);

        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                
                // Command system: colon (:) followed by letter
                // :Q = save and quit, :M = save and menu, :N = new world
                if (colonPressed) {
                    // Process command after colon
                    if (key == 'q') {
                        saveGame(seed, avatarPos, SAVE_FILE);
                        System.exit(0);
                    } else if (key == 'm') {
                        saveGame(seed, avatarPos, SAVE_FILE);
                        return;
                    } else if (key == 'n') {
                        long newSeed = showSeedEntry(ter);
                        world = new World(newSeed);
                        avatarPos = world.getAvatarStartPosition();
                        seed = newSeed;
                        // Reset camera to center on new avatar position
                        xOffset = Math.max(0, Math.min(WORLD_WIDTH - DISPLAY_WIDTH, avatarPos.x - DISPLAY_WIDTH / 2));
                        yOffset = Math.max(0, Math.min(WORLD_HEIGHT - DISPLAY_HEIGHT, avatarPos.y - DISPLAY_HEIGHT / 2));
                        saveMessage = "New world generated";
                        saveMessageFrames = 60;
                    }
                    colonPressed = false;
                } else if (key == ':') {
                    // Colon pressed, wait for next key to form command
                    colonPressed = true;
                } else {
                    // Not a command, reset colon state and process movement
                    colonPressed = false;
                    Position newPos = avatarPos;
                    if (key == 'w') {
                        newPos = new Position(avatarPos.x, avatarPos.y + 1);
                    } else if (key == 's') {
                        newPos = new Position(avatarPos.x, avatarPos.y - 1);
                    } else if (key == 'a') {
                        newPos = new Position(avatarPos.x - 1, avatarPos.y);
                    } else if (key == 'd') {
                        newPos = new Position(avatarPos.x + 1, avatarPos.y);
                    }
                    
                    if (world.canMoveTo(newPos.x, newPos.y)) {
                        avatarPos = newPos;
                        
                        // Camera follows avatar with scroll threshold
                        // When avatar gets within scrollThreshold tiles of screen edge, camera scrolls
                        int avatarScreenX = avatarPos.x - xOffset;
                        int avatarScreenY = avatarPos.y - yOffset;
                        
                        // Scroll left if avatar approaches left edge
                        if (avatarScreenX < scrollThreshold) {
                            // Clamp to world boundaries
                            xOffset = Math.max(0, avatarPos.x - scrollThreshold);
                        }
                        // Scroll right if avatar approaches right edge
                        else if (avatarScreenX >= DISPLAY_WIDTH - scrollThreshold) {
                            // Calculate new offset, ensuring we don't show out-of-bounds
                            xOffset = Math.min(WORLD_WIDTH - DISPLAY_WIDTH, avatarPos.x - DISPLAY_WIDTH + scrollThreshold + 1);
                        }
                        
                        // Scroll down if avatar approaches bottom edge
                        if (avatarScreenY < scrollThreshold) {
                            yOffset = Math.max(0, avatarPos.y - scrollThreshold);
                        }
                        // Scroll up if avatar approaches top edge
                        else if (avatarScreenY >= DISPLAY_HEIGHT - scrollThreshold) {
                            yOffset = Math.min(WORLD_HEIGHT - DISPLAY_HEIGHT, avatarPos.y - DISPLAY_HEIGHT + scrollThreshold + 1);
                        }
                        
                        // Additional boundary check: if avatar somehow got outside viewport,
                        // immediately center camera on avatar
                        avatarScreenX = avatarPos.x - xOffset;
                        avatarScreenY = avatarPos.y - yOffset;
                        
                        if (avatarScreenX < 0) {
                            xOffset = Math.max(0, avatarPos.x);
                        } else if (avatarScreenX >= DISPLAY_WIDTH) {
                            xOffset = Math.min(WORLD_WIDTH - DISPLAY_WIDTH, avatarPos.x - DISPLAY_WIDTH + 1);
                        }
                        
                        if (avatarScreenY < 0) {
                            yOffset = Math.max(0, avatarPos.y);
                        } else if (avatarScreenY >= DISPLAY_HEIGHT) {
                            yOffset = Math.min(WORLD_HEIGHT - DISPLAY_HEIGHT, avatarPos.y - DISPLAY_HEIGHT + 1);
                        }
                    }
                }
            }

            TETile[][] worldWithAvatar = world.getWorldWithAvatar(avatarPos);
            ter.renderFrame(worldWithAvatar, xOffset, yOffset);
            drawHUD(world, avatarPos, xOffset, yOffset, saveMessage);
            if (saveMessageFrames > 0) {
                saveMessageFrames--;
                if (saveMessageFrames == 0) {
                    saveMessage = null;
                }
            }
            StdDraw.show();
            StdDraw.pause(20);
        }
    }

    /**
     * Draws the Heads-Up Display (HUD) at the top of the screen.
     * Shows tile description under mouse cursor and optional save confirmation message.
     * @param world the world to query for tile information
     * @param avatarPos current avatar position (unused but kept for potential future use)
     * @param xOffset current camera x offset
     * @param yOffset current camera y offset
     * @param saveMessage optional message to display (e.g., "Game saved")
     */
    private static void drawHUD(World world, Position avatarPos, int xOffset, int yOffset, String saveMessage) {
        // Get mouse position and convert to world coordinates
        double mouseX = StdDraw.mouseX();
        double mouseY = StdDraw.mouseY();
        
        // Convert screen coordinates to world tile coordinates
        int tileX = (int) mouseX + xOffset;
        int tileY = (int) (mouseY - HUD_HEIGHT) + yOffset;
        
        // Get tile description at mouse position
        String description = "nothing";
        if (tileX >= 0 && tileX < WORLD_WIDTH && tileY >= 0 && tileY < WORLD_HEIGHT && mouseY >= HUD_HEIGHT) {
            TETile[][] worldTiles = world.getWorld();
            TETile tile = worldTiles[tileX][tileY];
            if (tile != null) {
                description = tile.description();
            }
        }
        
        // Display tile description on left side of HUD
        StdDraw.setPenColor(Color.WHITE);
        Font font = new Font("Monaco", Font.PLAIN, 14);
        StdDraw.setFont(font);
        StdDraw.textLeft(1, TOTAL_HEIGHT - 0.5, description);
        
        // Display save message on right side if present
        if (saveMessage != null) {
            StdDraw.setPenColor(Color.GREEN);
            StdDraw.textRight(DISPLAY_WIDTH - 1, TOTAL_HEIGHT - 0.5, saveMessage);
        }
    }

    /**
     * Saves the current game state to a file.
     * File format (one value per line):
     *   - seed (long)
     *   - avatarX (int)
     *   - avatarY (int)
     * @param seed the world generation seed
     * @param avatarPos the current avatar position
     * @param filename the file to save to
     */
    private static void saveGame(long seed, Position avatarPos, String filename) {
        Out out = new Out(filename);
        out.println(seed);
        out.println(avatarPos.x);
        out.println(avatarPos.y);
        out.close();
    }

    /**
     * Loads game state from a file.
     * Expected file format (one value per line):
     *   - seed (long)
     *   - avatarX (int)
     *   - avatarY (int)
     * @param filename the file to load from
     * @return GameState object if file exists and is valid, null otherwise
     */
    private static GameState loadGame(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            return null;
        }
        
        In in = new In(file);
        if (!in.hasNextLine()) {
            in.close();
            return null;
        }
        
        try {
            // Parse file contents with validation
            long seed = Long.parseLong(in.readLine());
            if (!in.hasNextLine()) {
                in.close();
                return null;
            }
            int avatarX = Integer.parseInt(in.readLine());
            if (!in.hasNextLine()) {
                in.close();
                return null;
            }
            int avatarY = Integer.parseInt(in.readLine());
            in.close();
            return new GameState(seed, new Position(avatarX, avatarY));
        } catch (NumberFormatException e) {
            // File corrupted or invalid format
            in.close();
            return null;
        }
    }

    /**
     * Immutable container for game state data.
     * Stores the world seed and avatar position for save/load functionality.
     */
    private static class GameState {
        final long seed;
        final Position avatarPos;

        GameState(long seed, Position avatarPos) {
            this.seed = seed;
            this.avatarPos = avatarPos;
        }
    }
}
