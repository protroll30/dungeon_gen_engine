package core;

public class Edge implements Comparable<Edge> {
    public final int cavern1Index;
    public final int cavern2Index;
    public final int distance;

    public Edge(int cavern1Index, int cavern2Index, int distance) {
        this.cavern1Index = cavern1Index;
        this.cavern2Index = cavern2Index;
        this.distance = distance;
    }

    @Override
    public int compareTo(Edge other) {
        return Integer.compare(this.distance, other.distance);
    }
}

