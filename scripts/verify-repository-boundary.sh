#!/usr/bin/env bash
set -euo pipefail

tracked_paths="$(git ls-files)"
if [[ -z "${tracked_paths}" ]]; then
    echo "Repository boundary check passed: no tracked paths yet."
    exit 0
fi

forbidden_path_pattern='(^|/)(docs?|documentation|ai|agents?|prompts?|instructions?|secrets?|signing|credentials?)(/|$)|(^|/)\.(superpowers|codex|agents)(/|$)|(^|/)(identity(-documents)?|ktp|passport)(\.[^/]*)?(/|$)|(^|/)(\.env[^/]*|local\.properties|keystore\.properties|google-services\.json|credentials?\.[^/]*)$|(^|/)(AGENTS|CLAUDE|GEMINI|CODEX|COPILOT|cursor-rules|copilot-instructions)\.md$|(^|/)(.*\.(prompt|prompts)|prompt|prompts|instructions)\.(md|txt)$|(^|/)([^/]+\.(pem|p12|jks|keystore)|.*\.ktp\..*|.*\.passport\..*|.*\.identity\..*)$'
forbidden_paths="$(printf '%s\n' "${tracked_paths}" | grep -E -i "${forbidden_path_pattern}" || true)"
if [[ -n "${forbidden_paths}" ]]; then
    echo "Forbidden tracked path(s):" >&2
    printf '%s\n' "${forbidden_paths}" >&2
    exit 1
fi

forbidden_text="$(git grep -I -n -E -i '(^|[^[:alnum:]])co-authored-by([:[:space:]]|$)|generated[- ]by[- ]ai|ai[- ]generated|-----BEGIN ([A-Z]+ )?PRIVATE KEY-----' -- . ':(exclude)scripts/verify-repository-boundary.sh' || true)"
if [[ -n "${forbidden_text}" ]]; then
    echo "Forbidden attribution or agent text found in tracked content:" >&2
    printf '%s\n' "${forbidden_text}" >&2
    exit 1
fi

echo "Repository boundary check passed."
