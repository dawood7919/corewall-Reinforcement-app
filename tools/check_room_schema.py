#!/usr/bin/env python3
"""
يتأكد إن الفهارس اللي الترحيلات بتعملها = الفهارس اللي Room متوقّعها.

ليه السكربت ده موجود:
Room بيقارن المخطط المتولّد من الـ`@Entity` بالقاعدة الحقيقية بعد كل ترحيل،
وبيرمي `IllegalStateException` لو فيه أي اختلاف — حتى لو الاختلاف فهرس واحد
زيادة. النتيجة على الجهاز إن التطبيق بيقفل بعد ثانيتين من الفتح، من غير أي
رسالة تفهّم المستخدم حصل إيه. والغلطة دي مستحيل الكومبايلر يمسكها لأن
الترحيلات مجرد نصّ SQL جوّه `execSQL`.

فالفحص هنا بيقرا الاتنين ويقارنهم كمجموعتين:
  • الفهارس المعلَنة في المخطط المصدَّر (`app/schemas/<db>/<version>.json`)
  • الفهارس اللي أي ترحيل بيعملها بـ`CREATE INDEX`

لازم يبقوا متطابقين بالظبط:
  فهرس في الكيان ومش في الترحيل  → المستخدمين القدام هيقع عندهم التطبيق.
  فهرس في الترحيل ومش في الكيان  → نفس الحكاية، بالعكس.

بيتشغّل في الـCI بعد البناء وقبل نشر الـAPK.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
SCHEMA_DIR = REPO / "app" / "schemas"
MIGRATIONS = REPO / "app/src/main/java/com/corewall/qaqc/data/db/AppDatabase.kt"

# CREATE [UNIQUE] INDEX [IF NOT EXISTS] `name` ON `table` (`col`, `col`)
CREATE_INDEX = re.compile(
    r"CREATE\s+(?P<unique>UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?"
    r"[`\"]?(?P<name>\w+)[`\"]?\s+ON\s+[`\"]?(?P<table>\w+)[`\"]?\s*"
    r"\((?P<cols>[^)]*)\)",
    re.IGNORECASE,
)


def normalise(unique: bool, name: str, table: str, cols) -> tuple:
    """شكل واحد للفهرس عشان المقارنة تبقى على المعنى مش على الكتابة."""
    if isinstance(cols, str):
        cols = [c.strip().strip("`\"") for c in cols.split(",")]
    return (table, name, tuple(cols), bool(unique))


def latest_schema() -> pathlib.Path:
    """أعلى نسخة مصدَّرة — دي اللي التطبيق شغّال عليها دلوقتي."""
    files = list(SCHEMA_DIR.glob("*/*.json"))
    if not files:
        raise SystemExit(
            f"✗ مفيش مخطط مصدَّر في {SCHEMA_DIR}.\n"
            "  المفروض `exportSchema = true` على @Database و"
            "`ksp { arg(\"room.schemaLocation\", ...) }` في build.gradle.kts،\n"
            "  والفحص ده يجري **بعد** البناء."
        )
    return max(files, key=lambda p: int(p.stem))


def from_schema(path: pathlib.Path) -> set[tuple]:
    data = json.loads(path.read_text(encoding="utf-8"))
    found = set()
    for entity in data["database"]["entities"]:
        table = entity["tableName"]
        for idx in entity.get("indices", []):
            found.add(
                normalise(idx.get("unique", False), idx["name"], table, idx["columnNames"])
            )
    return found


# نصّين ملزوقين بـ`+` في Kotlin: `"…" +\n    "…"`. بيتشالوا قبل البحث.
KOTLIN_CONCAT = re.compile(r'"\s*\+\s*"')


def join_literals(src: str) -> str:
    """
    بيلزق النصوص المقسومة على أكتر من سطر في Kotlin.

    من غير ده، `CREATE INDEX … ` + `ON …` مكتوبة على سطرين مابتتشافش —
    والفحص بيقول إن الترحيل مش بيعمل الفهرس وهو بيعمله. الاتجاه العكسي
    أخطر: فهرس **بيتعمل** في ترحيل ومش معلَن على الكيان بيعدّي من غير ما
    حد ياخد باله، والتطبيق بيقفل عند المستخدم.
    """
    return KOTLIN_CONCAT.sub("", src)


def from_migrations(path: pathlib.Path) -> set[tuple]:
    src = join_literals(path.read_text(encoding="utf-8"))
    return {
        normalise(bool(m.group("unique")), m.group("name"), m.group("table"), m.group("cols"))
        for m in CREATE_INDEX.finditer(src)
    }


def describe(idx: tuple) -> str:
    table, name, cols, unique = idx
    return f"    {table}.{name} ({', '.join(cols)}){' UNIQUE' if unique else ''}"


def main() -> int:
    schema_file = latest_schema()
    declared = from_schema(schema_file)
    created = from_migrations(MIGRATIONS)

    if declared == created:
        print(f"✓ فهارس Room متطابقة ({len(declared)}) — {schema_file.relative_to(REPO)}")
        for idx in sorted(declared):
            print(describe(idx))
        return 0

    print(f"✗ المخطط والترحيلات مش متطابقين — {schema_file.relative_to(REPO)}\n")
    missing = declared - created
    extra = created - declared
    if missing:
        print("  معلَن على الكيان ومفيش ترحيل بيعمله")
        print("  (المستخدم اللي بيحدّث هيقع عنده التطبيق أول ما يفتح):")
        for idx in sorted(missing):
            print(describe(idx))
        print()
    if extra:
        print("  الترحيل بيعمله ومش معلَن على الكيان")
        print("  (نفس النتيجة — Room بيرفض أي فهرس زيادة):")
        for idx in sorted(extra):
            print(describe(idx))
        print()
    print(
        "  الحل: عرّف الفهرس بـ`@Entity(indices = [Index(...)])` وسيب Room يسمّيه،\n"
        "  وخلّي جملة `CREATE INDEX` في الترحيل تستخدم نفس الاسم بالحرف\n"
        "  (`index_<جدول>_<عمود>_<عمود>`)."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
