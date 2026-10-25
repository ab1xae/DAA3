# Own tests

All commands run from the repository root. Python 3.12+ and JDK 17+ are required
(on Windows `py -3` can replace `python`).

| File | What it is |
|---|---|
| `GraphTests.java` | Main Java test suite: hand cases, random small graphs against brute force, checker acceptance/rejection, route helper, state reset, limit-size inputs and deep chains. |
| `cases.jsonl` | 24 hand-written JSON requests (one `{"name", "input"}` object per line). |
| `expected.jsonl` | Independently justified expected answer for each line of `cases.jsonl` (justification below). |
| `actual.jsonl` | Actual program output for `cases.jsonl`, written by `check_cases.py`. |
| `check_cases.py` | Runs `cases.jsonl` through the real JSON entry point twice in one process and checks each answer semantically; `--large` runs 15 seeded limit-size requests. |
| `Benchmark.java` | Kruskal vs Prim benchmark (see `ANALYSIS.md`). |
| `output/` | Recorded outputs of every command below. |

## Commands

```sh
javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
java -Xss256k -Xmx384m -cp .build/tests GraphTests
python tests/check_cases.py
python tests/check_cases.py --large
java -Xmx384m -cp .build/tests Benchmark
java -Xmx384m -cp .build/tests Main < requests.jsonl   # any plain JSON Lines file of requests
```

`GraphTests` runs with a 256 KiB thread stack (`-Xss256k`, well below the default)
to show that no algorithm depends on recursion depth: it processes 100,000-vertex
chains and rings.

Recorded results (`output/`): `GraphTests.txt` — 76 passed, 0 failed;
`check_cases.txt` — all 24 cases pass in both batches and the second batch is
identical to the first; `check_cases_large.txt` — 15/15 limit-size requests pass
in 3.2 s wall time in one process; `benchmark_run1.txt`, `benchmark_run2.txt` — two
benchmark runs.

## How answers are checked

Non-unique answers are never compared literally.

* **Forest** (`forestValid`): cost and component count equal the reference; IDs are
  distinct and in range; exactly `n - components` edges; no edge closes a cycle
  (a separate union-find, which also rejects self-loops); selected weights add up to
  `cost`; every input edge has both endpoints in one tree, so the forest spans each
  component.
* **SCC** (`sccValid`, O(n+m)): the condensation equals the set of cross-label pairs,
  with no duplicates; each label class is strongly connected (forward and backward
  BFS inside the class from one member reaches all members); the condensation graph
  is acyclic (Kahn), so no two classes can be merged. Together these hold exactly
  for the true SCC partition, whatever the label values.
* **Topological order** (`topoValid`): the `acyclic` flag equals the result of Kahn's
  algorithm; the order is a permutation and every edge goes forward.
* **DAG shortest paths** (`dagValid`): `acyclic` equals Kahn's result; `null` exactly
  for vertices that BFS cannot reach; source `0` / `-1`; every other parent edge ends
  at its vertex and is tight (`d[u] + w = d[v]`); no edge from a reached vertex can
  improve a distance; and every parent chain reaches the source. Tight parent paths
  plus "no edge improves" prove the distances are optimal.

References for random small graphs share no code with the solution: naive
relabelling (DSU), enumeration of all edge subsets of size `n - c` (MSF),
Floyd–Warshall transitive closure (SCC), Kahn's algorithm (cycle test) and
Bellman–Ford (DAG distances). 1,500 seeded random graphs are tested per method.

`witnessTests` shows that the checkers accept alternative valid answers (two different
optimal forests, relabelled SCCs, two topological orders, alternative tied parents)
and reject invalid witnesses (repeated ID, self-loop, cycle, wrong component count,
missing/duplicate condensation pair, merged or split SCC, backward edge, repeated
vertex, non-tight parent, parent edge ending elsewhere, consistent but non-shortest
tree, `-1` used as an unreachable distance).

`check_cases.py` has its own Python checker with the same semantic rules (forest
feasibility and cost, SCC partition and exact condensation, topological precedence,
distances with tight parent edges whose chains reach the source). `actual.jsonl` differs from `expected.jsonl` in several places
(SCC labels, the topological order of case 15, the Prim edge order) and every answer
is still accepted.

## Justification of `expected.jsonl`

Line numbers refer to `cases.jsonl`.

1. `dsu n=0`: no operations, so no states.
2. Start with 5 singletons. `[0,0]` changes nothing → `[5,1]`; `[0,1]` → `{0,1}` → `[4,2]`;
   `[1,0]` repeats → `[4,2]`; `[2,3]` → `[3,2]`; `[3,4]` → `{2,3,4}` → `[2,3]`;
   `[4,2]` is already joined → `[2,3]`; `[1,4]` joins everything → `[1,5]`.
3. One vertex: self-unions keep 1 component of size 1.
4. Empty graph: cost 0, no edges, 0 components.
5–6. Components `{0,1,2}`, `{3,4}`, `{5}` → 3. In `{0,1,2}` the two cheapest edges
   `-3` (ID 1) and `1` (ID 2) form no cycle → `-2`. In `{3,4}` the cheaper parallel
   edge `2` (ID 4) wins over `7`; loop `-100` (ID 5) can never be in a forest.
   Total `-2 + 2 = 0`, edges `{1,2,4}`.
7–8. A 4-cycle of equal weights `1e9`: any 3 of the 4 edges are optimal, cost
   `3e9 > 2^31 - 1`.
9. A path of three `-1e9` edges: all three are needed, cost `-3e9`.
10. Three isolated vertices: cost 0, no edges, 3 components.
11. Empty graph: empty arrays.
12. `0→1→2→0` is a cycle → SCC A=`{0,1,2}`; `3↔4` → SCC B=`{3,4}` (loop `4→4` is internal);
    `5` alone (loop) → C. Edges `2→3` and `1→3` both give the pair A→B, reported once.
13. No edges: three singleton SCCs, no pairs.
14. Empty graph: acyclic, empty order.
15. Edges `3→1, 1→0, 3→0 (twice), 2→4` have no cycle; `[3,1,0,2,4]` puts each tail before
    its head. Any order with that property is accepted.
16. `2→3→4→2` is a cycle in a component not containing vertex 0 → `false`, `[]`.
17. Self-loop `2→2` is a cycle.
18. `0→1, 0→2, 2→1`: the DFS from 0 finishes 1 before it reaches it again via 2; that is an
    edge to a finished vertex, not a cycle. Only `[0,2,1]` is valid.
19. Single vertex: distance `[0]`, parent `[-1]`.
20. `2→3→2` is a cycle that the source 0 cannot reach; the whole graph is still cyclic.
21. Source 1. `d(2) = -1` via ID 0; `d(3) = min(-1 + 2, 0 + 4) = 1` via ID 2. Vertex 0 has an
    edge *into* the source but no path *from* it → `null`; vertex 4 has no edges → `null`.
    `-1` here is a real distance, not "unreachable".
22. `d(2) = 1` (ID 2). `d(1) = 2` three ways: ID 0, parallel ID 1, or `0→2→1` (ID 3).
    `d(3) = 2 + 0 = 2` via ID 4. Any of the three parents of 1 is accepted.
23. Path of three `1e9` edges: distances `0, 1e9, 2e9, 3e9`.
24. Self-loop on the source makes the graph cyclic.
