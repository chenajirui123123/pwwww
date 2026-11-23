#!/usr/bin/env python3
"""Guard package dependencies; UI cannot reach persistence implementations or rule engines."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/wardrobe/atelier'
violations = []
rules = {
    'ui': [r'com\.wardrobe\.atelier\.data', r'\bLocalStore\b', r'getSharedPreferences\s*\(', r'\bSQLite\w*\b', r'\bvm\.perform\s*\(', r'\bGenerate(?:Outfit|Capsule)UseCase\b', r'\b(?:InspirationEngine|CapsuleEngine)\.generate\s*\('],
    'presentation': [r'com\.wardrobe\.atelier\.(?:ui|data)\b', r'\bLocalStore\b', r'getSharedPreferences\s*\(', r'\bSQLite\w*\b'],
    'domain': [r'^import (?:android\.|androidx\.)', r'com\.wardrobe\.atelier\.(?:ui|presentation|data)\b'],
    'data': [r'com\.wardrobe\.atelier\.(?:ui|presentation)\b'],
}
for package, patterns in rules.items():
    for path in (root / package).rglob('*.kt'):
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if any(re.search(pattern, line) for pattern in patterns):
                violations.append(f'{path.relative_to(root)}:{number}: {line.strip()}')
if violations:
    raise SystemExit('Architecture boundary violations:\n' + '\n'.join(violations))
print('PASS: UI, presentation, domain and data dependency boundaries')
