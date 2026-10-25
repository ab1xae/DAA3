"""Check own cases: python tests/check_cases.py [--large]"""
import argparse
import json
import pathlib
import random
import subprocess
import sys
import time
from collections import deque

ROOT = pathlib.Path(__file__).resolve().parents[1]
N, M = 100_000, 250_000
W = 1_000_000_000


def build():
    output = ROOT / '.build' / 'java'
    output.mkdir(parents=True, exist_ok=True)
    sources = sorted(str(path) for path in (ROOT / 'src' / 'Java').glob('*.java'))
    subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(output), *sources],
                   check=True, timeout=60)
    return ['java', '-Xmx384m', '-cp', str(output), 'Main']


def execute(command, requests):
    data = ''.join(json.dumps(request, separators=(',', ':')) + '\n' for request in requests)
    result = subprocess.run(command, input=data, capture_output=True, text=True, encoding='utf-8', timeout=20)
    if result.returncode:
        raise RuntimeError(f'program exited with code {result.returncode}: {result.stderr[:2000]}')
    lines = result.stdout.splitlines()
    if len(lines) != len(requests):
        raise RuntimeError(f'expected {len(requests)} result lines, got {len(lines)}')
    return [json.loads(line) for line in lines]


def is_int(value):
    return type(value) is int and -(1 << 63) <= value < (1 << 63)


def check(request, answer, expected):
    try:
        return CHECKS[request['task']](request, answer, expected)
    except (KeyError, TypeError, ValueError, IndexError):
        return False


def check_dsu(x, y, e):
    return y['states'] == e['states']


def check_forest(x, y, e):
    n, edges, ids = x['n'], x['edges'], y['edge_ids']
    if y['cost'] != e['cost'] or y['components'] != e['components']:
        return False
    if not all(is_int(i) and 0 <= i < len(edges) for i in ids) or len(set(ids)) != len(ids):
        return False
    if len(ids) != n - y['components']:
        return False
    parent = list(range(n))
    total = 0
    for i in ids:
        u, v, w = edges[i]
        a, b = find(parent, u), find(parent, v)
        if a == b:
            return False
        parent[a] = b
        total += w
    return total == y['cost'] and all(find(parent, u) == find(parent, v) for u, v, _ in edges)


def check_scc(x, y, e):
    labels, reference = y['component'], e['component']
    if len(labels) != len(reference) or not all(is_int(l) and l >= 0 for l in labels):
        return False
    forward, backward = {}, {}
    for label, ref in zip(labels, reference):
        if forward.setdefault(label, ref) != ref or backward.setdefault(ref, label) != label:
            return False
    pairs = [tuple(p) for p in y['condensation']]
    expected = {(labels[u], labels[v]) for u, v in x['edges'] if labels[u] != labels[v]}
    return len(pairs) == len(set(pairs)) and set(pairs) == expected


def check_topo(x, y, e):
    if y['acyclic'] is not e['acyclic']:
        return False
    if not y['acyclic']:
        return y['order'] == []
    n, order = x['n'], y['order']
    position = [-1] * n
    if len(order) != n:
        return False
    for index, v in enumerate(order):
        if not (is_int(v) and 0 <= v < n) or position[v] != -1:
            return False
        position[v] = index
    return all(position[u] < position[v] for u, v in x['edges'])


def check_dag(x, y, e):
    if y['acyclic'] is not e['acyclic']:
        return False
    if not y['acyclic']:
        return y['distance'] == [] and y['parent_edge'] == []
    n, source, edges = x['n'], x['source'], x['edges']
    distance, parent = y['distance'], y['parent_edge']
    if distance != e['distance'] or len(parent) != n:
        return False
    for v in range(n):
        if v == source or distance[v] is None:
            if parent[v] != -1:
                return False
            continue
        if not (is_int(parent[v]) and 0 <= parent[v] < len(edges)):
            return False
        u, head, w = edges[parent[v]]
        if head != v or distance[u] is None or distance[u] + w != distance[v]:
            return False
    state = [0] * n
    state[source] = 2
    for v in range(n):
        if distance[v] is None:
            continue
        chain, current = [], v
        while state[current] == 0:
            state[current] = 1
            chain.append(current)
            current = edges[parent[current]][0]
        if state[current] != 2:
            return False
        for c in chain:
            state[c] = 2
    return True


CHECKS = {'dsu': check_dsu, 'kruskal': check_forest, 'prim': check_forest,
          'scc': check_scc, 'topo': check_topo, 'dag': check_dag}


def small():
    cases = [json.loads(line) for line in (ROOT / 'tests/cases.jsonl').read_text(encoding='utf-8').splitlines()]
    expected = [json.loads(line) for line in (ROOT / 'tests/expected.jsonl').read_text(encoding='utf-8').splitlines()]
    assert len(cases) == len(expected)
    command = build()
    answers = execute(command, [case['input'] for case in cases] * 2)
    failures = 0
    for index, (case, answer) in enumerate(zip(cases * 2, answers)):
        ok = check(case['input'], answer, expected[index % len(cases)])
        failures += not ok
        print(('PASS ' if ok else 'FAIL ') + ('[repeat] ' if index >= len(cases) else '') + case['name'])
    first, second = answers[:len(cases)], answers[len(cases):]
    same = first == second
    failures += not same
    print(('PASS' if same else 'FAIL') + ' second batch in the same process repeats the first exactly')
    with open(ROOT / 'tests/actual.jsonl', 'w', encoding='utf-8', newline='\n') as out:
        for answer in first:
            out.write(json.dumps(answer, separators=(',', ':')) + '\n')
    return failures


def find(parent, x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x


def ref_dsu(n, ops):
    parent, size = list(range(n)), [1] * n
    components, largest, states = n, 1 if n else 0, []
    for u, v in ops:
        a, b = find(parent, u), find(parent, v)
        if a != b:
            if size[a] < size[b]:
                a, b = b, a
            parent[b] = a
            size[a] += size[b]
            components -= 1
            largest = max(largest, size[a])
        states.append([components, largest])
    return {'states': states}


def ref_forest(n, edges):
    parent, cost, used = list(range(n)), 0, 0
    for u, v, w in sorted(edges, key=lambda e: e[2]):
        a, b = find(parent, u), find(parent, v)
        if a != b:
            parent[a] = b
            cost += w
            used += 1
    return {'cost': cost, 'components': n - used}


def ref_dag(n, source, edges):
    out, indegree = [[] for _ in range(n)], [0] * n
    for u, v, w in edges:
        out[u].append((v, w))
        indegree[v] += 1
    queue = deque(v for v in range(n) if indegree[v] == 0)
    order = []
    while queue:
        u = queue.popleft()
        order.append(u)
        for v, _ in out[u]:
            indegree[v] -= 1
            if indegree[v] == 0:
                queue.append(v)
    if len(order) < n:
        return {'acyclic': False, 'distance': [], 'parent_edge': []}
    distance = [None] * n
    distance[source] = 0
    for u in order:
        if distance[u] is not None:
            for v, w in out[u]:
                if distance[v] is None or distance[u] + w < distance[v]:
                    distance[v] = distance[u] + w
    return {'acyclic': True, 'distance': distance}


def random_dag(rng, n, m):
    perm = list(range(n))
    rng.shuffle(perm)
    edges = []
    for _ in range(m):
        a, b = rng.randrange(n), rng.randrange(n - 1)
        b += b >= a
        edges.append([perm[min(a, b)], perm[max(a, b)]])
    return edges


def planted_scc(rng, n, m):
    perm = list(range(n))
    rng.shuffle(perm)
    group = [0] * n
    members, start, g = [], 0, 0
    while start < n:
        size = 1 if rng.random() < 0.3 else rng.randint(2, 400)
        block = perm[start:start + size]
        for v in block:
            group[v] = g
        members.append(block)
        start += size
        g += 1
    edges = []
    for block in members:
        if len(block) > 1:
            edges += [[block[i], block[(i + 1) % len(block)]] for i in range(len(block))]
    while len(edges) < m:
        a, b = rng.randrange(n), rng.randrange(n)
        if group[a] > group[b]:
            a, b = b, a
        edges.append([a, b])
    rng.shuffle(edges)
    return edges, group


def large():
    rng = random.Random(2026)
    cases = []

    ops = [[rng.randrange(N), rng.randrange(N)] for _ in range(M)]
    cases.append(('dsu random n=100000 q=250000', {'task': 'dsu', 'n': N, 'operations': ops}, ref_dsu(N, ops)))
    chain_ops = [[i, i + 1] for i in range(N - 1)]
    cases.append(('dsu chain of unions', {'task': 'dsu', 'n': N, 'operations': chain_ops}, ref_dsu(N, chain_ops)))

    weighted = [[rng.randrange(N), rng.randrange(N), rng.randint(-W, W)] for _ in range(M)]
    forest = ref_forest(N, weighted)
    for task in ('kruskal', 'prim'):
        cases.append((f'{task} random n=100000 m=250000', {'task': task, 'n': N, 'edges': weighted}, forest))
    path = [[i, i + 1, W] for i in range(N - 1)]
    for task in ('kruskal', 'prim'):
        cases.append((f'{task} path, cost 9.9999e13', {'task': task, 'n': N, 'edges': path},
                      {'cost': (N - 1) * W, 'components': 1}))

    planted, group = planted_scc(rng, N, M)
    cases.append(('scc planted groups n=100000 m=250000', {'task': 'scc', 'n': N, 'edges': planted},
                  {'component': group}))
    chain = [[i, i + 1] for i in range(N - 1)]
    cases.append(('scc deep chain', {'task': 'scc', 'n': N, 'edges': chain}, {'component': list(range(N))}))
    ring = chain + [[N - 1, 0]]
    cases.append(('scc ring', {'task': 'scc', 'n': N, 'edges': ring}, {'component': [0] * N}))

    dag_edges = random_dag(rng, N, M)
    cases.append(('topo random DAG n=100000 m=250000', {'task': 'topo', 'n': N, 'edges': dag_edges}, {'acyclic': True}))
    cases.append(('topo deep chain (descending IDs)', {'task': 'topo', 'n': N,
                  'edges': [[N - 1 - i, N - 2 - i] for i in range(N - 1)]}, {'acyclic': True}))
    cases.append(('topo ring', {'task': 'topo', 'n': N, 'edges': ring}, {'acyclic': False}))

    weighted_dag = [[u, v, rng.randint(-W, W)] for u, v in dag_edges]
    source = rng.randrange(N)
    cases.append(('dag random n=100000 m=250000', {'task': 'dag', 'n': N, 'source': source, 'edges': weighted_dag},
                  ref_dag(N, source, weighted_dag)))
    negative_chain = [[i, i + 1, -W] for i in range(N - 1)]
    cases.append(('dag deep chain, distance -9.9999e13', {'task': 'dag', 'n': N, 'source': 0, 'edges': negative_chain},
                  ref_dag(N, 0, negative_chain)))
    cases.append(('dag ring', {'task': 'dag', 'n': N, 'source': 0, 'edges': [e + [1] for e in ring]},
                  {'acyclic': False}))

    command = build()
    started = time.perf_counter()
    answers = execute(command, [request for _, request, _ in cases])
    elapsed = time.perf_counter() - started
    failures = 0
    for (name, request, expected), answer in zip(cases, answers):
        ok = check(request, answer, expected)
        failures += not ok
        print(('PASS ' if ok else 'FAIL ') + name)
    print(f'{len(cases)} limit-size requests in one process: {elapsed:.1f} s wall time '
          '(includes JVM start, JSON parsing and output)')
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--large', action='store_true')
    failures = large() if parser.parse_args().large else small()
    print('ALL PASSED' if failures == 0 else f'{failures} FAILED')
    return int(bool(failures))


if __name__ == '__main__':
    sys.exit(main())
