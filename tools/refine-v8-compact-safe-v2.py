from pathlib import Path

# Reuse the reviewed V8 migration but remove the one redundant replacement that
# made the first run stop before touching any project file.
script = Path('tools/refine-v8-compact-safe.py').read_text(encoding='utf-8')
redundant = "        ('OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(50.dp))', 'OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(42.dp))'),\n"
if script.count(redundant) != 1:
    raise SystemExit('Expected redundant News button replacement was not found exactly once')
script = script.replace(redundant, '', 1)
exec(compile(script, 'refine-v8-compact-safe.py', 'exec'), {'__name__': '__main__'})
