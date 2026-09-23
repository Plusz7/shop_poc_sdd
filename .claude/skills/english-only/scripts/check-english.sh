#!/usr/bin/env bash
# Flags Polish text in repository files (diacritics and common Polish domain words).
# Usage: check-english.sh [paths...]   (no args = files changed vs HEAD, incl. untracked)
# Exit code: 0 = clean, 1 = findings.
set -u
export LC_ALL=C.UTF-8   # grep -P must match characters, not bytes (→, – would hit ą, ł)

# Locale-only translation files and seed data may legitimately contain Polish.
EXCLUDE='(^|/)(pl|pl-PL)\.json$|(^|/)i18n/pl/|(^|/)seed/|node_modules/|target/|dist/|\.git/|\.claude/skills/english-only/'

DIACRITICS='[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]'
# Polish words without diacritics that tend to leak into identifiers and prose.
WORDS='\b([Kk]oszyk\w*|[Zz]amowieni\w*|[Pp]latnos\w*|[Kk]atalog(?!ue)\w*|[Rr]ealizacj\w*|[Pp]ozycj\w*|[Ii]losc\w*|[Kk]wot\w*|[Ss]um[ay]\b|[Cc]en[ay]\b|[Pp]rodukt\w*|[Kk]ategori\w*|[Kk]lient\w*|[Zz]darzeni\w*|[Mm]etryk\w*|[Dd]odaj\w*|[Uu]sun\w*|[Pp]obierz\w*|[Zz]apisz\w*|[Pp]owinien\w*|[Zz]adani[ae]\b|[Ww]ymagani\w*|[Dd]ostepnos\w*|[Zz]wrot\w*)'

if [ "$#" -eq 0 ]; then
  mapfile -t files < <( { git diff --name-only HEAD; git ls-files --others --exclude-standard; } | sort -u )
else
  mapfile -t files < <(for p in "$@"; do
    if [ -d "$p" ]; then git ls-files --cached --others --exclude-standard -- "$p"; else echo "$p"; fi
  done | sort -u)
fi

found=0
for f in "${files[@]}"; do
  [ -f "$f" ] || continue
  echo "$f" | grep -Eq "$EXCLUDE" && continue
  grep -Iq . "$f" 2>/dev/null || continue   # skip binaries / empty files
  if echo "$f" | grep -Pq "$DIACRITICS|$WORDS"; then
    echo "$f: [file name] Polish file name"; found=1
  fi
  if out=$(grep -nP "$DIACRITICS|$WORDS" "$f"); then
    echo "$out" | head -20 | while IFS= read -r line; do printf '%s:%s\n' "$f" "$line"; done
    n=$(echo "$out" | wc -l)
    [ "$n" -gt 20 ] && echo "$f: ... $((n - 20)) more"
    found=1
  fi
done

if [ "$found" -eq 0 ]; then echo "OK: no Polish text found in ${#files[@]} file(s)."; fi
exit "$found"
