#!/usr/bin/env python3
"""
Post-process CRD YAML to inject x-kubernetes-validations (CEL rules).

This script adds Kubernetes CEL validation rules to generated CRDs.
Run after Fabric8 CRD generation to enforce constraints at API server level.
"""

import sys
import re
from pathlib import Path

# CEL validations to inject per CRD
# Format: { "crd_name": { "path": "spec", "validations": [...] } }
CEL_VALIDATIONS = {
    "acls.messaging.example.com": {
        "path": "spec",
        "validations": [
            {
                "rule": "(has(self.topicRef) && !has(self.consumerGroupRef)) || (!has(self.topicRef) && has(self.consumerGroupRef))",
                "message": "Exactly one of topicRef or consumerGroupRef must be specified"
            }
        ]
    }
}


def format_validation_yaml(validations: list, indent: int) -> str:
    """Format validations as YAML with proper indentation."""
    spaces = " " * indent
    lines = [f"{spaces}x-kubernetes-validations:"]
    for v in validations:
        lines.append(f"{spaces}- rule: \"{v['rule']}\"")
        lines.append(f"{spaces}  message: \"{v['message']}\"")
    return "\n".join(lines)


def inject_validations(content: str) -> str:
    """Inject CEL validations into CRD YAML content."""
    result_docs = []

    # Split by YAML document separator
    documents = re.split(r'^---\s*$', content, flags=re.MULTILINE)

    for doc in documents:
        if not doc.strip():
            continue

        # Check if this document needs validation injection
        for crd_name, config in CEL_VALIDATIONS.items():
            if f"name: {crd_name}" in doc:
                doc = inject_into_document(doc, config)
                break

        result_docs.append(doc)

    return "---".join(result_docs)


def inject_into_document(doc: str, config: dict) -> str:
    """Inject validations into a single CRD document."""
    path = config["path"]
    validations = config["validations"]

    if path == "spec":
        # Find the spec section and inject after its "type: object" line
        # Pattern: find "spec:" section, then its closing "type: object"
        lines = doc.split("\n")
        result_lines = []
        in_spec = False
        spec_indent = 0
        found_type_object = False

        for i, line in enumerate(lines):
            result_lines.append(line)

            # Detect entering spec section (under properties)
            if re.match(r'^(\s*)spec:\s*$', line):
                match = re.match(r'^(\s*)spec:\s*$', line)
                # This is the spec field definition, look for properties under it
                in_spec = True
                spec_indent = len(match.group(1))
                continue

            if in_spec and not found_type_object:
                # Look for "type: object" at spec level (spec_indent + 2 spaces)
                expected_indent = spec_indent + 2
                type_pattern = rf'^{" " * expected_indent}type: object\s*$'
                if re.match(type_pattern, line):
                    # Check if next line is "status:" or less indented (end of spec)
                    if i + 1 < len(lines):
                        next_line = lines[i + 1]
                        next_indent = len(next_line) - len(next_line.lstrip())
                        # If next line is at same or lower indent, we're at the right place
                        if next_indent <= expected_indent or "status:" in next_line:
                            validation_yaml = format_validation_yaml(validations, expected_indent)
                            result_lines.append(validation_yaml)
                            found_type_object = True
                            in_spec = False

        return "\n".join(result_lines)

    return doc


def main():
    if len(sys.argv) < 2:
        print("Usage: inject-cel-validations.py <crd-file.yaml>")
        sys.exit(1)

    crd_file = Path(sys.argv[1])

    if not crd_file.exists():
        print(f"Error: File not found: {crd_file}")
        sys.exit(1)

    content = crd_file.read_text()

    # Check if already processed
    if "x-kubernetes-validations:" in content:
        print(f"CEL validations already present in {crd_file}")
        return

    result = inject_validations(content)
    crd_file.write_text(result)

    print(f"Injected CEL validations into {crd_file}")


if __name__ == "__main__":
    main()
