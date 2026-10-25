import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GraphTests {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        long started = System.nanoTime();
        dsuTests();
        forestTests();
        sccTests();
        topoTests();
        dagTests();
        routeHelperTests();
        witnessTests();
        stateResetTest();
        largeTests();
        System.out.printf(java.util.Locale.ROOT, "%nSUMMARY: %d passed, %d failed (%.1f s)%n",
                passed, failed, (System.nanoTime() - started) / 1e9);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    private static void checkAll(boolean[] results, String name) {
        int bad = 0;
        for (boolean ok : results) {
            if (!ok) {
                bad++;
            }
        }
        check(bad == 0, name + " (" + results.length + " cases" + (bad > 0 ? ", " + bad + " wrong" : "") + ")");
    }

    private static void dsuTests() {
        System.out.println("== DSU");
        check(Arrays.deepEquals(states(Algorithms.dsu(0, new int[0][])), new int[0][]),
                "dsu n=0 gives states=[]");
        check(Arrays.deepEquals(states(Algorithms.dsu(4, new int[][] {{0, 1}, {1, 2}, {0, 2}, {3, 3}})),
                new int[][] {{3, 2}, {2, 3}, {2, 3}, {2, 3}}), "dsu published example");
        check(Arrays.deepEquals(states(Algorithms.dsu(1, new int[][] {{0, 0}, {0, 0}})),
                new int[][] {{1, 1}, {1, 1}}), "dsu self-unions on a single vertex keep [1,1]");
        check(Arrays.deepEquals(states(Algorithms.dsu(5,
                new int[][] {{0, 0}, {0, 1}, {1, 0}, {2, 3}, {3, 4}, {4, 2}, {1, 4}})),
                new int[][] {{5, 1}, {4, 2}, {4, 2}, {3, 2}, {2, 3}, {2, 3}, {1, 5}}),
                "dsu self-union, repeated union and a final merge");

        Random random = new Random(11);
        boolean[] results = new boolean[1500];
        for (int t = 0; t < results.length; t++) {
            int n = random.nextInt(9);
            int q = n == 0 ? 0 : random.nextInt(15);
            int[][] ops = new int[q][];
            for (int i = 0; i < q; i++) {
                ops[i] = new int[] {random.nextInt(n), random.nextInt(n)};
            }
            results[t] = Arrays.deepEquals(states(Algorithms.dsu(n, ops)), bruteDsu(n, ops));
        }
        checkAll(results, "dsu random small graphs vs naive relabelling");
    }

    private static int[][] bruteDsu(int n, int[][] ops) {
        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            label[i] = i;
        }
        int[][] out = new int[ops.length][];
        for (int i = 0; i < ops.length; i++) {
            int a = label[ops[i][0]];
            int b = label[ops[i][1]];
            for (int v = 0; v < n; v++) {
                if (label[v] == b) {
                    label[v] = a;
                }
            }
            int[] count = new int[n];
            int components = 0;
            int largest = 0;
            for (int v = 0; v < n; v++) {
                if (count[label[v]]++ == 0) {
                    components++;
                }
                largest = Math.max(largest, count[label[v]]);
            }
            out[i] = new int[] {components, largest};
        }
        return out;
    }

    private static void forestTests() {
        System.out.println("== Kruskal / Prim");
        int[][] example = {{0, 1, 4}, {1, 2, 2}, {0, 2, 3}};
        int[][] mixed = {{0, 1, 5}, {1, 2, -3}, {0, 2, 1}, {3, 4, 7}, {3, 4, 2}, {4, 4, -100}};
        int[][] big = {{0, 1, 1_000_000_000}, {1, 2, 1_000_000_000}, {2, 3, 1_000_000_000},
                       {0, 3, 1_000_000_000}};
        int[][] negativeBig = {{0, 1, -1_000_000_000}, {1, 2, -1_000_000_000}, {2, 3, -1_000_000_000}};
        for (String method : new String[] {"kruskal", "prim"}) {
            check(forestValid(0, new int[0][], forest(method, 0, new int[0][]), 0, 0),
                    method + " n=0 gives cost 0, no edges, 0 components");
            check(forestValid(4, new int[0][], forest(method, 4, new int[0][]), 0, 4),
                    method + " four isolated vertices give 4 components");
            check(forestValid(4, example, forest(method, 4, example), 5, 2),
                    method + " published example (cost 5, 2 components)");
            check(forestValid(6, mixed, forest(method, 6, mixed), 0, 3),
                    method + " negative weights, parallel edges, loop -100 ignored, isolated 5 (cost 0)");
            check(forestValid(4, big, forest(method, 4, big), 3_000_000_000L, 1),
                    method + " total 3e9 exceeds 32 bits, tied weights");
            check(forestValid(4, negativeBig, forest(method, 4, negativeBig), -3_000_000_000L, 1),
                    method + " total -3e9 below 32-bit range");
        }

        Random random = new Random(22);
        boolean[] results = new boolean[1500];
        for (int t = 0; t < results.length; t++) {
            int n = random.nextInt(7);
            int m = n == 0 ? 0 : random.nextInt(10);
            int[][] edges = new int[m][];
            for (int i = 0; i < m; i++) {
                edges[i] = new int[] {random.nextInt(n), random.nextInt(n), random.nextInt(7) - 3};
            }
            long[] reference = bruteForest(n, edges);
            results[t] = forestValid(n, edges, Algorithms.kruskal(n, edges), reference[0], (int) reference[1])
                    && forestValid(n, edges, Algorithms.prim(n, edges), reference[0], (int) reference[1]);
        }
        checkAll(results, "kruskal and prim random small graphs vs subset enumeration");
    }

    private static Map<String, Object> forest(String method, int n, int[][] edges) {
        return method.equals("kruskal") ? Algorithms.kruskal(n, edges) : Algorithms.prim(n, edges);
    }

    static boolean forestValid(int n, int[][] edges, Map<String, Object> result,
                               long expectedCost, int expectedComponents) {
        long cost = (Long) result.get("cost");
        int components = (Integer) result.get("components");
        int[] ids = (int[]) result.get("edge_ids");
        if (cost != expectedCost || components != expectedComponents || ids.length != n - components) {
            return false;
        }
        int[] root = new int[n];
        for (int i = 0; i < n; i++) {
            root[i] = i;
        }
        Set<Integer> used = new HashSet<>();
        long sum = 0;
        for (int id : ids) {
            if (id < 0 || id >= edges.length || !used.add(id)) {
                return false;
            }
            int a = findRoot(root, edges[id][0]);
            int b = findRoot(root, edges[id][1]);
            if (a == b) {
                return false;
            }
            root[a] = b;
            sum += edges[id][2];
        }
        if (sum != cost) {
            return false;
        }
        for (int[] e : edges) {
            if (findRoot(root, e[0]) != findRoot(root, e[1])) {
                return false;
            }
        }
        return true;
    }

    private static int findRoot(int[] root, int x) {
        while (root[x] != x) {
            root[x] = root[root[x]];
            x = root[x];
        }
        return x;
    }

    private static long[] bruteForest(int n, int[][] edges) {
        int components = countUndirectedComponents(n, edges);
        int need = n - components;
        long best = Long.MAX_VALUE;
        for (int mask = 0; mask < (1 << edges.length); mask++) {
            if (Integer.bitCount(mask) != need) {
                continue;
            }
            int[] root = new int[n];
            for (int i = 0; i < n; i++) {
                root[i] = i;
            }
            boolean acyclic = true;
            long cost = 0;
            for (int i = 0; i < edges.length && acyclic; i++) {
                if ((mask >> i & 1) == 1) {
                    int a = findRoot(root, edges[i][0]);
                    int b = findRoot(root, edges[i][1]);
                    acyclic = a != b;
                    root[a] = b;
                    cost += edges[i][2];
                }
            }
            if (acyclic) {
                best = Math.min(best, cost);
            }
        }
        return new long[] {best, components};
    }

    private static int countUndirectedComponents(int n, int[][] edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            adj.get(e[0]).add(e[1]);
            adj.get(e[1]).add(e[0]);
        }
        boolean[] seen = new boolean[n];
        int components = 0;
        for (int s = 0; s < n; s++) {
            if (seen[s]) {
                continue;
            }
            components++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            seen[s] = true;
            while (!queue.isEmpty()) {
                for (int v : adj.get(queue.poll())) {
                    if (!seen[v]) {
                        seen[v] = true;
                        queue.add(v);
                    }
                }
            }
        }
        return components;
    }

    private static void sccTests() {
        System.out.println("== Kosaraju SCC");
        check(sccValid(0, new int[0][], Algorithms.scc(0, new int[0][])), "scc n=0 gives empty arrays");
        check(sccValid(3, new int[0][], Algorithms.scc(3, new int[0][]))
                        && distinctLabels(Algorithms.scc(3, new int[0][])) == 3,
                "scc isolated vertices are three separate SCCs");
        int[][] edges = {{0, 1}, {1, 2}, {2, 0}, {2, 3}, {3, 4}, {4, 3}, {4, 4}, {0, 1}, {5, 5}, {1, 3}};
        Map<String, Object> result = Algorithms.scc(6, edges);
        int[] c = (int[]) result.get("component");
        check(sccValid(6, edges, result) && c[0] == c[1] && c[1] == c[2] && c[3] == c[4]
                        && distinctLabels(result) == 3 && condensation(result).size() == 1,
                "scc duplicates, self-loops and two edges between the same SCCs give one condensation pair");

        Random random = new Random(33);
        boolean[] results = new boolean[1500];
        for (int t = 0; t < results.length; t++) {
            int n = random.nextInt(9);
            int m = n == 0 ? 0 : random.nextInt(16);
            int[][] g = randomDirected(random, n, m);
            Map<String, Object> answer = Algorithms.scc(n, g);
            results[t] = sccValid(n, g, answer) && sameScc(n, g, (int[]) answer.get("component"));
        }
        checkAll(results, "scc random small graphs vs transitive closure");
    }

    private static int distinctLabels(Map<String, Object> result) {
        Set<Integer> labels = new HashSet<>();
        for (int label : (int[]) result.get("component")) {
            labels.add(label);
        }
        return labels.size();
    }

    @SuppressWarnings("unchecked")
    private static List<int[]> condensation(Map<String, Object> result) {
        return (List<int[]>) result.get("condensation");
    }

    private static boolean sameScc(int n, int[][] edges, int[] label) {
        boolean[][] reach = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            reach[i][i] = true;
        }
        for (int[] e : edges) {
            reach[e[0]][e[1]] = true;
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    reach[i][j] |= reach[i][k] && reach[k][j];
                }
            }
        }
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                if ((label[u] == label[v]) != (reach[u][v] && reach[v][u])) {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    static boolean sccValid(int n, int[][] edges, Map<String, Object> result) {
        int[] label = (int[]) result.get("component");
        List<int[]> pairs = (List<int[]>) result.get("condensation");
        if (label.length != n) {
            return false;
        }
        for (int l : label) {
            if (l < 0) {
                return false;
            }
        }
        Set<Long> expected = new HashSet<>();
        for (int[] e : edges) {
            if (label[e[0]] != label[e[1]]) {
                expected.add(((long) label[e[0]] << 32) | label[e[1]]);
            }
        }
        Set<Long> given = new HashSet<>();
        for (int[] p : pairs) {
            if (p.length != 2 || !given.add(((long) p[0] << 32) | p[1])) {
                return false;
            }
        }
        if (!given.equals(expected)) {
            return false;
        }

        List<List<Integer>> out = new ArrayList<>();
        List<List<Integer>> in = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
            in.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            out.get(e[0]).add(e[1]);
            in.get(e[1]).add(e[0]);
        }
        java.util.Map<Integer, Integer> classSize = new java.util.HashMap<>();
        for (int l : label) {
            classSize.merge(l, 1, Integer::sum);
        }
        for (List<List<Integer>> graph : List.of(out, in)) {
            boolean[] seen = new boolean[n];
            Set<Integer> done = new HashSet<>();
            for (int s = 0; s < n; s++) {
                if (!done.add(label[s])) {
                    continue;
                }
                int reached = 0;
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(s);
                seen[s] = true;
                while (!queue.isEmpty()) {
                    int u = queue.poll();
                    reached++;
                    for (int v : graph.get(u)) {
                        if (!seen[v] && label[v] == label[s]) {
                            seen[v] = true;
                            queue.add(v);
                        }
                    }
                }
                if (reached != classSize.get(label[s])) {
                    return false;
                }
            }
        }

        java.util.Map<Integer, List<Integer>> next = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> indegree = new java.util.HashMap<>();
        for (int l : classSize.keySet()) {
            next.put(l, new ArrayList<>());
            indegree.put(l, 0);
        }
        for (int[] p : pairs) {
            next.get(p[0]).add(p[1]);
            indegree.merge(p[1], 1, Integer::sum);
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        indegree.forEach((l, d) -> {
            if (d == 0) {
                queue.add(l);
            }
        });
        int removed = 0;
        while (!queue.isEmpty()) {
            int l = queue.poll();
            removed++;
            for (int r : next.get(l)) {
                if (indegree.merge(r, -1, Integer::sum) == 0) {
                    queue.add(r);
                }
            }
        }
        return removed == classSize.size();
    }

    private static void topoTests() {
        System.out.println("== Topological order");
        check(topoValid(0, new int[0][], Algorithms.topo(0, new int[0][]), true), "topo n=0 is acyclic with order=[]");
        int[][] dagEdges = {{3, 1}, {1, 0}, {3, 0}, {3, 0}, {2, 4}};
        check(topoValid(5, dagEdges, Algorithms.topo(5, dagEdges), true),
                "topo DAG with a duplicate edge and two weakly connected parts");
        check(topoValid(1, new int[][] {{0, 0}}, Algorithms.topo(1, new int[][] {{0, 0}}), false),
                "topo self-loop is a cycle");
        int[][] laterCycle = {{0, 1}, {2, 3}, {3, 4}, {4, 2}};
        check(topoValid(5, laterCycle, Algorithms.topo(5, laterCycle), false),
                "topo cycle in a second component is found");
        int[][] crossEdge = {{0, 1}, {0, 2}, {2, 1}};
        check(topoValid(3, crossEdge, Algorithms.topo(3, crossEdge), true),
                "topo edge into a FINISHED vertex is not a cycle");

        Random random = new Random(44);
        boolean[] results = new boolean[1500];
        for (int t = 0; t < results.length; t++) {
            int n = random.nextInt(9);
            int m = n == 0 ? 0 : random.nextInt(14);
            int[][] g = random.nextBoolean() ? randomDag(random, n, m) : randomDirected(random, n, m);
            results[t] = topoValid(n, g, Algorithms.topo(n, g), kahnAcyclic(n, g));
        }
        checkAll(results, "topo random small graphs vs Kahn's algorithm");
    }

    static boolean kahnAcyclic(int n, int[][] edges) {
        int[] indegree = new int[n];
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            out.get(e[0]).add(e[1]);
            indegree[e[1]]++;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int v = 0; v < n; v++) {
            if (indegree[v] == 0) {
                queue.add(v);
            }
        }
        int removed = 0;
        while (!queue.isEmpty()) {
            removed++;
            for (int v : out.get(queue.poll())) {
                if (--indegree[v] == 0) {
                    queue.add(v);
                }
            }
        }
        return removed == n;
    }

    static boolean topoValid(int n, int[][] edges, Map<String, Object> result, boolean expectedAcyclic) {
        boolean acyclic = (Boolean) result.get("acyclic");
        int[] order = (int[]) result.get("order");
        if (acyclic != expectedAcyclic) {
            return false;
        }
        if (!acyclic) {
            return order.length == 0;
        }
        if (order.length != n) {
            return false;
        }
        int[] position = new int[n];
        Arrays.fill(position, -1);
        for (int i = 0; i < n; i++) {
            if (order[i] < 0 || order[i] >= n || position[order[i]] != -1) {
                return false;
            }
            position[order[i]] = i;
        }
        for (int[] e : edges) {
            if (position[e[0]] >= position[e[1]]) {
                return false;
            }
        }
        return true;
    }

    private static void dagTests() {
        System.out.println("== DAG shortest paths");
        int[][] example = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        Map<String, Object> r = Algorithms.dag(5, 0, example);
        check(dagValid(5, 0, example, r)
                        && Arrays.equals((Long[]) r.get("distance"), new Long[] {0L, 3L, -4L, 0L, null}),
                "dag published example distances [0,3,-4,0,null]");
        check(dagValid(1, 0, new int[0][], Algorithms.dag(1, 0, new int[0][])), "dag single vertex");
        int[][] unreachableCycle = {{0, 1, 1}, {2, 3, 1}, {3, 2, 1}};
        check(dagValid(5, 0, unreachableCycle, Algorithms.dag(5, 0, unreachableCycle))
                        && !(Boolean) Algorithms.dag(5, 0, unreachableCycle).get("acyclic"),
                "dag cycle unreachable from the source still makes acyclic=false");
        check(!(Boolean) Algorithms.dag(1, 0, new int[][] {{0, 0, 5}}).get("acyclic"),
                "dag self-loop makes the graph cyclic");
        int[][] minusOne = {{1, 2, -1}, {0, 1, 5}, {2, 3, 2}, {1, 3, 4}};
        Map<String, Object> m1 = Algorithms.dag(5, 1, minusOne);
        check(dagValid(5, 1, minusOne, m1)
                        && Arrays.equals((Long[]) m1.get("distance"), new Long[] {null, 0L, -1L, 1L, null}),
                "dag distance -1 is finite; vertex 0 with an edge into the source stays null");
        int[][] big = {{0, 1, 1_000_000_000}, {1, 2, 1_000_000_000}, {2, 3, 1_000_000_000}};
        check(Arrays.equals((Long[]) Algorithms.dag(4, 0, big).get("distance"),
                new Long[] {0L, 1_000_000_000L, 2_000_000_000L, 3_000_000_000L}), "dag distance 3e9 beyond 32 bits");

        Random random = new Random(55);
        boolean[] results = new boolean[1500];
        for (int t = 0; t < results.length; t++) {
            int n = 1 + random.nextInt(8);
            int m = random.nextInt(14);
            int[][] g = random.nextInt(4) == 0 ? withWeights(random, randomDirected(random, n, m))
                    : withWeights(random, randomDag(random, n, m));
            int source = random.nextInt(n);
            Map<String, Object> answer = Algorithms.dag(n, source, g);
            results[t] = dagValid(n, source, g, answer)
                    && (!(Boolean) answer.get("acyclic")
                        || Arrays.equals((Long[]) answer.get("distance"), bellmanFord(n, source, g)));
        }
        checkAll(results, "dag random small graphs vs Bellman-Ford");
    }

    private static Long[] bellmanFord(int n, int source, int[][] edges) {
        Long[] d = new Long[n];
        d[source] = 0L;
        for (int round = 0; round < n; round++) {
            for (int[] e : edges) {
                if (d[e[0]] != null && (d[e[1]] == null || d[e[0]] + e[2] < d[e[1]])) {
                    d[e[1]] = d[e[0]] + e[2];
                }
            }
        }
        return d;
    }

    static boolean dagValid(int n, int source, int[][] edges, Map<String, Object> result) {
        boolean acyclic = (Boolean) result.get("acyclic");
        Long[] distance = (Long[]) result.get("distance");
        int[] parent = (int[]) result.get("parent_edge");
        if (acyclic != kahnAcyclic(n, edges)) {
            return false;
        }
        if (!acyclic) {
            return distance.length == 0 && parent.length == 0;
        }
        if (distance.length != n || parent.length != n) {
            return false;
        }
        boolean[] reachable = bfsReachable(n, source, edges);
        for (int v = 0; v < n; v++) {
            if ((distance[v] != null) != reachable[v]) {
                return false;
            }
        }
        if (distance[source] != 0L || parent[source] != -1) {
            return false;
        }
        for (int v = 0; v < n; v++) {
            if (v == source) {
                continue;
            }
            if (distance[v] == null) {
                if (parent[v] != -1) {
                    return false;
                }
                continue;
            }
            int id = parent[v];
            if (id < 0 || id >= edges.length || edges[id][1] != v || distance[edges[id][0]] == null
                    || distance[edges[id][0]] + edges[id][2] != distance[v]) {
                return false;
            }
        }
        for (int[] e : edges) {
            if (distance[e[0]] != null && distance[e[0]] + e[2] < distance[e[1]]) {
                return false;
            }
        }
        byte[] state = new byte[n];
        state[source] = 2;
        for (int v = 0; v < n; v++) {
            if (distance[v] == null) {
                continue;
            }
            List<Integer> chain = new ArrayList<>();
            int x = v;
            while (state[x] == 0) {
                state[x] = 1;
                chain.add(x);
                x = edges[parent[x]][0];
            }
            if (state[x] != 2) {
                return false;
            }
            for (int y : chain) {
                state[y] = 2;
            }
        }
        return true;
    }

    private static boolean[] bfsReachable(int n, int source, int[][] edges) {
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            out.get(e[0]).add(e[1]);
        }
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        seen[source] = true;
        while (!queue.isEmpty()) {
            for (int v : out.get(queue.poll())) {
                if (!seen[v]) {
                    seen[v] = true;
                    queue.add(v);
                }
            }
        }
        return seen;
    }

    private static void routeHelperTests() {
        System.out.println("== Route helper");
        int[][] example = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        int[] parent = (int[]) Algorithms.dag(5, 0, example).get("parent_edge");
        List<Integer> toThree = Algorithms.route(example, parent, 0, 3);
        System.out.println("  example parent_edge=" + Arrays.toString(parent));
        System.out.println("  route(0 -> 3) = " + toThree + ", cost " + routeCost(example, parent, 0, 3));
        check(toThree.equals(List.of(0, 1, 2, 3)) && routeCost(example, parent, 0, 3) == 0
                        && routeFollowsEdges(example, parent, toThree),
                "route to reachable vertex 3 is [0,1,2,3] with cost 0 = distance[3]");
        List<Integer> toSource = Algorithms.route(example, parent, 0, 0);
        System.out.println("  route(0 -> 0) = " + toSource);
        check(toSource.equals(List.of(0)), "route to the source is [source]");
        List<Integer> toFour = Algorithms.route(example, parent, 0, 4);
        System.out.println("  route(0 -> 4) = " + toFour + " (unreachable)");
        check(toFour.isEmpty(), "route to unreachable vertex 4 is the empty list");

        int[][] tied = {{0, 1, 2}, {0, 1, 2}, {0, 2, 1}, {2, 1, 1}, {1, 3, 0}};
        Map<String, Object> r = Algorithms.dag(4, 0, tied);
        int[] tp = (int[]) r.get("parent_edge");
        List<Integer> tiedRoute = Algorithms.route(tied, tp, 0, 3);
        System.out.println("  tied parent_edge=" + Arrays.toString(tp) + " distance="
                + Arrays.toString((Long[]) r.get("distance")));
        System.out.println("  route(0 -> 3) = " + tiedRoute + ", cost " + routeCost(tied, tp, 0, 3)
                + ", parent_edge[1]=" + tp[1]);
        boolean acceptable = tiedRoute.equals(List.of(0, 1, 3)) || tiedRoute.equals(List.of(0, 2, 1, 3));
        check(acceptable && (tp[1] == 0 || tp[1] == 1 || tp[1] == 3) && routeCost(tied, tp, 0, 3) == 2
                        && routeFollowsEdges(tied, tp, tiedRoute),
                "route with parallel and tied edges is one of the optimal routes, cost 2");
        int[] alternative = tp.clone();
        alternative[1] = tp[1] == 0 ? 1 : 0;
        check(Algorithms.route(tied, alternative, 0, 3).equals(List.of(0, 1, 3))
                        && dagValid(4, 0, tied, dagResult(r, alternative)),
                "route helper and checker accept the other parallel edge as parent of 1");
        int[] viaTwo = tp.clone();
        viaTwo[1] = 3;
        check(Algorithms.route(tied, viaTwo, 0, 3).equals(List.of(0, 2, 1, 3))
                        && dagValid(4, 0, tied, dagResult(r, viaTwo)),
                "route helper and checker accept the tied detour 0->2->1->3");
    }

    private static long routeCost(int[][] edges, int[] parent, int source, int target) {
        long cost = 0;
        for (int v = target; v != source; v = edges[parent[v]][0]) {
            cost += edges[parent[v]][2];
        }
        return cost;
    }

    private static boolean routeFollowsEdges(int[][] edges, int[] parent, List<Integer> route) {
        for (int i = 1; i < route.size(); i++) {
            int[] e = edges[parent[route.get(i)]];
            if (e[0] != route.get(i - 1) || e[1] != route.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> dagResult(Map<String, Object> base, int[] parent) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(base);
        copy.put("parent_edge", parent);
        return copy;
    }

    private static void witnessTests() {
        System.out.println("== Checker accepts alternatives / rejects invalid witnesses");

        int[][] square = {{0, 1, 1}, {1, 2, 1}, {2, 3, 1}, {3, 0, 1}, {0, 2, 5}, {1, 1, -9}};
        check(forestValid(4, square, forestOf(3L, new int[] {0, 1, 2}, 1), 3, 1)
                        && forestValid(4, square, forestOf(3L, new int[] {1, 2, 3}, 1), 3, 1),
                "forest checker accepts two different optimal forests");
        check(!forestValid(4, square, forestOf(3L, new int[] {0, 0, 1}, 1), 3, 1),
                "forest checker rejects a repeated edge ID");
        check(!forestValid(4, square, forestOf(-7L, new int[] {0, 5, 2}, 1), 3, 1),
                "forest checker rejects a self-loop");
        check(!forestValid(4, square, forestOf(7L, new int[] {0, 1, 4}, 1), 3, 1),
                "forest checker rejects a cycle and a non-optimal cost");
        check(!forestValid(4, square, forestOf(3L, new int[] {0, 1, 2}, 2), 3, 1),
                "forest checker rejects a wrong component count");

        int[][] g = {{0, 1}, {1, 0}, {1, 2}, {0, 2}, {2, 3}};
        check(sccValid(4, g, sccOf(new int[] {5, 5, 7, 9}, new int[][] {{5, 7}, {7, 9}}))
                        && sccValid(4, g, sccOf(new int[] {0, 0, 1, 2}, new int[][] {{1, 2}, {0, 1}})),
                "scc checker accepts relabelled components and any pair order");
        check(!sccValid(4, g, sccOf(new int[] {0, 0, 1, 2}, new int[][] {{0, 1}})),
                "scc checker rejects a missing condensation pair");
        check(!sccValid(4, g, sccOf(new int[] {0, 0, 1, 2}, new int[][] {{0, 1}, {0, 1}, {1, 2}})),
                "scc checker rejects a duplicate condensation pair");
        check(!sccValid(4, g, sccOf(new int[] {0, 0, 0, 2}, new int[][] {{0, 2}})),
                "scc checker rejects a merged class that is not strongly connected");
        check(!sccValid(4, g, sccOf(new int[] {0, 1, 2, 3}, new int[][] {{0, 1}, {1, 0}, {1, 2}, {0, 2}, {2, 3}})),
                "scc checker rejects a split SCC (condensation would have a cycle)");

        int[][] t = {{0, 2}, {1, 2}, {2, 3}};
        check(topoValid(4, t, topoOf(true, new int[] {1, 0, 2, 3}), true)
                        && topoValid(4, t, topoOf(true, new int[] {0, 1, 2, 3}), true),
                "topo checker accepts two different valid orders");
        check(!topoValid(4, t, topoOf(true, new int[] {2, 0, 1, 3}), true),
                "topo checker rejects an order with a backward edge");
        check(!topoValid(4, t, topoOf(true, new int[] {0, 1, 2, 2}), true),
                "topo checker rejects a repeated vertex");

        int[][] d = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        Map<String, Object> good = Algorithms.dag(5, 0, d);
        check(!dagValid(5, 0, d, dagResult(good, new int[] {-1, 0, 1, 3, -1})),
                "dag checker rejects a parent edge that is not tight");
        check(!dagValid(5, 0, d, dagResult(good, new int[] {-1, 0, 3, 3, -1})),
                "dag checker rejects a parent edge that does not end at the vertex");
        Map<String, Object> wrongDistance = new java.util.LinkedHashMap<>(good);
        wrongDistance.put("distance", new Long[] {0L, 3L, 8L, 12L, null});
        check(!dagValid(5, 0, d, dagResult(wrongDistance, new int[] {-1, 0, 1, 3, -1})),
                "dag checker rejects a consistent but non-shortest tree");
        Map<String, Object> minusOneUnreachable = new java.util.LinkedHashMap<>(good);
        minusOneUnreachable.put("distance", new Long[] {0L, 3L, -4L, 0L, -1L});
        check(!dagValid(5, 0, d, minusOneUnreachable), "dag checker rejects -1 as an unreachable distance");
    }

    private static Map<String, Object> forestOf(long cost, int[] ids, int components) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("cost", cost);
        r.put("edge_ids", ids);
        r.put("components", components);
        return r;
    }

    private static Map<String, Object> sccOf(int[] labels, int[][] pairs) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("component", labels);
        r.put("condensation", new ArrayList<>(Arrays.asList(pairs)));
        return r;
    }

    private static Map<String, Object> topoOf(boolean acyclic, int[] order) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("acyclic", acyclic);
        r.put("order", order);
        return r;
    }

    private static void stateResetTest() {
        System.out.println("== State reset");
        String[] lines = {
            "{\"task\":\"dsu\",\"n\":4,\"operations\":[[0,1],[1,2],[0,2],[3,3]]}",
            "{\"task\":\"kruskal\",\"n\":4,\"edges\":[[0,1,4],[1,2,2],[0,2,3]]}",
            "{\"task\":\"prim\",\"n\":4,\"edges\":[[0,1,4],[1,2,2],[0,2,3]]}",
            "{\"task\":\"scc\",\"n\":4,\"edges\":[[0,1],[1,0],[1,2],[0,2],[2,3]]}",
            "{\"task\":\"topo\",\"n\":3,\"edges\":[[0,1],[1,2],[2,0]]}",
            "{\"task\":\"topo\",\"n\":4,\"edges\":[[0,2],[1,2],[2,3]]}",
            "{\"task\":\"dag\",\"n\":5,\"source\":0,\"edges\":[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]}",
            "{\"task\":\"dsu\",\"n\":0,\"operations\":[]}",
        };
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            for (String line : lines) {
                String out = JsonCodec.write(Main.handle(request(line)));
                (round == 0 ? first : second).add(out);
            }
        }
        boolean same = true;
        for (int i = 0; i < second.size(); i++) {
            same &= second.get(i).equals(first.get(i % lines.length));
        }
        check(same, "three rounds of mixed requests in one JVM give identical JSON results");

        check(JsonCodec.write(Main.handle(request("{\"task\":\"ping\"}"))).equals("{\"status\":\"ready\"}"),
                "ping answers {\"status\":\"ready\"}");
        check(JsonCodec.write(Main.handle(request(lines[6]))).equals(
                        "{\"acyclic\":true,\"distance\":[0,3,-4,0,null],\"parent_edge\":[-1,0,2,3,-1]}"),
                "dag example is written with null for the unreachable vertex");
        String text = "{\"s\":\"q\\\" b\\\\ n\\n t\\t u\\u00e9 Қ\",\"a\":[1,-2,[]],\"t\":true,\"f\":false,\"z\":null}";
        check(JsonCodec.write(JsonCodec.parse(text)).equals(
                        "{\"s\":\"q\\\" b\\\\ n\\n t\\t ué Қ\",\"a\":[1,-2,[]],\"t\":true,\"f\":false,\"z\":null}"),
                "JSON reader/writer round-trips escapes, unicode, nested arrays and literals");
        check(JsonCodec.write(new Object[] {new int[] {1, 2}, new Long[] {3_000_000_000L, null}, List.of()})
                        .equals("[[1,2],[3000000000,null],[]]"),
                "JSON writer handles int[], Long[] with null and lists");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> request(String line) {
        return (Map<String, Object>) JsonCodec.parse(line);
    }

    private static void largeTests() {
        System.out.println("== Large inputs (n = 100000, m or q = 250000) and deep chains");
        final int n = 100_000;
        final int m = 250_000;
        Random random = new Random(66);

        int[][] ops = new int[m][];
        for (int i = 0; i < m; i++) {
            ops[i] = new int[] {random.nextInt(n), random.nextInt(n)};
        }
        long t0 = System.nanoTime();
        int[][] st = states(Algorithms.dsu(n, ops));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int components = countUndirectedComponents(n, ops);
        boolean monotone = st.length == m;
        for (int i = 1; i < st.length && monotone; i++) {
            monotone = st[i][0] <= st[i - 1][0] && st[i][0] >= st[i - 1][0] - 1 && st[i][1] >= st[i - 1][1];
        }
        check(monotone && st[m - 1][0] == components, "dsu n=100000 q=250000: final components match BFS (" + ms + " ms)");
        int[][] chainOps = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) {
            chainOps[i] = new int[] {i, i + 1};
        }
        int[][] chainStates = states(Algorithms.dsu(n, chainOps));
        check(chainStates[n - 2][0] == 1 && chainStates[n - 2][1] == n, "dsu long chain of unions ends as one component of size n");

        int[][] weighted = new int[m][];
        for (int i = 0; i < m; i++) {
            weighted[i] = new int[] {random.nextInt(n), random.nextInt(n),
                    random.nextInt(2_000_000_001) - 1_000_000_000};
        }
        t0 = System.nanoTime();
        Map<String, Object> k = Algorithms.kruskal(n, weighted);
        long km = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        Map<String, Object> p = Algorithms.prim(n, weighted);
        long pm = (System.nanoTime() - t0) / 1_000_000;
        long cost = (Long) k.get("cost");
        int comps = countUndirectedComponents(n, weighted);
        check(forestValid(n, weighted, k, cost, comps) && forestValid(n, weighted, p, cost, comps),
                "kruskal (" + km + " ms) and prim (" + pm + " ms) agree at n=100000 m=250000, cost " + cost);
        int[][] weightedChain = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) {
            weightedChain[i] = new int[] {i, i + 1, 1_000_000_000};
        }
        check(forestValid(n, weightedChain, Algorithms.prim(n, weightedChain), 99_999_000_000_000L, 1)
                        && forestValid(n, weightedChain, Algorithms.kruskal(n, weightedChain), 99_999_000_000_000L, 1),
                "kruskal and prim on a 100000-vertex path, cost 9.9999e13");

        int[][] directed = randomDirected(random, n, m);
        t0 = System.nanoTime();
        Map<String, Object> s = Algorithms.scc(n, directed);
        ms = (System.nanoTime() - t0) / 1_000_000;
        check(sccValid(n, directed, s), "scc random n=100000 m=250000 passes the certificate (" + ms + " ms)");
        int[][] chain = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) {
            chain[i] = new int[] {i, i + 1};
        }
        Map<String, Object> sc = Algorithms.scc(n, chain);
        check(sccValid(n, chain, sc) && distinctLabels(sc) == n, "scc deep chain of 100000 vertices: n singleton SCCs");
        int[][] ring = Arrays.copyOf(chain, n);
        ring[n - 1] = new int[] {n - 1, 0};
        Map<String, Object> sr = Algorithms.scc(n, ring);
        check(sccValid(n, ring, sr) && distinctLabels(sr) == 1, "scc 100000-vertex ring is a single SCC");

        int[][] dagEdges = randomDag(random, n, m);
        t0 = System.nanoTime();
        Map<String, Object> to = Algorithms.topo(n, dagEdges);
        ms = (System.nanoTime() - t0) / 1_000_000;
        check(topoValid(n, dagEdges, to, true), "topo random DAG n=100000 m=250000 (" + ms + " ms)");
        int[][] backwardChain = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) {
            backwardChain[i] = new int[] {n - 1 - i, n - 2 - i};
        }
        check(topoValid(n, backwardChain, Algorithms.topo(n, backwardChain), true)
                        && topoValid(n, chain, Algorithms.topo(n, chain), true),
                "topo deep chains of 100000 vertices in both numbering directions");
        check(topoValid(n, ring, Algorithms.topo(n, ring), false), "topo 100000-vertex ring is cyclic");

        int[][] weightedDag = withWeights(random, dagEdges);
        int source = random.nextInt(n);
        t0 = System.nanoTime();
        Map<String, Object> dd = Algorithms.dag(n, source, weightedDag);
        ms = (System.nanoTime() - t0) / 1_000_000;
        check(dagValid(n, source, weightedDag, dd), "dag random n=100000 m=250000 passes the certificate (" + ms + " ms)");
        int[][] negativeChain = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) {
            negativeChain[i] = new int[] {i, i + 1, -1_000_000_000};
        }
        Map<String, Object> nc = Algorithms.dag(n, 0, negativeChain);
        Long[] dist = (Long[]) nc.get("distance");
        int[] parent = (int[]) nc.get("parent_edge");
        List<Integer> longRoute = Algorithms.route(negativeChain, parent, 0, n - 1);
        check(dagValid(n, 0, negativeChain, nc) && dist[n - 1] == -99_999_000_000_000L && longRoute.size() == n,
                "dag deep chain of 100000 vertices, distance -9.9999e13, route of 100000 vertices");
        int[][] ringWeighted = withWeights(random, ring);
        check(!(Boolean) Algorithms.dag(n, 0, ringWeighted).get("acyclic"), "dag 100000-vertex ring is cyclic");
    }

    private static int[][] states(Map<String, Object> result) {
        return (int[][]) result.get("states");
    }

    private static int[][] randomDirected(Random random, int n, int m) {
        int[][] edges = new int[n == 0 ? 0 : m][];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = new int[] {random.nextInt(n), random.nextInt(n)};
        }
        return edges;
    }

    private static int[][] randomDag(Random random, int n, int m) {
        if (n < 2) {
            return new int[0][];
        }
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        int[][] edges = new int[m][];
        for (int i = 0; i < m; i++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            while (b == a) {
                b = random.nextInt(n);
            }
            edges[i] = new int[] {perm[Math.min(a, b)], perm[Math.max(a, b)]};
        }
        return edges;
    }

    private static int[][] withWeights(Random random, int[][] edges) {
        int[][] out = new int[edges.length][];
        boolean small = edges.length < 50;
        for (int i = 0; i < edges.length; i++) {
            int w = small ? random.nextInt(9) - 4 : random.nextInt(2_000_000_001) - 1_000_000_000;
            out[i] = new int[] {edges[i][0], edges[i][1], w};
        }
        return out;
    }
}
