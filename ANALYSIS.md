# Assignment 3 — Analysis

Java 17. The algorithms and the route helper are in [`src/Java/Algorithms.java`](src/Java/Algorithms.java).
[`Main.java`](src/Java/Main.java) reads one JSON request per line and prints one JSON result per line;
[`JsonCodec.java`](src/Java/JsonCodec.java) parses and writes JSON. Tests are in [`tests/`](tests/README.md).

## 1. Commands

Run from the project root (JDK 17+, Python 3.12+; on Windows `py -3` instead of `python`).

```sh
javac --release 17 -encoding UTF-8 -d .build/java src/Java/*.java
java -Xmx384m -cp .build/java Main < requests.jsonl

javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
java -Xss256k -Xmx384m -cp .build/tests GraphTests
python tests/check_cases.py
python tests/check_cases.py --large
java -Xmx384m -cp .build/tests Benchmark
```

Results (saved in [`tests/output/`](tests/output)): `GraphTests` 76/76 passed; `check_cases.py`
24/24 cases passed twice in one process; `check_cases.py --large` 15/15 passed in 3.2 s.

Machine: AMD Ryzen 7 7840HS, 15.2 GB RAM, Windows 11 Pro, OpenJDK 25 (compiled with `--release 17`).

## 2. Common points

* **Graph storage.** Adjacency lists are stored as two arrays (CSR): `start[u]` and `arcs`, where
  `arcs[start[u] .. start[u+1])` are the IDs of the edges leaving `u`. Storing edge IDs keeps the
  original IDs for `edge_ids` and `parent_edge`. Building it is O(n+m).
* **State reset.** Every request gets new arrays; `Algorithms` has no static state. Tested by running
  all cases twice in one process and comparing the outputs.
* **Deep graphs.** No recursion. Every DFS uses an array stack and `next[u]` (the next edge to scan
  for `u`), so a vertex continues where it stopped and every edge is scanned once. Tests run with a
  small stack (`-Xss256k`) on chains of 100,000 vertices.
* **Overflow.** Costs and distances are `long`; the largest absolute value is about `10^14`.
* **Sorting key.** Edges are ordered by the `long` key `((w + 10^9) << 32) | id`: weight first,
  and `(int) key` gives the ID back.

## 3. DSU

**Steps.** Each vertex starts as its own set. For `[u, v]`: find both roots (with path compression);
if different, attach the smaller tree to the larger one, update `components` and `largest`;
record `[components, largest]`.

**Invariant.** The trees are the connected components of the operations so far; `size[root]` is
the component size.

**Correctness.** A union merges two components only when the roots differ, so the trees always equal
the components. Repeated and self unions change nothing. Sizes only grow, so updating `largest` at
each merge is enough.

**Boundary cases.** `n = 0` → `[]`; self unions; repeated unions.

**Complexity.** O(n + q·α(n)) time, O(n + q) memory.

## 4. Kruskal

**Steps.** Sort edges by weight. Go through them with a DSU and take an edge when
`find(u) != find(v)`. `components = n - taken edges`.

**Invariant.** The taken edges are always part of some minimum spanning forest.

**Correctness.** When an edge is taken, it is the lightest edge leaving its current tree (all lighter
edges were already taken or lie inside one tree), so by the cut property it belongs to a minimum
forest. Self-loops always have `find(u) == find(v)` and are never taken. A forest with `c` trees has
`n - c` edges, which gives the component count, isolated vertices included.

**Boundary cases.** Empty graph, isolated vertices, loops, parallel edges, ties, negative weights,
totals above 32 bits.

**Complexity.** O(n + m log m) time, O(n + m) memory.

## 5. Prim

**Steps.** Undirected adjacency without self-loops. For each unvisited vertex start a new tree
(`components++`): visit it and push its edges into a min-heap. Pop the cheapest edge; skip it if both
ends are visited (lazy deletion); otherwise take it and visit the new vertex. When the heap is empty
the component is done and the loop restarts from the next unvisited vertex.

**Invariant.** The current tree is part of a minimum spanning forest, and the heap holds every edge
leaving the tree.

**Correctness.** The popped edge is the lightest edge leaving the tree (cut property). An empty heap
means no edge leaves the tree, so it is a whole component. Restarting covers every component once.

**Boundary cases.** Same as Kruskal, tested on the same inputs. Both give the same cost; edge IDs may
differ when there are ties.

**Complexity.** Each edge is pushed at most twice: O(n + m log m) time, O(n + m) memory.

## 6. Kosaraju SCC

**Steps.** (1) DFS on the graph and record finish order. (2) DFS on the reversed graph in decreasing
finish order; each DFS tree gets a new label. (3) For each edge between different labels add the
pair once (hash set).

**Invariant.** If there is an edge from SCC `C` to SCC `D`, `C` finishes later than `D` in pass 1.

**Correctness.** So the vertex with the latest finish time is in an SCC that no unlabelled SCC
points into. In the reversed graph its DFS reaches exactly its own SCC and cannot leave it. So
every pass-2 tree is exactly one SCC.

**Boundary cases.** Empty graph, isolated vertices, self-loops, duplicate edges, several edges
between the same SCCs (one pair), chain of 100,000 vertices, ring of 100,000 vertices.

**Complexity.** O(n + m) expected time (hashing), O(n + m) memory.

## 7. Topological order (DFS)

**Steps.** States: unvisited, active, finished. DFS from every unvisited vertex. An edge to an active
vertex means a cycle → `acyclic=false, order=[]`. When a vertex finishes, write it at the end of
`order` (so `order` is the reverse finish order).

**Invariant.** Active vertices are exactly the current DFS path.

**Correctness.** An edge to an active vertex closes a cycle (a self-loop too). If a cycle exists,
the first visited cycle vertex is still active when the cycle edge back to it is checked. An edge
to a finished vertex is not a cycle. In a DAG, for every edge `u → v`, `v` finishes before `u`,
so in reverse finish order `u` comes first.

**Boundary cases.** Empty graph, self-loop, cycle in a later component, edge to a finished vertex,
long chains, ring.

**Complexity.** O(n + m) time and memory.

## 8. DAG shortest paths and route helper

**Steps.** Run the cycle check on the whole graph; any cycle → `acyclic=false` with empty arrays.
Otherwise go through vertices in topological order, skip unreached ones, and relax their outgoing
edges, saving the edge ID in `parent_edge[v]`. A separate `reached` array is used, so `-1` is never
used as a distance; unreachable vertices get `null`.

**Invariant.** When a vertex is processed, its distance is already final.

**Correctness.** All predecessors of `u` come before `u` in topological order, so the last edge of a
shortest path to `u` has already been relaxed. Negative weights are fine because there are no
cycles. Parent edges are tight and point backwards in the order, so they lead to the source.

**Route helper.** `route(edges, parentEdge, source, target)` follows `parent_edge` from the target
back to the source and reverses the list. O(L) for `L` vertices. Source → `[source]`.
**Unreachable target → empty list.**

Actual calls (from `tests/output/GraphTests.txt`):

| Case | `parent_edge` | Route | Edge IDs | Check |
|---|---|---|---|---|
| Example `[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]`, target 3 | `[-1,0,2,3,-1]` | `[0,1,2,3]` | 0, 2, 3 | `3 - 7 + 4 = 0 = distance[3]` |
| Same, target = source 0 | | `[0]` | — | cost 0 |
| Same, target 4 (unreachable) | `parent_edge[4] = -1` | `[]` | — | `distance[4] = null` |
| Parallel/tied `[[0,1,2],[0,1,2],[0,2,1],[2,1,1],[1,3,0]]`, target 3 | `[-1,0,2,4]` | `[0,1,3]` | 0, 4 | `2 + 0 = 2 = distance[3]` |
| Same, other parallel edge as parent of 1 | `[-1,1,2,4]` | `[0,1,3]` | 1, 4 | cost 2, accepted |
| Same, tied detour as parent of 1 | `[-1,3,2,4]` | `[0,2,1,3]` | 2, 3, 4 | `1 + 1 + 0 = 2`, accepted |

Each step of a route is checked to go along its parent edge in the right direction.

**Boundary cases.** Single vertex, unreachable cycle, self-loop, distance `-1`, unreachable vertex,
parallel edges and ties, distances above 32 bits, chain of 100,000 vertices.

**Complexity.** O(n + m) time and memory; route O(L).

## 9. Tests

See [`tests/README.md`](tests/README.md). In short: hand cases with explained answers, 1,500 random
small graphs per method compared with brute force, checkers that accept other valid answers and
reject wrong ones, state reset, and inputs at full size (`n = 100,000`, `m = 250,000`).

## 10. Kruskal vs Prim benchmark

* **Graphs:** `java.util.Random(20261025 + n)`, `m = 2.5n` random edges, weights in `[-10^9, 10^9]`.
  Both methods get the same array.
* **Checked first:** both forests valid, same cost, correct component count.
* **Timer:** `System.nanoTime()` around one method call. Includes building the method's own data
  (sort / adjacency / heap). Excludes JVM start, compilation, graph generation, JSON parsing and output.
* **Warm-up:** 5 calls of each method, then 7 timed runs, alternating which method runs first.
* **Command:** `java -Xmx384m -cp .build/tests Benchmark`.

Run 1 (`tests/output/benchmark_run1.txt`), milliseconds:

| n | m | Kruskal runs | Kruskal median | Prim runs | Prim median |
|---:|---:|---|---:|---|---:|
| 10,000 | 25,000 | 2.06, 1.53, 1.48, 1.55, 1.52, 1.48, 1.43 | **1.52** | 2.63, 2.74, 2.58, 2.60, 2.57, 2.41, 2.48 | **2.58** |
| 50,000 | 125,000 | 9.38, 8.78, 9.99, 9.10, 9.89, 10.13, 9.16 | **9.38** | 19.82, 21.37, 21.16, 19.97, 33.06, 22.95, 20.31 | **21.16** |
| 100,000 | 250,000 | 22.77, 24.97, 22.41, 21.99, 23.33, 22.34, 22.18 | **22.41** | 62.34, 64.20, 61.72, 63.41, 73.94, 65.32, 61.31 | **63.41** |

Run 2 medians (Kruskal / Prim): 2.04 / 2.59, 9.79 / 20.93, 24.27 / 64.13 ms.

**Observations.** Same cost at every size. Kruskal was faster: about 1.3–1.7× at the smallest size
and about 2.6–2.8× at the largest. A likely reason: Kruskal sorts one primitive array, while Prim does up
to `2m` heap operations with scattered memory access.

**Limitations.** One machine, one seed per size, only sparse random graphs. Small times are affected
by JIT and garbage collection. Dense graphs or another heap could give different results.

**Theory.** Both are O(m log m) here, so theory predicts the same growth; the difference is a
constant factor.

## 11. Sources

* Course materials.
* Cormen, Leiserson, Rivest, Stein, *Introduction to Algorithms* (graph algorithms, minimum
  spanning trees, shortest paths, disjoint sets).
