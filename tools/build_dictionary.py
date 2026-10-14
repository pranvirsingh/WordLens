"""Build WordLens's offline dictionary (a compact SQLite file) from WordNet 3.0.

Usage:
    py -I tools/build_dictionary.py <path-to-wordnet-dict-dir> app/src/main/assets/dictionary/wordnet.db

The input directory is the standard WordNet 3.0 database distribution
(index.noun, data.noun, ..., noun.exc, ...). The output schema is what
`DictionaryDatabase.kt` queries:

    lemma_senses(lemma, pos, rank, tag_count, synset_id)  -- one row per word sense
    synsets(id, pos, definition, examples, lemmas)
    exceptions(form, pos, base)                -- irregular forms, e.g. went -> go

Senses keep WordNet's own ordering (rank 0 is the most common sense), and
tag_count is how often that sense appeared in WordNet's tagged corpus — the
app uses it to decide which reading of a word (e.g. "saw" the tool vs. the
past tense of "see") to show first.
"""

import os
import re
import sqlite3
import sys

POS_FILES = {"n": "noun", "v": "verb", "a": "adj", "r": "adv"}
# Adjective "satellites" are ordinary adjectives as far as a reader cares.
SS_TYPE_TO_POS = {"n": "n", "v": "v", "a": "a", "s": "a", "r": "r"}
# index.sense encodes the synset type as a digit after the '%'.
SENSE_KEY_POS = {"1": "n", "2": "v", "3": "a", "4": "r", "5": "a"}
ADJ_MARKER = re.compile(r"\((a|p|ip)\)$")
EXAMPLE = re.compile(r'"([^"]*)"')
# WordNet writes inner quotes TeX-style (`to'); show them as real quote marks.
BACKTICK_QUOTE = re.compile(r"`([^`']*)'")
MAX_EXAMPLES = 2


def parse_gloss(gloss):
    """Split a WordNet gloss into (definition, [examples])."""
    gloss = BACKTICK_QUOTE.sub(r"‘\1’", gloss.strip())
    quote = gloss.find('"')
    if quote == -1:
        return gloss.rstrip("; ").strip(), []
    definition = gloss[:quote].rstrip("; ").strip()
    examples = [e.strip() for e in EXAMPLE.findall(gloss[quote:]) if e.strip()]
    return definition, examples[:MAX_EXAMPLES]


def clean_lemma(raw):
    return ADJ_MARKER.sub("", raw).replace("_", " ").strip()


def read_data_file(path, pos_name):
    """Yield (synset_key, pos, definition, examples, lemmas) for every synset."""
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("  "):  # license header
                continue
            head, _, gloss = line.partition("|")
            fields = head.split()
            offset, ss_type = fields[0], fields[2]
            word_count = int(fields[3], 16)
            lemmas = []
            for i in range(word_count):
                lemma = clean_lemma(fields[4 + 2 * i])
                if lemma not in lemmas:
                    lemmas.append(lemma)
            definition, examples = parse_gloss(gloss)
            yield (pos_name, offset), SS_TYPE_TO_POS[ss_type], definition, examples, lemmas


def read_index_file(path):
    """Yield (lemma, [synset offsets in frequency order]) for every index line."""
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("  "):
                continue
            fields = line.split()
            lemma = fields[0].replace("_", " ")
            synset_count = int(fields[2])
            pointer_count = int(fields[3])
            # lemma pos synset_cnt p_cnt [ptr...] sense_cnt tagsense_cnt offsets...
            offsets_start = 4 + pointer_count + 2
            yield lemma, fields[offsets_start:offsets_start + synset_count]


def read_tag_counts(path):
    """Map (lemma, pos, synset offset) -> how often that sense was seen in real text."""
    counts = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            sense_key, offset, _sense_number, tag_count = line.split()
            lemma, _, rest = sense_key.partition("%")
            key = (lemma.replace("_", " "), SENSE_KEY_POS[rest[0]], offset)
            counts[key] = counts.get(key, 0) + int(tag_count)
    return counts


def read_exceptions(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) >= 2:
                form = parts[0].replace("_", " ")
                for base in parts[1:]:
                    yield form, base.replace("_", " ")


def build(wordnet_dir, out_path):
    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    db = sqlite3.connect(out_path)
    db.executescript(
        """
        CREATE TABLE synsets(
            id INTEGER PRIMARY KEY,
            pos TEXT NOT NULL,
            definition TEXT NOT NULL,
            examples TEXT NOT NULL,
            lemmas TEXT NOT NULL
        );
        CREATE TABLE lemma_senses(
            lemma TEXT NOT NULL,
            pos TEXT NOT NULL,
            rank INTEGER NOT NULL,
            tag_count INTEGER NOT NULL,
            synset_id INTEGER NOT NULL
        );
        CREATE TABLE exceptions(
            form TEXT NOT NULL,
            pos TEXT NOT NULL,
            base TEXT NOT NULL
        );
        """
    )

    synset_ids = {}
    for pos, name in POS_FILES.items():
        for key, real_pos, definition, examples, lemmas in read_data_file(
            os.path.join(wordnet_dir, f"data.{name}"), name
        ):
            synset_id = len(synset_ids) + 1
            synset_ids[key] = synset_id
            db.execute(
                "INSERT INTO synsets VALUES (?, ?, ?, ?, ?)",
                (synset_id, real_pos, definition, "\n".join(examples), "\n".join(lemmas)),
            )

    tag_counts = read_tag_counts(os.path.join(wordnet_dir, "index.sense"))
    for pos, name in POS_FILES.items():
        for lemma, offsets in read_index_file(os.path.join(wordnet_dir, f"index.{name}")):
            for rank, offset in enumerate(offsets):
                db.execute(
                    "INSERT INTO lemma_senses VALUES (?, ?, ?, ?, ?)",
                    (lemma, pos, rank, tag_counts.get((lemma, pos, offset), 0), synset_ids[(name, offset)]),
                )
        for form, base in read_exceptions(os.path.join(wordnet_dir, f"{name}.exc")):
            db.execute("INSERT INTO exceptions VALUES (?, ?, ?)", (form, pos, base))

    db.executescript(
        """
        CREATE INDEX idx_lemma_senses ON lemma_senses(lemma, pos);
        CREATE INDEX idx_exceptions ON exceptions(form);
        PRAGMA user_version = 1;
        """
    )
    db.commit()
    db.execute("VACUUM")
    db.close()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
    print(f"wrote {sys.argv[2]} ({os.path.getsize(sys.argv[2]) // 1024} KB)")
