import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph algorithms: DSU, Kruskal, Prim, Kosaraju SCC, DFS topological order,
 * DAG shortest paths and a route-reconstruction helper.
 *
 * Every method takes plain arrays (row i of edges is edge ID i) and allocates
 * all of its state locally, so nothing survives from one request to the next.
 * All traversals use explicit stacks, never recursion.
 */
public final class Algorithms {
    private Algorithms() {
    }

    /** Shifts weights in [-1e9, 1e9] to [0, 2e9], which fits in 31 bits. */
    private static final long WEIGHT_OFFSET = 1_000_000_000L;

    // ------------------------------------------------------------------
    // DSU
    // ------------------------------------------------------------------

    /** Disjoint-set union with path compression and union by size. */
    static final class DisjointSets {
        private final int[] parent;
        private final int[] size;
        private int components;
        private int largest;

        DisjointSets(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
            components = n;
            largest = n > 0 ? 1 : 0;
        }

        /** Iterative find: locate the root, then point every vertex on the path at it. */
        int find(int x) {
            int root = x;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[x] != root) {
                int next = parent[x];
                parent[x] = root;
                x = next;
            }
            return root;
        }

        /** Attaches the smaller tree under the larger one; returns false if already joined. */
        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }
            if (size[ra] < size[rb]) {
                int t = ra;
                ra = rb;
                rb = t;
            }
            parent[rb] = ra;
            size[ra] += size[rb];
            components--;
            largest = Math.max(largest, size[ra]);
            return true;
        }

        int components() {
            return components;
        }

        int largest() {
            return largest;
        }
    }

    /** DSU with path compression and union by size; returns states [components, largest] after each operation. */
    static Map<String, Object> dsu(int n, int[][] operations) {
        DisjointSets sets = new DisjointSets(n);
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            sets.union(operations[i][0], operations[i][1]);
            states[i] = new int[] {sets.components(), sets.largest()};
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", states);
        return result;
    }

    // ------------------------------------------------------------------
    // Minimum spanning forest
    // ------------------------------------------------------------------

    /** Kruskal: edges sorted by weight, accepted when find(u) != find(v); returns cost, edge_ids, components. */
    static Map<String, Object> kruskal(int n, int[][] edges) {
        int m = edges.length;
        // Key = shifted weight in the high 32 bits, edge ID in the low 32 bits.
        // Sorting the primitive keys orders edges by weight, then by ID.
        long[] keys = new long[m];
        for (int id = 0; id < m; id++) {
            keys[id] = ((edges[id][2] + WEIGHT_OFFSET) << 32) | id;
        }
        Arrays.sort(keys);

        DisjointSets sets = new DisjointSets(n);
        int[] chosen = new int[Math.max(0, n - 1)];
        int count = 0;
        long cost = 0;
        for (long key : keys) {
            int id = (int) key;
            int[] e = edges[id];
            // A self-loop always has find(u) == find(v), so it is never selected.
            if (sets.union(e[0], e[1])) {
                chosen[count++] = id;
                cost += e[2];
            }
        }
        return forestResult(cost, Arrays.copyOf(chosen, count), n - count);
    }

    /** Prim with a lazy binary heap, restarted from every unvisited vertex; returns cost, edge_ids, components. */
    static Map<String, Object> prim(int n, int[][] edges) {
        int[][] adjacency = undirectedAdjacency(n, edges);
        int[] start = adjacency[0];
        int[] arcs = adjacency[1];

        boolean[] visited = new boolean[n];
        LongHeap heap = new LongHeap(arcs.length);
        int[] chosen = new int[Math.max(0, n - 1)];
        int count = 0;
        int components = 0;
        long cost = 0;

        for (int root = 0; root < n; root++) {
            if (visited[root]) {
                continue;
            }
            // A new tree starts here: one more component of the input graph.
            components++;
            int vertex = root;
            while (true) {
                visited[vertex] = true;
                for (int i = start[vertex]; i < start[vertex + 1]; i++) {
                    int id = arcs[i];
                    int other = edges[id][0] ^ edges[id][1] ^ vertex;
                    if (!visited[other]) {
                        heap.push(((edges[id][2] + WEIGHT_OFFSET) << 32) | id);
                    }
                }
                // Lazy deletion: skip edges whose endpoints are both in the tree.
                vertex = -1;
                while (!heap.isEmpty()) {
                    int id = (int) heap.pop();
                    int u = edges[id][0];
                    int v = edges[id][1];
                    if (visited[u] && visited[v]) {
                        continue;
                    }
                    chosen[count++] = id;
                    cost += edges[id][2];
                    vertex = visited[u] ? v : u;
                    break;
                }
                if (vertex < 0) {
                    break;
                }
            }
        }
        return forestResult(cost, Arrays.copyOf(chosen, count), components);
    }

    private static Map<String, Object> forestResult(long cost, int[] edgeIds, int components) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", edgeIds);
        result.put("components", components);
        return result;
    }

    /** Minimal binary min-heap of primitive longs, used by Prim. */
    static final class LongHeap {
        private long[] data;
        private int size;

        LongHeap(int capacity) {
            data = new long[Math.max(1, capacity)];
        }

        boolean isEmpty() {
            return size == 0;
        }

        void push(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (data[p] <= value) {
                    break;
                }
                data[i] = data[p];
                i = p;
            }
            data[i] = value;
        }

        long pop() {
            long top = data[0];
            long last = data[--size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && data[child + 1] < data[child]) {
                    child++;
                }
                if (data[child] >= last) {
                    break;
                }
                data[i] = data[child];
                i = child;
            }
            data[i] = last;
            return top;
        }
    }

    // ------------------------------------------------------------------
    // Adjacency lists in compressed (CSR) form
    // ------------------------------------------------------------------

    /**
     * Directed adjacency: arcs[start[u] .. start[u+1]) are the IDs of the edges
     * leaving u (or entering u when reversed). Edge IDs keep their input position.
     */
    static int[][] directedAdjacency(int n, int[][] edges, boolean reversed) {
        int from = reversed ? 1 : 0;
        int[] start = new int[n + 1];
        for (int[] e : edges) {
            start[e[from] + 1]++;
        }
        for (int u = 0; u < n; u++) {
            start[u + 1] += start[u];
        }
        int[] fill = Arrays.copyOf(start, n);
        int[] arcs = new int[edges.length];
        for (int id = 0; id < edges.length; id++) {
            arcs[fill[edges[id][from]]++] = id;
        }
        return new int[][] {start, arcs};
    }

    /** Undirected adjacency for Prim: each non-loop edge appears at both endpoints. */
    static int[][] undirectedAdjacency(int n, int[][] edges) {
        int[] start = new int[n + 1];
        for (int[] e : edges) {
            if (e[0] != e[1]) {
                start[e[0] + 1]++;
                start[e[1] + 1]++;
            }
        }
        for (int u = 0; u < n; u++) {
            start[u + 1] += start[u];
        }
        int[] fill = Arrays.copyOf(start, n);
        int[] arcs = new int[start[n]];
        for (int id = 0; id < edges.length; id++) {
            int[] e = edges[id];
            if (e[0] != e[1]) {
                arcs[fill[e[0]]++] = id;
                arcs[fill[e[1]]++] = id;
            }
        }
        return new int[][] {start, arcs};
    }

    // ------------------------------------------------------------------
    // Kosaraju SCC
    // ------------------------------------------------------------------

    /** Kosaraju SCC; returns component labels and the exact deduplicated condensation. */
    static Map<String, Object> scc(int n, int[][] edges) {
        int[] component = kosarajuLabels(n, edges);

        // Each cross-component pair is reported once; the key packs (from, to).
        Set<Long> seen = new HashSet<>();
        List<int[]> condensation = new ArrayList<>();
        for (int[] e : edges) {
            int a = component[e[0]];
            int b = component[e[1]];
            if (a != b && seen.add(((long) a << 32) | b)) {
                condensation.add(new int[] {a, b});
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", component);
        result.put("condensation", condensation);
        return result;
    }

    /** Returns one label per vertex; labels are 0..k-1 in discovery order of pass two. */
    static int[] kosarajuLabels(int n, int[][] edges) {
        int[][] forward = directedAdjacency(n, edges, false);
        int[][] backward = directedAdjacency(n, edges, true);

        // Pass 1 on G: record vertices in increasing finish time.
        int[] finishOrder = new int[n];
        int finished = 0;
        boolean[] visited = new boolean[n];
        int[] stack = new int[n];
        int[] next = new int[n]; // next arc index to scan for each vertex on the stack
        int[] fStart = forward[0];
        int[] fArcs = forward[1];
        for (int root = 0; root < n; root++) {
            if (visited[root]) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            visited[root] = true;
            next[root] = fStart[root];
            while (top > 0) {
                int u = stack[top - 1];
                if (next[u] < fStart[u + 1]) {
                    int v = edges[fArcs[next[u]++]][1];
                    if (!visited[v]) {
                        visited[v] = true;
                        next[v] = fStart[v];
                        stack[top++] = v;
                    }
                } else {
                    top--;
                    finishOrder[finished++] = u;
                }
            }
        }

        // Pass 2 on G^T in decreasing finish time: each tree is one SCC.
        int[] component = new int[n];
        Arrays.fill(component, -1);
        int[] bStart = backward[0];
        int[] bArcs = backward[1];
        int label = 0;
        for (int i = n - 1; i >= 0; i--) {
            int root = finishOrder[i];
            if (component[root] >= 0) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            component[root] = label;
            next[root] = bStart[root];
            while (top > 0) {
                int u = stack[top - 1];
                if (next[u] < bStart[u + 1]) {
                    int v = edges[bArcs[next[u]++]][0];
                    if (component[v] < 0) {
                        component[v] = label;
                        next[v] = bStart[v];
                        stack[top++] = v;
                    }
                } else {
                    top--;
                }
            }
            label++;
        }
        return component;
    }

    // ------------------------------------------------------------------
    // Topological ordering
    // ------------------------------------------------------------------

    private static final byte UNVISITED = 0;
    private static final byte ACTIVE = 1;
    private static final byte FINISHED = 2;

    /** Three-state DFS topological order; returns acyclic and order (empty when cyclic). */
    static Map<String, Object> topo(int n, int[][] edges) {
        int[] order = topologicalOrder(n, edges, directedAdjacency(n, edges, false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", order != null);
        result.put("order", order != null ? order : new int[0]);
        return result;
    }

    /**
     * Iterative three-state DFS over every vertex. Returns the reverse finish
     * order, or null as soon as an edge reaches an ACTIVE vertex (a cycle).
     * next[u] keeps the scan position of each suspended vertex, so every arc
     * is examined at most once.
     */
    static int[] topologicalOrder(int n, int[][] edges, int[][] adjacency) {
        int[] start = adjacency[0];
        int[] arcs = adjacency[1];
        byte[] state = new byte[n];
        int[] stack = new int[n];
        int[] next = new int[n];
        int[] order = new int[n];
        int position = n; // filled from the back: reverse finish order

        for (int root = 0; root < n; root++) {
            if (state[root] != UNVISITED) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            state[root] = ACTIVE;
            next[root] = start[root];
            while (top > 0) {
                int u = stack[top - 1];
                if (next[u] < start[u + 1]) {
                    int v = edges[arcs[next[u]++]][1];
                    if (state[v] == ACTIVE) {
                        return null; // back edge (a self-loop is the case v == u)
                    }
                    if (state[v] == UNVISITED) {
                        state[v] = ACTIVE;
                        next[v] = start[v];
                        stack[top++] = v;
                    }
                } else {
                    top--;
                    state[u] = FINISHED;
                    order[--position] = u;
                }
            }
        }
        return order;
    }

    // ------------------------------------------------------------------
    // DAG shortest paths
    // ------------------------------------------------------------------

    /** Whole-graph cycle check, then relaxation in topological order; returns acyclic, distance, parent_edge. */
    static Map<String, Object> dag(int n, int source, int[][] edges) {
        int[][] adjacency = directedAdjacency(n, edges, false);
        int[] order = topologicalOrder(n, edges, adjacency);
        Map<String, Object> result = new LinkedHashMap<>();
        if (order == null) {
            result.put("acyclic", false);
            result.put("distance", new Long[0]);
            result.put("parent_edge", new int[0]);
            return result;
        }

        int[] start = adjacency[0];
        int[] arcs = adjacency[1];
        long[] dist = new long[n];
        boolean[] reached = new boolean[n];
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        reached[source] = true;

        // Vertices before the source in the order are unreachable and stay unreached.
        for (int u : order) {
            if (!reached[u]) {
                continue;
            }
            for (int i = start[u]; i < start[u + 1]; i++) {
                int id = arcs[i];
                int v = edges[id][1];
                long candidate = dist[u] + edges[id][2];
                if (!reached[v] || candidate < dist[v]) {
                    reached[v] = true;
                    dist[v] = candidate;
                    parentEdge[v] = id;
                }
            }
        }

        Long[] distance = new Long[n];
        for (int v = 0; v < n; v++) {
            distance[v] = reached[v] ? dist[v] : null;
        }
        result.put("acyclic", true);
        result.put("distance", distance);
        result.put("parent_edge", parentEdge);
        return result;
    }

    /**
     * Route helper: rebuilds the vertex sequence source -> target by following
     * parent_edge IDs backwards and reversing. Runs in O(L) for a route of L vertices.
     *
     * No-route result: an EMPTY list when target is unreachable (parent_edge[target]
     * is -1 and target is not the source). A reachable route always contains at
     * least one vertex, so the empty list cannot be confused with a real route.
     * Expects the parent_edge array of an acyclic dag result.
     */
    static List<Integer> route(int[][] edges, int[] parentEdge, int source, int target) {
        List<Integer> path = new ArrayList<>();
        if (target != source && parentEdge[target] < 0) {
            return path;
        }
        int vertex = target;
        path.add(vertex);
        while (vertex != source) {
            vertex = edges[parentEdge[vertex]][0];
            path.add(vertex);
        }
        java.util.Collections.reverse(path);
        return path;
    }
}
