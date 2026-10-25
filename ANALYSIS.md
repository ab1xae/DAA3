# Assignment 3 — Analysis

Language: **Java 17** (`submission.json`: `{"language": "Java"}`). All six methods and the
route helper are in [`src/Java/Algorithms.java`](src/Java/Algorithms.java).
[`Main.java`](src/Java/Main.java) is the JSON Lines entry point (one request object per input
line, one result object per output line; `{"task":"ping"}` answers `{"status":"ready"}`), and
[`JsonCodec.java`](src/Java/JsonCodec.java) reads and writes JSON. Own tests are in
[`tests/`](tests/README.md).

## 1. Build, run and test commands

Run from the repository root. JDK 17+ and Python 3.12+ are needed; on Windows `py -3` can
replace `python`.

```sh
javac --release 17 -encoding UTF-8 -d .build/java src/Java/*.java
java -Xmx384m -cp .build/java Main < requests.jsonl   # any JSON Lines file of requests

javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
java -Xss256k -Xmx384m -cp .build/tests GraphTests   # own test suite (76 checks)
python tests/check_cases.py                           # hand JSONL cases through Main, twice in one process
python tests/check_cases.py --large                   # 15 seeded limit-size requests in one process
java -Xmx384m -cp .build/tests Benchmark              # Kruskal vs Prim benchmark
```

Results I recorded (outputs in [`tests/output/`](tests/output)): `GraphTests` — 76 passed,
0 failed; `check_cases.py` — 24/24 cases pass in both batches and the second batch repeats the
first exactly; `check_cases.py --large` — 15/15 pass in 3.2 s wall time.

Environment: AMD Ryzen 7 7840HS (8 cores / 16 threads), 15.2 GB RAM, Windows 11 Pro
(10.0.26200), OpenJDK 25 runtime; sources compiled with `javac --release 17`.

## 2. Shared design

* **Array-based graphs.** Each method gets `n` and `int[][] edges`, where row `i` is edge ID `i`.
  Directed adjacency is built in compressed (CSR) form by `directedAdjacency`: a counting
  pass gives `start[u]`, and `arcs[start[u] .. start[u+1])` holds the **edge IDs** that leave `u`
  (or enter `u` for the transpose). Storing IDs instead of endpoints keeps the original IDs
  for `parent_edge` and `edge_ids`. Building it takes O(n+m) time and memory.
* **State reset.** `Main` converts each request into fresh arrays and calls
  `method(n, edges)`, which allocates all state (arrays, DSU, heap, sets) as local variables.
  `Algorithms` has no mutable static fields, so nothing carries over between requests. This is
  checked by `check_cases.py` (all cases twice in one process; the second batch must be
  byte-for-byte equal to the first) and by `GraphTests.stateResetTest` (three rounds of mixed
  requests through `Main.handle` and `JsonCodec` in one JVM).
* **Deep graphs without recursion.** Every DFS uses an explicit `int[] stack` plus
  `next[u]`, the position of the next arc to scan for each vertex on the stack. A suspended
  vertex resumes exactly where it stopped, so each arc is scanned once (no rescans), and the
  depth is limited only by the `n`-element array, not by the Java call stack. `GraphTests` runs
  with `-Xss256k` and passes 100,000-vertex chains and rings for SCC, topo and DAG.
* **Overflow.** Weights fit in `int`; every total and distance is a `long`. `|cost| ≤ (n-1)·10^9 <
  10^14` and `|distance| ≤ (n-1)·10^9`, far below `2^63`. Tests cover `±3·10^9` and `±9.9999·10^13`.
* **Edge keys.** Kruskal and Prim order edges by the key `((w + 10^9) << 32) | id`. The shifted
  weight lies in `[0, 2·10^9] < 2^31` and the ID in `[0, 2^31)`, so the key is a non-negative
  `long`, it orders by weight first, and `(int) key` gives back the ID. Sorting primitive `long`s
  avoids boxing.

## 3. DSU (`dsu`)

**Steps.** `parent[i] = i`, `size[i] = 1`, `components = n`, `largest = (n > 0 ? 1 : 0)`. For each
operation `[u, v]`: `find` both roots; if they differ, attach the root of the smaller tree under
the larger one (union by size), add the sizes, decrease `components` and update
`largest = max(largest, size[newRoot])`; then append `[components, largest]`.

`find` is iterative with full path compression: it first walks to the root, then walks the path
again and points every vertex directly at the root.

**Invariant.** After each operation the trees match the connected components of the edges added
so far; `size[r]` is the size of the tree rooted at `r`; `components` is the number of roots;
`largest` is the maximum root size. Sizes only grow and trees only merge, so the maximum can only
grow and updating it at each merge is enough.

**Correctness.** A union adds an undirected connection. It merges two components exactly when
the endpoints are in different trees, so the trees always equal the components. Repeated unions
and self-unions find equal roots and change nothing, which gives the repeated states the
output requires. Size, not rank, is stored, so `largest` is a real vertex count.

**Boundary cases.** `n = 0` → `states = []` (no operations); a single vertex → `[1,1]` for every
self-union; repeated unions; disconnected input.

**Complexity.** Union by size plus path compression costs O(α(n)) amortised per operation:
O(n + q·α(n)) time. Memory O(n) for the DSU plus O(q) for the states, so O(n + q).

## 4. Kruskal (`kruskal`)

**Steps.** Build the `m` keys described above and sort them, so edges are ordered by
(weight, ID). Scan them in that order with a fresh DSU and accept edge `(u, v, w)` exactly when
`find(u) != find(v)`, i.e. when `union` returns true. Return the sum of accepted weights,
their IDs, and `components = n - accepted`.

**Invariant.** The accepted set is always contained in some minimum spanning forest (MSF), and
DSU trees are its connected parts.

**Correctness (cut property).** When edge `e = (u, v)` is accepted, `u` and `v` lie in different
DSU trees. Let `S` be the tree of `u`. All lighter edges were already scanned, and each was either
accepted or joins two vertices in one tree, so none crosses the cut `(S, V∖S)`. So `e` is a
minimum edge across that cut, and by the cut property (exchange argument: swapping `e` into an
MSF that lacks it does not raise the cost) the invariant survives. A rejected edge closes a
cycle. Negative weights do not affect the argument. Equal weights only change which optimal
forest is found. A self-loop has `find(u) == find(v)` and is never accepted. Every edge is
scanned once, so no ID repeats. At the end every input edge joins vertices in one tree, so the
forest spans each component. A forest with `c` trees on `n` vertices has `n - c` edges, which
gives `components = n - accepted`, isolated vertices included.

**Boundary cases.** `n = 0` → `0, [], 0`; isolated vertices; loops; parallel edges (the cheaper
one sorts first); tied optima; negative weights; totals beyond 32 bits.

**Complexity.** Sorting O(m log m), m DSU operations O(m·α(n)), initialisation O(n):
O(n + m log(m+1)) time. Memory: keys O(m), DSU O(n), result O(n), so O(n + m).

## 5. Prim (`prim`)

**Steps.** Build undirected CSR adjacency in which each non-loop edge ID appears at both
endpoints (loops are left out because they never belong to a forest). For every
`root = 0 .. n-1` that is still unvisited, increment `components` and grow a tree from it:
mark the vertex visited and push the key of every incident edge whose other endpoint is
unvisited. Then pop the minimum key. If both endpoints are already visited, discard it (this is
*lazy* deletion); otherwise accept the edge, add its weight, and visit its unvisited endpoint.
When the heap is empty the component is complete and the outer loop restarts from the next
unvisited vertex. The heap is a small binary min-heap of primitive `long` keys (`LongHeap`).

**Invariant.** The visited vertices of the current tree `T` and its accepted edges form a subtree
of some MSF. The heap contains every edge between `T` and `V∖T`, plus stale edges with both ends
in `T`.

**Correctness.** The popped non-stale edge is a lightest edge across the cut `(T, V∖T)`, so by
the cut property the invariant survives. When the heap empties, no edge leaves `T`: `T` is a whole
connected component and its accepted edges form a minimum spanning tree of it. Restarting from
every unvisited vertex covers each component exactly once, so `components` counts the
components of the input graph, isolated vertices included. The union of per-component MSTs is
an MSF. The accepted edge always has one unvisited endpoint, so there are no cycles and no repeated
IDs. Kruskal and Prim give the same optimal cost; their IDs may differ when ties exist (tests
accept both).

**Boundary cases.** As for Kruskal; tested on the same inputs.

**Complexity.** Each non-loop edge is pushed at most twice (once from each endpoint), so the heap
holds at most 2m keys and every push/pop costs O(log m). Total time
O(n + m log(m+1)). Memory: adjacency O(n + m), heap O(m), visited O(n), so O(n + m).

## 6. Kosaraju SCC (`scc`)

**Steps.** (1) Build CSR adjacency of `G` and of its transpose `Gᵀ`. (2) First pass: iterative
DFS on `G` from every unvisited vertex, appending each vertex to `finishOrder` when it finishes.
(3) Second pass: go through `finishOrder` from last to first; each still-unlabelled vertex starts
an iterative DFS on `Gᵀ` that labels everything it reaches with a new label `0, 1, 2, ...`.
(4) Condensation: for every edge `u → v` with `label[u] != label[v]`, add the pair if the hash set
of packed keys `(label[u] << 32) | label[v]` has not seen it yet.

**Invariant / key lemma.** For two different SCCs `C` and `D` with an edge `C → D`, the latest
finish time in `C` is greater than the latest finish time in `D`. So the vertex with the
largest remaining finish time always lies in a *source* SCC of the remaining condensation. In
`Gᵀ` all edges are reversed, so that SCC has no outgoing edges into unlabelled SCCs.

**Correctness.** The second-pass DFS from that vertex reaches all of its SCC (an SCC stays
strongly connected in `Gᵀ`). It cannot leave the SCC: any `Gᵀ` edge leaving it goes to an SCC that
is already labelled. So every second-pass tree is exactly one SCC, and labels identify SCCs
one-to-one. The condensation adds exactly the cross-label pairs, and the hash set removes
duplicates. Internal edges and self-loops have equal labels and are skipped. Duplicate input
edges give the same key.

**Boundary cases.** `n = 0` → `[]`, `[]`; isolated vertices → separate SCCs; self-loops;
duplicate edges; several edges between the same two SCCs (one pair); deep chains (100,000
singleton SCCs) and one 100,000-vertex ring.

**Complexity.** Two CSR builds O(n+m), two DFS passes O(n+m) (each arc scanned once thanks to
`next[]`), condensation O(m) expected with hashing: O(n+m) expected time. Memory: two adjacency
arrays, stack/next/visited, labels and up to m pairs, so O(n+m).

## 7. DFS topological ordering (`topo`)

**Steps.** States `UNVISITED`, `ACTIVE`, `FINISHED`. For every unvisited root run an iterative
DFS: a vertex becomes `ACTIVE` when pushed. On each step the top vertex `u` scans its next arc
`u → v`. If `v` is `ACTIVE`, there is a cycle: return `acyclic=false, order=[]`. If `v` is
`UNVISITED`, push it. A `FINISHED` `v` is ignored. When `u` has no arcs left it becomes
`FINISHED` and is written at the next free position from the **end** of `order`, which yields the
reverse finish order directly.

**Invariant.** The `ACTIVE` vertices are exactly the stack, i.e. the current DFS path from the
root. Every `FINISHED` vertex has all of its descendants finished.

**Correctness.** *Cycle detection:* an edge to an `ACTIVE` vertex `v` closes the path
`v → ... → u → v`, which is a cycle (a self-loop is `v = u`). Conversely, if there is a cycle, let
`v` be its first vertex discovered. All other cycle vertices are still unvisited at that moment,
so they are discovered and finished while `v` is active, and the cycle edge into `v` is then seen
while `v` is `ACTIVE`. An edge to a `FINISHED` vertex is not a cycle: that vertex and its
descendants are done and cannot reach the current path, since otherwise the path would have been
explored from it. *Order:* for every edge `u → v` in an acyclic graph, `v` finishes before `u`
(either `v` was already finished, or it is pushed and finishes first). Reversing the finish order
puts `u` before `v`. Every root is tried, so every component is checked and every vertex appears once.

**Boundary cases.** `n = 0` → `true, []`; isolated vertices; duplicate edges; self-loop;
a cycle only in a later component; an edge to a finished vertex (`0→1, 0→2, 2→1`); 100,000-vertex
chains numbered ascending and descending; a 100,000-vertex ring.

**Complexity.** O(n+m) time: each vertex is pushed once and each arc is scanned once, because
`next[u]` keeps the scan position of a suspended vertex. Memory O(n+m).

## 8. DAG shortest paths (`dag`) and route helper

**Steps.** Build the directed CSR and run the same three-state DFS on the **whole** graph. A cycle
anywhere, even one the source cannot reach, gives `acyclic=false, distance=[], parent_edge=[]`.
Otherwise set `reached[source] = true`, `dist[source] = 0`, `parent_edge[*] = -1`, and process
vertices in topological order. A vertex that is not reached is skipped, so we never relax from an
unreachable vertex. For a reached `u` and each outgoing edge ID `e = (u, v, w)`: if `v` is not
reached yet or `dist[u] + w < dist[v]`, set `dist[v] = dist[u] + w`, `parent_edge[v] = e` and mark
`v` reached. Output `distance[v] = dist[v]` for reached vertices and JSON `null` otherwise. The
separate `reached` flag means no number (such as `-1`) is used to mean "unreachable".

**Invariant.** When `u` is processed, all of its predecessors have already been processed, so
`dist[u]` is final and equals the shortest distance `δ(u)`.

**Correctness.** By induction along the topological order. Any shortest path to `v` ends with an
edge `(u, v)` whose tail `u` comes earlier in the order. When `u` was processed `dist[u] = δ(u)`, so
relaxing `(u, v)` made `dist[v] ≤ δ(u) + w = δ(v)`. Every value ever stored is the length of a
real path, so `dist[v] ≥ δ(v)`. Negative weights are fine because no cycles exist. A vertex is
reached exactly when some reached predecessor relaxed into it, i.e. when a path from the source
exists. `parent_edge[v]` is the edge that set the final `dist[v]`. It ends at `v`, it is tight,
and its tail comes earlier in the order, so following parents strictly moves backwards in the
order and must end at the source.

**Route helper** — `Algorithms.route(int[][] edges, int[] parentEdge, int source, int target)`
returns `List<Integer>`:

* `target == source` → `[source]`;
* `parentEdge[target] == -1` (and not the source) → **empty list**. This is my no-route result:
  a real route always has at least one vertex, so an empty list cannot be mistaken for a route;
* otherwise follow `v ← edges[parentEdge[v]][0]` until the source, then reverse.

It does O(L) work and uses O(L) output space for a route of `L` vertices. It expects the
`parent_edge` array of an acyclic `dag` result.

Actual calls (from `tests/output/GraphTests.txt`, test `routeHelperTests`):

| Graph / call | `parent_edge` | Result | Edge IDs used | Direction and weight check |
|---|---|---|---|---|
| Example `[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]`, `n=5`, `route(0→3)` | `[-1,0,2,3,-1]` | `[0,1,2,3]` | 0, 2, 3 | `0→1`, `1→2`, `2→3`; `3 + (-7) + 4 = 0 = distance[3]` |
| Same, `route(0→0)` (source) | — | `[0]` | none | cost 0 |
| Same, `route(0→4)` (unreachable) | `parent_edge[4] = -1` | `[]` | none | `distance[4] = null` |
| Tied/parallel `[[0,1,2],[0,1,2],[0,2,1],[2,1,1],[1,3,0]]`, `route(0→3)` | `[-1,0,2,4]`, distance `[0,2,1,2]` | `[0,1,3]` | 0, 4 | `0→1` (2) + `1→3` (0) = 2 = `distance[3]` |
| Same with `parent_edge[1] = 1` (other parallel edge) | `[-1,1,2,4]` | `[0,1,3]` | 1, 4 | also cost 2, accepted by the checker |
| Same with `parent_edge[1] = 3` (tied detour) | `[-1,3,2,4]` | `[0,2,1,3]` | 2, 3, 4 | `1 + 1 + 0 = 2`, accepted by the checker |

The test also checks that each consecutive pair `(a, b)` of a route is edge `parent_edge[b]`
directed `a → b`, and that the summed weight equals `distance[target]`. A 100,000-vertex negative
chain gives a route of 100,000 vertices and distance `-99,999,000,000,000`.

**Boundary cases.** Single vertex; a cycle the source cannot reach; self-loop; distance `-1`
next to unreachable `null`; an unreachable vertex with an edge *into* the source; parallel edges
and ties; distances beyond 32 bits; deep chains.

**Complexity.** CSR O(n+m), DFS cycle check and order O(n+m), one relaxation per edge O(m):
O(n+m) time and memory. Route reconstruction O(L).

## 9. Testing summary

Described in detail in [`tests/README.md`](tests/README.md): hand cases with justified expected
answers, 1,500 random small graphs per method against brute-force references (subset
enumeration for MSF, transitive closure for SCC, Kahn for acyclicity, Bellman–Ford for distances,
naive relabelling for DSU), O(n+m) certificate checkers for large outputs, acceptance of
alternative answers and rejection of invalid witnesses, state reset, and limit-size inputs
(`n = 100,000`, `m` or `q = 250,000`) both in-process and through the JSON entry point within
`-Xmx384m`.

## 10. Kruskal–Prim benchmark

**Setup.** [`tests/Benchmark.java`](tests/Benchmark.java). For each size, `java.util.Random(20261025 + n)`
draws `m` edges with uniform endpoints in `[0, n)` and uniform weights in `[-10^9, 10^9]` (loops
and parallel edges kept). The **same** `int[][]` is passed to both methods. Sizes keep `m = 2.5n`.

**Verification first.** At each size, before timing, both results are checked with
`GraphTests.forestValid` (distinct IDs, `n - c` edges, acyclic, spanning, weight sum = cost),
both costs must be equal, and the component count must equal an independent union-find count.

**Timer and boundary.** `System.nanoTime()` around one call of `Algorithms.kruskal(n, edges)` or
`Algorithms.prim(n, edges)`. The timed region includes everything the method builds (sort keys
or adjacency, DSU or heap, result map). It excludes JVM start, compilation, graph generation,
JSON parsing, `Input` conversion and output, the same for both methods.

**Warm-up and runs.** 5 untimed calls of each method per size (for JIT compilation), then 7 timed
rounds; the method that runs first alternates between rounds. Command:
`java -Xmx384m -cp .build/tests Benchmark` (OpenJDK 25, Windows 11, Ryzen 7 7840HS, laptop on
mains power, other applications open).

Run 1 (`tests/output/benchmark_run1.txt`), times in milliseconds:

| n | m | MSF cost | components | Kruskal runs | Kruskal median | Prim runs | Prim median | Prim / Kruskal |
|---:|---:|---:|---:|---|---:|---|---:|---:|
| 10,000 | 25,000 | -5,334,526,775,020 | 67 | 2.06, 1.53, 1.48, 1.55, 1.52, 1.48, 1.43 | **1.52** | 2.63, 2.74, 2.58, 2.60, 2.57, 2.41, 2.48 | **2.58** | 1.69 |
| 50,000 | 125,000 | -26,463,030,823,142 | 329 | 9.38, 8.78, 9.99, 9.10, 9.89, 10.13, 9.16 | **9.38** | 19.82, 21.37, 21.16, 19.97, 33.06, 22.95, 20.31 | **21.16** | 2.26 |
| 100,000 | 250,000 | -52,737,000,949,726 | 687 | 22.77, 24.97, 22.41, 21.99, 23.33, 22.34, 22.18 | **22.41** | 62.34, 64.20, 61.72, 63.41, 73.94, 65.32, 61.31 | **63.41** | 2.83 |

Run 2 (`tests/output/benchmark_run2.txt`, a separate JVM, same graphs) gave medians
2.04 / 2.59 ms, 9.79 / 20.93 ms and 24.27 / 64.13 ms (Kruskal / Prim), with ratios
1.27, 2.14 and 2.64.

**Observations.** Both methods return the same optimal cost at every size. On these sparse
random graphs Kruskal was faster at all three sizes, by about 1.3–1.7× at `n = 10^4` and about
2.6–2.8× at `n = 10^5`. From the smallest to the largest size (10× more edges), Kruskal's median
grew about 12–15× and Prim's about 25×. Likely reasons (an interpretation, not a measurement):
Kruskal sorts one contiguous primitive `long[]` and then does near-constant DSU work. Prim first
builds an adjacency array with `2m` entries, then pushes up to `2m` keys into a heap. Each push and
pop moves through `log` levels of a heap that no longer fits in the CPU cache at the larger sizes,
and every arc reads `edges[id]` at a random location. The graph has many components (687 at
`n = 10^5`) because with average degree 5 about `n·e⁻⁵ ≈ 670` vertices are isolated; Prim's
restart loop handles them cheaply.

**Limitations.** One machine, one seed per size and only sparse uniform random graphs with
`m = 2.5n`. Dense graphs or a different heap (an indexed heap with decrease-key, O((n+m) log n))
could change the ranking. Times of a few milliseconds are affected by JIT state, garbage
collection and OS scheduling (see the 33 ms and 74 ms outliers); medians reduce but do not remove
this. The runtime was JDK 25, while grading uses JDK 17. The numbers are wall-clock times of single
calls inside one JVM, not of the whole program.

**Relation to the asymptotic analysis.** Both algorithms are O(n + m log m) here (Prim with a lazy
heap), so the theory predicts the same growth order and says nothing about constant factors. The
measured gap is a constant-factor and memory-access effect. Separately, the slightly
faster-than-linear growth of both methods fits the `log m` factor plus cache effects, but three
sizes are too few to estimate exponents.

## 11. Sources and verification

* Course materials.
* Standard textbook descriptions of DSU, Kruskal, Prim, Kosaraju, DFS topological sort and
  DAG shortest paths (Cormen, Leiserson, Rivest, Stein, *Introduction to Algorithms*, chapters
  on elementary graph algorithms, minimum spanning trees, single-source shortest paths and
  disjoint sets).
* Verification: every command in section 1 was run and its output recorded in
  `tests/output/`; expected answers of the hand cases are computed by hand in
  `tests/README.md`; random cases are compared with brute-force references.
