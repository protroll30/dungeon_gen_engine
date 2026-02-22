package core;

/**
 * Represents an edge between two caves in the connectivity graph.
 * Used for Minimum Spanning Tree (MST) construction to connect all caves.
 * Edges are comparable by distance, allowing them to be sorted for Kruskal's algorithm.
 */
public class Edge implements Comparable<Edge> {
    public final int cavern1Index;
    public final int cavern2Index;
    public final int distance;

    public Edge(int cavern1Index, int cavern2Index, int distance) {
        this.cavern1Index = cavern1Index;
        this.cavern2Index = cavern2Index;
        this.distance = distance;
    }

    /**
     * Compares edges by distance for sorting in MST algorithm.
     * @param other the edge to compare to
     * @return negative if this edge is shorter, positive if longer, 0 if equal
     */
    @Override
    public int compareTo(Edge other) {
        return Integer.compare(this.distance, other.distance);
    }
}

