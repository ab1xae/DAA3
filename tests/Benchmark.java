import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Kruskal vs Prim on identical graphs at three sizes.
 *
 * Generator: for each size, java.util.Random(SEED + n) draws m edges with
 * uniform endpoints in [0, n) and uniform weights in [-1e9, 1e9]; loops and
 * parallel edges are kept. The same int[][] array is passed to both methods.
 *
 * Timed region: one call of Algorithms.kruskal(n, edges) or Algorithms.prim(n, edges),
 * measured with System.nanoTime(). It includes everything the method builds
 * (sort keys / adjacency lists, DSU / heap, result map). It excludes JVM start,
 * compilation, graph generation, JSON parsing, Input conversion and output, for
 * both methods alike.
 *
 * Protocol per size: verify both answers first (valid forest, equal cost,
 * component count equal to an independent union-find count), then WARMUP untimed calls of each
 * method, then RUNS timed rounds; each round runs Kruskal then Prim, and the
 * order alternates between rounds so neither method always runs first.
 *
 *   javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
 *   java -Xmx384m -cp .build/tests Benchmark
 */
public final class Benchmark {
    private static final long SEED = 20261025L;
    private static final int WARMUP = 5;
    private static final int RUNS = 7;
    private static final int[][] SIZES = {{10_000, 25_000}, {50_000, 125_000}, {100_000, 250_000}};

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name")
                + "), " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + ", " + Runtime.getRuntime().availableProcessors() + " logical CPUs, max heap "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB");
        System.out.println("seed=" + SEED + "+n, warm-up=" + WARMUP + " calls per method, timed runs=" + RUNS);
        System.out.println();

        List<String> table = new ArrayList<>();
        table.add("| n | m | cost | components | Kruskal runs (ms) | Kruskal median | Prim runs (ms) | Prim median | Prim / Kruskal |");
        table.add("|---:|---:|---:|---:|---|---:|---|---:|---:|");

        for (int[] size : SIZES) {
            int n = size[0];
            int m = size[1];
            int[][] edges = generate(n, m, new Random(SEED + n));

            Map<String, Object> k = Algorithms.kruskal(n, edges);
            Map<String, Object> p = Algorithms.prim(n, edges);
            long cost = (Long) k.get("cost");
            int components = (Integer) k.get("components");
            boolean valid = GraphTests.forestValid(n, edges, k, cost, components)
                    && GraphTests.forestValid(n, edges, p, cost, components)
                    && components == countComponents(n, edges);
            if (!valid) {
                throw new AssertionError("invalid forest at n=" + n);
            }
            System.out.printf(Locale.ROOT, "n=%d m=%d verified: cost=%d components=%d, both forests valid%n",
                    n, m, cost, components);

            for (int i = 0; i < WARMUP; i++) {
                Algorithms.kruskal(n, edges);
                Algorithms.prim(n, edges);
            }
            double[] kt = new double[RUNS];
            double[] pt = new double[RUNS];
            for (int r = 0; r < RUNS; r++) {
                if (r % 2 == 0) {
                    kt[r] = time(() -> Algorithms.kruskal(n, edges));
                    pt[r] = time(() -> Algorithms.prim(n, edges));
                } else {
                    pt[r] = time(() -> Algorithms.prim(n, edges));
                    kt[r] = time(() -> Algorithms.kruskal(n, edges));
                }
            }
            double km = median(kt);
            double pm = median(pt);
            table.add(String.format(Locale.ROOT, "| %d | %d | %d | %d | %s | %.2f | %s | %.2f | %.2f |",
                    n, m, cost, components, list(kt), km, list(pt), pm, pm / km));
        }
        System.out.println();
        table.forEach(System.out::println);
    }

    private static int[][] generate(int n, int m, Random random) {
        int[][] edges = new int[m][];
        for (int i = 0; i < m; i++) {
            edges[i] = new int[] {random.nextInt(n), random.nextInt(n),
                    random.nextInt(2_000_000_001) - 1_000_000_000};
        }
        return edges;
    }

    private static double time(Runnable call) {
        long start = System.nanoTime();
        call.run();
        return (System.nanoTime() - start) / 1e6;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    private static String list(double[] values) {
        StringBuilder out = new StringBuilder();
        for (double v : values) {
            out.append(out.length() == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.2f", v));
        }
        return out.toString();
    }

    /** Component count by union-find with no ranking, independent of both methods. */
    private static int countComponents(int n, int[][] edges) {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        int components = n;
        for (int[] e : edges) {
            int a = root(parent, e[0]);
            int b = root(parent, e[1]);
            if (a != b) {
                parent[a] = b;
                components--;
            }
        }
        return components;
    }

    private static int root(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }
}
