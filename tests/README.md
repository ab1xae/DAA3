# Tests

Run from the project root (commands are also in `ANALYSIS.md`):

```sh
javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/GraphTests.java tests/Benchmark.java
java -Xss256k -Xmx384m -cp .build/tests GraphTests
python tests/check_cases.py
python tests/check_cases.py --large
java -Xmx384m -cp .build/tests Benchmark
```

## Files

| File | Contents |
|---|---|
| `GraphTests.java` | Main test suite (76 checks). |
| `cases.jsonl` | 24 hand-written requests. |
| `expected.jsonl` | Expected answer for each case (explained below). |
| `actual.jsonl` | Real program output for the cases. |
| `check_cases.py` | Runs the cases through `Main` twice in one process and checks the answers; `--large` runs 15 full-size inputs. |
| `Benchmark.java` | Kruskal vs Prim benchmark. |
| `output/` | Saved output of every command. |

## What is tested

* **Brute force on small graphs:** 1,500 random graphs per method, compared with simple reference
  methods: naive relabelling (DSU), trying all edge subsets (spanning forest), reachability matrix
  (SCC), Kahn's algorithm (cycles), Bellman–Ford (distances).
* **Large inputs:** `n = 100,000`, `m = 250,000`, chains and rings of 100,000 vertices.
* **State reset:** the same mixed requests several times in one process give identical output.
* **Route helper:** reachable target, source, unreachable target, parallel/tied edges.
* **Checkers:** they accept other valid answers (another forest, other labels, another order,
  another tied parent) and reject wrong ones (repeated edge, self-loop, cycle, missing or
  duplicate condensation pair, wrong order, wrong parent edge, non-shortest distance, `-1` instead
  of `null`).

Answers that are not unique are checked by their properties, not compared as text:
forest — valid, spanning, correct cost; SCC — each group strongly connected and the condensation
exact; order — every edge goes forward; distances — parent edges are tight, lead to the source and
no edge gives a shorter path. That is why `actual.jsonl` can differ from `expected.jsonl` (other
labels or order) and still pass.

## Expected answers (`cases.jsonl` line by line)

1. DSU `n=0`: no states.
2. `[0,0]` → `[5,1]`; `[0,1]` → `[4,2]`; `[1,0]` repeat → `[4,2]`; `[2,3]` → `[3,2]`; `[3,4]` → `[2,3]`;
   `[4,2]` repeat → `[2,3]`; `[1,4]` → `[1,5]`.
3. One vertex: always `[1,1]`.
4. Empty graph: cost 0, no edges, 0 components.
5–6. Components `{0,1,2}`, `{3,4}`, `{5}`. Take `-3` and `1` in the first, `2` in the second (loop
   `-100` is never used). Cost `0`, edges `{1,2,4}`, 3 components.
7–8. Cycle of four equal `10^9` edges: any 3, cost `3·10^9`.
9. Path of three `-10^9` edges: cost `-3·10^9`.
10. Three isolated vertices: cost 0, 3 components.
11. Empty graph: empty arrays.
12. SCCs `{0,1,2}`, `{3,4}`, `{5}`; two edges between the first two give one pair.
13. No edges: 3 SCCs, no pairs.
14. Empty graph: acyclic, empty order.
15. No cycle; `[3,1,0,2,4]` is one valid order.
16. Cycle `2→3→4→2` → not acyclic.
17. Self-loop → not acyclic.
18. `0→1, 0→2, 2→1`: edge to a finished vertex, not a cycle; only `[0,2,1]` works.
19. One vertex: `[0]`, parent `[-1]`.
20. Cycle unreachable from the source → still not acyclic.
21. Source 1: `d(2) = -1`, `d(3) = 1`; vertices 0 and 4 unreachable → `null`.
22. `d(1) = 2` three ways (two parallel edges or `0→2→1`), `d(2) = 1`, `d(3) = 2`.
23. Distances `0, 10^9, 2·10^9, 3·10^9`.
24. Self-loop on the source → not acyclic.
