# assignment 3 — analysis

java 17. algorithms and the route helper: [`src/Java/Algorithms.java`](src/Java/Algorithms.java).
[`Main.java`](src/Java/Main.java) reads one json request per line and prints one result per line.
tests: [`tests/`](tests/README.md).

## commands

run from the project root (jdk 17+, python 3.12+):

```sh
javac --release 17 -encoding UTF-8 -d .build/java src/Java/*.java
java -cp .build/java Main < requests.jsonl

javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
java -Xss256k -Xmx384m -cp .build/tests GraphTests
python tests/check_cases.py
python tests/check_cases.py --large
java -Xmx384m -cp .build/tests Benchmark
```

results (in `tests/output/`): `GraphTests` 76/76, `check_cases.py` 24/24 (twice in one process),
`--large` 15/15 in 3.2 s. machine: ryzen 7 7840hs, 15 gb ram, windows 11, openjdk 25.

## common

- graphs are stored as arrays: `start[u]` and `arcs` with edge ids, so original ids are kept. o(n+m).
- state reset: every request gets new arrays, no static state. checked by running all cases twice.
- no recursion: every dfs uses an array stack and `next[u]`, so each edge is scanned once.
  tested with `-Xss256k` on chains of 100,000 vertices.
- costs and distances are `long` (max about 10^14).

## dsu

- steps: each vertex is its own set. for `[u,v]` find roots with path compression, if different
  attach the smaller tree to the larger, update `components` and `largest`, save the state.
- invariant: trees = connected components so far, `size[root]` = component size.
- correctness: merge only when roots differ, so repeated and self unions change nothing.
  sizes only grow, so updating `largest` on merge is enough.
- edge cases: `n=0` → `[]`, self unions, repeated unions.
- complexity: o(n + q·α(n)) time, o(n + q) memory.

## kruskal

- steps: sort edges by weight, take an edge if `find(u) != find(v)`. `components = n - taken`.
- invariant: taken edges are part of some minimum spanning forest.
- correctness: a taken edge is the lightest edge leaving its tree (cut property). self-loops are
  never taken. a forest with `c` trees has `n - c` edges.
- edge cases: empty graph, isolated vertices, loops, parallel edges, ties, negative weights, big sums.
- complexity: o(n + m log m) time, o(n + m) memory.

## prim

- steps: for each unvisited vertex start a new tree (`components++`). push its edges to a min-heap,
  pop the cheapest, skip it if both ends are visited, otherwise take it and visit the new vertex.
  empty heap → component done, restart from the next unvisited vertex.
- invariant: the current tree is part of a minimum forest, the heap has every edge leaving it.
- correctness: the popped edge is the lightest edge leaving the tree (cut property).
- edge cases: same as kruskal. same cost as kruskal, edge ids may differ on ties.
- complexity: each edge pushed at most twice, o(n + m log m) time, o(n + m) memory.

## kosaraju scc

- steps: dfs on the graph → finish order. dfs on the reversed graph in decreasing finish order,
  each tree gets a new label. cross-label edges go to the condensation once (hash set).
- invariant: if scc `c` has an edge to scc `d`, `c` finishes later than `d`.
- correctness: so the latest unlabelled vertex is in an scc that the reversed dfs cannot leave,
  so every second-pass tree is exactly one scc.
- edge cases: empty graph, isolated vertices, self-loops, duplicates, chain, ring.
- complexity: o(n + m) expected time, o(n + m) memory.

## topological order

- steps: states unvisited / active / finished. edge to an active vertex → cycle →
  `acyclic=false, order=[]`. finished vertices are written from the end of `order`.
- invariant: active vertices = the current dfs path.
- correctness: an edge to an active vertex closes a cycle (self-loop too). an edge to a finished
  vertex is not a cycle. for every edge `u → v`, `v` finishes first, so `u` comes first in the order.
- edge cases: empty graph, self-loop, cycle in a later component, edge to a finished vertex.
- complexity: o(n + m) time and memory.

## dag shortest paths

- steps: cycle check on the whole graph, any cycle → `acyclic=false`. then relax edges in topological
  order only from reached vertices and save the edge id in `parent_edge`. unreachable → `null`.
- invariant: when a vertex is processed its distance is final.
- correctness: all predecessors come earlier in the order, so the last edge of a shortest path is
  already relaxed. negative weights are ok because there are no cycles.
- edge cases: single vertex, unreachable cycle, self-loop, distance `-1`, ties, parallel edges.
- complexity: o(n + m) time and memory.

route helper `route(edges, parentEdge, source, target)` walks `parent_edge` back and reverses, o(L).
source → `[source]`, unreachable → empty list.

| case | parent_edge | route | edge ids | check |
|---|---|---|---|---|
| `[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]`, target 3 | `[-1,0,2,3,-1]` | `[0,1,2,3]` | 0, 2, 3 | 3 - 7 + 4 = 0 |
| same, target 0 (source) | | `[0]` | — | 0 |
| same, target 4 (unreachable) | | `[]` | — | null |
| `[[0,1,2],[0,1,2],[0,2,1],[2,1,1],[1,3,0]]`, target 3 | `[-1,0,2,4]` | `[0,1,3]` | 0, 4 | 2 + 0 = 2 |
| same, other parallel edge | `[-1,1,2,4]` | `[0,1,3]` | 1, 4 | 2 |
| same, tied path | `[-1,3,2,4]` | `[0,2,1,3]` | 2, 3, 4 | 1 + 1 + 0 = 2 |

## kruskal vs prim benchmark

same graphs for both: `Random(20261025 + n)`, `m = 2.5n`, weights in [-10^9, 10^9]. forests and
costs checked first. timer `System.nanoTime()` around one call (no jvm start, parsing or output).
5 warm-up calls, then 7 runs. command: `java -Xmx384m -cp .build/tests Benchmark`.

| n | m | kruskal runs, ms | median | prim runs, ms | median |
|---:|---:|---|---:|---|---:|
| 10,000 | 25,000 | 2.06, 1.53, 1.48, 1.55, 1.52, 1.48, 1.43 | **1.52** | 2.63, 2.74, 2.58, 2.60, 2.57, 2.41, 2.48 | **2.58** |
| 50,000 | 125,000 | 9.38, 8.78, 9.99, 9.10, 9.89, 10.13, 9.16 | **9.38** | 19.82, 21.37, 21.16, 19.97, 33.06, 22.95, 20.31 | **21.16** |
| 100,000 | 250,000 | 22.77, 24.97, 22.41, 21.99, 23.33, 22.34, 22.18 | **22.41** | 62.34, 64.20, 61.72, 63.41, 73.94, 65.32, 61.31 | **63.41** |

second run medians: 2.04 / 2.59, 9.79 / 20.93, 24.27 / 64.13 ms.

kruskal was faster, about 2.6–2.8× at the largest size: it sorts one array, while prim does up to
`2m` heap operations. both are o(m log m), so the gap is a constant factor. limits: one machine,
one seed, only sparse random graphs, jit and gc noise.
