import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Lines entry point: one request object per input line, one result object
 * per output line. Only results go to standard output.
 *
 *   javac --release 17 -encoding UTF-8 -d .build/java src/Java/*.java
 *   java -Xmx384m -cp .build/java Main < requests.jsonl
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> result;
            try {
                result = handle(asObject(JsonCodec.parse(line)));
            } catch (RuntimeException error) {
                // Keep one output line per request even for a bad request.
                result = new LinkedHashMap<>();
                result.put("error", String.valueOf(error.getMessage()));
            }
            out.write(JsonCodec.write(result));
            out.newLine();
            out.flush();
        }
    }

    /** Dispatches one parsed request; every call works on freshly built input arrays. */
    static Map<String, Object> handle(Map<String, Object> request) {
        String task = (String) request.get("task");
        switch (task) {
            case "ping":
                Map<String, Object> ready = new LinkedHashMap<>();
                ready.put("status", "ready");
                return ready;
            case "dsu":
                return Algorithms.dsu(integer(request, "n"), rows(request, "operations"));
            case "kruskal":
                return Algorithms.kruskal(integer(request, "n"), rows(request, "edges"));
            case "prim":
                return Algorithms.prim(integer(request, "n"), rows(request, "edges"));
            case "scc":
                return Algorithms.scc(integer(request, "n"), rows(request, "edges"));
            case "topo":
                return Algorithms.topo(integer(request, "n"), rows(request, "edges"));
            case "dag":
                return Algorithms.dag(integer(request, "n"), integer(request, "source"), rows(request, "edges"));
            default:
                throw new IllegalArgumentException("Unknown task: " + task);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("request must be a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private static int integer(Map<String, Object> request, String field) {
        return Math.toIntExact(((Number) request.get(field)).longValue());
    }

    /** Converts a JSON array of integer arrays into int[][]; row i keeps index i. */
    private static int[][] rows(Map<String, Object> request, String field) {
        List<?> list = (List<?>) request.get(field);
        int[][] result = new int[list.size()][];
        for (int i = 0; i < result.length; i++) {
            List<?> row = (List<?>) list.get(i);
            int[] values = new int[row.size()];
            for (int j = 0; j < values.length; j++) {
                values[j] = Math.toIntExact(((Number) row.get(j)).longValue());
            }
            result[i] = values;
        }
        return result;
    }
}
